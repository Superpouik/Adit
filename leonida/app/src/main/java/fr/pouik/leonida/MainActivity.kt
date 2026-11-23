package fr.pouik.leonida

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FOND = Color(0xFF1A0B2E)
private val ROSE = Color(0xFFFF2E88)
private val ORANGE = Color(0xFFFF9A3C)
private val TURQUOISE = Color(0xFF19E3D1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val carte = chargerCarte(this)
        val marques = Marques(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = ROSE, secondary = TURQUOISE, surface = FOND, background = FOND)) {
                Application(carte, marques)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Application(carte: Carte, marques: Marques) {
    var recherche by remember { mutableStateOf("") }
    var filtres by remember { mutableStateOf(setOf<String>()) }
    var seulementFavoris by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Poi?>(null) }
    var favoris by remember { mutableStateOf(marques.favoris()) }
    var visites by remember { mutableStateOf(marques.visites()) }
    var liste by remember { mutableStateOf(false) }
    var taille by remember { mutableStateOf(IntSize.Zero) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val focus = LocalFocusManager.current
    val categories = carte.categories.associateBy { it.id }

    LaunchedEffect(favoris, visites) { marques.enregistrer(favoris, visites) }

    val visibles = carte.pois.filter { p ->
        (filtres.isEmpty() || p.category in filtres) &&
            (!seulementFavoris || p.id in favoris) &&
            (recherche.isBlank() || p.name.contains(recherche, true) || p.region.contains(recherche, true) ||
                categories[p.category]?.label?.contains(recherche, true) == true)
    }

    fun vueInitiale(): Camera {
        val e = minOf(taille.width / LARGEUR_MONDE, taille.height / HAUTEUR_MONDE) * 0.95f
        return Camera(e, Offset((taille.width - LARGEUR_MONDE * e) / 2, (taille.height - HAUTEUR_MONDE * e) / 2))
    }
    fun centrer(p: Poi) {
        val base = vueInitiale().echelle
        val e = maxOf(camera?.echelle ?: base, base * 3f)
        camera = Camera(e, Offset(taille.width / 2f - p.x * e, taille.height * 0.42f - p.y * e))
        selection = p
    }

    Box(Modifier.fillMaxSize().background(FOND).onSizeChanged {
        taille = it; if (camera == null && it.width > 0) camera = vueInitiale()
    }) {
        camera?.let { cam ->
            VueCarte(carte, visibles, selection, favoris, cam, { camera = it },
                { selection = it; focus.clearFocus() }, Modifier.fillMaxSize())
        }

        // En-tête : titre, recherche, filtres
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(FOND, FOND.copy(alpha = 0.85f), Color.Transparent)))
            .statusBarsPadding().padding(top = 8.dp, bottom = 16.dp)) {
            Text("LEONIDA", Modifier.padding(horizontal = 16.dp), fontSize = 28.sp, fontWeight = FontWeight.Black,
                letterSpacing = 6.sp, style = LocalTextStyle.current.copy(
                    brush = Brush.horizontalGradient(listOf(ROSE, ORANGE, TURQUOISE))))
            Text("Carte interactive · ${visibles.size} lieux", Modifier.padding(horizontal = 16.dp),
                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            OutlinedTextField(recherche, { recherche = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Rechercher un lieu, une région…") }, singleLine = true,
                leadingIcon = { Text("🔍") },
                trailingIcon = { if (recherche.isNotEmpty()) Text("✕", Modifier.clickable { recherche = "" }.padding(8.dp)) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ROSE, unfocusedBorderColor = TURQUOISE.copy(alpha = 0.5f),
                    focusedContainerColor = FOND.copy(alpha = 0.9f), unfocusedContainerColor = FOND.copy(alpha = 0.9f)))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Puce("⭐ Favoris", seulementFavoris, Color(0xFFFFD23F)) { seulementFavoris = !seulementFavoris }
                }
                items(carte.categories) { c ->
                    Puce("${c.icon} ${c.label}", c.id in filtres, c.couleur) {
                        filtres = if (c.id in filtres) filtres - c.id else filtres + c.id
                    }
                }
            }
        }

        // Boutons flottants
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Rond("☰") { liste = true }
            Rond("+") { camera?.let { c -> camera = zoomer(c, 1.5f, taille) } }
            Rond("−") { camera?.let { c -> camera = zoomer(c, 1 / 1.5f, taille) } }
            Rond("⌂") { camera = vueInitiale(); selection = null }
        }

        // Fiche du lieu sélectionné
        selection?.let { p ->
            val c = categories[p.category]!!
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth()
                .background(FOND.copy(alpha = 0.96f), RoundedCornerShape(20.dp))
                .border(1.5.dp, Brush.horizontalGradient(listOf(ROSE, ORANGE, TURQUOISE)), RoundedCornerShape(20.dp))
                .padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.icon, fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Text("${c.label} · ${p.region}", color = c.couleur, fontSize = 13.sp)
                    }
                    Text("✕", Modifier.clickable { selection = null }.padding(8.dp), color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(8.dp))
                Text(p.description, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Puce(if (p.id in favoris) "⭐ Favori" else "☆ Favori", p.id in favoris, Color(0xFFFFD23F)) {
                        favoris = if (p.id in favoris) favoris - p.id else favoris + p.id
                    }
                    Puce(if (p.id in visites) "✔ Visité" else "○ Pas visité", p.id in visites, TURQUOISE) {
                        visites = if (p.id in visites) visites - p.id else visites + p.id
                    }
                    Puce("◎ Centrer", false, ROSE) { centrer(p) }
                }
            }
        }

        if (liste) {
            ModalBottomSheet({ liste = false }, containerColor = FOND) {
                Text("Lieux (${visibles.size}) · visités ${visites.size}/${carte.pois.size}",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, color = Color.White)
                LazyColumn(Modifier.fillMaxHeight(0.7f)) {
                    visibles.groupBy { it.region }.forEach { (region, lieux) ->
                        item { Text(region.uppercase(), Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), color = ROSE,
                            fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 12.sp) }
                        items(lieux, key = { it.id }) { p ->
                            val c = categories[p.category]!!
                            Row(Modifier.fillMaxWidth().clickable { liste = false; centrer(p) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(c.icon, fontSize = 20.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, color = Color.White)
                                    Text(c.label, color = c.couleur, fontSize = 12.sp)
                                }
                                if (p.id in favoris) Text("⭐")
                                if (p.id in visites) Text(" ✔", color = TURQUOISE)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun zoomer(c: Camera, f: Float, t: IntSize): Camera {
    val centre = Offset(t.width / 2f, t.height / 2f)
    val e = (c.echelle * f).coerceIn(0.2f, 12f)
    return Camera(e, (c.decalage - centre) * (e / c.echelle) + centre)
}

@Composable
private fun Puce(texte: String, actif: Boolean, couleur: Color, onClick: () -> Unit) {
    Text(texte, Modifier.clip20().background(if (actif) couleur.copy(alpha = 0.3f) else FOND.copy(alpha = 0.85f))
        .border(1.dp, if (actif) couleur else couleur.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        color = Color.White, fontSize = 13.sp, fontWeight = if (actif) FontWeight.Bold else FontWeight.Normal)
}

private fun Modifier.clip20() = this.clip(RoundedCornerShape(20.dp))

@Composable
private fun Rond(t: String, onClick: () -> Unit) {
    Box(Modifier.size(46.dp).clip(CircleShape).background(FOND.copy(alpha = 0.9f)).border(1.5.dp, TURQUOISE, CircleShape)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
