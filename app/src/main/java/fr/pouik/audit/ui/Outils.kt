package fr.pouik.audit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Les outils de la barre, dans l'ordre où ils y apparaissent. */
enum class Outil(val libelle: String) {
    SELECTION("Sélection"),
    RECTANGLE("Rectangle"),
    CADRE("Cadre"),
    ELLIPSE("Ellipse"),
    FLECHE("Flèche"),
    CRAYON("Crayon"),
    TEXTE("Texte"),
    FLOU("Flou"),
}

/**
 * Les icônes sont dessinées, pas importées.
 *
 * `material-icons-extended` pèse plusieurs milliers de classes pour une poignée de
 * symboles, et l'app n'est pas minifiée. Un rectangle, une ellipse et une flèche se
 * tracent en trois lignes — et ces trois lignes montrent exactement ce que l'outil
 * produira, ce qu'une icône générique ne ferait pas.
 */
@Composable
fun IconeOutil(outil: Outil, teinte: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val trait = Stroke(width = size.minDimension * 0.09f)
        val marge = size.minDimension * 0.16f
        val cadre = Rect(
            Offset(marge, marge),
            Size(size.width - 2 * marge, size.height - 2 * marge),
        )
        when (outil) {
            Outil.SELECTION -> {
                // Un curseur en forme de flèche pleine : le seul outil qui ne dessine
                // rien, donc le seul à ne pas se représenter par sa trace.
                val p = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.14f)
                    lineTo(size.width * 0.28f, size.height * 0.82f)
                    lineTo(size.width * 0.46f, size.height * 0.64f)
                    lineTo(size.width * 0.58f, size.height * 0.88f)
                    lineTo(size.width * 0.70f, size.height * 0.82f)
                    lineTo(size.width * 0.58f, size.height * 0.58f)
                    lineTo(size.width * 0.78f, size.height * 0.54f)
                    close()
                }
                drawPath(p, teinte)
            }
            Outil.RECTANGLE -> drawRect(teinte, cadre.topLeft, cadre.size)
            Outil.CADRE -> drawRect(teinte, cadre.topLeft, cadre.size, style = trait)
            Outil.ELLIPSE -> drawOval(teinte, cadre.topLeft, cadre.size, style = trait)
            Outil.FLECHE -> {
                val depart = Offset(marge, size.height - marge)
                val arrivee = Offset(size.width - marge, marge)
                drawLine(teinte, depart, arrivee, strokeWidth = trait.width)
                drawLine(teinte, arrivee, arrivee + Offset(-size.width * 0.26f, 0f), trait.width)
                drawLine(teinte, arrivee, arrivee + Offset(0f, size.height * 0.26f), trait.width)
            }
            Outil.CRAYON -> {
                val p = Path().apply {
                    moveTo(marge, size.height * 0.68f)
                    cubicTo(
                        size.width * 0.34f, size.height * 0.10f,
                        size.width * 0.52f, size.height * 0.92f,
                        size.width - marge, size.height * 0.30f,
                    )
                }
                drawPath(p, teinte, style = trait)
            }
            Outil.TEXTE -> {
                // La lettre T, tracée : un glyphe demanderait une mesure de police pour
                // rester centré à toutes les densités.
                drawLine(
                    teinte,
                    Offset(marge, marge * 1.6f),
                    Offset(size.width - marge, marge * 1.6f),
                    trait.width,
                )
                drawLine(
                    teinte,
                    Offset(size.width / 2, marge * 1.6f),
                    Offset(size.width / 2, size.height - marge),
                    trait.width,
                )
            }
            Outil.FLOU -> {
                // Un damier qui se dissout vers le bas : ce que fait le sous-échantillonnage.
                val pas = size.width / 5f
                for (ligne in 0 until 4) {
                    for (colonne in 0 until 4) {
                        if ((ligne + colonne) % 2 != 0) continue
                        drawRect(
                            teinte.copy(alpha = 1f - ligne * 0.22f),
                            Offset(marge + colonne * pas * 0.9f, marge + ligne * pas * 0.9f),
                            Size(pas * 0.9f, pas * 0.9f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * La palette : huit couleurs franches, pensées pour rester lisibles sur une photo de
 * rayon sous néons. Pas de nuances subtiles — une annotation doit se voir d'un coup
 * d'œil sur un écran de téléphone en plein magasin.
 */
val PALETTE = listOf(
    0xFFE53935L, // rouge
    0xFFFB8C00L, // orange
    0xFFFDD835L, // jaune
    0xFF43A047L, // vert
    0xFF1E88E5L, // bleu
    0xFF8E24AAL, // violet
    0xFFFFFFFFL, // blanc
    0xFF000000L, // noir
)
