package fr.pouik.audit.ui

import android.content.Context
import coil.request.ImageRequest
import fr.pouik.audit.donnees.Photo

/**
 * La requête d'affichage d'un cliché, versionnée.
 *
 * Annoter réécrit le fichier sans changer son URI : pour Coil, rien n'a bougé, et la
 * vignette reste celle d'avant jusqu'au prochain redémarrage. Les clés de cache portent
 * donc le numéro de version de la photo — c'est la seule chose qui distingue deux états
 * d'un même fichier.
 */
fun requeteImage(contexte: Context, photo: Photo): ImageRequest =
    ImageRequest.Builder(contexte)
        .data(photo.uri)
        .memoryCacheKey("${photo.uri}#${photo.version}")
        .diskCacheKey("${photo.uri}#${photo.version}")
        .build()
