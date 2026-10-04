package fr.pouik.audit.photos

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import fr.pouik.audit.donnees.cheminRelatif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tout ce qui touche aux fichiers photo, c'est-à-dire à MediaStore.
 *
 * Les clichés sont écrits dans `Pictures/Audits/<dossier>` : un vrai dossier,
 * visible en USB et dans l'explorateur de fichiers, avec un album par audit au
 * lieu de trente photos noyées dans la pellicule.
 *
 * Aucune permission de stockage n'est demandée : depuis Android 10, une app
 * écrit où elle veut dans les collections partagées et relit ensuite ce qu'elle
 * a écrit. La contrepartie, à connaître : après une désinstallation, l'app perd
 * la propriété de ces entrées et ne sait plus les relire. Les fichiers sont
 * toujours là — l'app repart simplement d'un catalogue vide.
 */
class Mediatheque(private val contexte: Context) {

    private val resolveur get() = contexte.contentResolver

    /**
     * Les métadonnées à passer à CameraX pour qu'il écrive au bon endroit.
     *
     * C'est CameraX qui crée l'entrée et lève le drapeau IS_PENDING pendant
     * l'écriture : dupliquer ce travail ici donnerait deux entrées pour un seul
     * fichier, dont une vide.
     */
    fun valeurs(dossier: String, fichier: String): ContentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fichier)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, cheminRelatif(dossier))
    }

    /** Le nom de fichier réellement retenu par MediaStore, qui a pu suffixer. */
    suspend fun nomReel(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            resolveur.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Renomme un cliché, et renvoie le nom finalement porté par le fichier.
     *
     * Changer DISPLAY_NAME renomme le fichier sur le disque, MediaStore s'en
     * charge. En cas d'échec — collision, fichier disparu — on renvoie le nom
     * actuel plutôt que de faire échouer la saisie d'une légende : la légende
     * est dans le catalogue, c'est elle qui compte.
     */
    suspend fun renomme(uri: Uri, nouveau: String): String? = withContext(Dispatchers.IO) {
        try {
            val valeurs = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, nouveau)
            }
            if (resolveur.update(uri, valeurs, null, null) > 0) nouveau else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun supprime(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            resolveur.delete(uri, null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Déplace des clichés vers un autre dossier d'audit.
     *
     * Sert au renommage d'un audit : le dossier ne suit pas le nom tout seul,
     * parce qu'un dossier qui change de nom sous les pieds de l'explorateur de
     * fichiers surprend plus qu'il n'aide. Quand l'utilisateur le demande
     * explicitement, en revanche, les photos doivent suivre.
     *
     * Renvoie le nombre de clichés effectivement déplacés : un déplacement
     * partiel est possible (fichier supprimé entre-temps, collision de nom), et
     * l'appelant doit pouvoir le dire au lieu d'afficher une réussite.
     */
    suspend fun deplace(uris: List<Uri>, versDossier: String): Int = withContext(Dispatchers.IO) {
        var deplacees = 0
        for (uri in uris) {
            try {
                val valeurs = ContentValues().apply {
                    put(MediaStore.Images.Media.RELATIVE_PATH, cheminRelatif(versDossier))
                }
                if (resolveur.update(uri, valeurs, null, null) > 0) deplacees++
            } catch (e: Exception) {
                // Un cliché qui refuse de bouger n'empêche pas les suivants.
            }
        }
        deplacees
    }

    /** Vrai si le fichier existe encore et nous est lisible. */
    suspend fun lisible(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            resolveur.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
