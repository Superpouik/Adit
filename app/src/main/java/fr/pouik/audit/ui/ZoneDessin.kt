package fr.pouik.audit.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.pouik.audit.donnees.Boite
import fr.pouik.audit.donnees.Forme
import fr.pouik.audit.donnees.deplacee
import fr.pouik.audit.donnees.redimensionnee
import fr.pouik.audit.photos.Rendu

/** Ce que le doigt est en train de faire. */
private enum class Geste { AUCUN, CREATION, DEPLACEMENT, REDIMENSION }

/**
 * La photo et ses annotations, avec les gestes qui vont avec.
 *
 * La zone de dessin épouse exactement l'image, bandes noires exclues : les coordonnées
 * du doigt s'y rapportent directement, sans correction de cadrage à écrire — et donc
 * sans décalage à découvrir le jour où l'on passe d'un écran plié à un écran déplié.
 */
@Composable
fun ZoneDessin(
    image: Bitmap,
    formes: List<Forme>,
    brouillon: Forme?,
    selection: String?,
    outil: Outil,
    modifier: Modifier = Modifier,
    onCommence: (Offset) -> Unit,
    onTire: (Offset) -> Unit,
    onFini: () -> Unit,
    onSelectionne: (String?) -> Unit,
    onDeplace: (Float, Float) -> Unit,
    onRedimensionne: (Float, Float) -> Unit,
    onPoseTexte: (Float, Float) -> Unit,
    onHistorique: () -> Unit,
) {
    val ratio = image.width.toFloat() / image.height.toFloat()
    val densite = LocalDensity.current

    // Fond sombre autour de la photo, quel que soit le thème : sur le blanc du mode
    // clair, le bord d'une photo claire se perdait, et on ne savait plus où finissait
    // l'image — donc où l'annotation serait rognée.
    BoxWithConstraints(
        modifier.fillMaxSize().background(Color(0xFF1C1B1F)),
        contentAlignment = Alignment.Center,
    ) {
        // L'image est limitée par la hauteur quand le cadre disponible est plus large
        // qu'elle : sans ce choix, `aspectRatio` partirait toujours de la largeur et
        // laisserait l'image déborder par le bas en mode déplié.
        val cadreRatio = maxWidth / maxHeight
        val parLaHauteur = cadreRatio > ratio

        Box(
            Modifier
                .fillMaxSize()
                .aspectRatio(ratio, matchHeightConstraintsFirst = parLaHauteur),
        ) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Photo à annoter",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )

            var zone by remember { mutableStateOf(Size.Zero) }
            // Mémorisées au début du geste : redimensionner à partir des valeurs
            // courantes ferait s'emballer la forme, chaque image réutilisant la taille
            // que la précédente venait de produire.
            var ancrage by remember { mutableStateOf(Offset.Zero) }
            var geste by remember { mutableStateOf(Geste.AUCUN) }

            androidx.compose.foundation.Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(outil, selection) {
                        detectTapGestures { position ->
                            val nx = position.x / size.width
                            val ny = position.y / size.height
                            when (outil) {
                                Outil.TEXTE -> onPoseTexte(nx, ny)
                                Outil.SELECTION -> onSelectionne(
                                    formeSous(formes, nx, ny, size.width.toFloat(), size.height.toFloat()),
                                )
                                else -> Unit
                            }
                        }
                    }
                    .pointerInput(outil, selection) {
                        val rayonPoignee = with(densite) { 28.dp.toPx() }
                        detectDragGestures(
                            onDragStart = { position ->
                                ancrage = position
                                val l = size.width.toFloat()
                                val h = size.height.toFloat()
                                val nx = position.x / l
                                val ny = position.y / h

                                // La poignée répond quel que soit l'outil tenu : elle
                                // est dessinée à l'écran, donc on s'attend à pouvoir la
                                // tirer. Obliger à repasser par l'outil Sélection pour
                                // agrandir la forme qu'on vient de poser était la
                                // première chose qui surprenait à l'usage.
                                val courante = formes.firstOrNull { it.id == selection }
                                val boite = courante?.let { Rendu.boiteDe(it, l, h) }
                                val surPoignee = boite != null && kotlin.math.hypot(
                                    position.x - boite.droite * l,
                                    position.y - boite.bas * h,
                                ) <= rayonPoignee
                                if (surPoignee) {
                                    onHistorique()
                                    geste = Geste.REDIMENSION
                                    return@detectDragGestures
                                }
                                if (outil != Outil.SELECTION) {
                                    geste = Geste.CREATION
                                    onCommence(Offset(nx, ny))
                                    return@detectDragGestures
                                }
                                val touchee = formeSous(formes, nx, ny, l, h)
                                onSelectionne(touchee)
                                geste = if (touchee != null) {
                                    onHistorique()
                                    Geste.DEPLACEMENT
                                } else {
                                    Geste.AUCUN
                                }
                            },
                            onDrag = { evenement, delta ->
                                evenement.consume()
                                val l = size.width.toFloat()
                                val h = size.height.toFloat()
                                when (geste) {
                                    Geste.CREATION -> onTire(
                                        Offset(evenement.position.x / l, evenement.position.y / h),
                                    )
                                    Geste.DEPLACEMENT -> onDeplace(delta.x / l, delta.y / h)
                                    Geste.REDIMENSION -> {
                                        val courante = formes.firstOrNull { it.id == selection }
                                        if (courante != null) {
                                            val b = Rendu.boiteDe(courante, l, h)
                                            onRedimensionne(
                                                evenement.position.x / l - b.x,
                                                evenement.position.y / h - b.y,
                                            )
                                        }
                                    }
                                    Geste.AUCUN -> Unit
                                }
                            },
                            onDragEnd = {
                                if (geste == Geste.CREATION) onFini()
                                geste = Geste.AUCUN
                            },
                            onDragCancel = {
                                if (geste == Geste.CREATION) onFini()
                                geste = Geste.AUCUN
                            },
                        )
                    },
            ) {
                zone = size
                drawIntoCanvas { toile ->
                    Rendu.dessine(
                        toile.nativeCanvas,
                        formes + listOfNotNull(brouillon),
                        size.width,
                        size.height,
                        image,
                    )
                }

                // Le cadre de sélection et sa poignée sont dessinés par-dessus, et ne
                // partent évidemment pas dans le fichier exporté : ils n'existent que
                // le temps de l'édition.
                val choisie = formes.firstOrNull { it.id == selection }
                if (choisie != null) {
                    val b: Boite = Rendu.boiteDe(choisie, size.width, size.height)
                    val marge = 6.dp.toPx()
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(b.x * size.width - marge, b.y * size.height - marge),
                        size = Size(
                            b.l * size.width + 2 * marge,
                            b.h * size.height + 2 * marge,
                        ),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(12f, 10f),
                            ),
                        ),
                    )
                    val poignee = Offset(b.droite * size.width, b.bas * size.height)
                    drawCircle(Color.White, radius = 11.dp.toPx(), center = poignee)
                    drawCircle(Color.Black, radius = 7.dp.toPx(), center = poignee)
                }
            }
        }
    }
}

/**
 * La forme sous le doigt, la dernière dessinée l'emportant.
 *
 * Parcours en sens inverse : au-dessus d'une pile d'annotations, c'est celle qu'on voit
 * qu'on cherche à attraper, pas celle qu'elle recouvre.
 */
private fun formeSous(
    formes: List<Forme>,
    nx: Float,
    ny: Float,
    largeur: Float,
    hauteur: Float,
): String? {
    // Une marge en fraction de la largeur : un trait fin reste attrapable au doigt.
    val marge = 0.02f
    return formes.asReversed().firstOrNull { forme ->
        Rendu.boiteDe(forme, largeur, hauteur).contient(nx, ny, marge)
    }?.id
}
