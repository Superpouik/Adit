package fr.pouik.audit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.pouik.audit.donnees.Photo
import fr.pouik.audit.donnees.heure

/**
 * Un cliché en grand, légende modifiable.
 *
 * Les photos défilent au doigt : relire un audit, c'est balayer la série dans
 * l'ordre de la visite, pas revenir à la grille entre chaque. Et la photo se
 * pince pour zoomer, seule façon de vérifier un numéro de série ou l'état d'une
 * prise sans ressortir l'appareil.
 */
@Composable
fun EcranPhoto(
    photos: List<Photo>,
    depart: String,
    onRetour: () -> Unit,
    onLegende: (Photo, String) -> Unit,
    onSupprime: (Photo) -> Unit,
    onPartage: (Photo) -> Unit,
    onAnnote: (Photo) -> Unit,
) {
    if (photos.isEmpty()) {
        // La dernière photo vient d'être supprimée : il n'y a plus rien à voir.
        LaunchedEffect(Unit) { onRetour() }
        return
    }

    val indexDepart = remember(depart) { photos.indexOfFirst { it.uri == depart }.coerceAtLeast(0) }
    val etat = rememberPagerState(initialPage = indexDepart, pageCount = { photos.size })
    // L'index courant peut dépasser après une suppression : le pager se
    // réajuste, mais on peut passer par une recomposition où il pointe trop loin.
    val courante = photos.getOrNull(etat.currentPage.coerceIn(0, photos.lastIndex)) ?: photos.last()

    var brouillon by remember(courante.uri) { mutableStateOf(courante.legende) }
    var aSupprimer by remember { mutableStateOf<Photo?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = etat, modifier = Modifier.fillMaxSize()) { page ->
            PhotoZoomable(photos[page])
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onRetour) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = Color.White,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${etat.currentPage + 1} / ${photos.size} · ${courante.fichier}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                Text(
                    "prise à ${heure(courante.priseLe)}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { onAnnote(courante) }) {
                Icon(Icons.Default.Edit, contentDescription = "Annoter", tint = Color.White)
            }
            IconButton(onClick = { onPartage(courante) }) {
                Icon(Icons.Default.Share, contentDescription = "Envoyer", tint = Color.White)
            }
            IconButton(onClick = { aSupprimer = courante }) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color.White)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = brouillon,
                onValueChange = { brouillon = it },
                placeholder = { Text("Légende") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onLegende(courante, brouillon) },
                ),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                ),
                modifier = Modifier.weight(1f),
            )
            EspaceH(8)
            Button(
                onClick = { onLegende(courante, brouillon) },
                enabled = brouillon != courante.legende,
            ) {
                Icon(Icons.Default.Check, contentDescription = "Enregistrer la légende")
            }
        }
    }

    aSupprimer?.let { cible ->
        Confirmation(
            titre = "Supprimer cette photo ?",
            texte = "Le fichier ${cible.fichier} sera supprimé du téléphone.",
            libelleAction = "Supprimer",
            onConfirme = { aSupprimer = null; onSupprime(cible) },
            onAnnule = { aSupprimer = null },
        )
    }
}

/**
 * Pincer pour zoomer, double-tap pour revenir à la taille d'origine.
 *
 * Le déplacement est libre et non borné : calculer les bornes exactes demande la
 * taille réelle de l'image à l'écran, et une photo qu'on pousse un peu trop loin
 * se ramène d'un double-tap.
 */
@Composable
private fun PhotoZoomable(photo: Photo) {
    val uri = photo.uri
    var echelle by remember(uri) { mutableFloatStateOf(1f) }
    var decalageX by remember(uri) { mutableFloatStateOf(0f) }
    var decalageY by remember(uri) { mutableFloatStateOf(0f) }

    val transformation = rememberTransformableState { zoom, glissement, _ ->
        echelle = (echelle * zoom).coerceIn(1f, 6f)
        if (echelle > 1f) {
            decalageX += glissement.x
            decalageY += glissement.y
        } else {
            decalageX = 0f
            decalageY = 0f
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = requeteImage(LocalContext.current, photo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = echelle,
                    scaleY = echelle,
                    translationX = decalageX,
                    translationY = decalageY,
                )
                .transformable(transformation)
                .pointerInput(uri) {
                    detectTapGestures(
                        onDoubleTap = {
                            echelle = if (echelle > 1f) 1f else 2.5f
                            decalageX = 0f
                            decalageY = 0f
                        },
                    )
                },
        )
    }
}
