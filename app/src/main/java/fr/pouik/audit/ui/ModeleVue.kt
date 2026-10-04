package fr.pouik.audit.ui

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.pouik.audit.donnees.Audit
import fr.pouik.audit.donnees.Depot
import fr.pouik.audit.donnees.Ecran
import fr.pouik.audit.donnees.Exigence
import fr.pouik.audit.donnees.Forme
import fr.pouik.audit.donnees.Photo
import fr.pouik.audit.donnees.cle
import fr.pouik.audit.donnees.ecran
import fr.pouik.audit.donnees.dossierLibre
import fr.pouik.audit.donnees.nomPhoto
import fr.pouik.audit.photos.Mediatheque
import fr.pouik.audit.photos.Retouche
import fr.pouik.audit.photos.SourceImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Tout ce qu'il faut savoir avant de déclencher : où écrire, et sous quel nom. */
data class Cliche(val numero: Int, val fichier: String, val valeurs: ContentValues)

class ModeleVue(application: Application) : AndroidViewModel(application) {

    private val depot = Depot(application)
    private val mediatheque = Mediatheque(application)
    private val retouche = Retouche(application)

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

    private val _apercu = MutableStateFlow<Bitmap?>(null)
    /** L'image chargée dans l'éditeur, en version réduite. */
    val apercu: StateFlow<Bitmap?> = _apercu.asStateFlow()

    private val _enregistreAnnotations = MutableStateFlow(false)
    val enregistreAnnotations: StateFlow<Boolean> = _enregistreAnnotations.asStateFlow()

    fun audit(id: String): Audit? = depot.audit(id)

    /**
     * Prépare l'éditeur : charge l'image de travail.
     *
     * C'est l'original mis de côté qui sert de base dès qu'il existe, jamais le fichier
     * visible : celui-ci porte déjà les annotations, et les redessiner par-dessus les
     * empilerait à chaque passage.
     */
    fun ouvreEditeur(idAudit: String, photo: Photo) {
        _apercu.value = null
        viewModelScope.launch {
            val original = retouche.original(photo.cle(idAudit))
            val source = if (original.exists() && original.length() > 0) {
                SourceImage.Fichier(original)
            } else {
                SourceImage.Entree(Uri.parse(photo.uri))
            }
            val image = retouche.apercu(source)
            if (image == null) _message.value = "Image illisible"
            _apercu.value = image
        }
    }

    /** Libère l'aperçu : quelques dizaines de mégaoctets qui n'ont plus de raison d'être. */
    fun fermeEditeur() {
        _apercu.value = null
    }

    /**
     * Grave les annotations dans le fichier et les garde en clair dans le catalogue.
     *
     * Une liste vide est un cas à part : elle veut dire « remets la photo d'origine ».
     * On restaure alors le fichier et on jette l'original privé, au lieu de laisser une
     * copie dormir indéfiniment dans le stockage de l'app.
     */
    fun enregistreAnnotations(
        idAudit: String,
        photo: Photo,
        formes: List<Forme>,
        ensuite: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _enregistreAnnotations.value = true
            val cle = photo.cle(idAudit)
            val uri = Uri.parse(photo.uri)
            val original = retouche.original(cle)

            val abouti = if (formes.isEmpty()) {
                val restaure = !original.exists() || retouche.restaure(cle, uri)
                if (restaure) retouche.oublieOriginal(cle)
                restaure
            } else {
                val base = retouche.metDeCote(uri, cle)
                if (base == null) {
                    false
                } else {
                    retouche.applique(SourceImage.Fichier(base), uri, formes)
                }
            }

            if (abouti) {
                depot.majAnnotations(idAudit, photo.uri, formes)
            } else {
                // Le catalogue n'est pas touché : annoncer que c'est enregistré alors
                // que le fichier envoyé au client ne porte rien serait le pire des cas.
                _message.value = "Annotations non enregistrées : écriture impossible"
            }
            _enregistreAnnotations.value = false
            if (abouti) ensuite()
        }
    }

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
            // Les originaux mis de côté partent dans tous les cas : ils ne servent qu'à
            // ré-éditer les annotations d'un audit qui n'existe plus.
            audit.photos.forEach { retouche.oublieOriginal(it.cle(id)) }
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
    suspend fun prepareCliche(id: String, ecranId: String?): Cliche? {
        val audit = depot.audit(id) ?: return null
        val numero = depot.reserveNumero(id) ?: return null
        val fichier = nomPhoto(audit.dossier, numero, ecran = audit.ecran(ecranId)?.numero)
        return Cliche(numero, fichier, mediatheque.valeurs(audit.dossier, fichier))
    }

    /** Enregistre un cliché que CameraX vient d'écrire. */
    fun clicheEnregistre(
        id: String,
        cliche: Cliche,
        uri: Uri,
        ecranId: String?,
        ensuite: (Photo) -> Unit = {},
    ) {
        viewModelScope.launch {
            // Le nom demandé n'est pas toujours celui retenu : MediaStore
            // suffixe en cas de collision, et c'est son nom qu'on doit afficher.
            val reel = mediatheque.nomReel(uri) ?: cliche.fichier
            val photo = Photo(
                uri = uri.toString(),
                fichier = reel,
                numero = cliche.numero,
                priseLe = System.currentTimeMillis(),
                ecranId = ecranId,
            )
            depot.ajoutePhoto(id, photo)
            ensuite(photo)
        }
    }

    // ---- Emplacements d'écran et fiche du site ------------------------------------

    fun ajouteEcran(id: String, ensuite: (Ecran) -> Unit = {}) {
        viewModelScope.launch {
            val ecran = depot.ajouteEcran(id)
            if (ecran == null) {
                _message.value = "Deux écrans au maximum par site"
            } else {
                ensuite(ecran)
            }
        }
    }

    fun majEcran(id: String, ecran: Ecran) {
        viewModelScope.launch {
            depot.majEcran(id, ecran)
            // Le support décide du nom de fichier (le cartouche en dépend) et de la
            // liste des photos exigées : les noms déjà posés doivent suivre.
            realigneNoms(id, ecran.id)
        }
    }

    fun supprimeEcran(id: String, idEcran: String) {
        viewModelScope.launch {
            depot.supprimeEcran(id, idEcran)
            // Les clichés redeviennent des photos de site : leur préfixe « E1 » n'a
            // plus de sens, et un dossier qui ment sur son contenu est pire que pas
            // de préfixe du tout.
            realigneNoms(id, null)
        }
    }

    fun majFicheSite(
        id: String,
        interlocuteur: String,
        contraintes: String,
        nacelle: Boolean?,
        hauteurPrerequis: String,
        dureeInstallation: String,
    ) {
        viewModelScope.launch {
            depot.majFicheSite(id, interlocuteur, contraintes, nacelle, hauteurPrerequis, dureeInstallation)
        }
    }

    /**
     * Rattache un cliché à un écran et à la case du PV qu'il honore, puis renomme le
     * fichier en conséquence.
     */
    fun rattache(id: String, photo: Photo, ecranId: String?, exigence: String?) {
        viewModelScope.launch {
            depot.majRattachement(id, photo.uri, ecranId, exigence)
            renomme(id, photo.uri)
        }
    }

    /**
     * Réécrit le nom de fichier d'un cliché d'après ce qu'on sait de lui.
     *
     * Le nom porte le numéro d'écran, la case du PV et la légende : c'est tout ce que
     * le destinataire aura pour s'y retrouver dans une pièce jointe, une fois le mail
     * ouvert loin de l'application.
     */
    private suspend fun renomme(id: String, uri: String) {
        val audit = depot.audit(id) ?: return
        val photo = audit.photos.firstOrNull { it.uri == uri } ?: return
        val role = Exigence.parCle(photo.exigence)?.libelle.orEmpty()
        val description = listOf(role, photo.legende).filter { it.isNotBlank() }.joinToString(" ")
        val voulu = nomPhoto(
            dossier = audit.dossier,
            numero = photo.numero,
            legende = description,
            ecran = audit.ecran(photo.ecranId)?.numero,
        )
        if (voulu == photo.fichier) return
        val obtenu = mediatheque.renomme(Uri.parse(photo.uri), voulu) ?: photo.fichier
        depot.majPhoto(id, photo.uri, photo.legende, obtenu)
    }

    private suspend fun realigneNoms(id: String, idEcran: String?) {
        val audit = depot.audit(id) ?: return
        audit.photos
            .filter { idEcran == null || it.ecranId == idEcran }
            .forEach { renomme(id, it.uri) }
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
            depot.majPhoto(id, photo.uri, legende, photo.fichier)
            renomme(id, photo.uri)
        }
    }

    fun supprimePhoto(id: String, photo: Photo) {
        viewModelScope.launch {
            // Le catalogue est nettoyé même si le fichier résiste : une vignette
            // qui ne mène à rien est plus gênante qu'un fichier orphelin.
            if (!mediatheque.supprime(Uri.parse(photo.uri))) {
                _message.value = "Fichier non supprimé, retiré de l'audit"
            }
            // L'original annoté dort dans le stockage privé : sans ça, supprimer une
            // photo laisserait sa copie occuper la place pour toujours.
            retouche.oublieOriginal(photo.cle(id))
            depot.retirePhoto(id, photo.uri)
        }
    }
}
