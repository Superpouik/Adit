package fr.pouik.audit

import fr.pouik.audit.donnees.Forme
import fr.pouik.audit.donnees.boiteDepuisCoins
import fr.pouik.audit.donnees.boiteGeometrique
import fr.pouik.audit.donnees.deplacee
import fr.pouik.audit.donnees.Point
import fr.pouik.audit.donnees.avecPoignee
import fr.pouik.audit.donnees.poigneesDe
import fr.pouik.audit.donnees.redresse
import fr.pouik.audit.donnees.sommets
import fr.pouik.audit.ui.Formes
import fr.pouik.audit.ui.Outil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La géométrie des annotations.
 *
 * Tout se joue à l'écran, où une erreur de signe ou de repère se voit tout de suite —
 * sauf qu'elle se voit sur la photo qu'on vient d'envoyer au client. D'où ces
 * vérifications, qui tournent sans téléphone.
 */
class FormesTest {

    private val bleu = 0xFF1E88E5L

    @Test
    fun `un rectangle trace de droite a gauche reste un rectangle`() {
        val depart = Formes.nouvelle(Outil.RECTANGLE, 0.8f, 0.7f, bleu) as Forme.Rectangle
        val tire = Formes.etire(depart, 0.8f, 0.7f, 0.2f, 0.3f) as Forme.Rectangle
        // Sans normalisation, la largeur serait négative : le dessin s'en sortirait,
        // la détection du toucher et la poignée de redimensionnement non.
        assertEquals(0.2f, tire.x, 0.0001f)
        assertEquals(0.3f, tire.y, 0.0001f)
        assertEquals(0.6f, tire.l, 0.0001f)
        assertEquals(0.4f, tire.h, 0.0001f)
    }

    @Test
    fun `la fleche garde son origine et suit le doigt par la pointe`() {
        val f = Formes.nouvelle(Outil.FLECHE, 0.1f, 0.1f, bleu) as Forme.Fleche
        val tire = Formes.etire(f, 0.1f, 0.1f, 0.9f, 0.6f) as Forme.Fleche
        assertEquals(0.1f, tire.x1, 0.0001f)
        assertEquals(0.1f, tire.y1, 0.0001f)
        assertEquals(0.9f, tire.x2, 0.0001f)
        assertEquals(0.6f, tire.y2, 0.0001f)
    }

    @Test
    fun `le crayon accumule ses points`() {
        var trait = Formes.nouvelle(Outil.CRAYON, 0.1f, 0.1f, bleu) as Forme.Trait
        trait = Formes.etire(trait, 0.1f, 0.1f, 0.2f, 0.2f) as Forme.Trait
        trait = Formes.etire(trait, 0.1f, 0.1f, 0.3f, 0.25f) as Forme.Trait
        assertEquals(listOf(0.1f, 0.1f, 0.2f, 0.2f, 0.3f, 0.25f), trait.points)
    }

    @Test
    fun `un simple contact ne laisse pas de forme sur la photo`() {
        val rien = Formes.nouvelle(Outil.RECTANGLE, 0.5f, 0.5f, bleu)!!
        assertFalse(Formes.estViable(rien))
        val vrai = Formes.etire(rien, 0.5f, 0.5f, 0.7f, 0.7f)
        assertTrue(Formes.estViable(vrai))
    }

    @Test
    fun `le texte et la selection ne se tracent pas au glisser`() {
        assertNull(Formes.nouvelle(Outil.TEXTE, 0.5f, 0.5f, bleu))
        assertNull(Formes.nouvelle(Outil.SELECTION, 0.5f, 0.5f, bleu))
    }

    @Test
    fun `changer l'opacite ne change pas la teinte`() {
        val rect = Forme.Rectangle("r", 0f, 0f, 0.5f, 0.5f, 0xFF1E88E5L)
        val transparent = Formes.avecOpacite(rect, 0.5f) as Forme.Rectangle
        assertEquals(0x1E88E5L, transparent.couleur and 0x00FFFFFFL)
        assertEquals(127L, (transparent.couleur ushr 24) and 0xFFL)
        assertEquals(0.5f, Formes.opaciteDe(transparent), 0.01f)
    }

    @Test
    fun `changer la couleur conserve l'opacite deja reglee`() {
        val rect = Forme.Rectangle("r", 0f, 0f, 0.5f, 0.5f, 0xFF1E88E5L)
        val demi = Formes.avecOpacite(rect, 0.4f)
        val rouge = Formes.avecCouleur(demi, Formes.teinte(0xFFE53935L, Formes.opaciteDe(demi)))
        assertEquals(0xE53935L, Formes.couleurDe(rouge)!! and 0x00FFFFFFL)
        assertEquals(0.4f, Formes.opaciteDe(rouge), 0.01f)
    }

    @Test
    fun `le flou n'a pas de couleur a changer`() {
        val flou = Forme.Flou("f", 0f, 0f, 0.3f, 0.3f)
        assertNull(Formes.couleurDe(flou))
        // Et lui en appliquer une ne doit rien casser.
        assertEquals(flou, Formes.avecCouleur(flou, 0xFFFF0000L))
    }

    @Test
    fun `deplacer un trait deplace tous ses points`() {
        val trait = Forme.Trait("t", listOf(0.1f, 0.2f, 0.3f, 0.4f), bleu)
        val bouge = trait.deplacee(0.05f, -0.1f) as Forme.Trait
        assertEquals(listOf(0.15f, 0.1f, 0.35f, 0.3f), bouge.points.map { kotlin.math.round(it * 100) / 100f })
    }

    @Test
    fun `tirer la poignee bas-droite d'un trait le met a l'echelle sans le deplacer`() {
        val trait = Forme.Trait("t", listOf(0.2f, 0.2f, 0.4f, 0.6f), bleu)
        val boiteAvant = trait.boiteGeometrique()!!
        val grand = avecPoignee(trait, 2, 0.6f, 1.0f, boiteAvant) as Forme.Trait
        val boite = grand.boiteGeometrique()!!
        // Le coin haut-gauche sert d'ancre : c'est la poignée opposée qu'on tire.
        assertEquals(0.2f, boite.x, 0.001f)
        assertEquals(0.2f, boite.y, 0.001f)
        assertEquals(0.4f, boite.l, 0.001f)
        assertEquals(0.8f, boite.h, 0.001f)
    }

    @Test
    fun `tirer la poignee haut-gauche laisse le coin oppose en place`() {
        val ellipse = Forme.Ellipse("e", 0.2f, 0.2f, 0.4f, 0.4f, bleu)
        val boite = ellipse.boiteGeometrique()!!
        val reduite = avecPoignee(ellipse, 0, 0.4f, 0.5f, boite) as Forme.Ellipse
        // Le bas-droite était à (0,6 ; 0,6) : il n'a pas bougé.
        assertEquals(0.6f, reduite.x + reduite.l, 0.001f)
        assertEquals(0.6f, reduite.y + reduite.h, 0.001f)
        assertEquals(0.4f, reduite.x, 0.001f)
        assertEquals(0.5f, reduite.y, 0.001f)
    }

    @Test
    fun `une forme ne peut pas etre reduite au point de devenir inattrapable`() {
        val ellipse = Forme.Ellipse("e", 0.1f, 0.1f, 0.5f, 0.5f, bleu)
        val boite = ellipse.boiteGeometrique()!!
        // On ramène la poignée bas-droite exactement sur son ancre.
        val minuscule = avecPoignee(ellipse, 2, 0.1f, 0.1f, boite) as Forme.Ellipse
        assertTrue(minuscule.l >= 0.01f)
        assertTrue(minuscule.h >= 0.01f)
    }

    @Test
    fun `tirer la poignee d'un texte change sa taille de police, pas sa largeur`() {
        val texte = Forme.Texte("t", 0.1f, 0.1f, "deux\nlignes", 0xFF000000L, taille = 0.04f)
        val boite = fr.pouik.audit.donnees.Boite(0.1f, 0.1f, 0.3f, 0.08f)
        val grand = avecPoignee(texte, 2, 0.6f, 0.3f, boite) as Forme.Texte
        // Deux lignes pour 0,2 de hauteur tirée : 0,1 par ligne.
        assertEquals(0.1f, grand.taille, 0.001f)
        assertEquals(0.1f, grand.x, 0.001f)
    }

    @Test
    fun `un rectangle neuf a quatre sommets deduits de son cadre`() {
        val rect = Forme.Rectangle("r", 0.2f, 0.3f, 0.4f, 0.2f, bleu)
        assertEquals(
            listOf(
                Point(0.2f, 0.3f),
                Point(0.6f, 0.3f),
                Point(0.6f, 0.5f),
                Point(0.2f, 0.5f),
            ),
            rect.sommets(),
        )
    }

    @Test
    fun `tirer un seul sommet deforme le rectangle en quadrilatere`() {
        val rect = Forme.Rectangle("r", 0.2f, 0.2f, 0.4f, 0.4f, bleu)
        val boite = rect.boiteGeometrique()!!
        // Le coin haut-droite rentre vers l'intérieur : c'est la perspective d'un écran
        // vu de biais.
        val trapeze = avecPoignee(rect, 1, 0.5f, 0.28f, boite) as Forme.Rectangle
        val sommets = trapeze.sommets()
        assertEquals(Point(0.5f, 0.28f), sommets[1])
        // Les trois autres n'ont pas bougé.
        assertEquals(Point(0.2f, 0.2f), sommets[0])
        assertEquals(Point(0.6f, 0.6f), sommets[2])
        assertEquals(Point(0.2f, 0.6f), sommets[3])
    }

    @Test
    fun `le cadre d'un rectangle deforme englobe ses sommets`() {
        val rect = Forme.Rectangle("r", 0.2f, 0.2f, 0.4f, 0.4f, bleu)
        val boite = rect.boiteGeometrique()!!
        // Le sommet haut-gauche part vers le haut et la gauche, hors du cadre d'origine.
        val tire = avecPoignee(rect, 0, 0.05f, 0.1f, boite) as Forme.Rectangle
        // Sans mise à jour du cadre, la forme ne serait plus attrapable là où elle est.
        assertEquals(0.05f, tire.x, 0.001f)
        assertEquals(0.1f, tire.y, 0.001f)
        assertEquals(0.55f, tire.l, 0.001f)
        assertEquals(0.5f, tire.h, 0.001f)
    }

    @Test
    fun `deplacer un rectangle deforme emmene ses sommets`() {
        val rect = Forme.Rectangle("r", 0.2f, 0.2f, 0.4f, 0.4f, bleu)
        val boite = rect.boiteGeometrique()!!
        val trapeze = avecPoignee(rect, 1, 0.5f, 0.28f, boite) as Forme.Rectangle
        val bouge = trapeze.deplacee(0.1f, 0.05f) as Forme.Rectangle
        assertEquals(Point(0.6f, 0.33f), bouge.sommets()[1])
        assertEquals(Point(0.3f, 0.25f), bouge.sommets()[0])
    }

    @Test
    fun `redresser rend son cadre droit a un rectangle deforme`() {
        val rect = Forme.Rectangle("r", 0.2f, 0.2f, 0.4f, 0.4f, bleu)
        val boite = rect.boiteGeometrique()!!
        val trapeze = avecPoignee(rect, 1, 0.5f, 0.28f, boite) as Forme.Rectangle
        val droit = trapeze.redresse()
        assertNull(droit.coins)
        // Le cadre reste celui qu'avait le quadrilatère : on redresse sur place.
        assertEquals(trapeze.x, droit.x, 0.001f)
        assertEquals(trapeze.l, droit.l, 0.001f)
    }

    @Test
    fun `une ligne et une fleche se manipulent par leurs deux extremites`() {
        val ligne = Forme.Fleche("f", 0.1f, 0.1f, 0.5f, 0.5f, bleu, pointe = false)
        val boite = ligne.boiteGeometrique()!!
        assertEquals(2, poigneesDe(ligne, boite).size)
        val tiree = avecPoignee(ligne, 1, 0.9f, 0.2f, boite) as Forme.Fleche
        assertEquals(0.9f, tiree.x2, 0.001f)
        assertEquals(0.2f, tiree.y2, 0.001f)
        // L'origine ne bouge pas, et le trait reste sans pointe.
        assertEquals(0.1f, tiree.x1, 0.001f)
        assertFalse(tiree.pointe)
    }

    @Test
    fun `les formes a surface offrent quatre poignees`() {
        val rect = Forme.Rectangle("r", 0.2f, 0.2f, 0.4f, 0.4f, bleu)
        val ellipse = Forme.Ellipse("e", 0.2f, 0.2f, 0.4f, 0.4f, bleu)
        val flou = Forme.Flou("f", 0.2f, 0.2f, 0.4f, 0.4f)
        listOf<Forme>(rect, ellipse, flou).forEach {
            assertEquals(4, poigneesDe(it, it.boiteGeometrique()!!).size)
        }
    }

    @Test
    fun `l'outil Trait produit une fleche sans pointe`() {
        val ligne = Formes.nouvelle(Outil.LIGNE, 0.1f, 0.1f, bleu) as Forme.Fleche
        assertFalse(ligne.pointe)
        val fleche = Formes.nouvelle(Outil.FLECHE, 0.1f, 0.1f, bleu) as Forme.Fleche
        assertTrue(fleche.pointe)
    }

    @Test
    fun `la boite d'un trait englobe tous ses points`() {
        val trait = Forme.Trait("t", listOf(0.5f, 0.5f, 0.2f, 0.8f, 0.9f, 0.3f), bleu)
        val b = trait.boiteGeometrique()!!
        assertEquals(0.2f, b.x, 0.001f)
        assertEquals(0.3f, b.y, 0.001f)
        assertEquals(0.7f, b.l, 0.001f)
        assertEquals(0.5f, b.h, 0.001f)
    }

    @Test
    fun `la boite du texte n'est pas calculable sans mesurer la police`() {
        // C'est le moteur de rendu qui s'en charge : le test documente la frontière.
        val texte = Forme.Texte("t", 0.1f, 0.1f, "x", 0xFF000000L)
        assertNull(texte.boiteGeometrique())
        assertNotNull(Forme.Rectangle("r", 0f, 0f, 1f, 1f, bleu).boiteGeometrique())
    }

    @Test
    fun `une boite tiree dans n'importe quel sens donne le meme cadre`() {
        val a = boiteDepuisCoins(0.2f, 0.3f, 0.8f, 0.9f)
        val b = boiteDepuisCoins(0.8f, 0.9f, 0.2f, 0.3f)
        assertEquals(a, b)
    }
}
