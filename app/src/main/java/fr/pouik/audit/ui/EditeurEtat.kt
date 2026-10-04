package fr.pouik.audit.ui

import fr.pouik.audit.donnees.EPAISSEUR_DEFAUT
import fr.pouik.audit.donnees.FLOU_DEFAUT
import fr.pouik.audit.donnees.Forme
import fr.pouik.audit.donnees.TAILLE_TEXTE_DEFAUT
import fr.pouik.audit.donnees.boiteDepuisCoins
import java.util.UUID

/**
 * La fabrique des formes et les retouches de leurs réglages.
 *
 * Isolé de l'écran pour être vérifiable sans émulateur : c'est ici que se joue
 * l'essentiel de ce qui pourrait se tromper silencieusement — un rectangle tracé de
 * droite à gauche, une couleur appliquée à la mauvaise forme, une opacité qui écrase
 * la teinte.
 */
object Formes {

    fun nouvelle(
        outil: Outil,
        x: Float,
        y: Float,
        couleur: Long,
        epaisseur: Float = EPAISSEUR_DEFAUT,
        force: Float = FLOU_DEFAUT,
    ): Forme? {
        val id = UUID.randomUUID().toString()
        return when (outil) {
            Outil.RECTANGLE -> Forme.Rectangle(id, x, y, 0f, 0f, couleur, plein = true, epaisseur = epaisseur)
            Outil.CADRE -> Forme.Rectangle(id, x, y, 0f, 0f, couleur, plein = false, epaisseur = epaisseur)
            Outil.ELLIPSE -> Forme.Ellipse(id, x, y, 0f, 0f, couleur, plein = false, epaisseur = epaisseur)
            Outil.FLECHE -> Forme.Fleche(id, x, y, x, y, couleur, epaisseur)
            Outil.CRAYON -> Forme.Trait(id, listOf(x, y), couleur, epaisseur)
            Outil.FLOU -> Forme.Flou(id, x, y, 0f, 0f, force)
            Outil.TEXTE, Outil.SELECTION -> null
        }
    }

    fun texte(x: Float, y: Float, contenu: String, couleur: Long, fond: Long?): Forme.Texte =
        Forme.Texte(
            id = UUID.randomUUID().toString(),
            x = x,
            y = y,
            contenu = contenu,
            couleur = couleur,
            taille = TAILLE_TEXTE_DEFAUT,
            fond = fond,
        )

    /** Étend la forme en cours de tracé jusqu'au point courant. */
    fun etire(forme: Forme, departX: Float, departY: Float, x: Float, y: Float): Forme =
        when (forme) {
            is Forme.Rectangle -> boiteDepuisCoins(departX, departY, x, y).let {
                forme.copy(x = it.x, y = it.y, l = it.l, h = it.h)
            }
            is Forme.Ellipse -> boiteDepuisCoins(departX, departY, x, y).let {
                forme.copy(x = it.x, y = it.y, l = it.l, h = it.h)
            }
            is Forme.Flou -> boiteDepuisCoins(departX, departY, x, y).let {
                forme.copy(x = it.x, y = it.y, l = it.l, h = it.h)
            }
            // La flèche garde son origine : c'est elle qui désigne, la pointe suit le
            // doigt.
            is Forme.Fleche -> forme.copy(x2 = x, y2 = y)
            is Forme.Trait -> forme.copy(points = forme.points + listOf(x, y))
            is Forme.Texte -> forme
        }

    /**
     * Une forme tracée par mégarde — un simple contact sans glissement — ne doit pas
     * rester sur la photo. Le seuil vaut pour les deux côtés sauf pour la flèche et le
     * crayon, où c'est la longueur parcourue qui compte.
     */
    fun estViable(forme: Forme): Boolean = when (forme) {
        is Forme.Rectangle -> forme.l > SEUIL && forme.h > SEUIL
        is Forme.Ellipse -> forme.l > SEUIL && forme.h > SEUIL
        is Forme.Flou -> forme.l > SEUIL && forme.h > SEUIL
        is Forme.Fleche -> kotlin.math.hypot(forme.x2 - forme.x1, forme.y2 - forme.y1) > SEUIL
        is Forme.Trait -> forme.points.size >= 6
        is Forme.Texte -> forme.contenu.isNotBlank()
    }

    fun couleurDe(forme: Forme): Long? = when (forme) {
        is Forme.Rectangle -> forme.couleur
        is Forme.Ellipse -> forme.couleur
        is Forme.Fleche -> forme.couleur
        is Forme.Trait -> forme.couleur
        is Forme.Texte -> forme.couleur
        is Forme.Flou -> null
    }

    fun avecCouleur(forme: Forme, couleur: Long): Forme = when (forme) {
        is Forme.Rectangle -> forme.copy(couleur = couleur)
        is Forme.Ellipse -> forme.copy(couleur = couleur)
        is Forme.Fleche -> forme.copy(couleur = couleur)
        is Forme.Trait -> forme.copy(couleur = couleur)
        is Forme.Texte -> forme.copy(couleur = couleur)
        is Forme.Flou -> forme
    }

    /**
     * Change l'opacité sans toucher à la teinte.
     *
     * Un rectangle plein sert aussi bien à masquer qu'à surligner : opaque, il cache le
     * produit concurrent ; à demi transparent, il met en valeur l'emplacement sans
     * effacer ce qu'il y a dessous. C'est le même outil, à l'alpha près.
     */
    fun avecOpacite(forme: Forme, opacite: Float): Forme {
        val couleur = couleurDe(forme) ?: return forme
        val alpha = (opacite.coerceIn(0f, 1f) * 255).toInt().toLong() shl 24
        return avecCouleur(forme, (couleur and 0x00FFFFFFL) or alpha)
    }

    fun opaciteDe(forme: Forme): Float {
        val couleur = couleurDe(forme) ?: return 1f
        return ((couleur ushr 24) and 0xFFL).toFloat() / 255f
    }

    fun avecEpaisseur(forme: Forme, epaisseur: Float): Forme = when (forme) {
        is Forme.Rectangle -> forme.copy(epaisseur = epaisseur)
        is Forme.Ellipse -> forme.copy(epaisseur = epaisseur)
        is Forme.Fleche -> forme.copy(epaisseur = epaisseur)
        is Forme.Trait -> forme.copy(epaisseur = epaisseur)
        is Forme.Texte, is Forme.Flou -> forme
    }

    /** Applique la teinte voulue en conservant l'opacité déjà réglée. */
    fun teinte(couleur: Long, opacite: Float): Long {
        val alpha = (opacite.coerceIn(0f, 1f) * 255).toInt().toLong() shl 24
        return (couleur and 0x00FFFFFFL) or alpha
    }

    private const val SEUIL = 0.015f
}
