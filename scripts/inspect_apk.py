"""Check APK asset isolation without printing any personal input."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def inspect(path, personal=False):
    path = Path(path)
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        for required in ("assets/nutrition/foods.json", "assets/nutrition/recipes.json", "assets/training/routine.json", "classes.dex"):
            assert required in names, f"Missing required APK entry: {required}"
        assert ("assets/profile.personal.json" in names) == personal, "Personal asset isolation failed"
        assert not any(name.endswith((".db", ".jks", ".keystore")) for name in names)
        profile = json.loads(archive.read("assets/profile.example.json"))
        assert all(profile.get(key) is None for key in ("age_years", "height_cm", "current_weight_kg", "target_weight_kg"))
        if personal:
            root = Path(__file__).resolve().parents[1]
            assert archive.read("assets/profile.personal.json") == (root / ".private/profile.local.json").read_bytes()
    checksum = hashlib.sha256(path.read_bytes()).hexdigest()
    print(f"PASS: {'personal LOCAL' if personal else 'generic'} APK isolation; {path.stat().st_size} bytes; SHA256 {checksum}")
    return checksum


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("--personal", action="store_true")
    args = parser.parse_args()
    inspect(args.apk, args.personal)
