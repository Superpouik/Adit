# Leonida Map

Carte interactive de l'État de Leonida, application Android fan-made, ambiance néon / coucher de soleil.

- Carte dessinée (côtes, Keys, marais, montagnes, routes) : déplacement au doigt, zoom à deux doigts, boutons + / − / ⌂.
- 51 points d'intérêt dans 17 catégories : magasins de vêtements, armureries, garages, restos, bars, clubs, hôpitaux, police, banques, stations, aéroports, marinas, plages, planques, activités…
- Recherche, filtres par catégorie, liste par région, favoris ⭐ et lieux visités ✔ (gardés sur le téléphone).

**Les emplacements sont approximatifs.** Tout est dans `app/src/main/assets/leonida.json` :
ajouter ou corriger un lieu = modifier `x` (0–1000, ouest → est) et `y` (0–1400, nord → sud).

Projet non officiel, sans lien avec Rockstar Games ni Take-Two ; aucun visuel du jeu n'est utilisé.

## Compiler

    cd leonida && ./gradlew assembleRelease
    adb install app/build/outputs/apk/release/app-release.apk
