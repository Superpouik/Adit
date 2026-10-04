package fr.pouik.audit.donnees

import java.util.Locale

/**
 * La légende que le client attend sur chaque photo d'emplacement.
 *
 * Forme reprise de son propre tutoriel : `CRF CITY - TOULOUSE-Muret231 - F/L 32' FIXE`.
 * Quatre informations imposées — enseigne et nom du magasin, rayon, taille de l'écran,
 * type de support — dont trois sont les mêmes pour tous les clichés d'un même écran.
 * D'où le pré-remplissage : elles se saisissent une fois dans la fiche de l'écran, pas
 * à chaque photo.
 *
 * Les parties manquantes sont simplement sautées : un cartouche incomplet posé sur
 * place vaut mieux qu'un cartouche refusé parce que la taille n'est pas encore tranchée.
 */
fun cartouche(audit: Audit, ecran: Ecran?): String {
    val morceaux = mutableListOf<String>()
    abrege(audit.nom).takeIf { it.isNotBlank() }?.let { morceaux += it }
    abregeLieu(audit.lieu).takeIf { it.isNotBlank() }?.let { morceaux += it }

    val fin = buildString {
        if (ecran != null) {
            abregeRayon(ecran.rayon).takeIf { it.isNotBlank() }?.let { append(it) }
            ecran.taille?.let {
                if (isNotEmpty()) append(' ')
                append("$it'")
            }
            ecran.support?.let {
                if (isNotEmpty()) append(' ')
                append(it.abrege)
            }
        }
    }
    if (fin.isNotBlank()) morceaux += fin
    return morceaux.joinToString(" - ")
}

/**
 * « Carrefour City » devient « CRF CITY ».
 *
 * L'abréviation vient du tutoriel client, qui écrit « CRF CITY » là où la fiche dit
 * « Carrefour City ». Tout le reste passe en capitales sans être touché : inventer des
 * abréviations maison pour chaque enseigne ferait exactement ce qu'on veut éviter, des
 * légendes qui ne se ressemblent pas d'un technicien à l'autre.
 */
fun abrege(nom: String): String =
    nom.trim()
        .replace(Regex("(?i)\\bcarrefour\\b"), "CRF")
        .uppercase(Locale.FRENCH)
        .replace(Regex("\\s+"), " ")

/**
 * Le lieu, compacté : « Toulouse, rue Muret 231 » donne « TOULOUSE-MURET231 ».
 *
 * La ville et le repère de rue collés par un tiret, comme dans l'exemple du client.
 * Les mots de liaison d'une adresse (rue, avenue, boulevard…) n'apportent rien dans
 * un cartouche qui doit tenir sur une photo.
 */
fun abregeLieu(lieu: String): String {
    val propre = lieu.trim()
    if (propre.isEmpty()) return ""
    val vides = setOf(
        "rue", "avenue", "av", "boulevard", "bd", "place", "chemin", "route", "allee",
        "allée", "impasse", "quai", "cours", "mail", "de", "du", "des", "la", "le",
        "les", "l", "d",
    )
    val mots = propre
        .split(Regex("[,;]"))
        .map { partie ->
            partie.trim().split(Regex("\\s+"))
                .filter { it.isNotBlank() && it.lowercase(Locale.FRENCH).trim('.') !in vides }
                // La ponctuation d'une initiale saute aussi : « F. Mitterrand » donne
                // « FMITTERRAND », pas « F.MITTERRAND ». Un cartouche doit se lire
                // d'un coup d'œil sur une photo, pas se déchiffrer.
                .joinToString("") { it.replace(Regex("[.'\u2019-]"), "") }
        }
        .filter { it.isNotBlank() }
    return mots.joinToString("-").uppercase(Locale.FRENCH)
}

/**
 * Les rayons, abrégés comme le fait le client.
 *
 * « F/L » pour fruits et légumes est le seul raccourci qu'on lui ait vu employer, et
 * c'est aussi le rayon cité partout dans les règles — c'est l'emplacement préférentiel
 * du premier écran. Les autres rayons passent en capitales, sans raccourci inventé.
 */
fun abregeRayon(rayon: String): String {
    val propre = rayon.trim()
    if (propre.isEmpty()) return ""
    val sansAccent = slug(propre, maximum = 100).lowercase(Locale.FRENCH)
    return when {
        sansAccent.contains("fruit") && sansAccent.contains("legume") -> "F/L"
        else -> propre.uppercase(Locale.FRENCH)
    }
}
