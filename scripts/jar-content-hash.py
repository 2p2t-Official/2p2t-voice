#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import sys
import zipfile
from pathlib import Path

def jar_content_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with zipfile.ZipFile(path, "r") as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            name = info.filename
            if name == "voice-integrity.properties" or name.startswith("META-INF/"):
                continue
            digest.update(name.encode("utf-8"))
            digest.update(zf.read(name))
    return digest.hexdigest()

def main() -> int:
    if len(sys.argv) < 2:
        print("usage: jar-content-hash.py <jar> [jar...]", file=sys.stderr)
        return 2
    for arg in sys.argv[1:]:
        path = Path(arg)
        print(f"{jar_content_sha256(path)}  {path.name}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
