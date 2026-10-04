package fr.pouik.audit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Champ texte d'une ligne, celui des fiches d'audit. */
@Composable
fun Champ(
    valeur: String,
    etiquette: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    aide: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = valeur,
        onValueChange = onChange,
        label = { Text(etiquette) },
        supportingText = aide?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = imeAction),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Confirmation à deux boutons, pour tout ce qui détruit quelque chose. */
@Composable
fun Confirmation(
    titre: String,
    texte: String,
    libelleAction: String,
    onConfirme: () -> Unit,
    onAnnule: () -> Unit,
    destructif: Boolean = true,
    secondaire: Pair<String, () -> Unit>? = null,
) {
    AlertDialog(
        onDismissRequest = onAnnule,
        title = { Text(titre) },
        text = {
            Column {
                Text(texte)
                // L'action secondaire est annoncée dans le corps : un troisième
                // bouton sur la rangée d'AlertDialog passe à la ligne et donne
                // trois boutons empilés dont on ne sait plus lequel est lequel.
                secondaire?.let {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = it.second) { Text(it.first) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirme) {
                Text(
                    libelleAction,
                    color = if (destructif) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = { TextButton(onClick = onAnnule) { Text("Annuler") } },
    )
}

/** Le vide, expliqué : un écran blanc laisse croire à une panne. */
@Composable
fun Vide(titre: String, explication: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(titre, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            explication,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Un espace vertical, nommé pour que la mise en page se lise. */
@Composable
fun EspaceV(dp: Int) {
    Spacer(Modifier.height(dp.dp))
}

/** Un espace horizontal, pendant d'EspaceV. */
@Composable
fun EspaceH(dp: Int) {
    Spacer(Modifier.width(dp.dp))
}
