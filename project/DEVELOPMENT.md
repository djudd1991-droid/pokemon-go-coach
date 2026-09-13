# GO Coach development guide

This `project/` directory is the canonical Android source. Java files live in
`app/src/main/java/com/david/gocoach/`.

## Feature ownership

| Area | Files |
| --- | --- |
| Entry, permissions, home and style | MainActivity, HomeScreen, CoachUi, res/ |
| Capture, OCR routing, floating controls | CoachService |
| Wild Map scanning and stop shape | WildMapScanner, WildMapDetector, WildMapForeground |
| Startup configuration and direct lookups | WildMapInitialization, WildMapStartupCache |
| Wild Map positions, labels and drawing | WildMapTracker, WildMapMatch, WildMapMarkerLayer |
| Wild Map appearance and timing | WildMapAppearance, WildMapTiming |
| Encounter confirmation and map association | EncounterEvidence, CoachLearning, LearningImages |
| Durable learning and deletion | LearningBank, LearningStore, LearningMemoryPage |
| Collection and reading | CollectionScreen, RecordStore, Reading |
| Detail-image references and streaming assets | VisualMatcher, AssetStreams |
| Advice and bundled PvP rankings | CoachAdvice, PvpGrade |

Keep map algorithms and presentation in `WildMap*.java`. Shared learning classes
contain narrow map-specific calls because they own transitions and storage.
Do not add a second map scanner to VisualMatcher.

## Invariants

- `WildMapInitialization` loads `wild-map/wild-map.json` once per process in
  Application.onCreate, even when Android starts the service directly. It publishes
  a private static HashMap; scanners use direct cached lookups without asset access
  or JSON parsing. Missing/corrupt files produce defaults once, with no live retries.

- Learn names only from exact, unambiguous dictionary matches confirmed twice.
- CP is optional. A predicted label is never training evidence.
- Automatic map association requires exactly one preceding candidate; crowded
  maps keep Unknown labels until an explicit correction is supplied.
- Exclude player/buddy portraits and the floating coach.
- Preserve binary memory format, collection records and signing identity.
- Keep credentials out of the app; read only the screen the player shares.

## Checks and build

From the repository root, with Java 17 and org.json 20240303 available:

```text
python tools/check.py --json-jar /path/to/json.jar
python tools/package.py
```

Use `--java-home /path/to/jdk` when JAVA_HOME is unavailable. Android builds need
SDK 35 and Gradle 8.11.1. CI overlays optional packs and runs
`gradle --no-daemon assembleDebug lintDebug` inside `project/`.

Java sources use google-java-format 1.24.0. StartupCacheCheck uses separate JVMs
for valid/malformed/missing assets and checks one open under concurrent startup,
safe publication, repeated key lookups, stream closure and no reload. Research programs compile selected
production classes with tiny Android storage stubs; they do not exercise the
capture permission UI, ML Kit, OpenCV or a real phone's rendering.

## Packaging

Each tracked asset and source ZIP must be at most 25,000,000 bytes.
`tools/package.py` checks this and emits deterministic ZIP entries. Large atlas
data is split into ordered 16 MB pieces and streamed at runtime. Stored learning
pixels are re-described on load without migrating or deleting data.
