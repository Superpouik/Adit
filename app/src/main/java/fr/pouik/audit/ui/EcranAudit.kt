package fr.pouik.audit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import fr.pouik.audit.donnees.Ecran
import fr.pouik.audit.donnees.Exigence
import fr.pouik.audit.donnees.Photo
import fr.pouik.audit.donnees.cartouche
import fr.pouik.audit.donnees.cheminRelatif
import fr.pouik.audit.donnees.comptePhotos
import fr.pouik.audit.donnees.date
import fr.pouik.audit.donnees.exigences
import fr.pouik.audit.donnees.manques
import fr.pouik.audit.donnees.photosDe

/**
 * Un audit ouvert, organisé comme le PV qu'il servira à remplir : le site, puis un bloc
 * par emplacement d'écran.
 *
 * Chaque bloc affiche les photos que le document réclame et coche celles qui sont
 * faites. Le vrai risque d'une prévisite n'est pas de mal annoter, c'est de quitter le
 * magasin sans un cliché obligatoire — ce qui ne se découvre qu'au bureau, trop tard.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EcranAudit(
    audit: Audit,
    onRetour: () -> Unit,
    onPhotographie: (String?) -> Unit,
    onOuvrePhoto: (Photo) -> Unit,
    onEditeFiche: () -> Unit,
    onFicheSite: () -> Unit,
    onEditeEcran: (Ecran) -> Unit,
    onAjouteEcran: () -> Unit,
    onPartagePhotos: () -> Unit,
    onPartageRecap: () -> Unit,
    onCopieChemin: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val manques = remember(audit) { audit.manques() }
    val ecrans = remember(audit.ecrans) { audit.ecrans.sortedBy { it.numero } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(audit.nom, maxLines = 1, style = MaterialTheme.typography.titleLarge)
                        Text(
                            comptePhotos(audit.photos.size),
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
                                text = { Text("Fiche du site") },
                                onClick = { menu = false; onFicheSite() },
                            )
                            DropdownMenuItem(
                                text = { Text("Modifier le nom et le lieu") },
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
                onClick = { onPhotographie(null) },
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
                Column {
                    EnteteFiche(audit)
                    if (manques.isNotEmpty()) {
                        EspaceV(8)
                        BandeauManques(manques.size)
                    }
                }
            }

            ecrans.forEach { ecran ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BlocEcran(
                        audit = audit,
                        ecran = ecran,
                        onFiche = { onEditeEcran(ecran) },
                        onPhotographie = { onPhotographie(ecran.id) },
                    )
                }
                val photos = audit.photosDe(ecran.id)
                if (photos.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TexteDoux("Aucune photo pour cet écran.")
                    }
                }
                items(photos, key = { it.uri }) { photo ->
                    CasePhoto(photo) { onOuvrePhoto(photo) }
                }
            }

            if (ecrans.size < 2) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(onClick = onAjouteEcran, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        EspaceH(8)
                        Text(
                            if (ecrans.isEmpty()) "Ajouter le premier écran" else "Ajouter le second écran",
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                BlocSite(audit) { onPhotographie(null) }
            }
            val photosSite = audit.photosDe(null)
            if (photosSite.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TexteDoux(
                        "Aucune photo générale. Le PV en réclame au moins une : celle du " +
                            "switch en baie informatique.",
                    )
                }
            }
            items(photosSite, key = { it.uri }) { photo ->
                CasePhoto(photo) { onOuvrePhoto(photo) }
            }
        }
    }
}

@Composable
private fun EnteteFiche(audit: Audit) {
    Column(Modifier.fillMaxWidth()) {
        if (audit.lieu.isNotBlank()) {
            Text(audit.lieu, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Créé le ${date(audit.creeLe)} · ${cheminRelatif(audit.dossier)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (audit.notes.isNotBlank()) {
            EspaceV(6)
            Text(
                audit.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le compte de ce qui manque, en tête d'écran.
 *
 * Volontairement sec et placé haut : c'est la seule information qu'il faut avoir vue
 * avant de reprendre la voiture.
 */
@Composable
private fun BandeauManques(combien: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (combien == 1) {
                "Il manque encore 1 élément au PV"
            } else {
                "Il manque encore $combien éléments au PV"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlocEcran(
    audit: Audit,
    ecran: Ecran,
    onFiche: () -> Unit,
    onPhotographie: () -> Unit,
) {
    val couvertes = remember(audit, ecran.id) {
        audit.photosDe(ecran.id).mapNotNull { it.exigence }.toSet()
    }
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Écran ${ecran.numero}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val resume = cartouche(audit, ecran)
                    Text(
                        resume.ifBlank { "Fiche à remplir" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (resume.isBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                TextButton(onClick = onFiche) { Text("Fiche") }
                TextButton(onClick = onPhotographie) { Text("Photos") }
            }
            EspaceV(6)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ecran.exigences().forEach { exigence ->
                    PuceExigence(exigence.libelle, exigence.cle in couvertes)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlocSite(audit: Audit, onPhotographie: () -> Unit) {
    val couvertes = remember(audit) {
        audit.photosDe(null).mapNotNull { it.exigence }.toSet()
    }
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Le site",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onPhotographie) { Text("Photos") }
            }
            EspaceV(6)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Exigence.pourSite().forEach { exigence ->
                    PuceExigence(exigence.libelle, exigence.cle in couvertes)
                }
            }
        }
    }
}

/** Faite ou pas faite — le coup d'œil doit suffire, sans compter ni lire. */
@Composable
private fun PuceExigence(libelle: String, couverte: Boolean) {
    val fond = if (couverte) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val encre = if (couverte) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Text(
        (if (couverte) "✓ " else "○ ") + libelle,
        style = MaterialTheme.typography.labelMedium,
        color = encre,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(fond)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun TexteDoux(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * Une case de la grille : la photo, son numéro, le rôle qu'elle remplit, sa légende.
 *
 * Le bandeau sombre n'est pas décoratif : une légende en blanc sur une photo de façade
 * au soleil est illisible, et c'est justement le genre de photo qu'on prend ici.
 */
@Composable
private fun CasePhoto(photo: Photo, onOuvre: () -> Unit) {
    val role = Exigence.parCle(photo.exigence)?.libelle
    val bas = listOfNotNull(role, photo.legende.ifBlank { null }).joinToString(" · ")
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
        if (bas.isNotBlank()) {
            Text(
                bas,
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
