"""Compile/run the production Java logic with minimal Android storage stubs.

Usage: python tools/check.py --json-jar PATH [--java-home PATH]
No Android SDK, phone, network, or real user data is required.
"""

import argparse
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / "project"
PURE = (
    "Appearance LearningBank EncounterEvidence Reading PvpGrade CoachAdvice "
    "ScreenKind WildMapDetector WildMapForeground WildMapAppearance WildMapTracker RecordStore"
).split()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json-jar", required=True, type=Path)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args()
    jar = args.json_jar.resolve(strict=True)

    def tool(name):
        return str(args.java_home / "bin" / name) if args.java_home else name

    java = PROJECT / "app/src/main/java/com/david/gocoach"
    sources = [java / (name + ".java") for name in PURE]
    sources += sorted((PROJECT / "research").rglob("*.java"))
    assets = PROJECT / "app/src/main/assets"
    checks = [
        ("ScannerRegression", []),
        ("LearningCheck", []),
        ("AutoSaveCheck", []),
        ("MemoryCheck", [assets / "moves.tsv"]),
        ("RecognitionCheck", [assets / "pvp.json"]),
        ("GradeCheck", [assets / "pvp.json"]),
        ("AdviceCheck", [assets / "pvp.json"]),
    ]
    output = ROOT / ".checks/classes"
    output.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            tool("javac"),
            "-encoding",
            "UTF-8",
            "-cp",
            str(jar),
            "-d",
            str(output),
            *map(str, sources),
        ],
        check=True,
    )
    cp = str(output) + os.pathsep + str(jar)
    for name, arguments in checks:
        subprocess.run(
            [
                tool("java"),
                "-cp",
                cp,
                "com.david.gocoach." + name,
                *map(str, arguments),
            ],
            check=True,
        )
    subprocess.run([tool("java"), "-cp", cp, "ScreenKindCheck"], check=True)
    print("All 8 logic regression suites passed.")


if __name__ == "__main__":
    main()
