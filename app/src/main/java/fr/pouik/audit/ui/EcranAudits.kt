package fr.pouik.audit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.comptePhotos
import fr.pouik.audit.donnees.dateCourte
import fr.pouik.audit.donnees.slug

/**
 * La liste des audits, écran d'accueil.
 *
 * Les plus récents en haut : on photographie l'audit du jour, pas celui du mois
 * dernier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranAudits(
    audits: List<Audit>,
    pret: Boolean,
    onOuvre: (Audit) -> Unit,
    onNouveau: () -> Unit,
    onEdite: (Audit) -> Unit,
    onRenommeDossier: (Audit) -> Unit,
    onSupprime: (Audit) -> Unit,
) {
    var filtre by remember { mutableStateOf("") }
    // Le champ de recherche n'apparaît qu'une fois la liste assez longue pour
    // qu'on y cherche quelque chose : au-dessous, il ne fait que prendre la
    // place d'un audit.
    val cherchable = audits.size > 6

    val visibles = remember(audits, filtre) {
        val recents = audits.sortedByDescending { it.creeLe }
        if (filtre.isBlank()) {
            recents
        } else {
            val cle = slug(filtre).lowercase()
            recents.filter {
                slug("${it.nom} ${it.lieu}").lowercase().contains(cle)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Audits") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNouveau,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nouvel audit") },
            )
        },
    ) { marges ->
        Column(Modifier.padding(marges).fillMaxSize()) {
            if (cherchable) {
                OutlinedTextField(
                    value = filtre,
                    onValueChange = { filtre = it },
                    placeholder = { Text("Chercher un site") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            when {
                !pret -> Unit
                audits.isEmpty() -> Vide(
                    titre = "Aucun audit",
                    explication = "Crée un audit par site à visiter : ses photos iront " +
                        "dans son propre dossier au lieu de s'empiler dans la galerie.",
                    modifier = Modifier.fillMaxSize(),
                )
                visibles.isEmpty() -> Vide(
                    titre = "Rien à ce nom",
                    explication = "Aucun audit ne correspond à « $filtre ».",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        // Place pour que le dernier audit ne finisse pas sous le
                        // bouton flottant.
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibles, key = { it.id }) { audit ->
                        CarteAudit(
                            audit = audit,
                            onOuvre = { onOuvre(audit) },
                            onEdite = { onEdite(audit) },
                            onRenommeDossier = { onRenommeDossier(audit) },
                            onSupprime = { onSupprime(audit) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarteAudit(
    audit: Audit,
    onOuvre: () -> Unit,
    onEdite: () -> Unit,
    onRenommeDossier: () -> Unit,
    onSupprime: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    Card(
        onClick = onOuvre,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Vignette(audit.photos.maxByOrNull { it.numero }?.uri, taille = 64)
            EspaceH(12)
            Column(Modifier.weight(1f)) {
                Text(
                    audit.nom,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                if (audit.lieu.isNotBlank()) {
                    Text(
                        audit.lieu,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    "${comptePhotos(audit.photos.size)} · ${dateCourte(audit.creeLe)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Modifier la fiche") },
                        onClick = { menu = false; onEdite() },
                    )
                    DropdownMenuItem(
                        text = { Text("Aligner le dossier sur le nom") },
                        onClick = { menu = false; onRenommeDossier() },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer") },
                        onClick = { menu = false; onSupprime() },
                    )
                }
            }
        }
    }
}

/**
 * Vignette d'un cliché, ou un carré sobre quand l'audit n'a pas encore de photo.
 *
 * Coil décode une version réduite : afficher trente JPEG de douze mégapixels à
 * leur taille réelle dans une liste, c'est trente OutOfMemory.
 */
@Composable
fun Vignette(uri: String?, taille: Int, modifier: Modifier = Modifier) {
    val forme = RoundedCornerShape(8.dp)
    if (uri == null) {
        Box(
            modifier
                .size(taille.dp)
                .clip(forme)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(taille.dp).clip(forme),
        )
    }
}
