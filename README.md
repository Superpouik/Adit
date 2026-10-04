# Audit

Application Android pour photographier des relevés de terrain **en les rangeant au
moment de la prise de vue**, au lieu de les retrouver mélangés dans la pellicule le
soir venu.

Le cas qui l'a fait naître : des audits avant installation d'écrans PLV. Trois ou
quatre sites dans la journée, trente photos par site, et une galerie où plus rien ne
se distingue.

| Liste et fiche | Appareil photo | Les clichés de l'audit |
|---|---|---|
| ![fiche](docs/fiche.png) | ![caméra](docs/camera.png) | ![grille](docs/grille.png) |

## Ce qu'elle fait

- Un **audit par site** : nom, ville ou adresse, notes libres. Créé en trois secondes,
  complété plus tard.
- Un **appareil photo intégré** : le cliché part directement dans le dossier de
  l'audit, sans passer par l'app photo du téléphone ni par un rangement manuel.
- Une **légende facultative** après chaque photo, qui rejoint le nom du fichier —
  `Lidl-Vitrolles-003-prise-elec.jpg` se comprend sans l'app.
- Un **vrai dossier** par audit, dans `Pictures/Audits/<site>` : visible en USB, dans
  l'explorateur de fichiers, et dans la galerie sous forme d'albums séparés.
- L'**envoi** des photos ou d'un récapitulatif texte, par mail ou messagerie.

## Les décisions qui ne se devinent pas à la lecture du code

**Le dossier ne suit pas les renommages de la fiche.** Corriger le nom d'un audit ne
déplace rien : des fichiers qui changent de place sans qu'on l'ait demandé cassent les
liens de ce qui a déjà été envoyé. Le déplacement existe, mais c'est une action
explicite du menu (« Aligner le dossier sur le nom »), qui déplace les photos *et*
réécrit leur préfixe.

**Les numéros de clichés ne sont jamais réutilisés.** Le compteur d'un audit est
réservé et persisté *avant* le déclenchement. Supprimer la photo 003 puis en prendre
une autre donne 004 : réutiliser le numéro donnerait deux fichiers homonymes dans le
même dossier, et MediaStore trancherait à notre place en suffixant « (1) ».

**Numérotation sur trois chiffres.** Pour que le tri alphabétique d'un explorateur de
fichiers soit l'ordre de la visite — c'est ce qui fait le compte-rendu.

**Une seule permission : l'appareil photo.** Depuis Android 10, une app écrit dans les
collections partagées et relit ensuite ce qu'elle y a écrit, sans rien demander.
Réclamer `READ_MEDIA_IMAGES` donnerait accès à toute la galerie, et sur Android 14
l'utilisateur pourrait répondre « photos sélectionnées » — ce qui nous couperait
justement de nos propres dossiers.

**Le catalogue est un JSON, pas une base.** `audits.json` dans `filesDir` porte ce que
MediaStore ne sait pas dire : à quel audit appartient un cliché, et ce qu'il montre.
Quelques dizaines de fiches ; Room n'apporterait qu'un processeur d'annotations et une
migration à écrire au premier champ ajouté. Écriture par fichier temporaire renommé,
et un catalogue illisible est mis de côté plutôt qu'écrasé.

## Limites connues

- **Après une désinstallation**, l'app perd la propriété de ses entrées MediaStore et
  ne sait plus les relire : elle repart d'un catalogue vide. Les photos, elles, sont
  toujours dans `Pictures/Audits` — seules les légendes et les fiches sont perdues.
- **Aligner un dossier laisse l'ancien, vide, derrière lui.** Supprimer un répertoire
  dans une collection partagée demanderait une permission bien plus large que ce que
  justifie un dossier vide.
- **L'APK pèse 23 Mo en release** (31 en debug) parce que rien n'est minifié
  (`isMinifyEnabled = false`, comme sur les autres projets maison). Le poids est dans le
  code : 21,5 Mo de dex contre 0,18 Mo de bibliothèques natives — filtrer les ABI, piste
  évidente et vérifiée au dézippage, ne rapporterait rien.

## Construire

```bash
./gradlew assembleRelease
```

L'APK signé sort dans `app/build/outputs/apk/release/`. La signature utilise
`cle-maison.jks`, le magasin partagé par les projets maison : sans lui, la build passe
mais l'app ne peut plus se mettre à jour par-dessus une version précédente.

`gradle.properties` épingle `org.gradle.java.home` sur le JDK 21 du système : Android
Studio 2026 embarque un JDK 25 que Gradle 8.11 ne sait pas lire, et la synchro échoue
alors sur un message vide.

## Vérifications

```bash
./gradlew test
```

24 tests JVM sur ce qui ne se contrôle pas à l'œil : la transcription des accents et
des caractères interdits dans les noms de dossiers, l'unicité des numéros de clichés
à travers un redémarrage, la relecture du catalogue, le récapitulatif.

Le reste a été vérifié sur un émulateur Android 37 : création d'un audit, octroi de la
permission, prise de vue en rafale, légende, renommage du fichier par la légende,
déplacement d'un dossier complet et suppression — fichiers et entrées MediaStore
contrôlés à chaque étape.
