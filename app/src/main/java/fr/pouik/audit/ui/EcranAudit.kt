package fr.pouik.audit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.pouik.audit.R
import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.Photo
import fr.pouik.audit.donnees.cheminRelatif
import fr.pouik.audit.donnees.comptePhotos
import fr.pouik.audit.donnees.date

/**
 * Un audit ouvert : sa fiche, ses clichés, et le bouton qui sert le plus souvent.
 *
 * Les photos sont rangées par numéro, c'est-à-dire par ordre de prise de vue : en
 * audit on suit un parcours — façade, entrée, emplacement, alimentation — et
 * relire les photos dans l'ordre de la visite est ce qui fait le compte-rendu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranAudit(
    audit: Audit,
    onRetour: () -> Unit,
    onPhotographie: () -> Unit,
    onOuvrePhoto: (Photo) -> Unit,
    onEditeFiche: () -> Unit,
    onPartagePhotos: () -> Unit,
    onPartageRecap: () -> Unit,
    onCopieChemin: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val photos = remember(audit.photos) { audit.photos.sortedBy { it.numero } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(audit.nom, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                        Text(
                            comptePhotos(photos.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onRetour) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Modifier la fiche") },
                                onClick = { menu = false; onEditeFiche() },
                            )
                            DropdownMenuItem(
                                text = { Text("Envoyer les photos") },
                                onClick = { menu = false; onPartagePhotos() },
                            )
                            DropdownMenuItem(
                                text = { Text("Envoyer le récapitulatif") },
                                onClick = { menu = false; onPartageRecap() },
                            )
                            DropdownMenuItem(
                                text = { Text("Copier le chemin du dossier") },
                                onClick = { menu = false; onCopieChemin() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPhotographie,
                icon = { Icon(painterResource(R.drawable.ic_photo), contentDescription = null) },
                text = { Text("Photographier") },
            )
        },
    ) { marges ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 108.dp),
            modifier = Modifier.padding(marges).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EnteteFiche(audit)
            }
            if (photos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Vide(
                        titre = "Aucune photo",
                        explication = "Appuie sur « Photographier » : les clichés iront " +
                            "directement dans ${cheminRelatif(audit.dossier)}.",
                    )
                }
            }
            items(photos, key = { it.uri }) { photo ->
                CasePhoto(photo) { onOuvrePhoto(photo) }
            }
        }
    }
}

@Composable
private fun EnteteFiche(audit: Audit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (audit.lieu.isNotBlank()) {
            Text(audit.lieu, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Créé le ${date(audit.creeLe)} · ${cheminRelatif(audit.dossier)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (audit.notes.isNotBlank()) {
            EspaceV(8)
            Text(
                audit.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Une case de la grille : la photo, son numéro, sa légende s'il y en a une.
 *
 * Le bandeau sombre n'est pas décoratif : une légende en blanc sur une photo de
 * façade au soleil est illisible, et c'est justement le genre de photo qu'on
 * prend ici.
 */
@Composable
private fun CasePhoto(photo: Photo, onOuvre: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOuvre),
    ) {
        AsyncImage(
            model = requeteImage(LocalContext.current, photo),
            contentDescription = photo.legende.ifBlank { "Photo ${photo.numero}" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            photo.numero.toString().padStart(3, '0'),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
        // Un point sur les clichés déjà annotés : en relisant trente photos, savoir
        // lesquelles sont traitées évite de refaire deux fois le même travail.
        if (photo.annotations.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (photo.legende.isNotBlank()) {
            Text(
                photo.legende,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            )
        }
    }
}
