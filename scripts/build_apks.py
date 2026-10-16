"""Produce distinctly named local debug APKs and restore generic build outputs."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

from inspect_apk import inspect


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--personal", action="store_true", help="Also build the ignored local personal input")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    os.chdir(root)
    wrapper = str(root / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    folder = root / "local-artifacts"
    folder.mkdir(exist_ok=True)
    source = root / "app/build/outputs/apk/debug/app-debug.apk"
    checksums = []

    def build(personal):
        subprocess.run([wrapper, "assembleDebug", f"-Ppersonal={str(personal).lower()}"], check=True)
        target = folder / ("gymapp-personal-debug.apk" if personal else "gymapp-generic-debug.apk")
        shutil.copy2(source, target)
        checksums.append(f"{inspect(target, personal)}  {target.name}")

    build(False)
    if args.personal:
        try:
            build(True)
        finally:
            subprocess.run([wrapper, "assembleDebug", "-Ppersonal=false"], check=True)
            target = folder / "gymapp-generic-debug.apk"
            shutil.copy2(source, target)
            checksums[0] = f"{inspect(target)}  {target.name}"
    (folder / "checksums.sha256").write_text("\n".join(checksums) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
