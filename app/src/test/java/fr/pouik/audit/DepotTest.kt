package fr.pouik.audit

import fr.pouik.audit.donnees.Depot
import fr.pouik.audit.donnees.Photo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Le catalogue, vérifié sans émulateur : c'est lui qui porte les légendes et le
 * rattachement des photos à leur audit, soit tout ce que MediaStore ne sait pas.
 */
class DepotTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private fun depot() = Depot(dossier.root)

    @Test
    fun `un audit cree est relu apres redemarrage`() = runTest {
        val premier = depot()
        premier.charge()
        val cree = premier.cree("Lidl Vitrolles", "Vitrolles", "porte Est", 1_000L)

        val second = depot()
        second.charge()
        val relu = second.audit(cree.id)
        assertNotNull(relu)
        assertEquals("Lidl Vitrolles", relu!!.nom)
        assertEquals("Lidl-Vitrolles", relu.dossier)
        assertEquals("porte Est", relu.notes)
        assertEquals(1_000L, relu.creeLe)
    }

    @Test
    fun `deux audits homonymes recoivent deux dossiers`() = runTest {
        val d = depot()
        d.charge()
        val a = d.cree("Lidl", "", "", 1L)
        val b = d.cree("Lidl", "", "", 2L)
        assertEquals("Lidl", a.dossier)
        assertEquals("Lidl-2", b.dossier)
    }

    @Test
    fun `les numeros ne se repetent jamais, meme apres suppression`() = runTest {
        val d = depot()
        d.charge()
        val a = d.cree("Site", "", "", 1L)

        assertEquals(1, d.reserveNumero(a.id))
        assertEquals(2, d.reserveNumero(a.id))
        d.ajoutePhoto(a.id, Photo("uri-2", "Site-002.jpg", 2, priseLe = 10L))
        d.retirePhoto(a.id, "uri-2")
        // Le troisième cliché est bien le 003 : réutiliser le 002 donnerait deux
        // fichiers de même nom dans le dossier.
        assertEquals(3, d.reserveNumero(a.id))
    }

    @Test
    fun `le compteur survit a un redemarrage`() = runTest {
        val premier = depot()
        premier.charge()
        val a = premier.cree("Site", "", "", 1L)
        premier.reserveNumero(a.id)
        premier.reserveNumero(a.id)

        val second = depot()
        second.charge()
        assertEquals(3, second.reserveNumero(a.id))
    }

    @Test
    fun `une legende modifiee remplace l'ancienne sans toucher aux autres`() = runTest {
        val d = depot()
        d.charge()
        val a = d.cree("Site", "", "", 1L)
        d.ajoutePhoto(a.id, Photo("uri-1", "Site-001.jpg", 1, priseLe = 1L))
        d.ajoutePhoto(a.id, Photo("uri-2", "Site-002.jpg", 2, "façade", 2L))

        d.majPhoto(a.id, "uri-1", " prise élec ", "Site-001-prise-elec.jpg")
        val photos = d.audit(a.id)!!.photos
        assertEquals("prise élec", photos.first { it.uri == "uri-1" }.legende)
        assertEquals("Site-001-prise-elec.jpg", photos.first { it.uri == "uri-1" }.fichier)
        assertEquals("façade", photos.first { it.uri == "uri-2" }.legende)
    }

    @Test
    fun `agir sur un audit inexistant ne casse rien`() = runTest {
        val d = depot()
        d.charge()
        assertNull(d.reserveNumero("fantome"))
        assertNull(d.majFiche("fantome", "X", "", ""))
        assertNull(d.ajoutePhoto("fantome", Photo("u", "f.jpg", 1, priseLe = 1L)))
        assertTrue(d.audits.value.isEmpty())
    }

    @Test
    fun `un catalogue illisible est mis de cote plutot qu'ecrase`() = runTest {
        val fichier = dossier.newFile("audits.json")
        fichier.writeText("{ ceci n'est pas du JSON")

        val d = depot()
        d.charge()
        assertTrue(d.audits.value.isEmpty())
        // Le fichier d'origine est conservé : les légendes de plusieurs audits
        // peuvent encore se récupérer à la main.
        assertTrue(java.io.File(dossier.root, "audits.corrompu.json").exists())
    }

    @Test
    fun `supprimer un audit laisse les autres en place`() = runTest {
        val d = depot()
        d.charge()
        val a = d.cree("A", "", "", 1L)
        val b = d.cree("B", "", "", 2L)
        d.supprime(a.id)
        assertEquals(listOf(b.id), d.audits.value.map { it.id })
    }
}
