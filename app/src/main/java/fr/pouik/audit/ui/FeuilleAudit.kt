package fr.pouik.audit.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.cheminRelatif
import fr.pouik.audit.donnees.dossierLibre

/**
 * La fiche d'un audit, en création comme en modification.
 *
 * Seul le nom est obligatoire : sur place, on crée l'audit en trois secondes et
 * on remplit le reste plus tard, dans la voiture. Le chemin du futur dossier est
 * affiché en direct pendant la frappe — c'est la seule façon de voir tout de
 * suite ce que donnera un nom à rallonge ou plein d'accents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleAudit(
    initial: Audit?,
    dossiersPris: Set<String>,
    onFerme: () -> Unit,
    onValide: (nom: String, lieu: String, notes: String) -> Unit,
) {
    var nom by remember(initial?.id) { mutableStateOf(initial?.nom ?: "") }
    var lieu by remember(initial?.id) { mutableStateOf(initial?.lieu ?: "") }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes ?: "") }
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onFerme, sheetState = etat) {
        Column(
            Modifier
                // Sans ça, le clavier recouvre « Créer et photographier » : la
                // feuille ne remonte pas d'elle-même, adjustResize ne vaut que
                // pour la fenêtre, pas pour ce qui est dessiné par-dessus.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                if (initial == null) "Nouvel audit" else "Modifier la fiche",
                style = MaterialTheme.typography.titleLarge,
            )
            EspaceV(16)

            Champ(
                valeur = nom,
                etiquette = "Nom du site",
                onChange = { nom = it },
                aide = if (initial == null) {
                    if (nom.isBlank()) {
                        "Le dossier prendra le nom du site"
                    } else {
                        "Dossier : " + cheminRelatif(dossierLibre(nom, dossiersPris))
                    }
                } else {
                    "Dossier actuel : " + cheminRelatif(initial.dossier)
                },
            )
            EspaceV(8)
            Champ(
                valeur = lieu,
                etiquette = "Ville ou adresse",
                onChange = { lieu = it },
            )
            EspaceV(8)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Default,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            EspaceV(20)

            Button(
                onClick = { onValide(nom, lieu, notes) },
                enabled = nom.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (initial == null) "Créer et photographier" else "Enregistrer")
            }
        }
    }
}
