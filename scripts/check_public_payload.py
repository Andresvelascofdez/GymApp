"""Inspect staged Git blobs, including CI's checkout index; never inspect private input."""
import json
import re
import subprocess
from pathlib import PurePosixPath


def git(*args):
    return subprocess.check_output(["git", *args])


def check():
    paths = git("ls-files", "-z").decode().split("\0")
    count = 0
    errors = []
    for path in filter(None, paths):
        p = PurePosixPath(path)
        if any(part in {".private", ".local", "local-artifacts", "build", ".gradle"} for part in p.parts) or p.name == "local.properties" or p.suffix.lower() in {".apk", ".aab", ".zip", ".db", ".jks", ".keystore", ".hprof"}:
            errors.append(f"Forbidden public path: {path}")
            continue
        blob = git("show", f":{path}")
        count += 1
        if p.suffix == ".jar":
            if path != "gradle/wrapper/gradle-wrapper.jar":
                errors.append(f"Unexpected binary: {path}")
            continue
        try:
            text = blob.decode("utf-8")
        except UnicodeDecodeError:
            errors.append(f"Unexpected non-UTF8 file: {path}")
            continue
        for pattern in (r"gh[pousr]_[A-Za-z0-9]{20,}", r"github_pat_[A-Za-z0-9_]{20,}", r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----", r"sk-[A-Za-z0-9]{30,}"):
            if re.search(pattern, text):
                errors.append(f"Possible secret: {path}")
        if p.suffix == ".json":
            try:
                data = json.loads(text)
            except json.JSONDecodeError:
                errors.append(f"Invalid JSON: {path}")
                continue
            if isinstance(data, dict) and "age_years" in data:
                if any(data.get(k) is not None for k in ("age_years", "height_cm", "current_weight_kg", "target_weight_kg")):
                    errors.append(f"Non-anonymous profile: {path}")
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"PASS: {count} public staged/index files; no forbidden payload detected")

if __name__ == "__main__":
    check()
