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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import fr.pouik.audit.donnees.Boite
import fr.pouik.audit.donnees.Forme
import fr.pouik.audit.donnees.poigneesDe
import fr.pouik.audit.donnees.sommets
import fr.pouik.audit.photos.Rendu

/** Ce que le doigt est en train de faire. */
private enum class Geste { AUCUN, CREATION, DEPLACEMENT, POIGNEE }

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
    onPoignee: (Int, Float, Float, Boite) -> Unit,
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

            var geste by remember { mutableStateOf(Geste.AUCUN) }
            var poigneeActive by remember { mutableIntStateOf(-1) }
            // Figée au début du geste : le coin opposé sert d'ancre, et le recalculer à
            // chaque image le ferait bouger dès qu'on traverse la forme — la figure
            // partirait alors en vrille au lieu de se retourner proprement.
            var ancrage by remember { mutableStateOf(Boite(0f, 0f, 0f, 0f)) }

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
                    .pointerInput(outil, selection, formes) {
                        val rayonPoignee = with(densite) { 30.dp.toPx() }
                        detectDragGestures(
                            onDragStart = { position ->
                                val l = size.width.toFloat()
                                val h = size.height.toFloat()
                                val nx = position.x / l
                                val ny = position.y / h

                                // Les poignées répondent quel que soit l'outil tenu :
                                // elles sont dessinées à l'écran, donc on s'attend à
                                // pouvoir les tirer. Obliger à repasser par l'outil
                                // Sélection pour ajuster la forme qu'on vient de poser
                                // était la première chose qui surprenait à l'usage.
                                val courante = formes.firstOrNull { it.id == selection }
                                val attrapee = courante?.let {
                                    poigneeLaPlusProche(it, position.x, position.y, l, h, rayonPoignee)
                                } ?: -1
                                if (attrapee >= 0 && courante != null) {
                                    onHistorique()
                                    poigneeActive = attrapee
                                    ancrage = Rendu.boiteDe(courante, l, h)
                                    geste = Geste.POIGNEE
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
                                    Geste.POIGNEE -> onPoignee(
                                        poigneeActive,
                                        evenement.position.x / l,
                                        evenement.position.y / h,
                                        ancrage,
                                    )
                                    Geste.AUCUN -> Unit
                                }
                            },
                            onDragEnd = {
                                if (geste == Geste.CREATION) onFini()
                                geste = Geste.AUCUN
                                poigneeActive = -1
                            },
                            onDragCancel = {
                                if (geste == Geste.CREATION) onFini()
                                geste = Geste.AUCUN
                                poigneeActive = -1
                            },
                        )
                    },
            ) {
                drawIntoCanvas { toile ->
                    Rendu.dessine(
                        toile.nativeCanvas,
                        formes + listOfNotNull(brouillon),
                        size.width,
                        size.height,
                        image,
                    )
                }

                // Le contour de sélection et ses poignées sont dessinés par-dessus, et
                // ne partent évidemment pas dans le fichier exporté : ils n'existent que
                // le temps de l'édition.
                formes.firstOrNull { it.id == selection }?.let { choisie ->
                    dessineSelection(choisie, poigneeActive)
                }
            }
        }
    }
}

/**
 * Le contour de la sélection, puis ses poignées.
 *
 * Le contour épouse les sommets réels : sur un rectangle déformé, un cadre droit
 * mentirait sur la forme qu'on est en train d'ajuster.
 */
private fun DrawScope.dessineSelection(forme: Forme, poigneeActive: Int) {
    val boite = Rendu.boiteDe(forme, size.width, size.height)
    val pointilles = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))

    if (forme is Forme.Rectangle) {
        val sommets = forme.sommets()
        val chemin = Path().apply {
            moveTo(sommets[0].x * size.width, sommets[0].y * size.height)
            sommets.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
            close()
        }
        drawPath(chemin, Color.White, style = Stroke(width = 2.dp.toPx(), pathEffect = pointilles))
    } else if (forme !is Forme.Fleche) {
        val marge = 6.dp.toPx()
        drawRect(
            color = Color.White,
            topLeft = Offset(boite.x * size.width - marge, boite.y * size.height - marge),
            size = androidx.compose.ui.geometry.Size(
                boite.l * size.width + 2 * marge,
                boite.h * size.height + 2 * marge,
            ),
            style = Stroke(width = 2.dp.toPx(), pathEffect = pointilles),
        )
    }

    poigneesDe(forme, boite).forEachIndexed { index, point ->
        val centre = Offset(point.x * size.width, point.y * size.height)
        // Celle qu'on tient grossit : sous le doigt, la poignée est cachée, et c'est le
        // seul moyen de savoir laquelle a été attrapée.
        val rayon = if (index == poigneeActive) 14.dp.toPx() else 11.dp.toPx()
        drawCircle(Color.White, radius = rayon, center = centre)
        drawCircle(Color.Black, radius = rayon * 0.6f, center = centre)
    }
}

/**
 * L'indice de la poignée sous le doigt, ou -1.
 *
 * La plus proche l'emporte : sur une forme réduite à presque rien, deux poignées se
 * chevauchent, et attraper systématiquement la première rendrait les autres
 * inatteignables.
 */
private fun poigneeLaPlusProche(
    forme: Forme,
    px: Float,
    py: Float,
    largeur: Float,
    hauteur: Float,
    rayon: Float,
): Int {
    val boite = Rendu.boiteDe(forme, largeur, hauteur)
    var meilleur = -1
    var distanceMin = rayon
    poigneesDe(forme, boite).forEachIndexed { index, point ->
        val d = kotlin.math.hypot(px - point.x * largeur, py - point.y * hauteur)
        if (d <= distanceMin) {
            distanceMin = d
            meilleur = index
        }
    }
    return meilleur
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
