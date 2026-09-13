# Wild Map startup cache

`WildMapInitialization` is registered as the Android Application in the manifest.
Android calls it before any Activity or Service. It is the only production caller
that opens `wild-map/wild-map.json`.

`WildMapStartupCache` parses the flat tuning file once into a private static
HashMap. A synchronized initialization guard and volatile publication prevent
duplicate reads and partial data across threads. Lookup methods do not initialize,
open files, parse text or expose mutable configuration. Missing/malformed assets
publish the existing default of 16 results and never retry during gameplay.

`WildMapScanner` has no Context, asset or JSON dependency. Each frame gets
`max_results` through a direct HashMap lookup and passes it to the detector.
The detector's geometry and learned appearance matching are unchanged.

"Once" means once per Android process, including after the OS kills and restarts
the app. A process cannot retain a Java HashMap after it has ended.

The JSON currently contains tuning metadata and max_results, not Pokémon images
or species identities. Configuration access has expected O(1) cost. Pixel analysis
and nearest-appearance comparisons still have their normal processing costs.
The Weedle screenshots show intermittent encounter name confirmation; this change
does not claim to fix OCR or make whole-frame recognition constant time.

## Compile on Windows

Open `repository/project` in Android Studio with JDK 17 and Android SDK 35 installed,
then build the debug APK. Alternatively, with Gradle 8.11.1 on PATH, from the
repository directory run:

```powershell
Expand-Archive -LiteralPath go-coach-portraits.zip -DestinationPath project/app/src/main/assets/portraits -Force
Expand-Archive -LiteralPath go-coach-wild-map.zip -DestinationPath project/app/src/main/assets -Force
gradle -p project --no-daemon assembleDebug lintDebug
```

Set ANDROID_HOME to the installed SDK, or put sdk.dir in project/local.properties.
Output: `project/app/build/outputs/apk/debug/app-debug.apk`.
The same instructions work with the source ZIP extracted into a project directory.
Keep its test-signing.jks to update the existing test installation.
