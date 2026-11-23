package fr.pouik.leonida

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Categorie(val id: String, val label: String, val color: String, val icon: String) {
    val couleur: Color get() = Color(android.graphics.Color.parseColor(color))
}

@Serializable
data class Region(val name: String, val x: Float, val y: Float)

@Serializable
data class Poi(
    val id: String,
    val name: String,
    val category: String,
    val x: Float,
    val y: Float,
    val description: String,
    val region: String,
)

@Serializable
data class Carte(val categories: List<Categorie>, val regions: List<Region>, val pois: List<Poi>)

/** Monde de la carte : 1000 × 1400 unités. Les points d'intérêt vivent dans assets/leonida.json. */
const val LARGEUR_MONDE = 1000f
const val HAUTEUR_MONDE = 1400f

fun chargerCarte(context: Context): Carte {
    val texte = context.assets.open("leonida.json").bufferedReader().use { it.readText() }
    return Json { ignoreUnknownKeys = true }.decodeFromString(Carte.serializer(), texte)
}

/** Favoris et lieux visités, gardés dans les préférences. */
class Marques(context: Context) {
    private val prefs = context.getSharedPreferences("marques", Context.MODE_PRIVATE)
    fun favoris(): Set<String> = prefs.getStringSet("favoris", emptySet())!!.toSet()
    fun visites(): Set<String> = prefs.getStringSet("visites", emptySet())!!.toSet()
    fun enregistrer(favoris: Set<String>, visites: Set<String>) {
        prefs.edit().putStringSet("favoris", favoris).putStringSet("visites", visites).apply()
    }
}
