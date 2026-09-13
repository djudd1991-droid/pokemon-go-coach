"""Rebuild the source ZIP from project/. Never edit generated ZIPs directly."""

from pathlib import Path
import argparse
import os
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LIMIT = 25_000_000
EXCLUDED = {".gradle", "build", ".idea", "__pycache__", "local.properties", ".DS_Store"}
TEXT = {
    ".java",
    ".py",
    ".md",
    ".xml",
    ".gradle",
    ".json",
    ".txt",
    ".tsv",
    ".properties",
}


def contents(path):
    data = path.read_bytes()
    return data.replace(b"\r\n", b"\n") if path.suffix in TEXT else data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Verify existing archive content without writing",
    )
    args = parser.parse_args()
    project = ROOT / "project"
    target = ROOT / "go-coach-source.zip"
    sources = [
        p
        for p in sorted(
            project.rglob("*"), key=lambda p: p.relative_to(project).as_posix()
        )
        if p.is_file()
        and not any(part in EXCLUDED for part in p.relative_to(project).parts)
    ]
    for path in sources:
        if path.stat().st_size > LIMIT:
            raise ValueError(f"File exceeds 25 MB: {path.relative_to(project)}")
    if args.check:
        if target.stat().st_size > LIMIT:
            raise ValueError("Source archive exceeds 25 MB")
        with zipfile.ZipFile(target) as archive:
            expected = {p.relative_to(project).as_posix() for p in sources}
            if (
                len(archive.namelist()) != len(expected)
                or set(archive.namelist()) != expected
            ):
                raise ValueError("Archive file list differs from project/")
            for path in sources:
                name = path.relative_to(project).as_posix()
                if archive.read(name) != contents(path):
                    raise ValueError(f"Archive content differs: {name}")
        print("Source archive matches project/; file and archive size limits passed.")
        return
    temp = target.with_suffix(".zip.tmp")
    try:
        with zipfile.ZipFile(
            temp, "w", zipfile.ZIP_DEFLATED, compresslevel=9
        ) as archive:
            for path in sources:
                relative = path.relative_to(project)
                if not path.is_file() or any(
                    part in EXCLUDED for part in relative.parts
                ):
                    continue
                if path.stat().st_size > LIMIT:
                    raise ValueError(f"File exceeds 25 MB: {relative}")
                entry = zipfile.ZipInfo(relative.as_posix(), (2026, 1, 1, 0, 0, 0))
                entry.create_system = 3
                entry.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(entry, contents(path), compresslevel=9)
        if temp.stat().st_size > LIMIT:
            raise ValueError(
                "Source archive exceeds 25 MB; split additional assets into a pack."
            )
        with zipfile.ZipFile(temp) as archive:
            if archive.testzip() is not None:
                raise ValueError("Archive failed integrity check")
        os.replace(temp, target)
        print(f"{target.name}: {target.stat().st_size:,} bytes; all entries <= 25 MB")
    finally:
        if temp.exists():
            temp.unlink()


if __name__ == "__main__":
    main()
