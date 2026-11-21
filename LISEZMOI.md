# Salat Lock 🕌

Application Android qui bloque le téléphone à l'heure de la prière.
Pour débloquer : scanner un lavabo (ablutions), puis prier pendant le compte à rebours.

## Recompiler (sans Android Studio)
Il faut Java 17+ et le SDK Android en ligne de commande (`sdk.dir` dans `local.properties`).

    ./gradlew assembleDebug

L'APK est dans `app/build/outputs/apk/debug/app-debug.apk`.
La clé `app/salatlock.keystore` permet d'installer les mises à jour par-dessus l'ancienne version.

## Structure
- `LockService.kt` : l'écran de blocage par-dessus toutes les applis + compte à rebours
- `ScanActivity.kt` / `SinkDetector.kt` : caméra + reconnaissance du lavabo (MobileNet, hors ligne)
- `PrayerCalc.kt` : heures de prière (bibliothèque Adhan) et Qibla
- `Scheduler.kt` : alarmes (rappel à l'adhan, blocage après le délai)
- `MainActivity.kt` / `SettingsActivity.kt` : accueil et réglages
