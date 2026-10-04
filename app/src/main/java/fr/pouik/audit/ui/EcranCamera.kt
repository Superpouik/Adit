package fr.pouik.audit.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import fr.pouik.audit.R
import fr.pouik.audit.donnees.Exigence
import fr.pouik.audit.donnees.Photo
import kotlinx.coroutines.launch

/**
 * L'appareil photo de l'app : plein écran, un seul geste par cliché.
 *
 * Passer par l'appareil photo du téléphone coûterait un aller-retour
 * d'application à chaque photo, et il faudrait ensuite ranger le fichier à la
 * main. Ici le cliché part directement dans le dossier de l'audit.
 *
 * La légende se saisit après coup, sans jamais bloquer : le bandeau du bas reste
 * affiché jusqu'au cliché suivant, et une légende commencée mais non validée est
 * enregistrée automatiquement quand on redéclenche — sinon la frappe serait
 * perdue au moment où on en a le plus besoin, en rafale.
 */
@Composable
fun EcranCamera(
    nomAudit: String,
    /** Les destinations possibles : le site, puis chaque écran relevé. */
    cibles: List<Cible>,
    cibleActive: String?,
    onCible: (String?) -> Unit,
    /** Les cases du PV que la cible courante réclame, dans un ordre qui ne bouge pas. */
    exigences: List<Exigence>,
    /** Celles qu'un cliché précédent honore déjà. */
    couvertes: Set<String>,
    prepare: suspend () -> Cliche?,
    enregistre: (Cliche, Uri, (Photo) -> Unit) -> Unit,
    onLegende: (Photo, String) -> Unit,
    onExigence: (Photo, String?) -> Unit,
    onEchec: (String) -> Unit,
    onRetour: () -> Unit,
) {
    val contexte = LocalContext.current
    val proprietaire = LocalLifecycleOwner.current
    val portee = rememberCoroutineScope()

    var autorise by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexte, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var refus by remember { mutableStateOf(false) }
    val demande = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { accorde ->
        autorise = accorde
        refus = !accorde
    }
    LaunchedEffect(Unit) {
        if (!autorise) demande.launch(Manifest.permission.CAMERA)
    }

    if (!autorise) {
        AutorisationManquante(refus, onRetour) { demande.launch(Manifest.permission.CAMERA) }
        return
    }

    val controleur = remember {
        LifecycleCameraController(contexte).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        }
    }
    DisposableEffect(Unit) {
        controleur.bindToLifecycle(proprietaire)
        onDispose { controleur.unbind() }
    }

    var flash by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    LaunchedEffect(flash) { controleur.imageCaptureFlashMode = flash }

    var prises by remember { mutableStateOf(0) }
    var enCours by remember { mutableStateOf(false) }
    var derniere by remember { mutableStateOf<Photo?>(null) }
    var legende by remember { mutableStateOf("") }
    var caseCochee by remember { mutableStateOf<String?>(null) }

    /** Valide la légende en attente, s'il y en a une à valider. */
    fun valideLegende() {
        val cible = derniere
        if (cible != null && legende.isNotBlank() && legende != cible.legende) {
            onLegende(cible, legende)
        }
    }

    fun declenche() {
        if (enCours) return
        enCours = true
        valideLegende()
        // Une case cochée vaut pour le cliché qu'on vient de prendre, pas pour le
        // suivant : on repart à blanc à chaque déclenchement.
        caseCochee = null
        portee.launch {
            val cliche = prepare()
            if (cliche == null) {
                enCours = false
                onEchec("audit introuvable")
                return@launch
            }
            val sortie = ImageCapture.OutputFileOptions
                .Builder(
                    contexte.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cliche.valeurs,
                )
                .build()
            controleur.takePicture(
                sortie,
                ContextCompat.getMainExecutor(contexte),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(resultat: ImageCapture.OutputFileResults) {
                        val uri = resultat.savedUri
                        if (uri == null) {
                            // Sans URI, impossible de relire ni de renommer le
                            // fichier : autant le dire franchement.
                            enCours = false
                            onEchec("destination inconnue")
                            return
                        }
                        enregistre(cliche, uri) { photo ->
                            derniere = photo
                            legende = ""
                            prises++
                            enCours = false
                        }
                    }

                    override fun onError(erreur: ImageCaptureException) {
                        enCours = false
                        onEchec(erreur.message ?: "erreur de l'appareil photo")
                    }
                },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    controller = controleur
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Barre du haut : de quoi savoir où l'on photographie et combien on en a
        // pris, parce qu'en magasin on perd le compte.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { valideLegende(); onRetour() }) {
                Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    nomAudit,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    if (prises == 0) "aucune prise" else "$prises prise(s)",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Trois modes et un seul bouton : l'éclair dit l'état, le mot dit
            // lequel. Un éclair seul laisse hésiter entre « auto » et « forcé »,
            // et se tromper de mode en réserve mal éclairée coûte une photo
            // inutilisable.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        flash = when (flash) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(
                    painterResource(
                        if (flash == ImageCapture.FLASH_MODE_OFF) {
                            R.drawable.ic_eclair_barre
                        } else {
                            R.drawable.ic_eclair
                        },
                    ),
                    contentDescription = "Flash",
                    tint = Color.White,
                )
                EspaceH(4)
                Text(
                    when (flash) {
                        ImageCapture.FLASH_MODE_ON -> "Forcé"
                        ImageCapture.FLASH_MODE_AUTO -> "Auto"
                        else -> "Sans"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // Où rangera-t-on les clichés suivants : le site, ou l'un des deux écrans.
        // Posé en haut, sous le nom du magasin, parce qu'on le change en changeant
        // d'endroit dans le magasin — pas à chaque photo.
        if (cibles.size > 1) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cibles.forEach { cible ->
                    PuceSombre(cible.libelle, cible.ecranId == cibleActive) {
                        onCible(cible.ecranId)
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Le clavier recouvrait le champ de légende qu'il sert à
                // remplir : on tapait à l'aveugle, sans voir ni le texte ni le
                // bouton de validation.
                .imePadding()
                .navigationBarsPadding(),
        ) {
            // Le champ légende ne s'affiche qu'après une prise : avant, il n'a
            // rien à légender et volerait de la place au viseur.
            derniere?.let { photo ->
                // Cocher la case du PV ici, et pas plus tard : c'est le seul instant où
                // l'on sait avec certitude ce que montre le cliché. Le nom du fichier
                // s'en trouve complété dans la foulée.
                if (exigences.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        exigences.forEach { exigence ->
                            // Le crochet dit ce qui est déjà fait sans changer l'ordre
                            // des puces. Les trier par ce qui manque semblait plus
                            // malin — jusqu'à ce qu'en rafale les puces s'échangent
                            // sous le doigt dès qu'on venait d'en cocher une, et qu'on
                            // marque deux clichés de suite comme la même chose.
                            PuceSombre(
                                libelle = if (exigence.cle in couvertes) {
                                    "✓ ${exigence.libelle}"
                                } else {
                                    exigence.libelle
                                },
                                choisie = caseCochee == exigence.cle,
                            ) {
                                val nouvelle = if (caseCochee == exigence.cle) null else exigence.cle
                                caseCochee = nouvelle
                                onExigence(photo, nouvelle)
                            }
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = legende,
                        onValueChange = { legende = it },
                        placeholder = { Text("Légende (facultatif)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { valideLegende() },
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
                    Button(onClick = { valideLegende() }) {
                        Icon(Icons.Default.Check, contentDescription = "Valider la légende")
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Vignette du dernier cliché : la seule vérification possible
                // sans quitter le viseur, et elle répond à la vraie question —
                // « est-ce que ma photo est nette ? ».
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    derniere?.let {
                        AsyncImage(
                            model = it.uri,
                            contentDescription = "Dernier cliché",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }

                Box(
                    Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(enabled = !enCours) { declenche() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (enCours) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.08f)),
                        )
                    }
                }

                Text(
                    "Terminer",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { valideLegende(); onRetour() }
                        .padding(8.dp),
                )
            }
        }
    }
}

/**
 * Sans l'appareil photo, l'app n'a plus d'objet. On explique une fois, et on
 * propose de redemander — un refus définitif se répare dans les réglages
 * système, pas ici.
 */
@Composable
private fun AutorisationManquante(refuse: Boolean, onRetour: () -> Unit, onRedemande: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Appareil photo refusé", style = MaterialTheme.typography.titleLarge)
        EspaceV(8)
        Text(
            if (refuse) {
                "L'app ne peut pas prendre de photo sans cette autorisation. " +
                    "Si la demande ne réapparaît plus, il faut l'accorder dans " +
                    "les réglages Android de l'application."
            } else {
                "Autorisation en attente."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        EspaceV(20)
        Button(onClick = onRedemande) { Text("Redemander") }
        EspaceV(8)
        Text(
            "Retour",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRetour).padding(8.dp),
        )
    }
}

/** Une destination de capture : le site entier, ou l'un des écrans relevés. */
data class Cible(val ecranId: String?, val libelle: String)

/**
 * Une puce lisible sur le viseur.
 *
 * Les puces de Material sont pensées pour un fond de thème ; posées sur une image de
 * rayon en plein soleil, elles disparaissent. D'où ces couleurs tenues à la main.
 */
@Composable
private fun PuceSombre(libelle: String, choisie: Boolean, onClic: () -> Unit) {
    Text(
        libelle,
        color = if (choisie) Color.Black else Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (choisie) Color.White else Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClic)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
