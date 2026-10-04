package fr.pouik.audit

import fr.pouik.audit.donnees.Depot
import fr.pouik.audit.donnees.Exigence
import fr.pouik.audit.donnees.Photo
import fr.pouik.audit.donnees.Support
import fr.pouik.audit.donnees.exigences
import fr.pouik.audit.donnees.manques
import fr.pouik.audit.donnees.nomPhoto
import fr.pouik.audit.donnees.photosDe
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Les emplacements d'écran et la liste de ce qui manque.
 *
 * C'est la partie de l'app qui porte les règles du client : deux écrans au maximum,
 * cinq photos exigées par écran, deux pour le site. Une erreur ici ne se voit pas à
 * l'écran — elle se voit quand le PV revient incomplet.
 */
class EcransTest {

    @get:Rule
    val dossier = TemporaryFolder()

    private suspend fun depotAvecAudit(): Pair<Depot, String> {
        val d = Depot(dossier.root)
        d.charge()
        val audit = d.cree("Carrefour City", "Rennes", "", 1L)
        return d to audit.id
    }

    @Test
    fun `deux ecrans au maximum, c'est la limite du contrat`() = runTest {
        val (d, id) = depotAvecAudit()
        assertEquals(1, d.ajouteEcran(id)?.numero)
        assertEquals(2, d.ajouteEcran(id)?.numero)
        assertNull("le troisième doit être refusé", d.ajouteEcran(id))
        assertEquals(2, d.audit(id)!!.ecrans.size)
    }

    @Test
    fun `le numero libere est repris`() = runTest {
        val (d, id) = depotAvecAudit()
        val premier = d.ajouteEcran(id)!!
        d.ajouteEcran(id)
        d.supprimeEcran(id, premier.id)
        // Le destinataire lit « Écran 1 » et « Écran 2 » : laisser un trou donnerait
        // un PV avec un écran 2 et pas d'écran 1.
        assertEquals(1, d.ajouteEcran(id)?.numero)
    }

    @Test
    fun `supprimer un ecran garde ses photos, qui reviennent au site`() = runTest {
        val (d, id) = depotAvecAudit()
        val ecran = d.ajouteEcran(id)!!
        d.ajoutePhoto(id, Photo("u1", "f.jpg", 1, priseLe = 1L, ecranId = ecran.id))
        d.majRattachement(id, "u1", ecran.id, "plan-large")

        d.supprimeEcran(id, ecran.id)
        val audit = d.audit(id)!!
        assertEquals(1, audit.photos.size)
        val photo = audit.photos.first()
        assertNull(photo.ecranId)
        // L'exigence tombe avec l'écran : « plan large » ne veut plus rien dire
        // rapporté au site entier.
        assertNull(photo.exigence)
        assertEquals(1, audit.photosDe(null).size)
    }

    @Test
    fun `un ecran mural ne reclame pas de photo d'accroche`() = runTest {
        val (d, id) = depotAvecAudit()
        val ecran = d.ajouteEcran(id)!!
        d.majEcran(id, ecran.copy(support = Support.MURAL_FIXE))
        val mural = d.audit(id)!!.ecrans.first()
        assertTrue(Exigence.ACCROCHE !in mural.exigences())
        assertEquals(4, mural.exigences().size)

        d.majEcran(id, ecran.copy(support = Support.FILINS))
        val suspendu = d.audit(id)!!.ecrans.first()
        assertTrue(Exigence.ACCROCHE in suspendu.exigences())
        assertEquals(5, suspendu.exigences().size)
    }

    @Test
    fun `un audit neuf signale qu'aucun ecran n'est releve`() = runTest {
        val (d, id) = depotAvecAudit()
        val manques = d.audit(id)!!.manques()
        assertTrue(manques.any { it.exigence == null })
        // Et les deux photos de site sont déjà attendues.
        assertTrue(manques.any { it.exigence == Exigence.SWITCH })
        assertTrue(manques.any { it.exigence == Exigence.MAGASIN })
    }

    @Test
    fun `une photo rattachee fait disparaitre le manque correspondant`() = runTest {
        val (d, id) = depotAvecAudit()
        assertTrue(d.audit(id)!!.manques().any { it.exigence == Exigence.SWITCH })

        d.ajoutePhoto(id, Photo("u1", "f.jpg", 1, priseLe = 1L))
        d.majRattachement(id, "u1", null, "switch")
        assertTrue(d.audit(id)!!.manques().none { it.exigence == Exigence.SWITCH })
    }

    @Test
    fun `un audit complet n'a plus rien a signaler`() = runTest {
        val (d, id) = depotAvecAudit()
        val ecran = d.ajouteEcran(id)!!
        d.majEcran(id, ecran.copy(support = Support.MURAL_FIXE, taille = 43))

        var numero = 1
        d.audit(id)!!.ecrans.first().exigences().forEach { exigence ->
            val uri = "u$numero"
            d.ajoutePhoto(id, Photo(uri, "f$numero.jpg", numero, priseLe = 1L, ecranId = ecran.id))
            d.majRattachement(id, uri, ecran.id, exigence.cle)
            numero++
        }
        Exigence.pourSite().forEach { exigence ->
            val uri = "u$numero"
            d.ajoutePhoto(id, Photo(uri, "f$numero.jpg", numero, priseLe = 1L))
            d.majRattachement(id, uri, null, exigence.cle)
            numero++
        }
        assertEquals(emptyList<Any>(), d.audit(id)!!.manques())
    }

    @Test
    fun `le nom de fichier porte le numero de l'ecran`() {
        assertEquals("Carrefour-City-E1-003.jpg", nomPhoto("Carrefour-City", 3, ecran = 1))
        // Le slug garde la casse d'origine : ces noms se lisent dans un explorateur
        // de fichiers, pas dans une URL.
        assertEquals(
            "Carrefour-City-E2-003-Plan-large.jpg",
            nomPhoto("Carrefour-City", 3, "Plan large", ecran = 2),
        )
        // Sans écran, c'est une vue générale : pas de préfixe.
        assertEquals("Carrefour-City-003-Switch.jpg", nomPhoto("Carrefour-City", 3, "Switch"))
    }

    @Test
    fun `le tri alphabetique du dossier groupe les photos par ecran`() {
        val noms = listOf(
            nomPhoto("Site", 5, ecran = 2),
            nomPhoto("Site", 1, ecran = 1),
            nomPhoto("Site", 9),
            nomPhoto("Site", 3, ecran = 1),
        ).sorted()
        // Les vues générales d'abord, puis l'écran 1, puis l'écran 2 — et dans chaque
        // groupe, l'ordre des prises. C'est l'ordre dans lequel le PV se lit.
        assertEquals(
            listOf("Site-009.jpg", "Site-E1-001.jpg", "Site-E1-003.jpg", "Site-E2-005.jpg"),
            noms,
        )
    }

    @Test
    fun `les ecrans et la fiche du site survivent a un redemarrage`() = runTest {
        val (premier, id) = depotAvecAudit()
        val ecran = premier.ajouteEcran(id)!!
        premier.majEcran(
            id,
            ecran.copy(rayon = "Fruits et Légumes", taille = 50, support = Support.MAT_PLAFOND,
                priseElectrique = true, prerequisProches = false),
        )
        premier.majFicheSite(id, "Le gérant", "Fermé le lundi", true, "3 m", "2 h")

        val second = Depot(dossier.root)
        second.charge()
        val relu = second.audit(id)!!
        assertNotNull(relu.ecrans.firstOrNull())
        val releve = relu.ecrans.first()
        assertEquals("Fruits et Légumes", releve.rayon)
        assertEquals(50, releve.taille)
        assertEquals(Support.MAT_PLAFOND, releve.support)
        assertEquals(true, releve.priseElectrique)
        assertEquals(false, releve.prerequisProches)
        assertNull(releve.priseReseau)
        assertEquals("Le gérant", relu.interlocuteur)
        assertEquals(true, relu.nacelle)
        assertEquals("3 m", relu.hauteurPrerequis)
        assertEquals("2 h", relu.dureeInstallation)
    }

    @Test
    fun `un catalogue d'avant les ecrans se relit sans rien perdre`() = runTest {
        java.io.File(dossier.root, "audits.json").writeText(
            """
            {"audits":[{"id":"a1","nom":"Site","lieu":"","notes":"","creeLe":1,
            "dossier":"Site","photos":[{"uri":"u1","fichier":"f.jpg","numero":1,
            "legende":"façade","priseLe":2}],"compteur":2}]}
            """.trimIndent(),
        )
        val d = Depot(dossier.root)
        d.charge()
        val audit = d.audit("a1")!!
        assertEquals("façade", audit.photos.first().legende)
        assertTrue(audit.ecrans.isEmpty())
        assertNull(audit.nacelle)
        // Et la photo d'alors compte comme une photo de site.
        assertEquals(1, audit.photosDe(null).size)
    }
}
