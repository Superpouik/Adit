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

/** « Oui », « Non », ou un tiret quand la case n'a pas encore été tranchée. */
fun ouiNon(valeur: Boolean?): String = when (valeur) {
    true -> "Oui"
    false -> "Non"
    null -> "—"
}

/**
 * Récapitulatif texte d'un audit, destiné au corps du mail ou au fichier joint.
 *
 * Il sert deux lectures. Sur place, la liste des manques : le vrai risque d'une
 * prévisite n'est pas de mal annoter, c'est de repartir du magasin sans un cliché
 * obligatoire. Au bureau, le reste : chaque ligne correspond à une case du PV papier,
 * dans son ordre, pour n'avoir rien à retrouver de mémoire le soir venu.
 */
fun recapitulatif(audit: Audit, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
    appendLine("AUDIT — ${audit.nom}")
    if (audit.lieu.isNotBlank()) appendLine("Lieu : ${audit.lieu}")
    appendLine("Créé le ${date(audit.creeLe, zone)}")
    appendLine("Dossier : ${cheminRelatif(audit.dossier)}")
    appendLine(comptePhotos(audit.photos.size).replaceFirstChar { it.uppercase() })

    val manques = audit.manques()
    if (manques.isNotEmpty()) {
        appendLine()
        appendLine("IL MANQUE ENCORE")
        manques.forEach { manque ->
            val ou = manque.ecran?.let { "Écran ${it.numero}" } ?: "Site"
            if (manque.exigence == null) {
                appendLine("- Aucun emplacement d'écran relevé")
            } else {
                appendLine("- $ou : ${manque.exigence.libelle} — ${manque.exigence.detail}")
            }
        }
    }

    appendLine()
    appendLine("SITE")
    if (audit.interlocuteur.isNotBlank()) appendLine("Interlocuteur : ${audit.interlocuteur}")
    appendLine("Nacelle nécessaire : ${ouiNon(audit.nacelle)}")
    if (audit.hauteurPrerequis.isNotBlank()) {
        appendLine("Hauteur des prérequis : ${audit.hauteurPrerequis}")
    }
    if (audit.dureeInstallation.isNotBlank()) {
        appendLine("Durée estimée de l'installation : ${audit.dureeInstallation}")
    }
    if (audit.contraintes.isNotBlank()) {
        appendLine("Contraintes : ${audit.contraintes.trim()}")
    }
    if (audit.notes.isNotBlank()) {
        appendLine("Notes : ${audit.notes.trim()}")
    }

    audit.ecrans.sortedBy { it.numero }.forEach { ecran ->
        appendLine()
        appendLine("ÉCRAN ${ecran.numero}")
        if (ecran.emplacement.isNotBlank()) appendLine("Emplacement : ${ecran.emplacement}")
        if (ecran.rayon.isNotBlank()) appendLine("Rayon : ${ecran.rayon}")
        appendLine(
            "Taille : ${ecran.taille?.let { "$it\u2033" } ?: "—"} · " +
                (if (ecran.portrait) "Portrait" else "Paysage"),
        )
        appendLine("Support : ${ecran.support?.libelle ?: "—"}")
        if (ecran.natureSurface.isNotBlank()) {
            appendLine("Nature de la surface : ${ecran.natureSurface}")
        }
        appendLine("Prise électrique 24/24 : ${ouiNon(ecran.priseElectrique)}")
        appendLine("Prise RJ45 : ${ouiNon(ecran.priseReseau)}")
        appendLine("Prérequis à 2 m maximum : ${ouiNon(ecran.prerequisProches)}")
        if (ecran.remarques.isNotBlank()) appendLine("Remarques : ${ecran.remarques.trim()}")
        val legende = cartouche(audit, ecran)
        if (legende.isNotBlank()) appendLine("Cartouche : $legende")
        listePhotos(audit, ecran, zone)
    }

    appendLine()
    appendLine("PHOTOS DU SITE")
    listePhotos(audit, null, zone)
}

/**
 * Les clichés d'un écran (ou du site), chacun précédé de la case du PV qu'il honore.
 *
 * Le nom du fichier est répété en clair : le destinataire reçoit des pièces jointes,
 * pas notre application, et c'est la seule chose qui relie une photo à ce qu'elle
 * montre une fois le mail ouvert.
 */
private fun StringBuilder.listePhotos(audit: Audit, ecran: Ecran?, zone: ZoneId) {
    val photos = audit.photosDe(ecran?.id)
    if (photos.isEmpty()) {
        appendLine("(aucune photo)")
        return
    }
    photos.forEach { photo ->
        val role = Exigence.parCle(photo.exigence)?.libelle
        val legende = photo.legende.ifBlank { null }
        val description = listOfNotNull(role, legende).joinToString(" — ").ifBlank { "(sans légende)" }
        appendLine("${photo.fichier} — $description — ${heure(photo.priseLe, zone)}")
    }
}
