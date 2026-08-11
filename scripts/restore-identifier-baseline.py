#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src" / "main" / "java"

def main() -> None:
    for path in SRC.rglob("*.java"):
        text = path.read_text()
        if "ResourceLocation" not in text:
            continue
        orig = text
        text = text.replace(
            "net.minecraft.resources.ResourceLocation",
            "net.minecraft.resources.Identifier",
        )
        text = text.replace("ResourceLocation.fromNamespaceAndPath", "Identifier.fromNamespaceAndPath")
        text = text.replace("ResourceLocation", "Identifier")
        if text != orig:
            path.write_text(text)
            print(f"restored Identifier baseline in {path.relative_to(ROOT)}")

if __name__ == "__main__":
    main()
