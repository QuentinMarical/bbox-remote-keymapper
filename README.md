# Bbox Remote KeyMapper

Application Android TV qui associe une touche de la télécommande (code scan Linux) à une application à lancer.

## Codes scan de la télécommande (relevés avec `getevent`)

| Touche | Code scan | Nom noyau |
|---|---|---|
| VOD | 186 | KEY_F16 |
| Accueil | 102 | KEY_HOME |
| Retour (probable) | 1 | KEY_ESC |

Vérifiez ces valeurs dans l'écran de l'application : la dernière touche reçue y est affichée avec son `scanCode`.

## Cible d'une correspondance

- Un nom de paquet (ex. `com.netflix.ninja`) lance l'application.
- `action:<nom>` lance une action Android. Pour reproduire le bouton REPLAY d'origine (relevé sur le launcher SFR) : `action:com.sfr.androidtv.REPLAY`.

## Compilation

Le projet utilise Gradle (Android Gradle Plugin 8.5.2, `compileSdk` 34, Java 17).

1. Installez Android Studio (il fournit le SDK et Gradle).
2. Ouvrez le dossier `L:\ConnectTV\BboxRemoteKeyMapper` avec « Open ».
3. Menu **Build > Build APK(s)**.
4. L'APK se trouve dans `app\build\outputs\apk\debug\app-debug.apk`.

## Installation sur le décodeur

```bash
adb connect 192.168.68.117:5555
adb install -r app-debug.apk
```

## Utilisation

1. Ouvrez l'application sur le décodeur.
2. Cliquez sur **Ouvrir les réglages d'accessibilité**, puis activez le service **Bbox Remote KeyMapper**. Android interdit d'activer ce service automatiquement : cette étape reste manuelle.
3. La correspondance VOD (`186` → REPLAY) est déjà incluse dans l'application. Pour en ajouter une autre, saisissez le code scan et la cible, puis **Enregistrer la correspondance**.
4. Appuyez sur VOD : REPLAY s'ouvre.

Les correspondances livrées sont définies dans `KeyMapStore.java` (table `DEFAULTS`). Une correspondance enregistrée depuis l'écran remplace la correspondance livrée pour le même code.

Pour trouver le nom du paquet d'une application :

```bash
adb shell pm list packages | grep -i vod
```

## Limites

- Le service d'accessibilité reçoit toutes les touches : une touche mappée est consommée et ne va plus à l'application au premier plan.
- Seul le lancement d'une application est proposé ; l'envoi d'une autre touche à Android demande des droits système.
- Ne mappez pas `KEY_HOME` (102) : vous perdriez le bouton Accueil.
