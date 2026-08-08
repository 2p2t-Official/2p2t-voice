#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src" / "main" / "java"


def ver_tuple(v: str) -> tuple:
    parts = [int(x) for x in v.split(".")]
    while len(parts) < 3:
        parts.append(0)
    return tuple(parts)


def rename_gui_graphics(text: str) -> str:
    text = text.replace(
        "import net.minecraft.client.gui.GuiGraphics;",
        "import net.minecraft.client.gui.GuiGraphicsExtractor;",
    )
    text = re.sub(r"(?<![A-Za-z])GuiGraphics(?!Extractor)", "GuiGraphicsExtractor", text)
    return text


def port(mc: str) -> None:
    use_gui_set_screen = ver_tuple(mc) >= ver_tuple("26.2")

    for path in SRC.rglob("*.java"):
        text = path.read_text()
        orig = text

        text = text.replace(
            "net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper",
            "net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper",
        )
        text = text.replace("KeyBindingHelper", "KeyMappingHelper")
        text = text.replace("registerKeyBinding", "registerKeyMapping")

        text = text.replace(
            "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback",
            "net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry",
        )
        text = text.replace(
            "PayloadTypeRegistry.playS2C()",
            "PayloadTypeRegistry.clientboundPlay()",
        )
        text = text.replace(
            "PayloadTypeRegistry.playC2S()",
            "PayloadTypeRegistry.serverboundPlay()",
        )

        text = rename_gui_graphics(text)

        text = text.replace("protected void renderWidget(", "protected void extractWidgetRenderState(")
        text = text.replace("public void renderWidget(", "public void extractWidgetRenderState(")

        if path.name.endswith("Screen.java"):
            text = text.replace("public void renderBackground(", "public void extractBackground(")
            text = text.replace("public void render(", "public void extractRenderState(")
            text = text.replace("super.render(", "super.extractRenderState(")

        text = re.sub(r"\b(g|graphics)\.drawString\(", r"\1.text(", text)
        text = re.sub(r"\b(g|graphics)\.drawCenteredString\(", r"\1.centeredText(", text)

        if path.name == "TwoptwotVoiceClient.java":
            text = text.replace(
                "HudRenderCallback.EVENT.register((graphics, delta) -> {",
                "HudElementRegistry.addLast(\n"
                "                net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, \"overlay\"),\n"
                "                (graphics, delta) -> {",
            )

        if use_gui_set_screen:
            text = text.replace("minecraft.setScreen(", "minecraft.gui.setScreen(")
            text = text.replace("client.setScreen(", "client.gui.setScreen(")
            text = text.replace("mc.setScreen(", "mc.gui.setScreen(")
            text = re.sub(r"\bclient\.screen\b", "client.gui.screen()", text)
            text = re.sub(r"\bmc\.screen\b", "mc.gui.screen()", text)
            text = text.replace("mc.options.hideGui", "mc.gui.hud.isHidden()")

        if text != orig:
            path.write_text(text)
            print(f"ported {path.relative_to(ROOT)}")


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: port-to-26.py <mc_version>", file=sys.stderr)
        return 2
    port(sys.argv[1])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
