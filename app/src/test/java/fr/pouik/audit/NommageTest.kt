package fr.pouik.audit

import fr.pouik.audit.donnees.cheminRelatif
import fr.pouik.audit.donnees.dossierLibre
import fr.pouik.audit.donnees.nomPhoto
import fr.pouik.audit.donnees.slug
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le nommage est la seule chose que l'app fait sans qu'on puisse la relire à
 * l'œil : un nom de dossier fautif ne se voit qu'une fois le téléphone branché
 * sur un PC, trente photos plus tard.
 */
class NommageTest {

    @Test
    fun `les accents sont transcrits, pas effaces`() {
        assertEquals("Allee-des-Chenes", slug("Allée des Chênes"))
        assertEquals("Leclerc-Aix", slug("Leclerc Aix"))
        assertEquals("Cafe", slug("Café"))
    }

    @Test
    fun `tout ce qui genait un systeme de fichiers disparait`() {
        // Le vrai danger, ce sont la barre oblique (qui crée un sous-dossier) et
        // les deux-points (interdits sur un volume Windows ou FAT).
        val sale = slug("Carrefour / Vitrolles : zone 3")
        assertEquals("Carrefour-Vitrolles-zone-3", sale)
        assertFalse(sale.contains('/'))
        assertFalse(sale.contains(':'))
    }

    @Test
    fun `les separateurs ne s'accumulent pas et ne debordent pas`() {
        assertEquals("A-B", slug("  A  ---  B  "))
        assertEquals("", slug("???"))
        assertEquals("", slug(""))
    }

    @Test
    fun `un nom trop long est coupe sur un mot entier`() {
        val long = slug("Centre commercial Grand Littoral porte Est niveau moins un")
        assertTrue("longueur ${long.length}", long.length <= 40)
        // Coupé entre deux mots : pas de fragment comme « Litto ».
        assertFalse(long.endsWith("-"))
        assertTrue(long.startsWith("Centre-commercial-Grand-Littoral"))
    }

    @Test
    fun `un premier mot plus long que la limite est quand meme coupe`() {
        val mot = "a".repeat(60)
        assertEquals(40, slug(mot).length)
    }

    @Test
    fun `deux audits du meme nom n'habitent pas le meme dossier`() {
        val pris = setOf("Lidl-Vitrolles")
        assertEquals("Lidl-Vitrolles-2", dossierLibre("Lidl Vitrolles", pris))
        assertEquals(
            "Lidl-Vitrolles-3",
            dossierLibre("Lidl Vitrolles", pris + "Lidl-Vitrolles-2"),
        )
    }

    @Test
    fun `un nom vide donne un dossier utilisable`() {
        assertEquals("Audit", dossierLibre("", emptySet()))
        assertEquals("Audit-2", dossierLibre("!!!", setOf("Audit")))
    }

    @Test
    fun `les clicheces sont numerotes sur trois chiffres`() {
        assertEquals("Lidl-001.jpg", nomPhoto("Lidl", 1))
        assertEquals("Lidl-012.jpg", nomPhoto("Lidl", 12))
        // Au-delà de 999 on ne tronque pas : mieux vaut un nom à quatre chiffres
        // qu'un doublon.
        assertEquals("Lidl-1000.jpg", nomPhoto("Lidl", 1000))
    }

    @Test
    fun `le tri alphabetique des fichiers suit l'ordre des prises`() {
        val noms = listOf(2, 10, 1, 100).map { nomPhoto("Site", it) }.sorted()
        assertEquals(
            listOf("Site-001.jpg", "Site-002.jpg", "Site-010.jpg", "Site-100.jpg"),
            noms,
        )
    }

    @Test
    fun `la legende rejoint le nom du fichier`() {
        assertEquals("Lidl-003-prise-elec.jpg", nomPhoto("Lidl", 3, "prise élec"))
        assertEquals("Lidl-003.jpg", nomPhoto("Lidl", 3, "   "))
    }

    @Test
    fun `le chemin reste sous Pictures`() {
        assertEquals("Pictures/Audits/Lidl", cheminRelatif("Lidl"))
    }
}
