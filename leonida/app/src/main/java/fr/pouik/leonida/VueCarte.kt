package fr.pouik.leonida

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

/** Vue courante : échelle (pixels par unité monde) et décalage écran. */
data class Camera(val echelle: Float, val decalage: Offset) {
    fun versEcran(x: Float, y: Float) = Offset(x * echelle + decalage.x, y * echelle + decalage.y)
}

private val COTE = listOf(
    40f to 120f, 420f to 90f, 700f to 110f, 760f to 180f, 780f to 300f, 800f to 450f,
    820f to 600f, 840f to 750f, 860f to 880f, 885f to 980f, 875f to 1080f, 830f to 1160f,
    760f to 1195f, 680f to 1180f, 600f to 1120f, 540f to 1020f, 470f to 880f, 430f to 760f,
    395f to 620f, 365f to 480f, 330f to 360f, 260f to 300f, 150f to 300f, 40f to 280f,
)
private val ILES = listOf(640f to 1230f, 590f to 1262f, 540f to 1290f, 485f to 1315f, 430f to 1335f, 375f to 1350f, 320f to 1362f)
private val ROUTES = listOf(
    listOf(80f to 200f, 300f to 210f, 520f to 200f, 700f to 220f),
    listOf(700f to 220f, 740f to 450f, 760f to 700f, 700f to 960f, 740f to 1080f),
    listOf(300f to 210f, 450f to 400f, 560f to 480f, 640f to 600f, 760f to 700f),
    listOf(560f to 480f, 520f to 700f, 560f to 900f, 680f to 960f),
    listOf(740f to 1080f, 680f to 1170f, 640f to 1230f, 320f to 1362f),
)

private fun pathDe(points: List<Pair<Float, Float>>, ferme: Boolean) = Path().apply {
    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
    if (ferme) close()
}

private val ROSE = Color(0xFFFF2E88)
private val ORANGE = Color(0xFFFF9A3C)
private val TURQUOISE = Color(0xFF19E3D1)

@Composable
fun VueCarte(
    carte: Carte,
    pois: List<Poi>,
    selection: Poi?,
    favoris: Set<String>,
    camera: Camera,
    onCamera: (Camera) -> Unit,
    onTapPoi: (Poi?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mesure = rememberTextMeasurer()
    val categories = carte.categories.associateBy { it.id }
    val cote = pathDe(COTE, true)

    Canvas(
        modifier
            .pointerInput(camera) {
                detectTransformGestures { centre, pan, zoom, _ ->
                    val e = (camera.echelle * zoom).coerceIn(0.2f, 12f)
                    val f = e / camera.echelle
                    onCamera(Camera(e, (camera.decalage - centre) * f + centre + pan))
                }
            }
            .pointerInput(pois, camera) {
                detectTapGestures { tap ->
                    val rayon = 24.dp2px(density)
                    val touche = pois.minByOrNull { p ->
                        val s = camera.versEcran(p.x, p.y); hypot(s.x - tap.x, s.y - tap.y)
                    }?.takeIf { p ->
                        val s = camera.versEcran(p.x, p.y); hypot(s.x - tap.x, s.y - tap.y) < rayon
                    }
                    onTapPoi(touche)
                }
            }
    ) {
        // Océan au coucher du soleil
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0B2E), Color(0xFF2B1055), Color(0xFF0B3A5B))))

        withTransform({
            translate(camera.decalage.x, camera.decalage.y)
            scale(camera.echelle, camera.echelle, Offset.Zero)
        }) {
            val trait = 1f / camera.echelle
            // Halo néon de la côte
            drawPath(cote, TURQUOISE.copy(alpha = 0.25f), style = Stroke(14f * trait + 6f))
            drawPath(cote, Brush.verticalGradient(listOf(Color(0xFF3B2A5C), Color(0xFF2E4A3F), Color(0xFF3D2E52)), 0f, 1200f))
            drawPath(cote, TURQUOISE, style = Stroke(2.5f * trait))
            ILES.forEach { (x, y) ->
                drawOval(Color(0xFF3D2E52), Offset(x - 22f, y - 8f), Size(44f, 16f))
                drawOval(TURQUOISE, Offset(x - 22f, y - 8f), Size(44f, 16f), style = Stroke(2f * trait))
            }
            // Marais de Grassrivers
            drawOval(Color(0xFF2F6B4F).copy(alpha = 0.6f), Offset(450f, 800f), Size(230f, 200f))
            // Lac
            drawOval(Color(0xFF0B3A5B), Offset(440f, 615f), Size(70f, 50f))
            // Montagnes de Kalaga
            listOf(470f to 150f, 520f to 130f, 570f to 155f).forEach { (x, y) ->
                drawPath(pathDe(listOf(x - 30f to y + 40f, x to y - 10f, x + 30f to y + 40f), true), Color(0xFF5B4B7A))
            }
            // Ville : quadrillage néon
            for (i in 0..6) {
                drawLine(ROSE.copy(alpha = 0.25f), Offset(680f + i * 25f, 960f), Offset(680f + i * 25f, 1140f), 1.2f * trait)
                drawLine(ROSE.copy(alpha = 0.25f), Offset(670f, 970f + i * 28f), Offset(860f, 970f + i * 28f), 1.2f * trait)
            }
            ROUTES.forEach { r ->
                drawPath(pathDe(r, false), ORANGE.copy(alpha = 0.7f),
                    style = Stroke(3f * trait, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f * trait, 8f * trait))))
            }
        }

        // Noms des régions (taille fixe à l'écran)
        val alphaRegions = (1.6f - camera.echelle / (size.width / LARGEUR_MONDE) / 3f).coerceIn(0.25f, 1f)
        carte.regions.forEach { r ->
            val s = camera.versEcran(r.x, r.y)
            val texte = mesure.measure(r.name.uppercase(), TextStyle(
                color = Color.White.copy(alpha = alphaRegions), fontSize = 15.sp,
                fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                shadow = androidx.compose.ui.graphics.Shadow(ROSE, blurRadius = 12f)))
            drawText(texte, topLeft = s - Offset(texte.size.width / 2f, texte.size.height / 2f))
        }

        // Marqueurs
        val r = 15.dp2px(density)
        pois.forEach { p ->
            val c = categories[p.category] ?: return@forEach
            val s = camera.versEcran(p.x, p.y)
            if (s.x < -r || s.y < -r || s.x > size.width + r || s.y > size.height + r) return@forEach
            val choisi = p.id == selection?.id
            val rr = if (choisi) r * 1.35f else r
            drawCircle(c.couleur.copy(alpha = 0.35f), rr * 1.45f, s)
            drawCircle(Color(0xFF1A0B2E), rr, s)
            drawCircle(c.couleur, rr, s, style = Stroke(if (choisi) 4f else 2.5f))
            val icone = mesure.measure(c.icon, TextStyle(fontSize = (if (choisi) 17 else 13).sp))
            drawText(icone, topLeft = s - Offset(icone.size.width / 2f, icone.size.height / 2f))
            if (p.id in favoris) drawCircle(Color(0xFFFFD23F), r * 0.3f, s + Offset(rr * 0.75f, -rr * 0.75f))
            if (choisi || camera.echelle > 2.2f * size.width / LARGEUR_MONDE) etiquette(mesure, p.name, s + Offset(0f, rr + 4f), choisi)
        }
    }
}

private fun Int.dp2px(density: Float) = this * density

private fun DrawScope.etiquette(mesure: androidx.compose.ui.text.TextMeasurer, nom: String, haut: Offset, gras: Boolean) {
    val t = mesure.measure(nom, TextStyle(color = Color.White, fontSize = 11.sp,
        fontWeight = if (gras) FontWeight.Bold else FontWeight.Medium))
    val coin = haut - Offset(t.size.width / 2f + 6f, 0f)
    drawRoundRect(Color(0xCC1A0B2E), coin, Size(t.size.width + 12f, t.size.height + 4f),
        androidx.compose.ui.geometry.CornerRadius(8f))
    drawText(t, topLeft = coin + Offset(6f, 2f))
}
