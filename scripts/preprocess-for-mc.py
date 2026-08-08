#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src" / "main" / "java"


def ver_tuple(v: str) -> tuple:
    if v.startswith("26.") or v.startswith("25."):
        parts = v.split(".")
        return (100 + int(parts[0]),) + tuple(int(x) for x in parts[1:])
    parts = [int(x) for x in v.split(".")]
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts)


def patch_files(mc: str) -> None:
    v = ver_tuple(mc)
    use_identifier = v >= ver_tuple("1.21.11")
    use_mouse_event = v >= ver_tuple("1.21.9")
    use_key_category = v >= ver_tuple("1.21.9")

    for path in SRC.rglob("*.java"):
        text = path.read_text()
        orig = text

        if not use_identifier:
            text = text.replace(
                "net.minecraft.resources.Identifier",
                "net.minecraft.resources.ResourceLocation",
            )
            text = text.replace(
                "Identifier.fromNamespaceAndPath",
                "ResourceLocation.fromNamespaceAndPath",
            )
            text = re.sub(r"\bIdentifier\b", "ResourceLocation", text)

        if not use_key_category and path.name == "VoiceKeybinds.java":
            text = re.sub(
                r"public static final KeyMapping\.Category CATEGORY\s*=\s*"
                r"KeyMapping\.Category\.register\([^;]+;\s*",
                'public static final String CATEGORY = "key.categories.twoptwotvoice";\n\n    ',
                text,
            )
            text = text.replace(
                "import net.minecraft.resources.ResourceLocation;\n",
                "",
            )
            text = text.replace(
                "import net.minecraft.resources.Identifier;\n",
                "",
            )

        if not use_mouse_event:
            text = text.replace(
                "import net.minecraft.client.input.MouseButtonEvent;\n",
                "",
            )
            text = re.sub(
                r"public boolean mouseClicked\(MouseButtonEvent event, boolean doubleClick\)",
                "public boolean mouseClicked(double mouseX, double mouseY, int button)",
                text,
            )
            text = re.sub(
                r"public boolean mouseDragged\(MouseButtonEvent event, double dx, double dy\)",
                "public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy)",
                text,
            )
            text = re.sub(
                r"public boolean mouseReleased\(MouseButtonEvent event\)",
                "public boolean mouseReleased(double mouseX, double mouseY, int button)",
                text,
            )
            text = re.sub(
                r"public void onClick\(MouseButtonEvent event, boolean doubleClick\)",
                "public void onClick(double mouseX, double mouseY)",
                text,
            )
            text = text.replace("event.button()", "button")
            text = text.replace("event.x()", "mouseX")
            text = text.replace("event.y()", "mouseY")
            text = text.replace("super.mouseClicked(event, doubleClick)", "super.mouseClicked(mouseX, mouseY, button)")
            text = text.replace("super.mouseDragged(event, dx, dy)", "super.mouseDragged(mouseX, mouseY, button, dx, dy)")
            text = text.replace("super.mouseReleased(event)", "super.mouseReleased(mouseX, mouseY, button)")

        if text != orig:
            path.write_text(text)
            print(f"patched {path.relative_to(ROOT)}")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: preprocess-for-mc.py <mc_version>", file=sys.stderr)
        return 2
    patch_files(sys.argv[1])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
