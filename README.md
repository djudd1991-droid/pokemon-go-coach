# GO Coach

An Android companion that reads the Pokémon GO screen you choose to share.
Current features: Wild Map candidates, name-confirmed encounter learning, saved
Pokémon collection details, and advice from bundled PvP data. Events, research,
bag/Pokédex tracking and raid/team coaching are planned, not implemented.

## Work here

- `project/` — canonical, readable Android source and regression fixtures.
- `project/app/src/main/java/com/david/gocoach/` — application code.
- `tools/check.py` — runs eight Java logic regression suites.
- `tools/package.py` — rebuilds `go-coach-source.zip` and enforces 25 MB limits.
- `go-coach-portraits.zip`, `go-coach-wild-map.zip` — optional asset/tuning packs.
- `.github/workflows/build.yml` — package parity, regression tests, Android build and lint.

Edit `project/`, then run `python tools/package.py` before committing. The ZIP is
a generated distribution copy. GitHub Actions builds the readable source and
verifies the ZIP matches it. Do not edit the ZIP directly.

The recognition atlas streams numbered `visual-atlas/part-*.bin` assets below
25 MB. The app package and test signing key are unchanged so the debug APK can
update the previous test installation and keep local data.

See [development guide](project/DEVELOPMENT.md) and
[0.9.20 validation and phone test steps](docs/0.9.20.md).
