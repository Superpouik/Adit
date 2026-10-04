package fr.pouik.audit

import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.Ecran
import fr.pouik.audit.donnees.Exigence
import fr.pouik.audit.donnees.Photo
import fr.pouik.audit.donnees.Support
import fr.pouik.audit.donnees.comptePhotos
import fr.pouik.audit.donnees.ouiNon
import fr.pouik.audit.donnees.recapitulatif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Le récapitulatif est ce que lira le collègue qui reçoit le mail, et ce que le
 * technicien recopiera dans le PV papier. Il doit permettre de relier chaque pièce
 * jointe à ce qu'elle montre, sans l'app, et de retrouver chaque case du document.
 */
class RecapitulatifTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val quandCree = 1_791_117_120_000L

    private val ecranUn = Ecran(
        id = "e1",
        numero = 1,
        emplacement = "Zone marché",
        rayon = "Fruits et Légumes",
        taille = 43,
        support = Support.MURAL_FIXE,
        natureSurface = "Tôle acier",
        priseElectrique = true,
        priseReseau = false,
        prerequisProches = true,
    )

    private val audit = Audit(
        id = "x",
        nom = "Carrefour City",
        lieu = "Rennes, Mail F. Mitterrand 89",
        notes = "Prévoir une rallonge.",
        creeLe = quandCree,
        dossier = "Carrefour-City",
        ecrans = listOf(ecranUn),
        interlocuteur = "Le gérant",
        nacelle = false,
        dureeInstallation = "2 h",
        photos = listOf(
            Photo("u2", "Carrefour-City-E1-002-plan-rapproche.jpg", 2, "", quandCree + 60_000,
                ecranId = "e1", exigence = "plan-rapproche"),
            Photo("u1", "Carrefour-City-E1-001-plan-large.jpg", 1, "façade", quandCree,
                ecranId = "e1", exigence = "plan-large"),
            Photo("u3", "Carrefour-City-003-switch.jpg", 3, "", quandCree + 120_000,
                exigence = "switch"),
        ),
    )

    @Test
    fun `le recapitulatif porte la fiche du site et le chemin du dossier`() {
        val texte = recapitulatif(audit, paris)
        assertTrue(texte.contains("AUDIT — Carrefour City"))
        assertTrue(texte.contains("Rennes, Mail F. Mitterrand 89"))
        assertTrue(texte.contains("Pictures/Audits/Carrefour-City"))
        assertTrue(texte.contains("Interlocuteur : Le gérant"))
        assertTrue(texte.contains("Nacelle nécessaire : Non"))
        assertTrue(texte.contains("Durée estimée de l'installation : 2 h"))
        assertTrue(texte.contains("Prévoir une rallonge."))
    }

    @Test
    fun `chaque case du PV se retrouve dans le bloc de l'ecran`() {
        val texte = recapitulatif(audit, paris)
        assertTrue(texte.contains("ÉCRAN 1"))
        assertTrue(texte.contains("Emplacement : Zone marché"))
        assertTrue(texte.contains("Rayon : Fruits et Légumes"))
        assertTrue(texte.contains("Taille : 43″ · Portrait"))
        assertTrue(texte.contains("Support : Mural support fixe"))
        assertTrue(texte.contains("Nature de la surface : Tôle acier"))
        assertTrue(texte.contains("Prise électrique 24/24 : Oui"))
        assertTrue(texte.contains("Prise RJ45 : Non"))
        assertTrue(texte.contains("Prérequis à 2 m maximum : Oui"))
    }

    @Test
    fun `le cartouche de chaque ecran est rappele tel qu'il ira sur les photos`() {
        assertTrue(
            recapitulatif(audit, paris)
                .contains("Cartouche : CRF CITY - RENNES-FMITTERRAND89 - F/L 43' FIXE"),
        )
    }

    @Test
    fun `ce qui manque est annonce avant tout le reste`() {
        val texte = recapitulatif(audit, paris)
        val manques = texte.indexOf("IL MANQUE ENCORE")
        val site = texte.indexOf("\nSITE")
        assertTrue("la liste des manques doit venir en premier", manques in 1 until site)
        // Trois cases de l'écran 1 et les vues du magasin n'ont pas été prises.
        assertTrue(texte.contains("Écran 1 : Contraintes"))
        assertTrue(texte.contains("Écran 1 : Prises existantes"))
        assertTrue(texte.contains("Site : Vues du magasin"))
        // Celles qui sont faites n'y figurent pas.
        assertFalse(texte.contains("Écran 1 : Plan large"))
        assertFalse(texte.contains("Site : Switch en baie"))
    }

    @Test
    fun `la surface d'accroche n'est reclamee que pour un ecran suspendu`() {
        val mural = recapitulatif(audit, paris)
        assertFalse(mural.contains("Surface d'accroche"))

        val suspendu = audit.copy(ecrans = listOf(ecranUn.copy(support = Support.FILINS)))
        assertTrue(recapitulatif(suspendu, paris).contains("Écran 1 : Surface d'accroche"))
    }

    @Test
    fun `les photos sont listees avec la case qu'elles honorent`() {
        val texte = recapitulatif(audit, paris)
        assertTrue(texte.contains("Carrefour-City-E1-001-plan-large.jpg — Plan large — façade"))
        assertTrue(texte.contains("Carrefour-City-003-switch.jpg — Switch en baie"))
    }

    @Test
    fun `les photos d'un ecran sont listees sous son bloc, pas melangees au site`() {
        val lignes = recapitulatif(audit, paris).lines()
        val blocEcran = lignes.indexOfFirst { it == "ÉCRAN 1" }
        val blocSite = lignes.indexOfFirst { it == "PHOTOS DU SITE" }
        val planLarge = lignes.indexOfFirst { it.startsWith("Carrefour-City-E1-001") }
        val switch = lignes.indexOfFirst { it.startsWith("Carrefour-City-003") }
        assertTrue(planLarge in (blocEcran + 1) until blocSite)
        assertTrue(switch > blocSite)
    }

    @Test
    fun `un audit sans aucun ecran le dit en toutes lettres`() {
        val vide = audit.copy(ecrans = emptyList(), photos = emptyList())
        val texte = recapitulatif(vide, paris)
        assertTrue(texte.contains("Aucun emplacement d'écran relevé"))
        assertTrue(texte.contains("Aucune photo"))
        assertTrue(texte.contains("(aucune photo)"))
    }

    @Test
    fun `une case non tranchee se distingue d'un non`() {
        assertEquals("Oui", ouiNon(true))
        assertEquals("Non", ouiNon(false))
        // Sur un PV, « non » et « je n'ai pas regardé » n'ont pas les mêmes suites.
        assertEquals("—", ouiNon(null))
    }

    @Test
    fun `le pluriel des photos est juste`() {
        assertEquals("aucune photo", comptePhotos(0))
        assertEquals("1 photo", comptePhotos(1))
        assertEquals("12 photos", comptePhotos(12))
    }

    @Test
    fun `les cles d'exigences sont celles qu'on serialise`() {
        // Elles partent dans le catalogue JSON : les renommer sans migration
        // décrocherait silencieusement toutes les photos déjà rattachées.
        assertEquals(
            listOf("plan-large", "plan-rapproche", "accroche", "contraintes", "prises", "switch", "magasin"),
            Exigence.entries.map { it.cle },
        )
    }
}
