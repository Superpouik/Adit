package fr.pouik.audit.donnees

import kotlinx.serialization.Serializable

/**
 * Un emplacement d'écran, tel que le PV de prévisite le demande.
 *
 * Deux au maximum par site : c'est la limite du contrat, pas une limite technique.
 * Chaque champ correspond à une case à remplir sur le PV papier — le but n'est pas de
 * remplacer le document, mais de ne plus avoir à le recopier de tête le soir venu.
 */
@Serializable
data class Ecran(
    val id: String,
    /** 1 ou 2 : c'est ainsi que le PV les désigne, et le destinataire avec. */
    val numero: Int,
    /** Où se trouve l'écran dans le magasin, en clair. */
    val emplacement: String = "",
    /** Le rayon, tel qu'il doit apparaître dans la légende : « Fruits et Légumes ». */
    val rayon: String = "",
    /** 32, 43 ou 50 pouces. Le client demande de favoriser les grandes tailles. */
    val taille: Int? = null,
    val support: Support? = null,
    /** Mur, cloison, poutre, tôle... : le PV demande la nature de la surface. */
    val natureSurface: String = "",
    /**
     * Portrait par défaut, et c'est écrit en capitales dans le PV : « A INSTALLER
     * OBLIGATOIREMENT EN PORTRAIT ». Le champ existe quand même, parce qu'un relevé
     * qui ne peut pas noter une exception ne sert à rien le jour où il y en a une.
     */
    val portrait: Boolean = true,
    val priseElectrique: Boolean? = null,
    val priseReseau: Boolean? = null,
    /** Les prérequis ne doivent pas être à plus de deux mètres de l'écran. */
    val prerequisProches: Boolean? = null,
    val remarques: String = "",
)

/** Les types de support proposés par le PV, dans son ordre. */
@Serializable
enum class Support(val libelle: String, val abrege: String, val suspendu: Boolean) {
    MURAL_FIXE("Mural support fixe", "FIXE", false),
    MURAL_ORIENTABLE("Mural support orientable", "ORIENT", false),
    MURAL_INCLINABLE("Mural support inclinable", "INCLIN", false),
    MAT_PLAFOND("Mât plafond", "MAT", true),
    FILINS("Filins", "FILINS", true),
}

/** Les tailles d'écran du catalogue Philips retenu. */
val TAILLES = listOf(32, 43, 50)

/**
 * Une photo que le PV réclame.
 *
 * Le vrai risque d'une prévisite n'est pas de mal annoter : c'est de repartir du
 * magasin sans un cliché obligatoire, et de devoir y retourner. D'où cette liste,
 * tirée mot pour mot du PV et des règles client.
 */
enum class Exigence(
    val cle: String,
    val libelle: String,
    val detail: String,
    val portee: Portee,
) {
    PLAN_LARGE(
        "plan-large",
        "Plan large",
        "Emplacement de l'écran + prérequis, avec désignation",
        Portee.ECRAN,
    ),
    PLAN_RAPPROCHE(
        "plan-rapproche",
        "Plan rapproché",
        "Emplacement de l'écran + prérequis, avec désignation",
        Portee.ECRAN,
    ),
    ACCROCHE(
        "accroche",
        "Surface d'accroche",
        "Demandée seulement si l'écran est suspendu (filins ou mât)",
        Portee.ECRAN,
    ),
    CONTRAINTES(
        "contraintes",
        "Contraintes",
        "Luminaires, tuyaux, tout ce qui gênera l'installation",
        Portee.ECRAN,
    ),
    PRISES(
        "prises",
        "Prises existantes",
        "Prises réseau et électrique au niveau de l'emplacement",
        Portee.ECRAN,
    ),
    SWITCH(
        "switch",
        "Switch en baie",
        "Ports libres et occupés visibles — réclamé en capitales dans le PV",
        Portee.SITE,
    ),
    MAGASIN(
        "magasin",
        "Vues du magasin",
        "Rayons, entrée, caisses : des plans larges, le plus possible",
        Portee.SITE,
    ),
    ;

    enum class Portee { ECRAN, SITE }

    companion object {
        fun parCle(cle: String?): Exigence? = entries.firstOrNull { it.cle == cle }
        fun pourEcran(): List<Exigence> = entries.filter { it.portee == Portee.ECRAN }
        fun pourSite(): List<Exigence> = entries.filter { it.portee == Portee.SITE }
    }
}

/**
 * Les exigences qui s'appliquent vraiment à cet écran.
 *
 * La surface d'accroche ne concerne que les écrans suspendus : la réclamer pour un
 * support mural ferait clignoter un manque qui n'en est pas un, et on finirait par ne
 * plus regarder la liste du tout.
 */
fun Ecran.exigences(): List<Exigence> = Exigence.pourEcran().filter {
    it != Exigence.ACCROCHE || support?.suspendu == true
}
