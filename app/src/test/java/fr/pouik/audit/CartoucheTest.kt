package fr.pouik.audit

import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.Ecran
import fr.pouik.audit.donnees.Support
import fr.pouik.audit.donnees.abrege
import fr.pouik.audit.donnees.abregeLieu
import fr.pouik.audit.donnees.abregeRayon
import fr.pouik.audit.donnees.cartouche
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le cartouche est la seule chose que le client lit sur chaque photo.
 *
 * Sa forme vient de son propre tutoriel — `CRF CITY - TOULOUSE-Muret231 - F/L 32' FIXE` —
 * et c'est elle qu'on reproduit ici, abréviations comprises.
 */
class CartoucheTest {

    private val audit = Audit(
        id = "x",
        nom = "Carrefour City",
        lieu = "Toulouse, rue Muret 231",
        creeLe = 1L,
        dossier = "Carrefour-City",
    )

    private val ecran = Ecran(
        id = "e1",
        numero = 1,
        rayon = "Fruits et Légumes",
        taille = 32,
        support = Support.MURAL_FIXE,
    )

    @Test
    fun `le cartouche reprend la forme du tutoriel client`() {
        assertEquals(
            "CRF CITY - TOULOUSE-MURET231 - F/L 32' FIXE",
            cartouche(audit, ecran),
        )
    }

    @Test
    fun `carrefour s'abrege en CRF, le reste passe en capitales`() {
        assertEquals("CRF CITY", abrege("Carrefour City"))
        assertEquals("CRF CONTACT", abrege("carrefour contact"))
        assertEquals("CRF MARKET", abrege("Carrefour  Market"))
        // Une enseigne qu'on ne connaît pas n'est pas inventée.
        assertEquals("PROXI CENTRE", abrege("Proxi Centre"))
    }

    @Test
    fun `le lieu perd ses mots de liaison et garde ses reperes`() {
        assertEquals("TOULOUSE-MURET231", abregeLieu("Toulouse, rue Muret 231"))
        // « Mail » est un type de voie, au même titre que rue ou avenue : il saute.
        assertEquals("RENNES-FMITTERRAND89", abregeLieu("Rennes, Mail F. Mitterrand 89"))
        assertEquals("BEAUVAIS-HACHETTE", abregeLieu("Beauvais, avenue Hachette"))
        assertEquals("", abregeLieu("   "))
    }

    @Test
    fun `seul le rayon que le client abrege lui-meme est abrege`() {
        assertEquals("F/L", abregeRayon("Fruits et Légumes"))
        assertEquals("F/L", abregeRayon("fruits legumes"))
        assertEquals("CAISSES", abregeRayon("Caisses"))
        assertEquals("ENTRÉE", abregeRayon("Entrée"))
        assertEquals("", abregeRayon(""))
    }

    @Test
    fun `un cartouche incomplet reste posable`() {
        // Sur place, la taille n'est pas toujours tranchée : mieux vaut une légende
        // partielle qu'aucune, elle se complète ensuite dans l'éditeur.
        assertEquals(
            "CRF CITY - TOULOUSE-MURET231 - F/L",
            cartouche(audit, ecran.copy(taille = null, support = null)),
        )
        assertEquals(
            "CRF CITY - TOULOUSE-MURET231",
            cartouche(audit, ecran.copy(rayon = "", taille = null, support = null)),
        )
    }

    @Test
    fun `une photo du site, sans ecran, garde le magasin et le lieu`() {
        assertEquals("CRF CITY - TOULOUSE-MURET231", cartouche(audit, null))
    }

    @Test
    fun `chaque support a son abreviation`() {
        assertEquals("CRF CITY - F/L 32' MAT", cartouche(
            audit.copy(lieu = ""),
            ecran.copy(support = Support.MAT_PLAFOND),
        ))
        assertEquals("CRF CITY - F/L 32' FILINS", cartouche(
            audit.copy(lieu = ""),
            ecran.copy(support = Support.FILINS),
        ))
    }
}
