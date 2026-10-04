package fr.pouik.audit.donnees

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val JOUR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
private val JOUR_COURT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
private val HEURE = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH)

fun date(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    JOUR.format(Instant.ofEpochMilli(ms).atZone(zone))

fun dateCourte(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    JOUR_COURT.format(Instant.ofEpochMilli(ms).atZone(zone))

fun heure(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    HEURE.format(Instant.ofEpochMilli(ms).atZone(zone))

/** « 12 photos », « 1 photo », « aucune photo » — le pluriel compte à l'œil. */
fun comptePhotos(n: Int): String = when (n) {
    0 -> "aucune photo"
    1 -> "1 photo"
    else -> "$n photos"
}

/**
 * Récapitulatif texte d'un audit, destiné au corps du mail ou au fichier joint.
 *
 * Il doit se lire par quelqu'un qui n'a pas l'app : d'où les légendes en clair
 * à côté des noms de fichiers, seule façon de relier une pièce jointe à ce
 * qu'elle montre.
 */
fun recapitulatif(audit: Audit, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
    appendLine("AUDIT — ${audit.nom}")
    if (audit.lieu.isNotBlank()) appendLine("Lieu : ${audit.lieu}")
    appendLine("Créé le ${date(audit.creeLe, zone)}")
    appendLine("Dossier : ${cheminRelatif(audit.dossier)}")
    appendLine(comptePhotos(audit.photos.size).replaceFirstChar { it.uppercase() })
    if (audit.notes.isNotBlank()) {
        appendLine()
        appendLine("NOTES")
        appendLine(audit.notes.trim())
    }
    if (audit.photos.isNotEmpty()) {
        appendLine()
        appendLine("PHOTOS")
        audit.photos.sortedBy { it.numero }.forEach { p ->
            val legende = p.legende.ifBlank { "(sans légende)" }
            appendLine("${p.fichier} — $legende — ${heure(p.priseLe, zone)}")
        }
    }
}
