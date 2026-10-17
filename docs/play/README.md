# Éléments graphiques de la fiche Google Play

| Fichier | Format | Usage dans la console |
|---|---|---|
| `icon-512.png` | 512 × 512, PNG 32 bits avec alpha, 3 Ko | Icône de l'application (carré, sans coins arrondis : Play applique le masque). Même dessin que `app/src/main/res/drawable/ic_launcher.xml`. |
| `feature-graphic-1024x500-fr.png` | 1024 × 500, PNG 24 bits sans alpha | Visuel de présentation, fiche en français. |
| `feature-graphic-1024x500-en.png` | idem | Visuel de présentation, fiche en anglais. |
| `captions.html` | page autonome, aucun réseau | Pose la légende sur les 4 captures brutes que l'on prend soi-même. |

Sources : `src/icon.svg`, `src/feature-graphic.html` (police Inter, SIL OFL 1.1, non incluse : le rendu a été fait avec Inter chargée localement).

## Captures d'écran

Les captures ne sont **pas** dans ce dossier : elles demandent de lancer l'application (émulateur ou téléphone).

1. Installer une version qui contient le changement de nom « EcoDrive OBD » (l'en-tête de l'écran Direct l'affiche).
2. Prendre 4 captures en **mode simulateur**, sans légende, en **1080 × 2160** exactement (rapport 2,0) ou 1080 × 1920, dans l'ordre : direct, trajet, carte, véhicule. Sur l'émulateur : AVD « Pixel » avec une résolution personnalisée de 1080 × 2160 ; sur un téléphone en 20:9, la capture sort en 1080 × 2400 (rapport 2,22) et sera refusée.
3. Ouvrir `captions.html` dans un navigateur, choisir les 4 fichiers, vérifier les légendes, télécharger les 4 JPEG (sans transparence). L'outil refuse toute taille autre que 1080 × 2160 ou 1080 × 1920 : il n'étire ni ne recadre.
