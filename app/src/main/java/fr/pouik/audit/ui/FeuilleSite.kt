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
import androidx.compose.ui.unit.dp
import fr.pouik.audit.donnees.Audit

/**
 * Ce que le PV demande sur le site lui-même, par opposition à chaque écran.
 *
 * Quatre champs seulement, mais deux d'entre eux sont réclamés « obligatoirement » par
 * le document : la durée estimée de l'installation, et la nécessité d'une nacelle —
 * cette dernière décidant de la venue d'un engin que personne ne peut improviser le
 * jour de la pose.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleSite(
    audit: Audit,
    onFerme: () -> Unit,
    onEnregistre: (
        interlocuteur: String,
        contraintes: String,
        nacelle: Boolean?,
        hauteur: String,
        duree: String,
    ) -> Unit,
) {
    var interlocuteur by remember(audit.id) { mutableStateOf(audit.interlocuteur) }
    var contraintes by remember(audit.id) { mutableStateOf(audit.contraintes) }
    var nacelle by remember(audit.id) { mutableStateOf(audit.nacelle) }
    var hauteur by remember(audit.id) { mutableStateOf(audit.hauteurPrerequis) }
    var duree by remember(audit.id) { mutableStateOf(audit.dureeInstallation) }
    val etat = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onFerme, sheetState = etat) {
        Column(
            Modifier
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Fiche du site", style = MaterialTheme.typography.titleLarge)
                EspaceV(16)

                Champ(
                    interlocuteur,
                    "Interlocuteur sur site",
                    { interlocuteur = it },
                    aide = "Gérant, ou l'adjoint désigné par téléphone",
                )
                EspaceV(8)
                OutlinedTextField(
                    value = contraintes,
                    onValueChange = { contraintes = it },
                    label = { Text("Contraintes") },
                    placeholder = { Text("Horaires d'ouverture, accès, sécurité…") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                EspaceV(12)

                TroisEtats("Nacelle nécessaire pour le câblage", nacelle) { nacelle = it }
                if (nacelle == true) {
                    // Le PV ne réclame la hauteur que dans ce cas : l'afficher toujours
                    // ferait un champ vide à ignorer neuf fois sur dix.
                    EspaceV(4)
                    Champ(
                        hauteur,
                        "Hauteur des prérequis",
                        { hauteur = it },
                        aide = "À destination du prestataire de câblage",
                    )
                }
                EspaceV(12)
                Champ(
                    duree,
                    "Durée estimée de l'installation",
                    { duree = it },
                    aide = "Mentionnée comme obligatoire sur le PV",
                )
            }

            EspaceV(16)
            Button(
                onClick = {
                    onEnregistre(
                        interlocuteur,
                        contraintes,
                        nacelle,
                        if (nacelle == true) hauteur else "",
                        duree,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enregistrer")
            }
        }
    }
}
