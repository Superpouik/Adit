package fr.pouik.audit.donnees

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Le catalogue des audits : un fichier JSON dans filesDir, chargé en mémoire au
 * démarrage.
 *
 * Les photos, elles, ne sont pas ici — elles vivent dans MediaStore. Ce fichier
 * ne contient que ce que MediaStore ne sait pas dire : à quel audit appartient
 * un cliché, et ce qu'il montre.
 *
 * Prend un dossier et non un Context pour que la persistance soit testable sur
 * la JVM, sans émulateur.
 */
class Depot(dossier: File) {

    constructor(contexte: Context) : this(contexte.filesDir)

    private val fichier = File(dossier, "audits.json")
    private val temporaire = File(dossier, "audits.json.tmp")
    private val json = Json {
        prettyPrint = true
        // Un champ ajouté plus tard ne doit pas rendre illisible un catalogue
        // écrit par une version précédente.
        ignoreUnknownKeys = true
    }

    private val verrou = Mutex()
    private val _audits = MutableStateFlow<List<Audit>>(emptyList())
    val audits: StateFlow<List<Audit>> = _audits.asStateFlow()

    suspend fun charge() = withContext(Dispatchers.IO) {
        _audits.value = lis()
    }

    private fun lis(): List<Audit> = try {
        if (fichier.exists()) {
            json.decodeFromString<Catalogue>(fichier.readText()).audits
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        // Catalogue illisible : on le met de côté au lieu de l'écraser. Les
        // photos sont toujours dans Pictures/Audits, donc récupérables même si
        // leurs légendes sont perdues.
        if (fichier.exists()) fichier.renameTo(File(fichier.parentFile, "audits.corrompu.json"))
        emptyList()
    }

    fun audit(id: String): Audit? = _audits.value.firstOrNull { it.id == id }

    /**
     * Crée un audit et renvoie la fiche telle qu'elle a été enregistrée — son
     * `dossier` peut différer du nom demandé si celui-ci était déjà pris.
     */
    suspend fun cree(nom: String, lieu: String, notes: String, maintenant: Long): Audit =
        modifieCatalogue { liste ->
            val audit = Audit(
                id = UUID.randomUUID().toString(),
                nom = nom.trim(),
                lieu = lieu.trim(),
                notes = notes.trim(),
                creeLe = maintenant,
                dossier = dossierLibre(nom, liste.map { it.dossier }.toSet()),
            )
            liste + audit to audit
        }

    suspend fun majFiche(id: String, nom: String, lieu: String, notes: String): Audit? =
        modifie(id) { it.copy(nom = nom.trim(), lieu = lieu.trim(), notes = notes.trim()) }

    /** Suit un dossier renommé sur le disque (voir Mediatheque.deplace). */
    suspend fun majDossier(id: String, dossier: String, photos: List<Photo>): Audit? =
        modifie(id) { it.copy(dossier = dossier, photos = photos) }

    suspend fun supprime(id: String): Boolean = modifieCatalogue { liste ->
        liste.filterNot { it.id == id } to true
    }

    /**
     * Réserve le numéro du prochain cliché, avant même de déclencher.
     *
     * Réservé et persisté d'avance : une capture qui échoue brûle un numéro,
     * ce qui ne coûte rien, là où deux captures qui se partagent le même
     * numéro donneraient deux fichiers de même nom.
     */
    suspend fun reserveNumero(id: String): Int? {
        var numero: Int? = null
        modifie(id) { audit ->
            numero = audit.compteur
            audit.copy(compteur = audit.compteur + 1)
        }
        return numero
    }

    suspend fun ajoutePhoto(id: String, photo: Photo): Audit? =
        modifie(id) { it.copy(photos = it.photos + photo) }

    suspend fun majPhoto(id: String, uri: String, legende: String, fichier: String): Audit? =
        modifie(id) { audit ->
            audit.copy(
                photos = audit.photos.map {
                    if (it.uri == uri) it.copy(legende = legende.trim(), fichier = fichier) else it
                },
            )
        }

    suspend fun majAnnotations(id: String, uri: String, formes: List<Forme>): Audit? =
        modifie(id) { audit ->
            audit.copy(
                photos = audit.photos.map {
                    if (it.uri == uri) {
                        it.copy(annotations = formes, version = it.version + 1)
                    } else {
                        it
                    }
                },
            )
        }

    suspend fun retirePhoto(id: String, uri: String): Audit? =
        modifie(id) { audit -> audit.copy(photos = audit.photos.filterNot { it.uri == uri }) }

    /**
     * Applique une transformation à un audit et réécrit le catalogue.
     *
     * Le mutex n'est pas décoratif : lire `_audits.value` puis le réécrire n'est
     * pas atomique. Deux captures rapprochées lançaient deux coroutines partant
     * de la même liste, et la seconde écrasait la photo de la première.
     */
    private suspend fun modifie(id: String, transforme: (Audit) -> Audit): Audit? =
        modifieCatalogue { liste ->
            val cible = liste.firstOrNull { it.id == id } ?: return@modifieCatalogue liste to null
            val nouveau = transforme(cible)
            liste.map { if (it.id == id) nouveau else it } to nouveau
        }

    private suspend fun <T> modifieCatalogue(transforme: (List<Audit>) -> Pair<List<Audit>, T>): T =
        verrou.withLock {
            withContext(Dispatchers.IO) {
                val (nouveau, resultat) = transforme(_audits.value)
                if (nouveau !== _audits.value) {
                    _audits.value = nouveau
                    ecris(nouveau)
                }
                resultat
            }
        }

    /**
     * Écrit le catalogue par fichier temporaire renommé : un plantage au mauvais
     * moment laisserait sinon un JSON tronqué, et toutes les légendes avec lui.
     *
     * `renameTo` renvoie un booléen qu'il ne faut pas ignorer — un renommage
     * refusé laisserait croire que c'est enregistré. Le repli recopie
     * directement, quitte à perdre l'atomicité.
     */
    private fun ecris(liste: List<Audit>): Boolean = try {
        val contenu = json.encodeToString(Catalogue(liste))
        temporaire.writeText(contenu)
        if (temporaire.renameTo(fichier)) {
            true
        } else {
            fichier.writeText(contenu)
            temporaire.delete()
            true
        }
    } catch (e: Exception) {
        false
    }
}
