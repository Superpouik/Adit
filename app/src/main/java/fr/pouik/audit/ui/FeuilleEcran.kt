package fr.pouik.audit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.Ecran
import fr.pouik.audit.donnees.Support
import fr.pouik.audit.donnees.TAILLES
import fr.pouik.audit.donnees.cartouche

/**
 * La fiche d'un emplacement d'écran : une case du PV par ligne, dans son ordre.
 *
 * Remplie sur place, elle évite de reconstituer le relevé de mémoire le soir. Le
 * cartouche s'affiche en direct pendant la saisie : c'est ce qui ira sur les photos, et
 * le voir se former dit tout de suite s'il manque une information.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeuilleEcran(
    audit: Audit,
    initial: Ecran,
    onFerme: () -> Unit,
    onEnregistre: (Ecran) -> Unit,
    onSupprime: (Ecran) -> Unit,
) {
    var brouillon by remember(initial.id) { mutableStateOf(initial) }
    var confirmeSuppression by remember { mutableStateOf(false) }
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
                Text("Écran ${brouillon.numero}", style = MaterialTheme.typography.titleLarge)
                EspaceV(4)
                Text(
                    cartouche(audit, brouillon).ifBlank { "Le cartouche se remplira ici" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                EspaceV(16)

                Champ(brouillon.emplacement, "Emplacement dans le magasin", {
                    brouillon = brouillon.copy(emplacement = it)
                })
                EspaceV(8)
                Champ(
                    brouillon.rayon,
                    "Rayon",
                    { brouillon = brouillon.copy(rayon = it) },
                    aide = "Tel qu'il doit apparaître : « Fruits et Légumes »",
                )
                EspaceV(12)

                Etiquette("Taille de l'écran")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAILLES.forEach { taille ->
                        FilterChip(
                            selected = brouillon.taille == taille,
                            onClick = {
                                brouillon = brouillon.copy(
                                    taille = if (brouillon.taille == taille) null else taille,
                                )
                            },
                            label = { Text("$taille″") },
                        )
                    }
                }
                // Le client le demande en toutes lettres, et c'est le genre de consigne
                // qu'on oublie devant un emplacement où le 32 rentrerait mieux.
                Text(
                    "Favoriser les grandes tailles : 43″ et surtout 50″",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EspaceV(12)

                Etiquette("Type de support")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Support.entries.forEach { support ->
                        FilterChip(
                            selected = brouillon.support == support,
                            onClick = {
                                brouillon = brouillon.copy(
                                    support = if (brouillon.support == support) null else support,
                                )
                            },
                            label = { Text(support.libelle) },
                        )
                    }
                }
                if (brouillon.support?.suspendu == true) {
                    Text(
                        "Suspendu : une photo de la surface d'accroche est exigée, et le " +
                            "client demande de limiter ce type de pose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                EspaceV(8)
                Champ(brouillon.natureSurface, "Nature de la surface", {
                    brouillon = brouillon.copy(natureSurface = it)
                })
                EspaceV(12)

                Etiquette("Orientation")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = brouillon.portrait,
                        onClick = { brouillon = brouillon.copy(portrait = true) },
                        label = { Text("Portrait") },
                    )
                    FilterChip(
                        selected = !brouillon.portrait,
                        onClick = { brouillon = brouillon.copy(portrait = false) },
                        label = { Text("Paysage") },
                    )
                }
                if (!brouillon.portrait) {
                    Text(
                        "Le PV impose le portrait : à ne mettre en paysage qu'avec une " +
                            "raison à écrire dans les remarques.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                EspaceV(12)

                TroisEtats("Prise électrique 24/24", brouillon.priseElectrique) {
                    brouillon = brouillon.copy(priseElectrique = it)
                }
                TroisEtats("Prise RJ45", brouillon.priseReseau) {
                    brouillon = brouillon.copy(priseReseau = it)
                }
                TroisEtats("Prérequis à 2 m maximum", brouillon.prerequisProches) {
                    brouillon = brouillon.copy(prerequisProches = it)
                }
                EspaceV(8)

                OutlinedTextField(
                    value = brouillon.remarques,
                    onValueChange = { brouillon = brouillon.copy(remarques = it) },
                    label = { Text("Remarques") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                EspaceV(8)
                TextButton(
                    onClick = { confirmeSuppression = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Supprimer cet écran",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            EspaceV(16)
            Button(
                onClick = { onEnregistre(brouillon) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enregistrer")
            }
        }
    }

    if (confirmeSuppression) {
        Confirmation(
            titre = "Supprimer l'écran ${brouillon.numero} ?",
            texte = "Ses photos sont conservées : elles redeviennent des photos du site.",
            libelleAction = "Supprimer",
            onConfirme = { confirmeSuppression = false; onSupprime(brouillon) },
            onAnnule = { confirmeSuppression = false },
        )
    }
}

@Composable
fun Etiquette(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EspaceV(4)
}

/**
 * Oui, non, ou pas encore regardé.
 *
 * Le troisième état n'est pas un luxe : sur un PV, « non » et « je n'ai pas vérifié »
 * n'ont pas du tout les mêmes conséquences pour le prestataire de câblage. Un simple
 * interrupteur forcerait à répondre avant d'avoir regardé.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroisEtats(libelle: String, valeur: Boolean?, onChange: (Boolean?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(libelle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = valeur == true,
                onClick = { onChange(if (valeur == true) null else true) },
                label = { Text("Oui") },
            )
            FilterChip(
                selected = valeur == false,
                onClick = { onChange(if (valeur == false) null else false) },
                label = { Text("Non") },
            )
        }
    }
}
