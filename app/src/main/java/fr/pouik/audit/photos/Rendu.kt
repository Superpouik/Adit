package fr.pouik.audit.photos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import fr.pouik.audit.donnees.Boite
import fr.pouik.audit.donnees.Forme
import fr.pouik.audit.donnees.boiteGeometrique
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Le dessin des annotations, en un seul exemplaire.
 *
 * L'aperçu de l'éditeur et le fichier exporté passent par ce même code, sur un
 * `android.graphics.Canvas` dans les deux cas — l'un posé sur le canevas Compose,
 * l'autre sur le bitmap pleine résolution. Deux moteurs de rendu finiraient par
 * diverger d'un demi-pixel ici et d'une police là, et ce qu'on enverrait au client ne
 * serait plus tout à fait ce qu'on avait vu à l'écran.
 */
object Rendu {

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinceauTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    /**
     * Dessine toutes les formes sur [canvas], dont la zone utile mesure [largeur] sur
     * [hauteur] pixels.
     *
     * [source] n'est nécessaire qu'au floutage, qui prélève ses pixels dans l'image
     * elle-même ; il doit cadrer la même image que le canevas.
     */
    fun dessine(
        canvas: Canvas,
        formes: List<Forme>,
        largeur: Float,
        hauteur: Float,
        source: Bitmap? = null,
    ) {
        formes.forEach { forme ->
            when (forme) {
                is Forme.Rectangle -> {
                    prepare(forme.couleur, forme.plein, forme.epaisseur, largeur)
                    canvas.drawRect(enPixels(forme.x, forme.y, forme.l, forme.h, largeur, hauteur), pinceau)
                }
                is Forme.Ellipse -> {
                    prepare(forme.couleur, forme.plein, forme.epaisseur, largeur)
                    canvas.drawOval(enPixels(forme.x, forme.y, forme.l, forme.h, largeur, hauteur), pinceau)
                }
                is Forme.Fleche -> dessineFleche(canvas, forme, largeur, hauteur)
                is Forme.Trait -> dessineTrait(canvas, forme, largeur, hauteur)
                is Forme.Texte -> dessineTexte(canvas, forme, largeur, hauteur)
                is Forme.Flou -> dessineFlou(canvas, forme, largeur, hauteur, source)
            }
        }
    }

    /**
     * Boîte englobante d'une forme, texte compris.
     *
     * Le texte est le seul cas qui demande une mesure : sa largeur dépend de la police.
     * D'où le passage par ici plutôt que par la géométrie seule.
     */
    fun boiteDe(forme: Forme, largeur: Float, hauteur: Float): Boite {
        forme.boiteGeometrique()?.let { return it }
        val texte = forme as Forme.Texte
        val lignes = texte.contenu.split('\n')
        pinceauTexte.textSize = texte.taille * hauteur
        val largeurPx = lignes.maxOfOrNull { pinceauTexte.measureText(it) } ?: 0f
        val hauteurPx = lignes.size * pinceauTexte.fontSpacing
        return Boite(texte.x, texte.y, largeurPx / largeur, hauteurPx / hauteur)
    }

    private fun prepare(couleur: Long, plein: Boolean, epaisseur: Float, largeur: Float) {
        pinceau.color = couleur.toInt()
        pinceau.style = if (plein) Paint.Style.FILL else Paint.Style.STROKE
        pinceau.strokeWidth = epaisseur * largeur
        pinceau.strokeCap = Paint.Cap.ROUND
        pinceau.strokeJoin = Paint.Join.ROUND
    }

    private fun enPixels(x: Float, y: Float, l: Float, h: Float, largeur: Float, hauteur: Float) =
        RectF(x * largeur, y * hauteur, (x + l) * largeur, (y + h) * hauteur)

    private fun dessineFleche(canvas: Canvas, f: Forme.Fleche, largeur: Float, hauteur: Float) {
        prepare(f.couleur, plein = false, epaisseur = f.epaisseur, largeur = largeur)
        val x1 = f.x1 * largeur
        val y1 = f.y1 * hauteur
        val x2 = f.x2 * largeur
        val y2 = f.y2 * hauteur
        canvas.drawLine(x1, y1, x2, y2, pinceau)

        // La pointe suit l'épaisseur du trait, pas une taille fixe : une flèche fine
        // avec une tête énorme, ou l'inverse, ne se lit pas.
        val longueur = hypot(x2 - x1, y2 - y1)
        if (longueur < 1f) return
        val taille = (f.epaisseur * largeur * 4f).coerceAtMost(longueur * 0.5f)
        val angle = atan2(y2 - y1, x2 - x1)
        val ouverture = 0.5f
        canvas.drawLine(
            x2, y2,
            x2 - taille * cos(angle - ouverture), y2 - taille * sin(angle - ouverture),
            pinceau,
        )
        canvas.drawLine(
            x2, y2,
            x2 - taille * cos(angle + ouverture), y2 - taille * sin(angle + ouverture),
            pinceau,
        )
    }

    private fun dessineTrait(canvas: Canvas, f: Forme.Trait, largeur: Float, hauteur: Float) {
        if (f.points.size < 4) return
        prepare(f.couleur, plein = false, epaisseur = f.epaisseur, largeur = largeur)
        val chemin = android.graphics.Path()
        chemin.moveTo(f.points[0] * largeur, f.points[1] * hauteur)
        var i = 2
        while (i + 1 < f.points.size) {
            chemin.lineTo(f.points[i] * largeur, f.points[i + 1] * hauteur)
            i += 2
        }
        canvas.drawPath(chemin, pinceau)
    }

    private fun dessineTexte(canvas: Canvas, f: Forme.Texte, largeur: Float, hauteur: Float) {
        val lignes = f.contenu.split('\n')
        pinceauTexte.textSize = f.taille * hauteur
        val interligne = pinceauTexte.fontSpacing
        val marge = pinceauTexte.textSize * 0.25f
        val x = f.x * largeur
        val y = f.y * hauteur

        f.fond?.let { fond ->
            val large = lignes.maxOfOrNull { pinceauTexte.measureText(it) } ?: 0f
            pinceau.style = Paint.Style.FILL
            pinceau.color = fond.toInt()
            canvas.drawRect(
                x - marge,
                y - marge,
                x + large + marge,
                y + lignes.size * interligne + marge,
                pinceau,
            )
        }

        pinceauTexte.color = f.couleur.toInt()
        // La première ligne est posée sur sa ligne de base, pas sur son sommet : sans
        // le décalage de l'ascendante, le texte déborde par le haut de son cartouche.
        var ligneBase = y - pinceauTexte.fontMetrics.ascent
        lignes.forEach { ligne ->
            canvas.drawText(ligne, x, ligneBase, pinceauTexte)
            ligneBase += interligne
        }
    }

    /**
     * Floute une zone en la réduisant puis en la réétirant.
     *
     * RenderEffect ferait plus propre mais n'existe qu'à partir d'Android 12, et
     * RenderScript est retiré : le sous-échantillonnage, lui, marche partout et donne
     * exactement ce qu'on cherche — un visage ou un nom de client devenu illisible.
     */
    private fun dessineFlou(
        canvas: Canvas,
        f: Forme.Flou,
        largeur: Float,
        hauteur: Float,
        source: Bitmap?,
    ) {
        val cible = enPixels(f.x, f.y, f.l, f.h, largeur, hauteur)
        if (source == null || cible.width() < 1f || cible.height() < 1f) {
            // Sans image sous la main, mieux vaut un aplat opaque qu'une zone laissée
            // lisible : le flou sert à cacher.
            pinceau.style = Paint.Style.FILL
            pinceau.color = android.graphics.Color.DKGRAY
            canvas.drawRect(cible, pinceau)
            return
        }

        // La zone est exprimée dans le repère du canevas ; le bitmap source peut avoir
        // une tout autre résolution, d'où le passage par les fractions.
        val src = Rect(
            (f.x * source.width).toInt().coerceIn(0, source.width - 1),
            (f.y * source.height).toInt().coerceIn(0, source.height - 1),
            ((f.x + f.l) * source.width).toInt().coerceIn(1, source.width),
            ((f.y + f.h) * source.height).toInt().coerceIn(1, source.height),
        )
        if (src.width() < 1 || src.height() < 1) return

        val pavesParLargeur = (f.l / f.force).toInt().coerceIn(2, 64)
        val petitL = pavesParLargeur
        val petitH = (petitL * src.height() / src.width().toFloat()).toInt().coerceAtLeast(2)
        try {
            val morceau = Bitmap.createBitmap(source, src.left, src.top, src.width(), src.height())
            val reduit = Bitmap.createScaledBitmap(morceau, petitL, petitH, true)
            // Le réétirement est filtré : sans ça on obtient des carrés nets façon
            // mosaïque, qui attirent l'œil au lieu de l'en détourner.
            canvas.drawBitmap(reduit, null, cible, Paint(Paint.FILTER_BITMAP_FLAG))
            if (reduit !== morceau) reduit.recycle()
            morceau.recycle()
        } catch (e: Exception) {
            pinceau.style = Paint.Style.FILL
            pinceau.color = android.graphics.Color.DKGRAY
            canvas.drawRect(cible, pinceau)
        }
    }
}
