package fr.pouik.audit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.pouik.audit.donnees.Forme

/** Le rail vertical de l'écran déplié : tous les outils visibles d'un coup. */
@Composable
fun RailOutils(actif: Outil, onChoisit: (Outil) -> Unit) {
    Column(
        Modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Outil.entries.forEach { BoutonOutil(it, it == actif, avecLibelle = true) { onChoisit(it) } }
    }
}

/**
 * La barre du bas, en écran replié.
 *
 * Défilante plutôt que compressée : huit outils dans la largeur d'un téléphone fermé
 * donneraient des cibles de 40 dp, sous le seuil où l'on touche juste du premier coup.
 */
@Composable
fun BarreOutils(actif: Outil, onChoisit: (Outil) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Outil.entries.forEach { BoutonOutil(it, it == actif, avecLibelle = false) { onChoisit(it) } }
    }
}

@Composable
private fun BoutonOutil(
    outil: Outil,
    actif: Boolean,
    avecLibelle: Boolean,
    onClic: () -> Unit,
) {
    val fond = if (actif) MaterialTheme.colorScheme.primary else Color.Transparent
    val teinte = if (actif) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(fond)
            .clickable(onClick = onClic)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconeOutil(outil, teinte)
        if (avecLibelle) {
            Text(
                outil.libelle,
                style = MaterialTheme.typography.labelSmall,
                color = teinte,
            )
        }
    }
}

/** Panneau latéral de l'écran déplié : tous les réglages dépliés, rien de caché. */
@Composable
fun PanneauReglages(
    modifier: Modifier = Modifier,
    outil: Outil,
    selection: Forme?,
    couleur: Long,
    opacite: Float,
    epaisseur: Float,
    force: Float,
    onCouleur: (Long) -> Unit,
    onOpacite: (Float) -> Unit,
    onEpaisseur: (Float) -> Unit,
    onForce: (Float) -> Unit,
    onEditeTexte: () -> Unit,
    onSupprime: () -> Unit,
    onToutEffacer: () -> Unit,
) {
    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (selection == null) "Prochain tracé" else "Forme sélectionnée",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )

        val cible = selection
        val montreCouleur = cible !is Forme.Flou
        if (montreCouleur) {
            Palette(couleur, enGrille = true, onChoisit = onCouleur)
            Reglage("Opacité", opacite, 0.2f..1f, onOpacite)
        }
        if (cible is Forme.Flou || (cible == null && outil == Outil.FLOU)) {
            Reglage("Grain du flou", force, 0.005f..0.08f, onForce)
        }
        if (cible !is Forme.Texte && cible !is Forme.Flou) {
            Reglage("Épaisseur", epaisseur, 0.002f..0.02f, onEpaisseur)
        }

        if (cible is Forme.Texte) {
            OutlinedButton(onClick = onEditeTexte, modifier = Modifier.fillMaxWidth()) {
                Text("Modifier le texte")
            }
        }
        if (cible != null) {
            OutlinedButton(onClick = onSupprime, modifier = Modifier.fillMaxWidth()) {
                Text("Supprimer la forme", color = MaterialTheme.colorScheme.error)
            }
        }
        TextButton(onClick = onToutEffacer, modifier = Modifier.fillMaxWidth()) {
            Text("Tout effacer", color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * La même matière, en une bande au-dessus de la barre d'outils.
 *
 * Un seul curseur à la fois, celui qui concerne ce qu'on tient : empiler trois réglages
 * sur un téléphone replié mangerait le tiers de la photo.
 */
@Composable
fun ReglagesCompacts(
    outil: Outil,
    selection: Forme?,
    couleur: Long,
    opacite: Float,
    epaisseur: Float,
    force: Float,
    onCouleur: (Long) -> Unit,
    onOpacite: (Float) -> Unit,
    onEpaisseur: (Float) -> Unit,
    onForce: (Float) -> Unit,
    onEditeTexte: () -> Unit,
    onSupprime: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selection !is Forme.Flou) {
                Palette(couleur, enGrille = false, onChoisit = onCouleur, modifier = Modifier.weight(1f))
            } else {
                Box(Modifier.weight(1f))
            }
            if (selection is Forme.Texte) {
                TextButton(onClick = onEditeTexte) { Text("Texte") }
            }
            if (selection != null) {
                TextButton(onClick = onSupprime) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        when {
            selection is Forme.Flou || (selection == null && outil == Outil.FLOU) ->
                Reglage("Grain", force, 0.005f..0.08f, onForce)
            selection is Forme.Texte -> Reglage("Opacité", opacite, 0.2f..1f, onOpacite)
            selection != null -> Reglage("Épaisseur", epaisseur, 0.002f..0.02f, onEpaisseur)
            outil == Outil.RECTANGLE -> Reglage("Opacité", opacite, 0.2f..1f, onOpacite)
            else -> Reglage("Épaisseur", epaisseur, 0.002f..0.02f, onEpaisseur)
        }
    }
}

@Composable
private fun Palette(
    actuelle: Long,
    enGrille: Boolean,
    onChoisit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pastille: @Composable (Long) -> Unit = { teinte ->
        val choisie = (teinte and 0x00FFFFFFL) == (actuelle and 0x00FFFFFFL)
        Box(
            Modifier
                .size(if (enGrille) 44.dp else 34.dp)
                .clip(CircleShape)
                .background(Color(teinte.toInt()))
                .border(
                    width = if (choisie) 3.dp else 1.dp,
                    color = if (choisie) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                )
                .clickable { onChoisit(teinte) },
        )
    }

    if (enGrille) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
            PALETTE.chunked(4).forEach { rangee ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rangee.forEach { pastille(it) }
                }
            }
        }
    } else {
        Row(
            modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PALETTE.forEach { pastille(it) }
        }
    }
}

@Composable
private fun Reglage(
    libelle: String,
    valeur: Float,
    plage: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(
            libelle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = valeur.coerceIn(plage),
            onValueChange = onChange,
            valueRange = plage,
        )
    }
}

@Composable
fun DialogueTexte(
    initial: SaisieTexte,
    onFerme: () -> Unit,
    onValide: (String, Boolean) -> Unit,
) {
    var contenu by remember(initial) { mutableStateOf(initial.contenu) }
    var avecFond by remember(initial) { mutableStateOf(initial.avecFond) }

    AlertDialog(
        onDismissRequest = onFerme,
        title = { Text(if (initial.idExistant == null) "Ajouter du texte" else "Modifier le texte") },
        text = {
            Column {
                OutlinedTextField(
                    value = contenu,
                    onValueChange = { contenu = it },
                    label = { Text("Texte") },
                    // Plusieurs lignes dès l'ouverture : le cartouche d'un audit tient
                    // rarement en une, entre l'enseigne, l'adresse et le matériel.
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                EspaceV(8)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = avecFond, onCheckedChange = { avecFond = it })
                    Text("Cartouche blanc derrière le texte")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onValide(contenu, avecFond) },
                enabled = contenu.isNotBlank(),
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onFerme) { Text("Annuler") } },
    )
}
