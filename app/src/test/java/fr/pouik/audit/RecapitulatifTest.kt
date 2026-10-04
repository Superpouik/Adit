package fr.pouik.audit

import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.Photo
import fr.pouik.audit.donnees.comptePhotos
import fr.pouik.audit.donnees.recapitulatif
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Le récapitulatif est ce que lira le collègue qui reçoit le mail : il doit
 * permettre de relier chaque pièce jointe à ce qu'elle montre, sans l'app.
 */
class RecapitulatifTest {

    private val paris = ZoneId.of("Europe/Paris")

    // 4 octobre 2026, 14 h 32 à Paris.
    private val quandCree = 1_791_117_120_000L

    private val audit = Audit(
        id = "x",
        nom = "Lidl Vitrolles",
        lieu = "ZI les Estroublans",
        notes = "Prévoir une rallonge.",
        creeLe = quandCree,
        dossier = "Lidl-Vitrolles",
        photos = listOf(
            Photo("u2", "Lidl-Vitrolles-002.jpg", 2, "", quandCree + 60_000),
            Photo("u1", "Lidl-Vitrolles-001-facade.jpg", 1, "façade", quandCree),
        ),
    )

    @Test
    fun `le recapitulatif porte la fiche et le chemin du dossier`() {
        val texte = recapitulatif(audit, paris)
        assertTrue(texte.contains("AUDIT — Lidl Vitrolles"))
        assertTrue(texte.contains("ZI les Estroublans"))
        assertTrue(texte.contains("Pictures/Audits/Lidl-Vitrolles"))
        assertTrue(texte.contains("Prévoir une rallonge."))
        assertTrue(texte.contains("2 photos"))
    }

    @Test
    fun `les photos sont listees dans l'ordre des prises`() {
        val lignes = recapitulatif(audit, paris).lines()
        val un = lignes.indexOfFirst { it.startsWith("Lidl-Vitrolles-001") }
        val deux = lignes.indexOfFirst { it.startsWith("Lidl-Vitrolles-002") }
        assertTrue("001 absent", un >= 0)
        assertTrue("002 absent", deux >= 0)
        assertTrue("l'ordre suit la liste, pas la prise de vue", un < deux)
    }

    @Test
    fun `une photo sans legende le dit, au lieu de laisser un blanc`() {
        val texte = recapitulatif(audit, paris)
        assertTrue(texte.contains("Lidl-Vitrolles-002.jpg — (sans légende)"))
        assertTrue(texte.contains("Lidl-Vitrolles-001-facade.jpg — façade"))
    }

    @Test
    fun `un audit vide reste lisible`() {
        val vide = audit.copy(photos = emptyList(), notes = "", lieu = "")
        val texte = recapitulatif(vide, paris)
        assertTrue(texte.contains("Aucune photo"))
        assertTrue(!texte.contains("NOTES"))
        assertTrue(!texte.contains("PHOTOS"))
    }

    @Test
    fun `le pluriel des photos est juste`() {
        assertEquals("aucune photo", comptePhotos(0))
        assertEquals("1 photo", comptePhotos(1))
        assertEquals("12 photos", comptePhotos(12))
    }
}
