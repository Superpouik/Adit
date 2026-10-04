package fr.pouik.audit

import fr.pouik.audit.donnees.Depot
import fr.pouik.audit.donnees.Forme
import fr.pouik.audit.donnees.Photo
import fr.pouik.audit.donnees.cle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * La relecture des annotations.
 *
 * Elles voyagent en JSON polymorphe : six types de formes derrière une seule interface.
 * Un discriminant oublié ne se verrait qu'au redémarrage suivant, et se traduirait par
 * un catalogue entier mis au rebut — donc par toutes les légendes perdues, pas
 * seulement les annotations.
 */
class AnnotationsPersistanceTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private val toutesLesFormes = listOf(
        Forme.Rectangle("a", 0.1f, 0.1f, 0.3f, 0.2f, 0xFF1E88E5L, plein = true),
        Forme.Rectangle("b", 0.2f, 0.2f, 0.3f, 0.2f, 0xFFE53935L, plein = false),
        // Un quadrilatère déformé : ses sommets libres doivent survivre au
        // redémarrage, sinon l'aplat posé sur un écran vu de biais redeviendrait
        // un rectangle droit et déborderait.
        Forme.Rectangle(
            "b2", 0.2f, 0.2f, 0.4f, 0.4f, 0xFF1E88E5L,
            coins = listOf(0.2f, 0.25f, 0.6f, 0.2f, 0.6f, 0.55f, 0.2f, 0.6f),
        ),
        Forme.Ellipse("c", 0.3f, 0.3f, 0.2f, 0.2f, 0xFF43A047L),
        Forme.Fleche("d", 0.1f, 0.9f, 0.6f, 0.4f, 0xFFFDD835L),
        Forme.Fleche("d2", 0.2f, 0.8f, 0.7f, 0.8f, 0xFFFFFFFFL, pointe = false),
        Forme.Trait("e", listOf(0.1f, 0.1f, 0.2f, 0.3f, 0.4f, 0.2f), 0xFF8E24AAL),
        Forme.Texte("f", 0.05f, 0.05f, "Carrefour City\n89 Mail F. Mitterrand", 0xFF000000L, fond = 0xFFFFFFFFL),
        Forme.Flou("g", 0.7f, 0.7f, 0.2f, 0.2f, force = 0.03f),
    )

    @Test
    fun `tous les types de formes se relisent a l'identique`() = runTest {
        val premier = Depot(dossier.root)
        premier.charge()
        val audit = premier.cree("Carrefour City", "Rennes", "", 1L)
        premier.ajoutePhoto(audit.id, Photo("u1", "f.jpg", 1, priseLe = 1L))
        premier.majAnnotations(audit.id, "u1", toutesLesFormes)

        val second = Depot(dossier.root)
        second.charge()
        val relues = second.audit(audit.id)!!.photos.first().annotations
        assertEquals(toutesLesFormes, relues)
    }

    @Test
    fun `enregistrer des annotations fait avancer la version de la photo`() = runTest {
        val d = Depot(dossier.root)
        d.charge()
        val audit = d.cree("Site", "", "", 1L)
        d.ajoutePhoto(audit.id, Photo("u1", "f.jpg", 1, priseLe = 1L))
        assertEquals(0, d.audit(audit.id)!!.photos.first().version)

        d.majAnnotations(audit.id, "u1", toutesLesFormes)
        assertEquals(1, d.audit(audit.id)!!.photos.first().version)
        // Effacer est aussi une écriture du fichier : la vignette en cache doit être
        // jetée dans ce sens-là aussi.
        d.majAnnotations(audit.id, "u1", emptyList())
        assertEquals(2, d.audit(audit.id)!!.photos.first().version)
    }

    @Test
    fun `une photo ancienne sans annotations se relit encore`() = runTest {
        // Un catalogue écrit par la version d'avant l'éditeur n'a ni « annotations » ni
        // « version » : il doit se charger, pas finir dans audits.corrompu.json.
        java.io.File(dossier.root, "audits.json").writeText(
            """
            {"audits":[{"id":"a1","nom":"Site","lieu":"","notes":"","creeLe":1,
            "dossier":"Site","photos":[{"uri":"u1","fichier":"f.jpg","numero":1,
            "legende":"façade","priseLe":2}],"compteur":2}]}
            """.trimIndent(),
        )
        val d = Depot(dossier.root)
        d.charge()
        val photo = d.audit("a1")!!.photos.first()
        assertEquals("façade", photo.legende)
        assertTrue(photo.annotations.isEmpty())
        assertEquals(0, photo.version)
    }

    @Test
    fun `la cle d'un original est stable et lisible`() = runTest {
        val photo = Photo("u", "f.jpg", 7, priseLe = 1L)
        assertEquals("abc-007", photo.cle("abc"))
    }
}
