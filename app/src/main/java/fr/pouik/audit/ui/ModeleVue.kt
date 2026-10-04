package fr.pouik.audit.ui

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.Depot
import fr.pouik.audit.donnees.Photo
import fr.pouik.audit.donnees.dossierLibre
import fr.pouik.audit.donnees.nomPhoto
import fr.pouik.audit.photos.Mediatheque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Tout ce qu'il faut savoir avant de déclencher : où écrire, et sous quel nom. */
data class Cliche(val numero: Int, val fichier: String, val valeurs: ContentValues)

class ModeleVue(application: Application) : AndroidViewModel(application) {

    private val depot = Depot(application)
    private val mediatheque = Mediatheque(application)

    val audits: StateFlow<List<Audit>> = depot.audits

    private val _pret = MutableStateFlow(false)
    /** Faux pendant la lecture du catalogue : sans ça, l'écran affiche « aucun
     *  audit » une fraction de seconde avant de se remplir. */
    val pret: StateFlow<Boolean> = _pret.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            depot.charge()
            _pret.value = true
        }
    }

    fun audit(id: String): Audit? = depot.audit(id)

    fun messageLu() {
        _message.value = null
    }

    fun cree(nom: String, lieu: String, notes: String, ensuite: (Audit) -> Unit = {}) {
        viewModelScope.launch {
            val audit = depot.cree(nom, lieu, notes, System.currentTimeMillis())
            ensuite(audit)
        }
    }

    fun majFiche(id: String, nom: String, lieu: String, notes: String) {
        viewModelScope.launch { depot.majFiche(id, nom, lieu, notes) }
    }

    /**
     * Supprime un audit. [avecPhotos] décide du sort des fichiers : les laisser
     * dans `Pictures/Audits` est souvent ce qu'on veut (le compte-rendu est
     * parti, les photos restent archivées), d'où le choix explicite plutôt
     * qu'une règle imposée.
     */
    fun supprime(id: String, avecPhotos: Boolean) {
        viewModelScope.launch {
            val audit = depot.audit(id) ?: return@launch
            if (avecPhotos) {
                var ratees = 0
                audit.photos.forEach { if (!mediatheque.supprime(Uri.parse(it.uri))) ratees++ }
                if (ratees > 0) _message.value = "$ratees photo(s) n'ont pas pu être supprimées"
            }
            depot.supprime(id)
        }
    }

    /**
     * Aligne le dossier sur le nom actuel de l'audit, en y déplaçant les photos.
     *
     * Action volontaire : renommer la fiche ne touche pas au disque, parce que
     * des fichiers qui changent de place sans qu'on l'ait demandé cassent les
     * liens de ce qui a déjà été envoyé.
     */
    fun renommeDossier(id: String) {
        viewModelScope.launch {
            val audit = depot.audit(id) ?: return@launch
            val pris = depot.audits.value.filter { it.id != id }.map { it.dossier }.toSet()
            val cible = dossierLibre(audit.nom, pris)
            if (cible == audit.dossier) {
                _message.value = "Le dossier porte déjà ce nom"
                return@launch
            }
            val deplacees = mediatheque.deplace(audit.photos.map { Uri.parse(it.uri) }, cible)
            // Les noms de fichiers portent l'ancien préfixe de dossier : on les
            // réaligne, sinon le dossier « Lidl-Vitrolles » contiendrait des
            // « Carrefour-Vitrolles-001.jpg ».
            val photos = audit.photos.map { p ->
                val voulu = nomPhoto(cible, p.numero, p.legende)
                val obtenu = mediatheque.renomme(Uri.parse(p.uri), voulu) ?: p.fichier
                p.copy(fichier = obtenu)
            }
            depot.majDossier(id, cible, photos)
            _message.value = if (deplacees == audit.photos.size) {
                "Dossier renommé en $cible"
            } else {
                "Dossier renommé, $deplacees photo(s) sur ${audit.photos.size} déplacées"
            }
        }
    }

    /**
     * Réserve le nom et le numéro du prochain cliché.
     *
     * Appelé juste avant le déclenchement : le numéro est persisté d'avance, si
     * bien qu'une capture ratée brûle un numéro — sans conséquence — là où deux
     * captures simultanées produiraient deux fichiers homonymes.
     */
    suspend fun prepareCliche(id: String): Cliche? {
        val audit = depot.audit(id) ?: return null
        val numero = depot.reserveNumero(id) ?: return null
        val fichier = nomPhoto(audit.dossier, numero)
        return Cliche(numero, fichier, mediatheque.valeurs(audit.dossier, fichier))
    }

    /** Enregistre un cliché que CameraX vient d'écrire. */
    fun clicheEnregistre(id: String, cliche: Cliche, uri: Uri, ensuite: (Photo) -> Unit = {}) {
        viewModelScope.launch {
            // Le nom demandé n'est pas toujours celui retenu : MediaStore
            // suffixe en cas de collision, et c'est son nom qu'on doit afficher.
            val reel = mediatheque.nomReel(uri) ?: cliche.fichier
            val photo = Photo(
                uri = uri.toString(),
                fichier = reel,
                numero = cliche.numero,
                priseLe = System.currentTimeMillis(),
            )
            depot.ajoutePhoto(id, photo)
            ensuite(photo)
        }
    }

    fun echecCapture(raison: String) {
        _message.value = "Photo non enregistrée : $raison"
    }

    /**
     * Pose ou change la légende d'un cliché, et renomme le fichier pour qu'elle
     * se lise aussi depuis un explorateur de fichiers.
     */
    fun majLegende(id: String, photo: Photo, legende: String) {
        viewModelScope.launch {
            val audit = depot.audit(id) ?: return@launch
            val voulu = nomPhoto(audit.dossier, photo.numero, legende)
            val obtenu = if (voulu == photo.fichier) {
                photo.fichier
            } else {
                mediatheque.renomme(Uri.parse(photo.uri), voulu) ?: photo.fichier
            }
            depot.majPhoto(id, photo.uri, legende, obtenu)
        }
    }

    fun supprimePhoto(id: String, photo: Photo) {
        viewModelScope.launch {
            // Le catalogue est nettoyé même si le fichier résiste : une vignette
            // qui ne mène à rien est plus gênante qu'un fichier orphelin.
            if (!mediatheque.supprime(Uri.parse(photo.uri))) {
                _message.value = "Fichier non supprimé, retiré de l'audit"
            }
            depot.retirePhoto(id, photo.uri)
        }
    }
}
