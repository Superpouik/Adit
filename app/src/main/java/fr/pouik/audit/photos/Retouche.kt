package fr.pouik.audit.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import fr.pouik.audit.donnees.Forme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * La retouche des clichés : garder l'original, produire la version annotée.
 *
 * Le fichier visible dans `Pictures/Audits` est toujours celui qu'on enverra au
 * client — annotations comprises, puisque personne ne lira notre JSON. Mais une
 * annotation doit rester corrigible des semaines plus tard : l'original part donc dans
 * le stockage privé de l'app au moment de la première retouche, et c'est lui qu'on
 * réannote à chaque fois. Réappliquer les formes sur une image déjà annotée
 * empilerait les rectangles les uns sur les autres.
 *
 * Contrepartie assumée : une photo retouchée occupe deux fois sa place sur le
 * téléphone.
 */
class Retouche(private val contexte: Context) {

    private val dossier = File(contexte.filesDir, "originaux")

    fun original(cle: String): File = File(dossier, "$cle.jpg")

    /**
     * Met l'original de côté s'il ne l'est pas déjà, et renvoie le fichier.
     *
     * Appelé avant la première écriture annotée, jamais après : le second appel
     * copierait la version annotée par-dessus l'original.
     */
    suspend fun metDeCote(uri: Uri, cle: String): File? = withContext(Dispatchers.IO) {
        val cible = original(cle)
        if (cible.exists() && cible.length() > 0) return@withContext cible
        try {
            dossier.mkdirs()
            contexte.contentResolver.openInputStream(uri)?.use { entree ->
                cible.outputStream().use { sortie -> entree.copyTo(sortie) }
            } ?: return@withContext null
            cible
        } catch (e: Exception) {
            cible.delete()
            null
        }
    }

    suspend fun oublieOriginal(cle: String) = withContext(Dispatchers.IO) {
        original(cle).delete()
        Unit
    }

    /**
     * Charge une version réduite pour l'éditeur.
     *
     * Un cliché de douze mégapixels occupe 48 Mo une fois décodé : trois photos
     * ouvertes d'affilée suffiraient à faire tomber l'app. L'éditeur n'a de toute façon
     * jamais plus de deux mille pixels à afficher — les coordonnées des annotations
     * étant normalisées, travailler sur l'aperçu n'enlève rien à la précision du
     * rendu final.
     */
    suspend fun apercu(source: SourceImage, cotePreferre: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ouvre(source)?.use { BitmapFactory.decodeStream(it, null, dimensions) }
                val plusGrandCote = maxOf(dimensions.outWidth, dimensions.outHeight)
                if (plusGrandCote <= 0) return@withContext null

                val options = BitmapFactory.Options().apply {
                    inSampleSize = echantillon(plusGrandCote, cotePreferre)
                }
                val brut = ouvre(source)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: return@withContext null
                redresse(brut, source)
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Écrit la version annotée dans l'entrée MediaStore [cible], à partir de l'original.
     *
     * Renvoie false si l'image n'a pas pu être écrite — l'appelant doit alors garder
     * les annotations dans le catalogue sans prétendre que le fichier est à jour.
     */
    suspend fun applique(source: SourceImage, cible: Uri, formes: List<Forme>): Boolean =
        withContext(Dispatchers.IO) {
            val image = decodeModifiable(source) ?: return@withContext false
            try {
                // Les formes se lisent dans le repère normalisé : le même JSON donne le
                // même résultat sur l'aperçu de 2000 px et sur l'original de 4000.
                Canvas(image).also { toile ->
                    Rendu.dessine(
                        toile,
                        formes,
                        image.width.toFloat(),
                        image.height.toFloat(),
                        // Le flou prélève ses pixels dans l'image qu'il recouvre ; il
                        // doit les lire avant qu'on ne dessine par-dessus, d'où la
                        // copie.
                        source = image.copy(Bitmap.Config.ARGB_8888, false),
                    )
                }
                contexte.contentResolver.openOutputStream(cible, "wt")?.use { sortie ->
                    image.compress(Bitmap.CompressFormat.JPEG, 92, sortie)
                } ?: return@withContext false
                true
            } catch (e: Exception) {
                false
            } catch (e: OutOfMemoryError) {
                false
            } finally {
                image.recycle()
            }
        }

    /** Remet l'original dans l'entrée MediaStore : l'annulation de toutes les retouches. */
    suspend fun restaure(cle: String, cible: Uri): Boolean = withContext(Dispatchers.IO) {
        val fichier = original(cle)
        if (!fichier.exists()) return@withContext false
        try {
            contexte.contentResolver.openOutputStream(cible, "wt")?.use { sortie ->
                fichier.inputStream().use { it.copyTo(sortie) }
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun decodeModifiable(source: SourceImage): Bitmap? {
        var essai = 1
        while (essai <= 4) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = essai
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = true
                }
                val brut = ouvre(source)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: return null
                return redresse(brut, source)
            } catch (e: OutOfMemoryError) {
                // Plutôt une photo de moitié moins large qu'une annotation perdue :
                // on réessaie en divisant, jusqu'à quatre fois.
                essai *= 2
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    /**
     * Applique l'orientation EXIF au bitmap.
     *
     * L'appareil photo n'écrit pas une image tournée : il écrit l'image telle que la
     * voit le capteur et note la rotation à côté. BitmapFactory ignore cette note —
     * sans ce redressement, une photo prise en paysage s'annote couchée, et les
     * annotations se retrouvent à 90° du sujet.
     *
     * Le fichier réécrit perd son EXIF, ce qui est voulu : l'image étant désormais
     * droite, une consigne de rotation la ferait tourner une seconde fois.
     */
    private fun redresse(image: Bitmap, source: SourceImage): Bitmap {
        val rotation = try {
            ouvre(source)?.use { flux ->
                when (ExifInterface(flux).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (rotation == 0f) return image
        return try {
            val matrice = Matrix().apply { postRotate(rotation) }
            val tourne = Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrice, true)
            if (tourne !== image) image.recycle()
            tourne
        } catch (e: OutOfMemoryError) {
            image
        }
    }

    private fun ouvre(source: SourceImage) = try {
        when (source) {
            is SourceImage.Fichier -> source.fichier.inputStream()
            is SourceImage.Entree -> contexte.contentResolver.openInputStream(source.uri)
        }
    } catch (e: Exception) {
        null
    }

    private fun echantillon(cote: Int, voulu: Int): Int {
        var facteur = 1
        while (cote / (facteur * 2) >= voulu) facteur *= 2
        return facteur
    }
}

/** D'où vient l'image à retoucher : l'original mis de côté, ou l'entrée MediaStore. */
sealed interface SourceImage {
    data class Fichier(val fichier: File) : SourceImage
    data class Entree(val uri: Uri) : SourceImage
}
