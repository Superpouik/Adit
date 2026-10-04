package fr.pouik.audit.photos

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.recapitulatif
import fr.pouik.audit.donnees.slug
import java.io.File

/**
 * Envoyer un audit à quelqu'un : les photos, le récapitulatif, ou le chemin du
 * dossier dans le presse-papiers.
 */

/**
 * Partage les clichés d'un audit, récapitulatif dans le corps du message.
 *
 * Le type reste `image/jpeg` et non un type passe-partout : y glisser le fichier texte ferait
 * basculer le type commun, et plusieurs applications de messagerie refusent
 * alors d'afficher les images en aperçu. Les légendes voyagent dans le texte,
 * là où tout le monde les lira.
 */
fun partagePhotos(contexte: Context, audit: Audit): Boolean {
    if (audit.photos.isEmpty()) return false
    val uris = ArrayList(audit.photos.sortedBy { it.numero }.map { Uri.parse(it.uri) })
    val envoi = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/jpeg"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        putExtra(Intent.EXTRA_SUBJECT, "Audit — ${audit.nom}")
        putExtra(Intent.EXTRA_TEXT, recapitulatif(audit))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return lance(contexte, envoi, "Envoyer les photos")
}

/** Partage le récapitulatif seul, en pièce jointe texte. */
fun partageRecapitulatif(contexte: Context, audit: Audit): Boolean {
    val fichier = try {
        val dossier = File(contexte.cacheDir, "partage").apply { mkdirs() }
        File(dossier, "audit-${slug(audit.nom).ifEmpty { "sans-nom" }}.txt").apply {
            writeText(recapitulatif(audit))
        }
    } catch (e: Exception) {
        return false
    }
    val uri = try {
        FileProvider.getUriForFile(contexte, "${contexte.packageName}.fichiers", fichier)
    } catch (e: Exception) {
        return false
    }
    val envoi = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Audit — ${audit.nom}")
        putExtra(Intent.EXTRA_TEXT, recapitulatif(audit))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return lance(contexte, envoi, "Envoyer le récapitulatif")
}

/** Partage un seul cliché. */
fun partageUnePhoto(contexte: Context, uri: String, legende: String): Boolean {
    val envoi = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        if (legende.isNotBlank()) putExtra(Intent.EXTRA_TEXT, legende)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return lance(contexte, envoi, "Envoyer la photo")
}

fun copie(contexte: Context, texte: String): Boolean = try {
    val presse = contexte.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    presse.setPrimaryClip(ClipData.newPlainText("Audit", texte))
    true
} catch (e: Exception) {
    false
}

/**
 * Un téléphone sans application capable de recevoir l'envoi ferait planter
 * `startActivity` : on renvoie false, l'écran affiche un message.
 */
private fun lance(contexte: Context, envoi: Intent, titre: String): Boolean = try {
    contexte.startActivity(Intent.createChooser(envoi, titre))
    true
} catch (e: Exception) {
    false
}
