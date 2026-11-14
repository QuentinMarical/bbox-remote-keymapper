# Bbox Remote KeyMapper

Application Android TV pour SFR Connect TV qui relie les touches de la télécommande Bbox Miami à des actions du décodeur (ou à une application).

L'application se connecte elle-même au démon ADB du décodeur (`127.0.0.1:5555`) et lit la télécommande : aucun PC n'est nécessaire une fois l'application installée.

## Touches reliées

Codes issus de la table `AdbWatchService.BUTTONS` et des captures `getevent` faites sur la télécommande `Telecommande`.

| Touche | Code scan (hex) | Code scan (décimal) | Nom noyau | Action par défaut |
|---|---|---|---|---|
| VOD | `00ba` | 186 | KEY_F16 | `KEYCODE_SDMCEXTEND6` (REPLAY) |
| Retour | `0001` | 1 | KEY_ESC | `KEYCODE_BACK` |
| Accueil | `0066` | 102 | KEY_HOME | `KEYCODE_HOME` |
| TV | `00b7` | 183 | KEY_F13 | `KEYCODE_SDMCEXTEND2` (bouton TV d'origine du SFR) |
| Menu | `0082` | 130 | KEY_PROPS | application (YouTube par défaut) |
| Favoris | `00bb` | 187 | KEY_F17 | application (Netflix par défaut) |

Les touches TV, Menu et Favoris peuvent être changées depuis l'écran de l'application, avec le bouton **Changer**. Pour TV, le premier choix du sélecteur rétablit l'action d'origine.

Les autres touches de la télécommande ne sont pas encore documentées ici.

### Télécommande d'origine (pour référence)

Le bouton REPLAY de la télécommande d'origine envoie le scancode `0x195` (405) sur le périphérique `RemoteB038`. Le système le traduit en `KEYCODE_SDMCEXTEND6`, qui lance REPLAY via le service `GlobalKey`.

## Compilation

Le projet ne contient pas le wrapper Gradle. Il faut :
- JDK 17 ou plus ;
- Gradle 8.7 ;
- Android SDK : plate-forme `android-34` et build-tools `34.0.0`.

Avec Android Studio, ouvrez le dossier du projet puis **Build > Build APK(s)**. En ligne de commande :

```bash
gradle assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`.

## Installation sur le décodeur

Activez le débogage ADB sur le décodeur (réseau ou USB), puis :

```bash
adb connect 192.168.1.100:5555
adb install -r app-debug.apk
```

Remplacez `192.168.1.100` par l'adresse IP de votre décodeur. Acceptez la demande d'autorisation qui s'affiche sur l'écran, avec « toujours autoriser ».

## Utilisation

1. Ouvrez l'application sur le décodeur : le pont ADB démarre tout seul.
2. Si le pont est arrêté, le bouton **Relancer le pont** le redémarre.
3. Pour changer une touche, cliquez sur **Changer** et choisissez l'application dans la liste.
4. Au démarrage du décodeur, le pont se relance seul.

## Fonctionnement et limites

- Le pont s'exécute au premier plan avec une notification, pour qu'Android ne l'arrête pas quand l'application est inactive.
- Pendant la veille, le réseau du décodeur est coupé : le pont se reconnecte au réveil, en réessayant toutes les 3 à 10 secondes.
- Le numéro `/dev/input/eventN` de la télécommande change parfois au redémarrage : le pont retrouve la télécommande par son nom (`Telecommande`).
- Les choix faits dans le sélecteur sont enregistrés sur le décodeur et restent après un redémarrage.
- Pour ajouter une touche, complétez la table `BUTTONS` dans `app/src/main/java/fr/bbox/keymapper/AdbWatchService.java`.
