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
import fr.pouik.audit.donnees.Ecran as EcranReleve
import fr.pouik.audit.donnees.Exigence
import fr.pouik.audit.donnees.cartouche
import fr.pouik.audit.donnees.cheminRelatif
import fr.pouik.audit.donnees.ecran
import fr.pouik.audit.donnees.exigences
import fr.pouik.audit.donnees.photosDe
import fr.pouik.audit.photos.copie
import fr.pouik.audit.photos.partagePhotos
import fr.pouik.audit.photos.partageRecapitulatif
import fr.pouik.audit.photos.partageUnePhoto
import fr.pouik.audit.ui.Cible
import fr.pouik.audit.ui.Confirmation
import fr.pouik.audit.ui.EcranAudit
import fr.pouik.audit.ui.EcranAudits
import fr.pouik.audit.ui.EcranCamera
import fr.pouik.audit.ui.EcranEditeur
import fr.pouik.audit.ui.EcranPhoto
import fr.pouik.audit.ui.FeuilleAudit
import fr.pouik.audit.ui.FeuilleEcran
import fr.pouik.audit.ui.FeuilleSite
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
 * Les cinq destinations de l'app.
 *
 * Navigation tenue à la main plutôt qu'avec navigation-compose : cinq écrans, dont
 * quatre portent un identifiant, ne valent pas une bibliothèque et son graphe de routes
 * à sérialiser.
 */
private sealed interface Vue {
    data object Liste : Vue
    data class Detail(val id: String) : Vue
    /** [ecranId] dit où rangera les clichés : un emplacement, ou le site entier. */
    data class Camera(val id: String, val ecranId: String?) : Vue
    data class Visionneuse(val id: String, val uri: String) : Vue
    data class Editeur(val id: String, val uri: String) : Vue
}

@Composable
private fun Application(modele: ModeleVue) {
    val contexte = LocalContext.current
    val audits by modele.audits.collectAsStateWithLifecycle()
    val pret by modele.pret.collectAsStateWithLifecycle()
    val message by modele.message.collectAsStateWithLifecycle()
    val apercu by modele.apercu.collectAsStateWithLifecycle()
    val enregistreAnnotations by modele.enregistreAnnotations.collectAsStateWithLifecycle()

    var vue by remember { mutableStateOf<Vue>(Vue.Liste) }
    var fiche by remember { mutableStateOf<Pair<Audit?, Boolean>?>(null) }
    var ficheEcran by remember { mutableStateOf<String?>(null) }
    var ficheSite by remember { mutableStateOf(false) }
    var aSupprimer by remember { mutableStateOf<Audit?>(null) }

    message?.let { texte ->
        LaunchedEffect(texte) {
            Toast.makeText(contexte, texte, Toast.LENGTH_LONG).show()
            modele.messageLu()
        }
    }

    // L'audit affiché est relu dans la liste à chaque recomposition : garder la fiche
    // de côté dans l'état de navigation la figerait, et la grille ne montrerait plus
    // les photos prises depuis.
    val ouvert = when (val v = vue) {
        is Vue.Detail -> v.id
        is Vue.Camera -> v.id
        is Vue.Visionneuse -> v.id
        is Vue.Editeur -> v.id
        Vue.Liste -> null
    }
    val audit = audits.firstOrNull { it.id == ouvert }

    // Un audit supprimé pendant qu'on le regarde (ou un identifiant devenu caduc)
    // ramène à la liste au lieu d'afficher un écran vide.
    LaunchedEffect(ouvert, audit) {
        if (ouvert != null && audit == null) vue = Vue.Liste
    }

    BackHandler(enabled = vue != Vue.Liste) {
        vue = when (val v = vue) {
            is Vue.Visionneuse -> Vue.Detail(v.id)
            is Vue.Camera -> Vue.Detail(v.id)
            // Le retour depuis l'éditeur ramène à la photo qu'on vient d'annoter, pas
            // à la grille : on veut vérifier son travail en grand.
            is Vue.Editeur -> Vue.Visionneuse(v.id, v.uri)
            else -> Vue.Liste
        }
    }

    when (val v = vue) {
        is Vue.Liste -> EcranAudits(
            audits = audits,
            pret = pret,
            onOuvre = { vue = Vue.Detail(it.id) },
            onNouveau = { fiche = null to true },
            onEdite = { fiche = it to true },
            onRenommeDossier = { modele.renommeDossier(it.id) },
            onSupprime = { aSupprimer = it },
        )

        is Vue.Detail -> audit?.let { a ->
            EcranAudit(
                audit = a,
                onRetour = { vue = Vue.Liste },
                onPhotographie = { ecranId -> vue = Vue.Camera(a.id, ecranId) },
                onOuvrePhoto = { vue = Vue.Visionneuse(a.id, it.uri) },
                onEditeFiche = { fiche = a to true },
                onFicheSite = { ficheSite = true },
                onEditeEcran = { ficheEcran = it.id },
                onAjouteEcran = {
                    // On enchaîne sur la fiche : un écran sans taille ni support ne
                    // sert à rien, et c'est devant l'emplacement qu'on les connaît.
                    modele.ajouteEcran(a.id) { nouveau -> ficheEcran = nouveau.id }
                },
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

        is Vue.Camera -> audit?.let { a ->
            val cibles = remember(a.ecrans) {
                listOf(Cible(null, "Le site")) +
                    a.ecrans.sortedBy { it.numero }.map { Cible(it.id, "Écran ${it.numero}") }
            }
            val couvertes = remember(a, v.ecranId) {
                a.photosDe(v.ecranId).mapNotNull { it.exigence }.toSet()
            }
            val attendues = remember(a.ecrans, v.ecranId) {
                a.ecran(v.ecranId)?.exigences() ?: Exigence.pourSite()
            }
            EcranCamera(
                nomAudit = a.nom,
                cibles = cibles,
                cibleActive = v.ecranId,
                onCible = { vue = Vue.Camera(a.id, it) },
                exigences = attendues,
                couvertes = couvertes,
                prepare = { modele.prepareCliche(a.id, v.ecranId) },
                enregistre = { cliche, uri, ensuite ->
                    modele.clicheEnregistre(a.id, cliche, uri, v.ecranId, ensuite)
                },
                onLegende = { photo, legende -> modele.majLegende(a.id, photo, legende) },
                onExigence = { photo, cle -> modele.rattache(a.id, photo, v.ecranId, cle) },
                onEchec = modele::echecCapture,
                onRetour = { vue = Vue.Detail(a.id) },
            )
        }

        is Vue.Visionneuse -> audit?.let { a ->
            EcranPhoto(
                photos = a.photos.sortedBy { it.numero },
                depart = v.uri,
                onRetour = { vue = Vue.Detail(a.id) },
                onLegende = { photo, legende -> modele.majLegende(a.id, photo, legende) },
                onSupprime = { modele.supprimePhoto(a.id, it) },
                onPartage = { partageUnePhoto(contexte, it.uri, it.legende) },
                onAnnote = { vue = Vue.Editeur(a.id, it.uri) },
            )
        }

        is Vue.Editeur -> {
            val photo = audit?.photos?.firstOrNull { it.uri == v.uri }
            if (audit == null || photo == null) {
                LaunchedEffect(v.uri) { vue = Vue.Liste }
            } else {
                // Le chargement est lancé une fois par photo ouverte : relire le fichier
                // à chaque recomposition relirait plusieurs mégaoctets pour rien.
                LaunchedEffect(v.uri) { modele.ouvreEditeur(v.id, photo) }
                DisposableEffect(v.uri) { onDispose { modele.fermeEditeur() } }
                EcranEditeur(
                    titre = photo.legende.ifBlank { photo.fichier },
                    image = apercu,
                    initiales = photo.annotations,
                    cartouche = cartouche(audit, audit.ecran(photo.ecranId)),
                    enregistrement = enregistreAnnotations,
                    onRetour = { vue = Vue.Visionneuse(v.id, v.uri) },
                    onEnregistre = { formes ->
                        modele.enregistreAnnotations(v.id, photo, formes) {
                            vue = Vue.Visionneuse(v.id, v.uri)
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
                    // Créer puis enchaîner sur l'appareil photo : on crée un audit
                    // parce qu'on est devant le site, pas pour remplir un catalogue.
                    modele.cree(nom, lieu, notes) { cree -> vue = Vue.Camera(cree.id, null) }
                } else {
                    modele.majFiche(initial.id, nom, lieu, notes)
                }
            },
        )
    }

    ficheEcran?.let { idEcran ->
        val courant: EcranReleve? = audit?.ecran(idEcran)
        if (audit == null || courant == null) {
            LaunchedEffect(idEcran) { ficheEcran = null }
        } else {
            FeuilleEcran(
                audit = audit,
                initial = courant,
                onFerme = { ficheEcran = null },
                onEnregistre = {
                    ficheEcran = null
                    modele.majEcran(audit.id, it)
                },
                onSupprime = {
                    ficheEcran = null
                    modele.supprimeEcran(audit.id, it.id)
                },
            )
        }
    }

    if (ficheSite && audit != null) {
        FeuilleSite(
            audit = audit,
            onFerme = { ficheSite = false },
            onEnregistre = { interlocuteur, contraintes, nacelle, hauteur, duree ->
                ficheSite = false
                modele.majFicheSite(audit.id, interlocuteur, contraintes, nacelle, hauteur, duree)
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
