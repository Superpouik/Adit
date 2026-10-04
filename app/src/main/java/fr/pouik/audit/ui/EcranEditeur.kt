package fr.pouik.audit.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.pouik.audit.donnees.Forme
import fr.pouik.audit.donnees.avecPoignee
import fr.pouik.audit.donnees.deplacee
import fr.pouik.audit.donnees.redresse

/**
 * L'éditeur d'annotations.
 *
 * Pensé d'abord pour l'écran déplié : la photo au centre, les outils en rail à gauche,
 * les réglages à droite — tout atteignable sans masquer l'image. Replié, la même
 * matière se réorganise en colonne, la photo gardant la plus grosse part.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranEditeur(
    titre: String,
    image: Bitmap?,
    initiales: List<Forme>,
    /** La légende imposée par le client, déjà composée depuis la fiche de l'écran. */
    cartouche: String,
    enregistrement: Boolean,
    onRetour: () -> Unit,
    onEnregistre: (List<Forme>) -> Unit,
) {
    var formes by remember { mutableStateOf(initiales) }
    var historique by remember { mutableStateOf<List<List<Forme>>>(emptyList()) }
    var selection by remember { mutableStateOf<String?>(null) }
    var outil by remember { mutableStateOf(Outil.RECTANGLE) }
    var couleur by remember { mutableStateOf(PALETTE[0]) }
    var opacite by remember { mutableStateOf(1f) }
    var epaisseur by remember { mutableStateOf(fr.pouik.audit.donnees.EPAISSEUR_DEFAUT) }
    var force by remember { mutableStateOf(fr.pouik.audit.donnees.FLOU_DEFAUT) }
    var brouillon by remember { mutableStateOf<Forme?>(null) }
    var depart by remember { mutableStateOf(Offset.Zero) }
    var saisie by remember { mutableStateOf<SaisieTexte?>(null) }
    var confirmeSortie by remember { mutableStateOf(false) }

    val modifie = formes != initiales

    fun pousse() {
        // Vingt états suffisent largement et bornent la mémoire : chaque entrée est une
        // copie de la liste des formes.
        historique = (historique + listOf(formes)).takeLast(20)
    }

    fun annule() {
        historique.lastOrNull()?.let {
            formes = it
            historique = historique.dropLast(1)
            selection = null
        }
    }

    /**
     * Changer d'outil de tracé lâche la sélection.
     *
     * Sans ça, choisir « Trait » puis une couleur repeignait la forme d'avant au lieu
     * de préparer la suivante : la palette agit sur la sélection quand il y en a une,
     * et prendre un outil de dessin veut dire qu'on en a fini avec la forme précédente.
     * L'outil Sélection, lui, garde ce qui est sélectionné — c'est sa raison d'être.
     */
    fun choisitOutil(nouveau: Outil) {
        if (nouveau != Outil.SELECTION) selection = null
        outil = nouveau
    }

    /**
     * Pose la légende réglementaire en haut à gauche, sur son cartouche blanc.
     *
     * Les quatre informations qu'elle contient — magasin, rayon, taille, support — sont
     * les mêmes pour toutes les photos d'un même écran : les retaper à chaque cliché
     * est le genre de corvée qui finit par produire des légendes qui ne se ressemblent
     * pas. Elle reste déplaçable et modifiable comme n'importe quel texte.
     */
    fun poseCartouche() {
        if (cartouche.isBlank()) return
        pousse()
        val texte = Formes.texte(0.03f, 0.03f, cartouche, 0xFF000000L, 0xFFFFFFFFL)
        formes = formes + texte
        selection = texte.id
        outil = Outil.SELECTION
    }

    fun modifieSelection(transforme: (Forme) -> Forme) {
        val cible = selection ?: return
        formes = formes.map { if (it.id == cible) transforme(it) else it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titre, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { if (modifie) confirmeSortie = true else onRetour() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { annule() }, enabled = historique.isNotEmpty()) {
                        Icon(Icons.Default.Refresh, contentDescription = "Annuler la dernière action")
                    }
                    if (enregistrement) {
                        CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).width(24.dp))
                    } else {
                        TextButton(onClick = { onEnregistre(formes) }, enabled = modifie) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            EspaceH(4)
                            Text("Enregistrer")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
    ) { marges ->
        if (image == null) {
            Box(Modifier.fillMaxSize().padding(marges), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val zone: @Composable (Modifier) -> Unit = { mod ->
            ZoneDessin(
                image = image,
                formes = formes,
                brouillon = brouillon,
                selection = selection,
                outil = outil,
                modifier = mod,
                onCommence = { point ->
                    depart = point
                    brouillon = Formes.nouvelle(
                        outil = outil,
                        x = point.x,
                        y = point.y,
                        couleur = Formes.teinte(couleur, opacite),
                        epaisseur = epaisseur,
                        force = force,
                    )
                },
                onTire = { point ->
                    brouillon = brouillon?.let {
                        Formes.etire(it, depart.x, depart.y, point.x, point.y)
                    }
                },
                onFini = {
                    val finie = brouillon
                    brouillon = null
                    if (finie != null && Formes.estViable(finie)) {
                        pousse()
                        formes = formes + finie
                        // La forme qu'on vient de tracer est sélectionnée : neuf fois
                        // sur dix, le geste suivant est d'ajuster sa couleur ou sa
                        // taille, pas d'en tracer une autre à l'aveugle.
                        selection = finie.id
                    }
                },
                onSelectionne = { selection = it },
                onDeplace = { dx, dy -> modifieSelection { it.deplacee(dx, dy) } },
                onPoignee = { index, nx, ny, ancrage ->
                    modifieSelection { avecPoignee(it, index, nx, ny, ancrage) }
                },
                onPoseTexte = { x, y -> saisie = SaisieTexte(x = x, y = y) },
                onHistorique = { pousse() },
            )
        }

        BoxWithConstraints(Modifier.padding(marges).fillMaxSize()) {
            // 720 dp : au-delà, l'écran déplié a de quoi loger le rail et le panneau
            // sans rogner la photo. En deçà — téléphone replié — tout passe en colonne.
            val large = maxWidth >= 720.dp

            if (large) {
                Row(Modifier.fillMaxSize()) {
                    RailOutils(outil) { choisitOutil(it) }
                    Box(Modifier.weight(1f)) { zone(Modifier) }
                    PanneauReglages(
                        modifier = Modifier.width(300.dp),
                        outil = outil,
                        selection = formes.firstOrNull { it.id == selection },
                        couleur = couleur,
                        opacite = opacite,
                        epaisseur = epaisseur,
                        force = force,
                        cartouche = cartouche,
                        onCartouche = { poseCartouche() },
                        onCouleur = {
                            couleur = it
                            modifieSelection { f -> Formes.avecCouleur(f, Formes.teinte(it, opacite)) }
                        },
                        onOpacite = {
                            opacite = it
                            modifieSelection { f -> Formes.avecOpacite(f, it) }
                        },
                        onEpaisseur = {
                            epaisseur = it
                            modifieSelection { f -> Formes.avecEpaisseur(f, it) }
                        },
                        onForce = {
                            force = it
                            modifieSelection { f ->
                                if (f is Forme.Flou) f.copy(force = it) else f
                            }
                        },
                        onEditeTexte = {
                            val t = formes.firstOrNull { it.id == selection } as? Forme.Texte
                            if (t != null) saisie = SaisieTexte(t.x, t.y, t.contenu, t.fond != null, t.id)
                        },
                        onRedresse = {
                            pousse()
                            modifieSelection { f ->
                                if (f is Forme.Rectangle) f.redresse() else f
                            }
                        },
                        onSupprime = {
                            pousse()
                            formes = formes.filterNot { it.id == selection }
                            selection = null
                        },
                        onToutEffacer = {
                            pousse()
                            formes = emptyList()
                            selection = null
                        },
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) { zone(Modifier) }
                    ReglagesCompacts(
                        outil = outil,
                        selection = formes.firstOrNull { it.id == selection },
                        couleur = couleur,
                        opacite = opacite,
                        epaisseur = epaisseur,
                        force = force,
                        cartouche = cartouche,
                        onCartouche = { poseCartouche() },
                        onCouleur = {
                            couleur = it
                            modifieSelection { f -> Formes.avecCouleur(f, Formes.teinte(it, opacite)) }
                        },
                        onOpacite = {
                            opacite = it
                            modifieSelection { f -> Formes.avecOpacite(f, it) }
                        },
                        onEpaisseur = {
                            epaisseur = it
                            modifieSelection { f -> Formes.avecEpaisseur(f, it) }
                        },
                        onForce = {
                            force = it
                            modifieSelection { f -> if (f is Forme.Flou) f.copy(force = it) else f }
                        },
                        onEditeTexte = {
                            val t = formes.firstOrNull { it.id == selection } as? Forme.Texte
                            if (t != null) saisie = SaisieTexte(t.x, t.y, t.contenu, t.fond != null, t.id)
                        },
                        onRedresse = {
                            pousse()
                            modifieSelection { f ->
                                if (f is Forme.Rectangle) f.redresse() else f
                            }
                        },
                        onSupprime = {
                            pousse()
                            formes = formes.filterNot { it.id == selection }
                            selection = null
                        },
                    )
                    BarreOutils(outil) { choisitOutil(it) }
                }
            }
        }
    }

    saisie?.let { en ->
        DialogueTexte(
            initial = en,
            onFerme = { saisie = null },
            onValide = { contenu, avecFond ->
                pousse()
                val fond = if (avecFond) 0xFFFFFFFFL else null
                // Un texte sur fond blanc reste noir : écrire en blanc sur blanc est la
                // faute la plus facile à commettre, et elle ne se voit qu'à l'export.
                val teinteTexte = if (avecFond && couleur == 0xFFFFFFFFL) 0xFF000000L else couleur
                formes = if (en.idExistant != null) {
                    formes.map {
                        if (it.id == en.idExistant && it is Forme.Texte) {
                            it.copy(contenu = contenu, fond = fond)
                        } else {
                            it
                        }
                    }
                } else {
                    val nouveau = Formes.texte(en.x, en.y, contenu, teinteTexte, fond)
                    selection = nouveau.id
                    // Garder l'outil Texte actif ferait naître un second cartouche au
                    // prochain contact avec la photo, alors qu'on cherche à placer
                    // celui-ci.
                    outil = Outil.SELECTION
                    formes + nouveau
                }
                saisie = null
            },
        )
    }

    if (confirmeSortie) {
        Confirmation(
            titre = "Quitter sans enregistrer ?",
            texte = "Les annotations posées depuis la dernière sauvegarde seront perdues.",
            libelleAction = "Quitter",
            onConfirme = { confirmeSortie = false; onRetour() },
            onAnnule = { confirmeSortie = false },
        )
    }
}

/** Ce que le dialogue de saisie a besoin de savoir : où écrire, et quoi modifier. */
data class SaisieTexte(
    val x: Float,
    val y: Float,
    val contenu: String = "",
    val avecFond: Boolean = true,
    val idExistant: String? = null,
)
