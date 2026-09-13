# GO Coach 0.9.21

Wild Map tuning now loads once per process at application startup into a static
HashMap. Scanning uses cached key lookups without reopening or parsing JSON.
See [startup-cache.md](../docs/startup-cache.md) for the design and Windows build steps.

Use the readable source in this directory. Run checks and packaging from the
repository root; see [DEVELOPMENT.md](DEVELOPMENT.md). The home screen and floating
coach were refreshed, night scanning was simplified, and encounter learning now
confirms the name with CP optional. Install the new test APK over the existing app.

Current release notes and phone tests: [docs/0.9.20.md](../docs/0.9.20.md).

## Historical release notes

The instructions below describe older releases, not the current build workflow.

# GO Coach 0.9.19 — lasting learning memory and coaching

Replace only **go-coach-source.zip** at your GitHub repository root, build with the existing workflow and install the APK. Keep your portraits ZIP, wild-map ZIP and workflow. The app title shows 0.9.19. Install over the existing app to preserve its data.

## How to use it

1. Start the reader and open a wild Pokémon. Keep its name and CP visible for two readings. Shrink or move the transparent coach away from the name and Pokémon body.
2. The coach saves the confirmed name and CP to phone memory. When the body is unobstructed it can also save an encounter picture. Repeated frames do not count as new visits.
3. To teach its map appearance, scan the map before opening it, then use **Teach map** within 90 seconds of that scan. Pick the thumbnail of the Pokémon you opened. Cancel if none shows it clearly. No name typing is needed. This explicit selection avoids assigning a nearby Pokémon the wrong name.
4. Future sufficiently similar map pictures can show **Pidgey?**, for example. The question mark means a tentative picture match. Opening the Pokémon confirms its name; predicted labels never train themselves.
5. **Learning memory** shows remembered species, encounter visits and saved pictures. Remove incorrect map examples there before teaching again. **Coach goal** selects collection, Candy or PvP advice. The encounter coach also reports matching saved collection records when available. Open details and Appraise for the existing individual-stat collection flow.

## What changed

Focused encounter OCR reads the name/CP strip separately. CP parsing no longer absorbs digits from the next line. Confirmed names, encounters and separate map/encounter picture examples are stored atomically in a versioned phone file. Existing detail-image learning and collection records remain separate and retained. Picture matching is conservative and requires a strong match and separation from competing species. Conflicting identical map examples are rejected. Memory supports up to eight pictures per species per view type, with 512 pictures total; it does not silently discard older species to make room.

Names and pictures persist across restarts and ordinary updates with the same app identity/signature. Uninstalling or clearing app data removes local memory; no cloud backup or export is added here. Visits are confirmed encounter sessions, not verified catches or unique owned Pokémon.

## Validation and limits

Pure-Java regression checks passed for exact-name confirmation, ambiguous names, two-reading confirmation, repeated-frame deduplication, separate appearance domains, conflict rejection, save/reopen persistence, forgetting map examples and preserving corrupt memory files. Offline Tesseract read Pidgey / CP137 from the supplied screenshot's header; the parser accepted that actual OCR output. All application Java files passed syntax parsing. These checks do not establish Android ML Kit accuracy or APK build success. Android SDK/Gradle dependencies are unavailable locally; GitHub build and phone testing remain necessary.

This is example-based visual memory plus basic contextual coaching, not a general model that learns game strategy on its own. New poses, zoom, weather, crowds and species can still be missed. Map teaching needs a correct thumbnail selection and enough clear examples. The existing detector can still miss Pokémon and PokéStops. Advice currently covers map exploration, catching, collection and appraisal/PvP context; it does not yet understand raids, inventory, quests or all gameplay. The prior single-app capture and transparent overlay changes remain, but device freeze resolution is not established by these offline checks.

Appraisal and resource guidance references:
- https://niantic.helpshift.com/hc/en/6-pokemon-go/faq/129-appraising-your-pokemon/
- https://niantic.helpshift.com/hc/en/6-pokemon-go/faq/2372-powering-up-pokemon/

Previous release notes follow.

# GO Coach 0.9.18 — conservative map scanner

Replace only go-coach-source.zip at the repository root, run the existing build workflow and install the APK. Keep the current wild-map ZIP, portraits ZIP and workflow. The app home and expanded coach title show 0.9.18. The 0.9.17 app-sharing choice and background capture changes are retained.

Map changes:
- Compact colored foreground candidates must have supporting local spawn-ring arcs. Background brightness is compared with a local median to reduce matches on flat road shading.
- Added filtering for narrow road fragments, the player anchor and likely humanoid silhouettes. Dark ringed stops with a separated red Rocket marker are grouped as stops, rather than separate Pokemon/R markers.
- A candidate needs confirmation in a second consecutive frame. Positions always come from the current frame. Disappeared objects, camera jumps, capture gaps and pause resets do not retain old positions.
- Marker drawings expire after 2.2 seconds without a fresh update. Small labeled arrows replace the large yellow circles. Coordinates retain fractional percentages to reduce rounding offset.
- Map objects still use Unknown for Pokemon candidates. This is a heuristic, not a species-identification model, and does not teach itself a map species from the encounter screen.

Validation:
- Compiled and ran the actual Java detector against six selected frames from the user's 47-second video: five map views at different zooms/angles and the Noibat encounter. The encounter was routed away from map detection. Candidate outputs were visually checked against the original map frames, with no road candidates in those selected samples. Some visible Pokemon and stops were missed, and one close view yielded no candidates.
- Compiled and ran the road-only negative fixture and tracker regression tests for confirmation, current positions, disappearance, camera jumps, capture gaps and pause resets. Passed Java syntax parsing for all application sources.
- No Android APK was built locally because Android SDK and Gradle dependencies are unavailable. GitHub Actions compilation and live phone capture/overlay verification remain necessary. Offline timing is not a phone performance guarantee.

Limits: partial/crowded/very small objects, cyan effects, lighting, weather and untested views can still cause misses or false positives. The central trainer exclusion can also suppress a Pokemon overlapping the player. PokéStop labels are tentative shape/color classifications and do not establish spin availability. The map detector has not been trained as a general-purpose Pokemon model.

Previous release notes follow.

# GO Coach 0.9.17 — restore app sharing and reduce capture stalls

Replace only go-coach-source.zip in the GitHub repository, build with the existing workflow, and install the new APK. Keep the current wild-map ZIP, portraits ZIP and workflow. Confirm version 0.9.17 on the home screen.

Start the reader, choose Share one app in Android's sharing prompt, then select Pokémon GO. The v0.9.16 request forcing the default display has been removed. Full-screen sharing remains an optional Android choice.

Capture acquisition, bitmap conversion, scaling and resize operations now run on a dedicated background HandlerThread. Frame ownership is released on failures, and stop/resize work is serialized with image acquisition. When Android reports the selected app hidden, scanning pauses and stale markers clear. Loading screens without CP, HP or appraisal evidence skip the expensive Pokémon-detail analysis. Normalized species names are cached so OCR lookups do not repeatedly compile a regex for each dictionary entry.

Validation: compiled and ran the loading-screen gate tests using the actual Reading and ScreenKind classes. Loading, coach and blank frames were rejected while encounter CP, detail HP and appraisal text remained accepted. All 10 application Java source files passed syntax parsing. An Android SDK/Gradle build and device ANR verification were not available in this environment. The exact cause of the reported ANR is unconfirmed without a device trace; this update removes observed UI-thread workload risks.

This update preserves the existing map detector. The road/empty-ground false positives shown in the latest screenshots are not fixed in this release. Single-app coordinate alignment and capture behavior still need phone verification.

Android references:
- https://developer.android.com/media/grow/media-projection
- https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs

Previous release notes follow; 0.9.17 supersedes the full-screen-only instructions below.

# GO Coach 0.9.16 — transparent coach and map detection

Replace go-coach-source.zip and go-coach-wild-map.zip at your GitHub repository root, run the existing build workflow, then install its new APK. Keep go-coach-portraits.zip and your existing build.yml. The app home and expanded floating title now show 0.9.16, so you can confirm the installed build.

The floating panel has a transparent background, text shadows and translucent controls. It starts collapsed. Tap GO to expand; drag the title to move it.

Map detection runs before OCR on a separate worker. It examines current-frame connected color regions instead of fixed scan spots. The capture throttle is one second; actual device speed can vary. Candidate markers say Unknown; detected cyan PokéStop disks get their own label. A timestamp and counts show each completed map scan. The trainer/buddy HUD, nearby tray, coach panel, and central trainer are excluded. Marker text is masked from analysis to reduce self-detection. Full-screen sharing keeps captured coordinates aligned with markers, and the marker window allows touches through.

Validation: the actual Java detector compiled and ran on the supplied screenshot, detecting both visible PokéStops and multiple candidates while excluding the bottom-left trainer/buddy icons. Solid-green and black frames were rejected. All application Java sources passed syntax parsing. An Android APK was not built in this environment because the Android SDK and Gradle dependencies are unavailable. GitHub Actions still needs to compile the app; live capture and overlay behavior need phone verification.

Limits: this is a color/shape heuristic, not species identification. Some spawns are missed and some map features can still produce Unknown candidates. Cyan PokéStop detection does not establish spin availability. Night/weather themes and other map views need more testing. Opening a Pokémon reads its encounter name; existing detail-image learning is retained, but it does not automatically train this map detector from that encounter.

Android implementation references:
- https://developer.android.com/reference/android/media/projection/MediaProjectionConfig
- https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events

# GO Coach v0.9.7 - separate wild map file

This update makes wild-map tuning its own small upload file.

Files:
- go-coach-source.zip: app code and main recognition atlas.
- go-coach-portraits.zip: collection pictures.
- go-coach-wild-map.zip: wild-map scan spots and matching thresholds.
- build.yml: GitHub workflow that combines the optional files while building the APK.

Why this helps:
- We can tune map recognition later by changing go-coach-wild-map.zip without touching the big recognition atlas.
- Wild-map scanning now uses wild-map.json when it is present.
- The first wild-map coaching behavior from v0.9.6 is included.


# GO Coach v0.9.6 - first wild map coaching

This update starts wild-map recognition.

Changes:
- On the Pokemon GO map, GO Coach now checks likely wild-spawn areas.
- If it recognizes spawns, it shows a short message like: Wild nearby: Dragonite, Ducklett.
- The coach tells you to tap one and open details so it can save and learn the Pokemon.
- Detail-screen recognition, image learning, collection cleanup, duplicate cleanup, and saved-scan delete remain included.

This is an early wild-recognition pass. It will miss small, covered, distant, or angled spawns, but it gets the app moving toward live game coaching instead of only detail-screen reading.


# GO Coach v0.9.5 - faster recognition start

This update reduces the wait before Pokemon image recognition is ready.

Changes:
- Image recognition now warms up in the background as soon as the floating coach starts.
- The overlay says when recognition is loading instead of appearing stuck.
- If a Pokemon screen appears before warm-up finishes, text reading still continues and image matching catches up once ready.

This keeps the v0.9.4 collection cleanup, saved-scan delete, duplicate merge, and local image learning changes.


# GO Coach v0.9.4 - collection and scan cleanup

This update makes the collection easier to manage.

Changes:
- Long-press a Pokemon row in My Pokemon to delete that saved collection row.
- Deleting a collection row does not delete learned image memory.
- The collection menu has Clean duplicate records.
- The collection automatically runs duplicate cleanup when opened.
- Duplicate partial records can merge into the better record when species + CP + HP match, or when species + HP + weight + height match.
- New scans can update an existing partial record instead of creating another copy.

Saved scans can now be deleted from Review saved scans. Tap a saved scan to review it, then tap Delete, or long-press the scan to delete it directly.

Image learning remains separate from collection records and saved scans, so cleaning the list will not make recognition forget the pictures it learned.


# GO Coach v0.9.3 - learning fix for missed CP

This update fixes the Dragonite-style problem where Pokemon GO draws the CP behind the Pokemon model and OCR misses it.

Changes:
- CP reading now accepts messier OCR like cP3305, CP 3 305, and spaced CP text.
- GO Coach can use the visible Pokemon name even when CP was missed.
- Image learning no longer requires CP. If the screen has a stable species plus HP/weight/height/appraisal evidence, it can save picture memory.
- The visual matcher loads on detail/appraisal screens even when CP is missing, so learning is available sooner.

The collection still needs CP, HP, weight and height before it can safely save the individual Pokemon record. Image learning is separate from collection saving now.


# GO Coach v0.9.2 - local image learning

This update keeps the v0.9.1 split upload format so each file stays under GitHub mobile's 25 MB limit.

What changed:
- GO Coach now saves a small local picture memory after it has a stable Pokemon detail reading.
- It learns only when the species came from the visible name/appraisal text, so a weak visual guess does not train itself on the wrong Pokemon.
- Learned images load as high-priority recognition references the next time recognition starts.
- The overlay tells you: "I saved its picture memory" when it learns one.
- It keeps up to 8 learned pictures per species and 250 learned pictures total on the phone.

Use the same upload files:
1. go-coach-source.zip
2. go-coach-portraits.zip
3. .github/workflows/build.yml from build.yml

This still will not magically know every wild Pokemon from tiny map spawns yet. The learning system helps most when you open the Pokemon's details once, let GO Coach see the name/image, and then it can remember that visual for later.


# GO Coach v0.9.1 - GitHub phone upload fix

This package is split so every upload file stays under GitHub mobile's 25 MB limit.

Upload these files to the repository root:
1. go-coach-source.zip
2. go-coach-portraits.zip

Also update .github/workflows/build.yml with the new build.yml from this package. After that, future builds can use the same split-file upload flow.

The app behavior is the same recognition update as v0.9:
- broad Pokemon visual reference atlas for species matching,
- visible-name fallback on Pokemon detail screens,
- automatic collection saving when enough details are visible,
- remembered appraisals and moves after the same Pokemon is seen again,
- optional PvP details, while the main coach panel stays focused on simple recognition/save status.

Known limits remain:
- map recognition is still experimental,
- some costumes/forms and very small/covered Pokemon may still miss,
- Armored Mewtwo and Glimmet were hard cases in desktop tests and still need more reference work.


# GO Coach 0.9 — expanded Pokémon recognition

Replace go-coach-source.zip in the existing GitHub repository. Build with the
existing Actions workflow and install the APK over GO Coach (do not uninstall).
The home screen should show 0.9. First recognition startup builds an on-device
index and may take longer than later readings.

## What changed
- 870 base-species icons plus the previous seven references: 877 reference
  entries with 871 distinct species/form labels, including Armored Mewtwo.
  This is reference coverage, NOT a claim of validated recognition accuracy.
- Indexed SIFT shortlist followed by geometric checks; descriptors stored in a
  compact binary atlas. Transparent icon pixels are composited before feature
  extraction to avoid artificial silhouette gradients.
- Exact visible species-name fallback above the HP line on details screens.
  Renamed Pokémon still need visual recognition or the appraisal sentence.
- Automatic collection capture and remembered appraisal/moves retained.
  A unique matching live IV/species/weight/height reconnects a powered-up record
  even if CP/HP changed. Missing or ambiguous identifying data still needs review.
- Broad reference portraits in the collection. No shiny/form inference from art.
- Live panel focuses on Pokémon data and saving. PvP is optional, not pushed.
- Experimental single-encounter crop when CP but no HP is visible. Encounters
  are not automatically added as owned Pokémon. This path is not phone-tested.

## Validation and limitations
The final Python/OpenCV image-only test identified Cinderace, Metagross, Lapras,
and Mewtwo in the supplied game screenshots. Armored Mewtwo and Glimmet remained
unknown; map and bag crops were rejected. This is a small test, not broad accuracy.
Six resized/composited reference images (Bulbasaur, Charizard, Pikachu, Dragonite,
Tyranitar, Gardevoir) passed retrieval checks. These use reference-derived images
and demonstrate index operation, NOT generalization to game poses.
Species-name and power-up matching tests passed, as did automatic-saving tests.
VisualMatcher and UI compile against Android 35/OpenCV APIs. CoachService passed
Java syntax checks; full Android build remains the GitHub Actions gate. The local
compile lacked the ML Kit text-common dependency. No phone latency, thermal,
full-APK or capture-permission tests were performed here.

Wild-map multiple Pokémon, raid recognition/counters, reliable all-form/shiny/
background recognition, inventory and transfer tracking are not implemented.
Glimmet can use its visible name/appraisal; this atlas has no Glimmet icon.
Automatic matching is screen-derived, not a game ID. Evolving, missing appraisal
on a power-up, or indistinguishable duplicates can still need review.

## Sources and reproducibility
Source icons: https://github.com/PokeMiners/pogo_assets under Images/Pokemon.
research/recognition/manifest.json records all 870 source paths. Existing image
rights/educational-use notes below still apply. Game data uses the bundled
PvPoke snapshot and its included MIT license. No remote inference is performed.
The new atlas uses big-endian version/count, UTF-8 name length/name, priority,
feature count, x/y float32 points, then 128 unsigned descriptor bytes per point.
For rebuilds, download manifest source paths into research/recognition/icons,
then run its build.py (OpenCV + NumPy), followed by evaluate.py. Test inputs and
results are included. Original reference JSON is retained for the seven priority
entries. Signing key and application ID are preserved.

---

# Earlier release notes

# GO Coach 0.4 — remembered appraisals and visible moves

## Update and test
Replace go-coach-source.zip in the GitHub repository root. Keep the workflow unchanged. Install the new APK over the existing app; do not uninstall. The home screen must say 0.4. Existing signing key, application ID, saved scans, and collection remain in use.

1. Start reader and share Pokémon GO only.
2. Open Cinderace's ordinary details screen. Keep CP, current/max HP, weight, height, and moves visible; move the coach below its model. Wait for two stable readings.
3. Tap GO > Link saved Pokémon > choose the existing Cinderace record > Link and update. This one-time link is needed for older records lacking structured identifying fields.
4. Its stored appraisal should appear with “saved”. Show the GYMS & RAIDS / TRAINER BATTLES section and visible move names. Fast/charged slots should fill after two readings and update the linked record.
5. Open Appraise, then return to details: the linked record preserves IVs and moves. Restarting the reader should recall a unique matching record once the identifying fields are read again.
6. Switch Pokémon: mismatching/incomplete identifying fields clear the active link. Never confirm a link unless it is the same individual.

## Exact behavior
Persistent record matching uses species, CP, max HP, weight and height. It requires all five; it never joins by CP alone. This is a conservative screen-derived signature, NOT a game-provided unique Pokémon ID. Identical unscanned Pokémon remain a limitation. Two saved matches require manual selection; contradictory visible IVs block automatic updates. Power-ups/evolution or unreadable fields may require a fresh explicit link. Scrolling those fields offscreen disables linking until they are visible again.

Legacy appraisal text is migrated only when a user explicitly links a record. Existing IDs and protection/trait fields are preserved. A prior structured snapshot is retained on a data change. Appraisal and moves are merged without replacing missing readings with unknown. Previously saved IVs and moves are labeled. Current HP is read live, not copied from saved health.

Moves use a packaged catalog derived from PokeMiners Game Master moveSettings, fetched during this update. Source: https://github.com/PokeMiners/game_masters/blob/master/latest/latest.json . Named *_FAST entries are classified fast; other named move entries charged. Numeric/unnamed entries are excluded. Exact normalized OCR names are accepted only below the visible battle-section heading and above NEW ATTACK. OCR damage numbers are ignored. A missed or offscreen row is not evidence that a move was removed. An unseen second charged move is retained and labeled saved; it may be stale after a TM until rescanned. This is an English-layout prototype and does not infer hidden moves or advise TMs.

Live auto-updates apply only to the matched existing record; creating new Pokémon still requires scan review. If free-form stats are edited while creating a record, the unedited structured scan is not silently attached to the edited record. Old scan records remain readable.

## Validation
Java syntax parsing passed. The actual Reading and RecordStore classes were compiled against a small preferences test adapter and JSON library. Checks passed for legacy-IV migration, persistence after reopening, fast/charged name parsing and noise rejection, same-CP/different-weight identity separation, missing-field rejection, duplicate ambiguity, conflicting-IV non-overwrite, protection preservation, and retaining an offscreen second charged move.

Android compilation is delegated to the existing GitHub workflow. v0.4 has not yet been built or run on the phone in this session. Live OCR bounding boxes, matching consistency, and overlay usability need device testing. Visual recognition remains the limited v0.3 experiment; no expansion of wild-map, raid, shiny, background, or item coverage in this update.

Test adapter files: research/memory-check. They require org.json:json:20240303 and are not included in the APK source set. No user APK key changes.

---

Previous visual-prototype implementation and evidence:

# GO Coach 0.3 — experimental visual matching

## Phone update
Upload go-coach-source.zip to the existing repository root, replacing the prior ZIP. Keep .github/workflows/build.yml unchanged. After the newest build succeeds, download its APK and install over version 0.2. The GitHub artifact may still be named v0.1: the installed app must display GO Coach 0.3. Do not uninstall, because that deletes saved records. Existing test signing key and application ID are preserved.

## What changed
Added offline OpenCV SIFT reference matching for a single Pokémon on the details screen, with seven reference descriptors for Cinderace, normal/shiny Metagross, normal/Mystic-accessory Lapras, Mewtwo and Armored Mewtwo. The source data is from PokeMiners; see research/reference-manifest.json. Visual matching operates on the upper model area, excluding CP and name labels. OCR is used to determine whether a details screen is open, not to identify the species visually.

Tentative visual species is used only when the appraisal sentence has not already identified the species. A visual match does not verify shiny, costume, background, IVs or individual collection identity. Normal and alternate references are grouped by species, except Armored Mewtwo which is a distinct target. Existing appraisal parsing, scan review, protected manual collection records, capture consent, movable 56dp bubble, Pause/Stop and notification Stop are preserved. Frames stay in memory.

Visual processing uses a single worker thread and one OpenCV thread; capture processing is throttled to one cycle per three seconds and skips frames while busy. Native performance, memory use and compatibility still need device testing. The APK will be larger because OpenCV includes native libraries.

## Evidence and limitations
A Python/OpenCV baseline with independent icon references identified four supplied details screenshots: Cinderace, shiny Metagross, decorated Lapras and regular Mewtwo. Armored Mewtwo remained unknown at the fixed acceptance threshold. An unseen Glimmet screenshot, unrelated map crop and item-bag crop were rejected. This tiny selected set is not a general accuracy measurement.

A separate exploratory Glimmet test used foreground-extracted reference frames at seconds 1, 3 and 4, then checked frames 6, 8 and 10 from the SAME recording. None passed. These correlated frames would not establish generalization even if successful. Glimmet references are therefore excluded from the app.

App reference vectors were validated and predictions with species grouping checked. Java syntax parsing passed. Existing standalone species/health/appraisal tests passed. A full Android build has NOT been performed locally; GitHub must compile version 0.3, and live recognition must be tested. Version 0.2 was previously built and tested successfully by the user.

This is visual feature matching, not a trained neural detector. Wild-map multi-object identification, general raid recognition, Glimmet, reliable armor detection, broad species/form coverage and visual collectible verification are NOT ready. Unknown results are expected when poses/angles change or features are insufficient. No transfer recommendation is based on this experiment. No paid API is used.

## First device test
1. Confirm header says version 0.3.
2. Start screen sharing for Pokémon GO only.
3. Open Cinderace's ordinary details screen, with Appraise CLOSED.
4. Keep the GO bubble below the model; wait 6–10 seconds, then expand it.
5. Check for Cinderace and Species: tentative image match.
6. Test regular Mewtwo, Metagross and Lapras the same way.
7. Show an unrelated Pokémon: an unknown result is preferable to a wrong name.
8. Report lag, crashes, wrong matches or unknown results. Keep Appraise available as a text-based fallback.

## Reproduce desktop baseline
Inside research, run: python -m pip install opencv-python-headless numpy
Then: python evaluate.py
Test inputs are cropped screen regions with no species names or CP. Evaluation covers the supplied views only and does not benchmark Android latency. The output JSON includes match counts, RANSAC inliers and spatial coverage. Fixed gates: at least 10 inliers, 1.5% target area coverage, and 4 more inliers than runner-up. Android compares runners-up by species.

Sources:
https://github.com/PokeMiners/pogo_assets
https://docs.opencv.org/4.10.0/d5/df8/tutorial_dev_with_OCV_on_Android.html
https://docs.opencv.org/3.4.18/d1/de0/tutorial_py_feature_homography.html

Assets retain their owners' rights; the PokeMiners repository describes them as educational-use material. This is a private research prototype, not a production redistribution license. The bundled signing key is for development only.

## Version 0.5 — move reader and PvP grades

- Move parser tolerates up to two leading OCR icon letters and damage digits;
  missing section headings use a bounded details-screen fallback.
- Saved IVs/moves and signing key retained. Open the moves section for live
  move updates; linked records retain values when those fields leave the screen.
- PvP grade is available in the floating coach and each structured collection
  record. The live panel shows CP eligibility automatically.
- IV rank: all 4096 spreads, IV floor 0, levels 1–50 in half levels, highest
  legal CP level, attack × defense × floored HP, competition ranks for ties.
  No Best Buddy boost, evolution projections, or cup-specific eligibility.
- Species rankings and recommended moves: bundled PvPoke open-league overall
  snapshot downloaded 2026-09-08. These are not live rankings or a simulation
  of the scanned moves/team. Elite/event moves are marked; bag ownership is
  not checked. Species recognition remains tentative and supports only the
  existing reference set; grading data coverage does not expand recognition.
- Over-limit Pokémon do not get misleading capped-league investment grades.
- Validation: Java parser/memory tests, rank/CP-boundary/missing-data checks,
  Java syntax parsing. No Android SDK is available locally; GitHub Actions
  builds the APK, and live ML Kit behavior still requires phone verification.

Data and CPM source: https://github.com/pvpoke/pvpoke (MIT; license bundled in
research/PVPOKE-LICENSE.txt). Ranking source paths:
`src/data/rankings/all/overall/rankings-{1500,2500,10000}.json`;
base stats and moves: `src/data/gamemaster.json`; multipliers:
`src/js/pokemon/Pokemon.js`. No PvPoke code executes remotely at runtime.

## Version 0.6 — collection redesign

White collection list and blue navigation bar, native IV percentage rings,
reference portraits, CP/HP and saved IV values, type-colored move badges.
Search species/nickname/moves; sort by time, IV, CP or name; filter protected
records. Tap for full appraisal details, league grade access and a working
transfer-protection setting. Removal confirms and targets stable record ID.
Insets keep navigation clear of Android system controls. Unknown data remains
unknown. Bundled reference artwork is not a scan of the individual and does
not establish shiny/costume/background status. No invented letter grades,
levels or unsupported simulator switches. Existing records/signing preserved.

## Version 0.7 — advice first

The overlay now automatically shows a short PvP verdict and next action.
Full stats remain in saved readings and optional Show details. Saved collection
advice also starts with the verdict. Smaller reading panel (150dp).
Advice is a simple rule over the best eligible league in the bundled snapshot:
score below 70 = not a priority investment, 70–84.9 = possible use, 85+ =
promising species. These are coach heuristics, not PvPoke letter grades or a
team simulation. Missing league data prevents a categorical negative judgment.
Unknown identity/CP blocks advice; promising species still require appraisal,
moves and team review before investment. This does not recommend transfers.
Verified Cinderace verdict and unknown/partial-data handling with AdviceCheck.

## Version 0.8 — automatic collection capture

Two stable readings with species, CP, maximum HP, weight and height now create
one protected collection record automatically. Later exact fingerprint matches
update it and recall missing appraisal/moves. No Save reading → app → Add flow.
Image-based identities are marked Review ID. Review match remains optional for
ambiguous/conflicting readings. Unknown species and incomplete fingerprints do
not create records. Existing legacy entries without fingerprints may need one
manual review; powering up changes the fingerprint and may create another
record. Indistinguishable individuals cannot be uniquely separated by screen
reading. No automatic transfers/removals or complete collection synchronization.
Tests cover create/repeat/recall, partial reads, conflicts and duplicate matches.
Java syntax checked; full Android build remains the GitHub Actions gate.
