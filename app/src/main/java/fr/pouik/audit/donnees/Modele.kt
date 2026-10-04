package fr.pouik.audit.donnees

import kotlinx.serialization.Serializable
import java.text.Normalizer

/**
 * Une photo d'audit.
 *
 * `uri` est l'entrée MediaStore : c'est elle qui compte, le fichier n'existe
 * pas ailleurs. `fichier` n'est gardé que pour l'affichage et le
 * récapitulatif — MediaStore peut avoir ajouté un suffixe au nom demandé.
 */
@Serializable
data class Photo(
    val uri: String,
    val fichier: String,
    val numero: Int,
    val legende: String = "",
    val priseLe: Long,
    /**
     * Les annotations, gardées en clair plutôt que seulement gravées dans le JPEG :
     * c'est ce qui permet de rouvrir l'éditeur dans six mois et de déplacer un
     * rectangle au lieu de tout refaire. L'original correspondant dort dans le
     * stockage privé de l'app (voir Retouche).
     */
    val annotations: List<Forme> = emptyList(),
    /** L'écran auquel ce cliché se rattache ; nul quand il concerne le site entier. */
    val ecranId: String? = null,
    /** La case du PV que ce cliché honore, s'il en honore une (voir Exigence). */
    val exigence: String? = null,
    /**
     * Incrémenté à chaque réécriture du fichier.
     *
     * L'URI MediaStore ne change pas quand on réécrit le JPEG annoté : sans ce
     * numéro dans la clé de cache, Coil continuerait d'afficher la version
     * d'avant, et on croirait l'enregistrement raté.
     */
    val version: Int = 0,
)

/**
 * Clé d'un cliché pour le stockage de son original.
 *
 * Bâtie sur l'audit et le numéro plutôt que sur l'URI MediaStore : l'URI peut changer
 * (fichier recréé, média réindexé) alors que le couple audit/numéro, lui, ne bouge
 * jamais — les numéros n'étant jamais réutilisés.
 */
fun Photo.cle(idAudit: String): String = "$idAudit-${numero.toString().padStart(3, '0')}"

/**
 * Un audit : une fiche et un dossier de photos.
 *
 * `dossier` est figé à la création et ne suit pas les renommages du `nom` :
 * les fichiers déjà écrits ne bougent pas, et un dossier qui change de nom
 * sous les pieds de l'explorateur de fichiers est une mauvaise surprise.
 * Le renommage du dossier est une action explicite (voir Mediatheque.deplace).
 */
@Serializable
data class Audit(
    val id: String,
    val nom: String,
    val lieu: String = "",
    val notes: String = "",
    val creeLe: Long,
    val dossier: String,
    val photos: List<Photo> = emptyList(),
    /** Les emplacements d'écran relevés, deux au maximum (limite du contrat). */
    val ecrans: List<Ecran> = emptyList(),
    /** Nécessité d'une nacelle pour le câblage ; le PV veut une réponse explicite. */
    val nacelle: Boolean? = null,
    /** Hauteur à laquelle poser les prérequis, demandée dès qu'une nacelle est requise. */
    val hauteurPrerequis: String = "",
    /** « Durée estimée de l'installation du site (à mentionner obligatoirement) ». */
    val dureeInstallation: String = "",
    /** Le gérant ou son adjoint, dont le PV exige la présence et le nom. */
    val interlocuteur: String = "",
    /** Horaires, accès, sécurité : la case « contraintes éventuelles » du PV. */
    val contraintes: String = "",
    /**
     * Numéro du prochain cliché. Jamais décrémenté : supprimer la photo 003
     * puis en prendre une autre doit donner 004, sinon deux fichiers portent
     * le même nom et MediaStore tranche à notre place en ajoutant « (1) ».
     */
    val compteur: Int = 1,
)

@Serializable
data class Catalogue(val audits: List<Audit> = emptyList())

/** Chemin relatif du dossier d'un audit, tel que MediaStore l'attend. */
fun cheminRelatif(dossier: String): String = "$RACINE/$dossier"

const val RACINE = "Pictures/Audits"

/**
 * Transforme un texte libre en fragment de nom de fichier sûr.
 *
 * Les accents sont décomposés puis leurs diacritiques jetés : « Allée » donne
 * « Allee » et non « All e ». Tout ce qui n'est ni lettre ni chiffre devient un
 * tiret, et les tirets ne s'accumulent pas. La casse est conservée — ces noms
 * se lisent dans un explorateur de fichiers.
 */
fun slug(texte: String, maximum: Int = 40): String {
    val sansAccent = Normalizer.normalize(texte, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    val propre = buildString {
        for (c in sansAccent) {
            when {
                c.isLetterOrDigit() && c.code < 128 -> append(c)
                isEmpty() || last() == '-' -> Unit
                else -> append('-')
            }
        }
    }.trim('-')
    if (propre.length <= maximum) return propre
    // Couper au dernier tiret plutôt qu'au milieu d'un mot, sauf si le premier
    // mot dépasse déjà la limite à lui seul.
    val coupe = propre.take(maximum)
    val dernier = coupe.lastIndexOf('-')
    return (if (dernier > maximum / 2) coupe.take(dernier) else coupe).trim('-')
}

/**
 * Nom de dossier pour un audit, garanti différent de ceux déjà pris.
 *
 * Deux magasins peuvent porter le même nom ; deux dossiers ne peuvent pas, ou
 * leurs photos se mélangeraient — exactement ce qu'on cherche à éviter.
 */
fun dossierLibre(nom: String, dejaPris: Set<String>): String {
    val base = slug(nom).ifEmpty { "Audit" }
    if (base !in dejaPris) return base
    var n = 2
    while ("$base-$n" in dejaPris) n++
    return "$base-$n"
}

/**
 * Nom du fichier d'un cliché : préfixé du dossier pour rester parlant une fois
 * la photo sortie de son dossier (jointe à un mail, par exemple), numéroté sur
 * trois chiffres pour que le tri alphabétique soit le tri chronologique.
 */
fun nomPhoto(
    dossier: String,
    numero: Int,
    legende: String = "",
    /**
     * Numéro de l'écran concerné, s'il y en a un.
     *
     * Le préfixe « E1 » / « E2 » groupe les clichés par emplacement dans le tri
     * alphabétique du dossier : celui qui reçoit les photos les parcourt écran par
     * écran, comme le PV les lui demande, et non dans l'ordre où le technicien a
     * tourné dans le magasin. Les vues générales, sans préfixe, passent en tête.
     */
    ecran: Int? = null,
): String {
    val suffixe = slug(legende, maximum = 30)
    val numeroteur = numero.toString().padStart(3, '0')
    val marque = if (ecran != null) "-E$ecran" else ""
    return if (suffixe.isEmpty()) {
        "$dossier$marque-$numeroteur.jpg"
    } else {
        "$dossier$marque-$numeroteur-$suffixe.jpg"
    }
}

/** Les clichés rattachés à un écran donné, dans l'ordre des prises. */
fun Audit.photosDe(ecranId: String?): List<Photo> =
    photos.filter { it.ecranId == ecranId }.sortedBy { it.numero }

/** L'écran que désigne un identifiant, s'il existe encore. */
fun Audit.ecran(id: String?): Ecran? = ecrans.firstOrNull { it.id == id }

/**
 * Ce qui manque encore pour que le PV soit complet.
 *
 * Une exigence est honorée dès qu'un cliché la porte : c'est le technicien qui le
 * déclare au moment de la prise de vue, personne ne peut le deviner à sa place.
 */
fun Audit.manques(): List<Manque> {
    val resultat = mutableListOf<Manque>()
    val cles = { ecranId: String? ->
        photosDe(ecranId).mapNotNull { it.exigence }.toSet()
    }
    ecrans.sortedBy { it.numero }.forEach { ecran ->
        val couvertes = cles(ecran.id)
        ecran.exigences().forEach { exigence ->
            if (exigence.cle !in couvertes) resultat += Manque(exigence, ecran)
        }
    }
    val couvertesSite = cles(null)
    Exigence.pourSite().forEach { exigence ->
        // Les vues générales du magasin n'ont pas de nombre imposé : une seule suffit
        // à lever l'alerte, le document dit « le plus possible », pas « au moins n ».
        if (exigence.cle !in couvertesSite) resultat += Manque(exigence, null)
    }
    // Un site sans aucun écran relevé n'est pas une prévisite finie.
    if (ecrans.isEmpty()) resultat += Manque(null, null)
    return resultat
}

/** Une case du PV encore vide. [ecran] est nul pour ce qui concerne le site entier. */
data class Manque(val exigence: Exigence?, val ecran: Ecran?)
