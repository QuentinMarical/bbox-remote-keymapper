<p align="center">
  <img src="assets/icon.svg" width="128" alt="Icône Bbox Remote KeyMapper" />
</p>

<h1 align="center">Bbox Remote KeyMapper</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Android%20TV-12-0055A4?logo=android&logoColor=white" alt="Android TV 12" />
  <img src="https://img.shields.io/badge/minSdk-24-E4572E" alt="minSdk 24" />
</p>

<p align="center">
  Relie les touches de la télécommande <strong>Bbox Miami</strong> sur <strong>SFR Connect TV</strong> à des actions du décodeur ou à des applications.
</p>

L'application se connecte elle-même au démon ADB du décodeur (`127.0.0.1:5555`) et lit la télécommande : aucun PC n'est nécessaire une fois l'application installée.

<p align="center">
  <img src="assets/capture-app.png" width="640" alt="Écran de l'application sur le décodeur" />
</p>

**Compatibilité** : testé uniquement sur une SFR Connect TV (Android TV 12) avec la télécommande Bbox Miami. Les autres boîtiers Android TV n'ont pas été testés.

---

## 📑 Sommaire

- [Touches reliées](#-touches-reliées)
- [Installation sur le décodeur](#-installation-sur-le-décodeur)
- [Activer le mode développeur et ADB](#-activer-le-mode-développeur-et-adb)
- [Utilisation](#-utilisation)
- [Compilation](#-compilation)
- [Fonctionnement et limites](#-fonctionnement-et-limites)

---

## 🎛️ Touches reliées

Codes issus de la table `AdbWatchService.BUTTONS` et des captures `getevent` faites sur la télécommande `Telecommande`.

| Touche | Code scan (hex) | Code scan (déc.) | Nom noyau | Action par défaut |
|---|---|---|---|---|
| 🎬 **VOD** | `00ba` | 186 | KEY_F16 | `KEYCODE_SDMCEXTEND6` (REPLAY) |
| ↩️ **Retour** | `0001` | 1 | KEY_ESC | `KEYCODE_BACK` |
| 🏠 **Accueil** | `0066` | 102 | KEY_HOME | `KEYCODE_HOME` |
| 📺 **TV** | `00b7` | 183 | KEY_F13 | `KEYCODE_SDMCEXTEND2` (bouton TV d'origine du SFR) |
| ⋮ **Menu** | `0082` | 130 | KEY_PROPS | application (YouTube par défaut) |
| ❤️ **Favoris** | `00bb` | 187 | KEY_F17 | application (Netflix par défaut) |

Les touches **TV**, **Menu** et **Favoris** peuvent être changées depuis l'écran de l'application, avec le bouton **Changer**. Pour la touche TV, le premier choix du sélecteur rétablit l'action d'origine.

Les autres touches sont gérées nativement par Android TV.

### Télécommande d'origine (pour référence)

Le bouton REPLAY de la télécommande d'origine envoie le scancode `0x195` (405) sur le périphérique `RemoteB038`. Le système le traduit en `KEYCODE_SDMCEXTEND6`, qui lance REPLAY via le service `GlobalKey`.

---

## 📦 Installation sur le décodeur

Il faut d'abord que le débogage ADB soit actif sur le décodeur : l'application s'y connecte elle-même (`127.0.0.1:5555`). Voir [Activer le mode développeur et ADB](#-activer-le-mode-développeur-et-adb).

Ensuite, deux méthodes pour installer `app-debug.apk` :

**Méthode 1 : depuis le décodeur**

1. Copiez `app-debug.apk` sur le décodeur, par exemple depuis une clé USB, et installez-le avec un gestionnaire de fichiers.
2. Ouvrez l'application une fois.

**Méthode 2 : depuis un PC avec ADB**

Vous avez besoin d'ADB sur l'ordinateur. Deux possibilités :

- **Android Studio** : téléchargez-le sur [developer.android.com/studio](https://developer.android.com/studio). ADB est fourni avec le SDK (dossier `platform-tools`).
- **Minimal ADB and Fastboot** (Windows uniquement) : téléchargez-le depuis le [fil XDA-Developers](https://forum.xda-developers.com/showthread.php?t=2317790). Vérifiez le fichier avant de l'exécuter, car il existe des copies non officielles.
- Pour toute plate-forme, le plus sûr reste les [platform-tools officiels de Google](https://developer.android.com/tools/releases/platform-tools).

Puis :

```bash
adb connect 192.168.1.100:5555
adb install -r app-debug.apk
```

Remplacez `192.168.1.100` par l'adresse IP de votre décodeur.

**Première ouverture** : l'application démarre le pont ADB, qui affiche une demande d'autorisation sur l'écran du décodeur. Cochez « toujours autoriser ». Cette demande apparaît généralement au premier lancement : si vous ne regardez pas la télé à ce moment-là, l'application peut sembler ne pas réagir. Ouvrez-la de nouveau et regardez l'écran.

---

## 🔧 Activer le mode développeur et ADB

Ces étapes supposent que les options pour développeurs sont déjà accessibles sur le décodeur. Les libellés des menus peuvent varier selon la version du système.

1. Ouvrez les **Paramètres** du décodeur, puis **Préférences de l'appareil** (ou **À propos**).
2. Sélectionnez **Informations sur le système**, puis **Numéro de build** (ou **Version**), et appuyez plusieurs fois dessus jusqu'à l'apparition d'un message indiquant que les options pour développeurs sont activées.
3. Revenez aux paramètres, ouvrez **Options pour les développeurs**.
4. Activez **Débogage USB** et, si le menu le propose, **Débogage réseau** (ou **Débogage ADB sur le réseau**).
5. Notez l'adresse IP du décodeur, affichée dans **Réseau** ou **Informations réseau**.

Documentation officielle Android : [Options pour les développeurs](https://developer.android.com/studio/debug/dev-options).

---

## ▶️ Utilisation

1. Ouvrez l'application sur le décodeur : le pont ADB démarre tout seul.
2. Si le pont est arrêté, le bouton **Relancer le pont** le redémarre.
3. Pour changer une touche, cliquez sur **Changer** et choisissez l'application dans la liste.
4. Au démarrage du décodeur, le pont se relance seul.

---

## 🛠️ Compilation

Le projet ne contient pas le wrapper Gradle. Il faut :
- JDK 17 ou plus ;
- Gradle 8.7 ;
- Android SDK : plate-forme `android-34` et build-tools `34.0.0`.

Avec Android Studio, ouvrez le dossier du projet puis **Build > Build APK(s)**. En ligne de commande :

```bash
gradle assembleDebug
```

L'APK est généré dans `app/build/outputs/apk/debug/app-debug.apk`.

---

## ⚠️ Fonctionnement et limites

- Le pont s'exécute au premier plan avec une notification, pour qu'Android ne l'arrête pas quand l'application est inactive.
- Pendant la veille, le réseau du décodeur est coupé : le pont se reconnecte au réveil, en réessayant toutes les 3 à 10 secondes.
- Le numéro `/dev/input/eventN` de la télécommande change parfois au redémarrage : le pont retrouve la télécommande par son nom (`Telecommande`).
- Les choix faits dans le sélecteur sont enregistrés sur le décodeur et restent après un redémarrage.
- Pour ajouter une touche, complétez la table `BUTTONS` dans `app/src/main/java/fr/bbox/keymapper/AdbWatchService.java`.
