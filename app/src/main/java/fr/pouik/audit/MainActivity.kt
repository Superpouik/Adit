package fr.pouik.audit

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.cheminRelatif
import fr.pouik.audit.photos.copie
import fr.pouik.audit.photos.partagePhotos
import fr.pouik.audit.photos.partageRecapitulatif
import fr.pouik.audit.photos.partageUnePhoto
import fr.pouik.audit.ui.Confirmation
import fr.pouik.audit.ui.EcranAudit
import fr.pouik.audit.ui.EcranAudits
import fr.pouik.audit.ui.EcranEditeur
import fr.pouik.audit.ui.EcranCamera
import fr.pouik.audit.ui.EcranPhoto
import fr.pouik.audit.ui.FeuilleAudit
import fr.pouik.audit.ui.ModeleVue
import fr.pouik.audit.ui.ThemeAudit

class MainActivity : ComponentActivity() {
    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        enableEdgeToEdge()
        setContent {
            ThemeAudit {
                Application(viewModel())
            }
        }
    }
}

/**
 * Les quatre destinations de l'app.
 *
 * Navigation tenue à la main plutôt qu'avec navigation-compose : quatre écrans,
 * dont deux portent un identifiant, ne valent pas une bibliothèque et son graphe
 * de routes à sérialiser.
 */
private sealed interface Ecran {
    data object Liste : Ecran
    data class Detail(val id: String) : Ecran
    data class Camera(val id: String) : Ecran
    data class Visionneuse(val id: String, val uri: String) : Ecran
    data class Editeur(val id: String, val uri: String) : Ecran
}

@Composable
private fun Application(modele: ModeleVue) {
    val contexte = LocalContext.current
    val audits by modele.audits.collectAsStateWithLifecycle()
    val pret by modele.pret.collectAsStateWithLifecycle()
    val message by modele.message.collectAsStateWithLifecycle()
    val apercu by modele.apercu.collectAsStateWithLifecycle()
    val enregistreAnnotations by modele.enregistreAnnotations.collectAsStateWithLifecycle()

    var ecran by remember { mutableStateOf<Ecran>(Ecran.Liste) }
    // null = fermée, Pair(null) = création, Pair(audit) = modification
    var fiche by remember { mutableStateOf<Pair<Audit?, Boolean>?>(null) }
    var aSupprimer by remember { mutableStateOf<Audit?>(null) }

    message?.let { texte ->
        LaunchedEffect(texte) {
            Toast.makeText(contexte, texte, Toast.LENGTH_LONG).show()
            modele.messageLu()
        }
    }

    // L'audit affiché est relu dans la liste à chaque recomposition : garder la
    // fiche de côté dans l'état de navigation la figerait, et la grille ne
    // montrerait plus les photos prises depuis.
    val ouvert = (ecran as? Ecran.Detail)?.id
        ?: (ecran as? Ecran.Camera)?.id
        ?: (ecran as? Ecran.Visionneuse)?.id
        ?: (ecran as? Ecran.Editeur)?.id
    val audit = audits.firstOrNull { it.id == ouvert }

    // Un audit supprimé pendant qu'on le regarde (ou un identifiant devenu
    // caduc) ramène à la liste au lieu d'afficher un écran vide.
    LaunchedEffect(ouvert, audit) {
        if (ouvert != null && audit == null) ecran = Ecran.Liste
    }

    BackHandler(enabled = ecran != Ecran.Liste) {
        ecran = when (val e = ecran) {
            is Ecran.Visionneuse -> Ecran.Detail(e.id)
            is Ecran.Camera -> Ecran.Detail(e.id)
            // Le retour depuis l'éditeur ramène à la photo qu'on vient d'annoter, pas
            // à la grille : on veut vérifier son travail en grand.
            is Ecran.Editeur -> Ecran.Visionneuse(e.id, e.uri)
            else -> Ecran.Liste
        }
    }

    when (val e = ecran) {
        is Ecran.Liste -> EcranAudits(
            audits = audits,
            pret = pret,
            onOuvre = { ecran = Ecran.Detail(it.id) },
            onNouveau = { fiche = null to true },
            onEdite = { fiche = it to true },
            onRenommeDossier = { modele.renommeDossier(it.id) },
            onSupprime = { aSupprimer = it },
        )

        is Ecran.Detail -> audit?.let { a ->
            EcranAudit(
                audit = a,
                onRetour = { ecran = Ecran.Liste },
                onPhotographie = { ecran = Ecran.Camera(a.id) },
                onOuvrePhoto = { ecran = Ecran.Visionneuse(a.id, it.uri) },
                onEditeFiche = { fiche = a to true },
                onPartagePhotos = {
                    if (!partagePhotos(contexte, a)) {
                        Toast.makeText(contexte, "Rien à envoyer", Toast.LENGTH_SHORT).show()
                    }
                },
                onPartageRecap = {
                    if (!partageRecapitulatif(contexte, a)) {
                        Toast.makeText(contexte, "Envoi impossible", Toast.LENGTH_SHORT).show()
                    }
                },
                onCopieChemin = {
                    copie(contexte, cheminRelatif(a.dossier))
                    Toast.makeText(contexte, "Chemin copié", Toast.LENGTH_SHORT).show()
                },
            )
        }

        is Ecran.Camera -> audit?.let { a ->
            EcranCamera(
                nomAudit = a.nom,
                prepare = { modele.prepareCliche(a.id) },
                enregistre = { cliche, uri, ensuite ->
                    modele.clicheEnregistre(a.id, cliche, uri, ensuite)
                },
                onLegende = { photo, legende -> modele.majLegende(a.id, photo, legende) },
                onEchec = modele::echecCapture,
                onRetour = { ecran = Ecran.Detail(a.id) },
            )
        }

        is Ecran.Visionneuse -> audit?.let { a ->
            EcranPhoto(
                photos = a.photos.sortedBy { it.numero },
                depart = e.uri,
                onRetour = { ecran = Ecran.Detail(a.id) },
                onLegende = { photo, legende -> modele.majLegende(a.id, photo, legende) },
                onSupprime = { modele.supprimePhoto(a.id, it) },
                onPartage = { partageUnePhoto(contexte, it.uri, it.legende) },
                onAnnote = { ecran = Ecran.Editeur(a.id, it.uri) },
            )
        }

        is Ecran.Editeur -> {
            val photo = audit?.photos?.firstOrNull { it.uri == e.uri }
            if (photo == null) {
                LaunchedEffect(e.uri) { ecran = Ecran.Liste }
            } else {
                // Le chargement est lancé une fois par photo ouverte : relire le fichier
                // à chaque recomposition relirait plusieurs mégaoctets pour rien.
                LaunchedEffect(e.uri) { modele.ouvreEditeur(e.id, photo) }
                DisposableEffect(e.uri) { onDispose { modele.fermeEditeur() } }
                EcranEditeur(
                    titre = photo.legende.ifBlank { photo.fichier },
                    image = apercu,
                    initiales = photo.annotations,
                    enregistrement = enregistreAnnotations,
                    onRetour = { ecran = Ecran.Visionneuse(e.id, e.uri) },
                    onEnregistre = { formes ->
                        modele.enregistreAnnotations(e.id, photo, formes) {
                            ecran = Ecran.Visionneuse(e.id, e.uri)
                        }
                    },
                )
            }
        }
    }

    fiche?.let { (initial, _) ->
        FeuilleAudit(
            initial = initial,
            dossiersPris = audits.map { it.dossier }.toSet(),
            onFerme = { fiche = null },
            onValide = { nom, lieu, notes ->
                fiche = null
                if (initial == null) {
                    // Créer puis enchaîner sur l'appareil photo : on crée un
                    // audit parce qu'on est devant le site, pas pour remplir un
                    // catalogue.
                    modele.cree(nom, lieu, notes) { cree -> ecran = Ecran.Camera(cree.id) }
                } else {
                    modele.majFiche(initial.id, nom, lieu, notes)
                }
            },
        )
    }

    aSupprimer?.let { cible ->
        Confirmation(
            titre = "Supprimer « ${cible.nom} » ?",
            texte = "Les ${cible.photos.size} photo(s) restent dans " +
                "${cheminRelatif(cible.dossier)} : seule la fiche disparaît.",
            libelleAction = "Supprimer la fiche",
            secondaire = "Supprimer aussi les photos" to {
                aSupprimer = null
                modele.supprime(cible.id, avecPhotos = true)
            },
            onConfirme = {
                aSupprimer = null
                modele.supprime(cible.id, avecPhotos = false)
            },
            onAnnule = { aSupprimer = null },
        )
    }
}
