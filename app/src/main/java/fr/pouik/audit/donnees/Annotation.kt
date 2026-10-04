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

/**
 * Boîte d'une forme, sauf le texte : sa largeur dépend de la police, donc de la mesure,
 * qui appartient au moteur de rendu (voir `Rendu.boiteDe`).
 */
fun Forme.boiteGeometrique(): Boite? = when (this) {
    is Forme.Rectangle -> Boite(x, y, l, h)
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

/** Déplace une forme de [dx] et [dy] (en fraction de l'image). */
fun Forme.deplacee(dx: Float, dy: Float): Forme = when (this) {
    is Forme.Rectangle -> copy(x = x + dx, y = y + dy)
    is Forme.Ellipse -> copy(x = x + dx, y = y + dy)
    is Forme.Flou -> copy(x = x + dx, y = y + dy)
    is Forme.Texte -> copy(x = x + dx, y = y + dy)
    is Forme.Fleche -> copy(x1 = x1 + dx, y1 = y1 + dy, x2 = x2 + dx, y2 = y2 + dy)
    is Forme.Trait -> copy(
        points = points.mapIndexed { i, v -> if (i % 2 == 0) v + dx else v + dy },
    )
}

/**
 * Redimensionne une forme par sa poignée bas-droite.
 *
 * Les dimensions sont bornées par le bas : une forme ramenée à zéro deviendrait
 * invisible et impossible à rattraper, puisqu'on ne pourrait plus la toucher.
 */
fun Forme.redimensionnee(nouvelleLargeur: Float, nouvelleHauteur: Float): Forme {
    val l = nouvelleLargeur.coerceAtLeast(MINIMUM)
    val h = nouvelleHauteur.coerceAtLeast(MINIMUM)
    return when (this) {
        is Forme.Rectangle -> copy(l = l, h = h)
        is Forme.Ellipse -> copy(l = l, h = h)
        is Forme.Flou -> copy(l = l, h = h)
        is Forme.Fleche -> copy(x2 = x1 + nouvelleLargeur, y2 = y1 + nouvelleHauteur)
        // Un texte ne s'étire pas : on change sa taille de police, en suivant la
        // hauteur tirée. L'étirer déformerait les lettres.
        is Forme.Texte -> {
            val lignes = contenu.count { it == '\n' } + 1
            copy(taille = (h / lignes).coerceIn(0.01f, 0.5f))
        }
        is Forme.Trait -> {
            val b = boiteGeometrique() ?: return this
            val fx = if (b.l > MINIMUM) l / b.l else 1f
            val fy = if (b.h > MINIMUM) h / b.h else 1f
            copy(
                points = points.mapIndexed { i, v ->
                    if (i % 2 == 0) b.x + (v - b.x) * fx else b.y + (v - b.y) * fy
                },
            )
        }
    }
}

private const val MINIMUM = 0.01f
