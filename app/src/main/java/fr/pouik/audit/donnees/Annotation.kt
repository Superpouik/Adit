package fr.pouik.audit.donnees

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Une annotation posée sur un cliché.
 *
 * **Toutes les coordonnées sont normalisées** : 0 au bord gauche (ou haut), 1 au bord
 * droit (ou bas) de la photo. Un rectangle gardé en pixels se retrouverait décalé dès
 * qu'on change de téléphone, de zoom, ou qu'on rouvre l'éditeur plié après l'avoir
 * fermé déplié. Les épaisseurs et la taille du texte suivent la même règle, rapportées
 * respectivement à la largeur et à la hauteur de l'image.
 */
@Serializable
sealed class Forme {

    /** Identifiant stable : c'est lui qui désigne la forme sélectionnée. */
    abstract val id: String

    @Serializable
    @SerialName("rect")
    data class Rectangle(
        override val id: String,
        val x: Float,
        val y: Float,
        val l: Float,
        val h: Float,
        val couleur: Long,
        /** Rempli pour masquer une zone, en contour pour l'entourer sans la cacher. */
        val plein: Boolean = true,
        val epaisseur: Float = EPAISSEUR_DEFAUT,
        /**
         * Les quatre sommets, quand la forme a été déformée — x et y alternés, dans
         * l'ordre haut-gauche, haut-droite, bas-droite, bas-gauche.
         *
         * Nul tant qu'on n'a pas tiré sur un coin : le rectangle reste alors décrit par
         * son cadre droit. Un écran PLV photographié de biais n'est pas un rectangle
         * mais un trapèze ; sans sommets libres, l'aplat déborde d'un côté ou laisse
         * voir l'écran de l'autre.
         *
         * Le cadre droit (x, y, l, h) reste tenu à jour comme boîte englobante des
         * sommets : c'est lui qui sert à attraper la forme au doigt.
         */
        val coins: List<Float>? = null,
    ) : Forme()

    @Serializable
    @SerialName("ellipse")
    data class Ellipse(
        override val id: String,
        val x: Float,
        val y: Float,
        val l: Float,
        val h: Float,
        val couleur: Long,
        val plein: Boolean = false,
        val epaisseur: Float = EPAISSEUR_DEFAUT,
    ) : Forme()

    @Serializable
    @SerialName("fleche")
    data class Fleche(
        override val id: String,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val couleur: Long,
        val epaisseur: Float = EPAISSEUR_DEFAUT,
        /**
         * Sans pointe, c'est un simple trait.
         *
         * Même forme et mêmes gestes : une flèche désigne, un trait relie ou souligne —
         * la ligne d'accroche d'un écran suspendu, par exemple, où une pointe laisserait
         * croire qu'on montre quelque chose.
         */
        val pointe: Boolean = true,
    ) : Forme()

    @Serializable
    @SerialName("texte")
    data class Texte(
        override val id: String,
        val x: Float,
        val y: Float,
        val contenu: String,
        val couleur: Long,
        /** Hauteur de police, en fraction de la hauteur de l'image. */
        val taille: Float = TAILLE_TEXTE_DEFAUT,
        /** Cartouche derrière le texte : indispensable sur une photo de rayon. */
        val fond: Long? = null,
    ) : Forme()

    @Serializable
    @SerialName("trait")
    data class Trait(
        override val id: String,
        /** x et y alternés : un seul tableau plat, c'est trois fois moins de JSON. */
        val points: List<Float>,
        val couleur: Long,
        val epaisseur: Float = EPAISSEUR_DEFAUT,
    ) : Forme()

    @Serializable
    @SerialName("flou")
    data class Flou(
        override val id: String,
        val x: Float,
        val y: Float,
        val l: Float,
        val h: Float,
        /** Taille des pavés, en fraction de la largeur : plus c'est gros, moins c'est lisible. */
        val force: Float = FLOU_DEFAUT,
    ) : Forme()
}

const val EPAISSEUR_DEFAUT = 0.006f
const val TAILLE_TEXTE_DEFAUT = 0.045f
const val FLOU_DEFAUT = 0.02f

/** Boîte englobante normalisée, hors texte dont la mesure dépend du rendu. */
data class Boite(val x: Float, val y: Float, val l: Float, val h: Float) {
    val droite get() = x + l
    val bas get() = y + h
    fun contient(px: Float, py: Float, marge: Float = 0f): Boolean =
        px >= x - marge && px <= droite + marge && py >= y - marge && py <= bas + marge
}

/** Un sommet, en coordonnées normalisées. */
data class Point(val x: Float, val y: Float)

/**
 * Normalise une boîte tracée à l'envers.
 *
 * Un rectangle tiré de bas en haut donne une hauteur négative : le dessin s'en
 * accommode, mais la détection du toucher et les poignées de redimensionnement, non.
 */
fun boiteDepuisCoins(x1: Float, y1: Float, x2: Float, y2: Float): Boite = Boite(
    x = minOf(x1, x2),
    y = minOf(y1, y2),
    l = kotlin.math.abs(x2 - x1),
    h = kotlin.math.abs(y2 - y1),
)

/** Les quatre sommets d'une boîte : haut-gauche, haut-droite, bas-droite, bas-gauche. */
fun Boite.sommets(): List<Point> = listOf(
    Point(x, y),
    Point(droite, y),
    Point(droite, bas),
    Point(x, bas),
)

/**
 * Les sommets effectifs d'un rectangle : ceux qu'on a tirés s'il a été déformé, ceux de
 * son cadre sinon.
 */
fun Forme.Rectangle.sommets(): List<Point> {
    val libres = coins
    if (libres == null || libres.size != 8) return Boite(x, y, l, h).sommets()
    return (0 until 4).map { Point(libres[it * 2], libres[it * 2 + 1]) }
}

/**
 * Boîte d'une forme, sauf le texte : sa largeur dépend de la police, donc de la mesure,
 * qui appartient au moteur de rendu (voir `Rendu.boiteDe`).
 */
fun Forme.boiteGeometrique(): Boite? = when (this) {
    is Forme.Rectangle -> englobe(sommets())
    is Forme.Ellipse -> Boite(x, y, l, h)
    is Forme.Flou -> Boite(x, y, l, h)
    is Forme.Fleche -> boiteDepuisCoins(x1, y1, x2, y2)
    is Forme.Trait -> {
        val xs = points.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = points.filterIndexed { i, _ -> i % 2 == 1 }
        if (xs.isEmpty() || ys.isEmpty()) {
            null
        } else {
            Boite(xs.min(), ys.min(), xs.max() - xs.min(), ys.max() - ys.min())
        }
    }
    is Forme.Texte -> null
}

private fun englobe(points: List<Point>): Boite {
    val xs = points.map { it.x }
    val ys = points.map { it.y }
    return Boite(xs.min(), ys.min(), xs.max() - xs.min(), ys.max() - ys.min())
}

/** Déplace une forme de [dx] et [dy] (en fraction de l'image). */
fun Forme.deplacee(dx: Float, dy: Float): Forme = when (this) {
    is Forme.Rectangle -> copy(
        x = x + dx,
        y = y + dy,
        // Les sommets libres suivent, sans quoi déplacer une forme déformée la
        // laisserait sur place en ne bougeant que sa boîte.
        coins = coins?.mapIndexed { i, v -> if (i % 2 == 0) v + dx else v + dy },
    )
    is Forme.Ellipse -> copy(x = x + dx, y = y + dy)
    is Forme.Flou -> copy(x = x + dx, y = y + dy)
    is Forme.Texte -> copy(x = x + dx, y = y + dy)
    is Forme.Fleche -> copy(x1 = x1 + dx, y1 = y1 + dy, x2 = x2 + dx, y2 = y2 + dy)
    is Forme.Trait -> copy(
        points = points.mapIndexed { i, v -> if (i % 2 == 0) v + dx else v + dy },
    )
}

/**
 * Les points qu'on peut attraper pour déformer une forme.
 *
 * Quatre coins pour tout ce qui occupe une surface, deux extrémités pour une ligne.
 * [boite] vient de l'appelant parce que celle d'un texte demande une mesure de police.
 */
fun poigneesDe(forme: Forme, boite: Boite): List<Point> = when (forme) {
    is Forme.Rectangle -> forme.sommets()
    is Forme.Fleche -> listOf(Point(forme.x1, forme.y1), Point(forme.x2, forme.y2))
    else -> boite.sommets()
}

/**
 * Déplace la poignée [index] vers (nx, ny).
 *
 * Le rectangle est le seul à se déformer librement : chacun de ses sommets va où on le
 * met, pour épouser un objet vu de biais. Les autres formes gardent un cadre droit et
 * se redimensionnent en laissant fixe le coin opposé à celui qu'on tire — c'est ce qui
 * permet d'ajuster un bord sans avoir à repositionner la forme ensuite.
 */
fun avecPoignee(forme: Forme, index: Int, nx: Float, ny: Float, boite: Boite): Forme {
    if (forme is Forme.Rectangle) {
        val sommets = forme.sommets().toMutableList()
        if (index !in sommets.indices) return forme
        sommets[index] = Point(nx, ny)
        val cadre = englobe(sommets)
        return forme.copy(
            x = cadre.x,
            y = cadre.y,
            l = cadre.l,
            h = cadre.h,
            coins = sommets.flatMap { listOf(it.x, it.y) },
        )
    }
    if (forme is Forme.Fleche) {
        return if (index == 0) forme.copy(x1 = nx, y1 = ny) else forme.copy(x2 = nx, y2 = ny)
    }

    // Le sommet diagonalement opposé ne bouge pas : il sert d'ancre.
    val ancre = boite.sommets()[(index + 2) % 4]
    val cadre = boiteDepuisCoins(ancre.x, ancre.y, nx, ny)
    val l = cadre.l.coerceAtLeast(MINIMUM)
    val h = cadre.h.coerceAtLeast(MINIMUM)
    return when (forme) {
        is Forme.Ellipse -> forme.copy(x = cadre.x, y = cadre.y, l = l, h = h)
        is Forme.Flou -> forme.copy(x = cadre.x, y = cadre.y, l = l, h = h)
        // Un texte ne s'étire pas : on change sa taille de police en suivant la hauteur
        // tirée. L'étirer déformerait les lettres.
        is Forme.Texte -> {
            val lignes = forme.contenu.count { it == '\n' } + 1
            forme.copy(
                x = cadre.x,
                y = cadre.y,
                taille = (h / lignes).coerceIn(0.01f, 0.5f),
            )
        }
        is Forme.Trait -> {
            val b = forme.boiteGeometrique() ?: return forme
            val fx = if (b.l > MINIMUM) l / b.l else 1f
            val fy = if (b.h > MINIMUM) h / b.h else 1f
            forme.copy(
                points = forme.points.mapIndexed { i, v ->
                    if (i % 2 == 0) {
                        cadre.x + (v - b.x) * fx
                    } else {
                        cadre.y + (v - b.y) * fy
                    }
                },
            )
        }
        else -> forme
    }
}

/**
 * Rend son cadre droit à un rectangle déformé.
 *
 * Sans cette sortie de secours, quatre sommets mal tirés ne se rattrapent qu'en
 * supprimant la forme et en la retraçant.
 */
fun Forme.Rectangle.redresse(): Forme.Rectangle = copy(coins = null)

private const val MINIMUM = 0.01f
