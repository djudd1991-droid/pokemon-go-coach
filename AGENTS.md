# GO Coach architecture requirements

- Wild Map configuration must load exactly once per app process, during
  `WildMapInitialization.onCreate()`, before activities or scanning services start.
- Only the startup initializer may open `wild-map/wild-map.json`. No scanner,
  detector, frame callback or lazy getter may read, parse or reload that file.
- Keep its data in the private global static HashMap in `WildMapStartupCache`.
  Publish the complete cache once and do not mutate or expose it after startup.
- Obtain configuration values with direct key lookups. Do not scan JSON or iterate
  configuration entries during identification. This does not make pixel processing
  or visual similarity matching constant time.
- Missing/malformed configuration publishes defaults once; never retry during play.
- Keep Wild Map implementation in WildMap files. Limit changes elsewhere to needed
  application registration, service wiring, build settings and focused checks.
- Run the startup-cache scenarios and existing scanner/learning regression checks
  after relevant changes. Preserve phone learning data and app signing identity.
