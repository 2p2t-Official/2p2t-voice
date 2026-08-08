# 2p2t Voice

Fabric client mod for [2p2t](https://2p2t.org) proximity and channel voice chat. It talks to the same backend as [voice.2p2t.org](https://voice.2p2t.org), so browser and in-game users can hear each other.

## Requirements

- Fabric Loader
- Fabric API
- A supported Minecraft release (see [Releases](https://github.com/WaffleStealz/2p2t-voice/releases))
- Joining `2p2t.org`

## Install

1. Install Fabric for your game version.
2. Drop the matching `twoptwotvoice-…+<mc>.jar` into `.minecraft/mods`.
3. Install Fabric API for that same version.
4. Join the server and open the voice menu with the keybind (default: check Controls → 2p2t Voice).

## Updates

The mod checks [GitHub Releases](https://github.com/WaffleStealz/2p2t-voice/releases) for a jar built for your Minecraft version.

- Auto-update can be toggled in Voice Settings.
- **Check updates** and **Update now** are also there if you want to run it by hand.
- After an update installs, restart the game so Fabric loads the new jar.

## Channels

Global, proximity, spawn (within 1000 blocks of 0,0), staff (permission gated), and player groups. Spawn range checks are enforced by the server as well as the client.

## Supported game versions

Release assets cover:

- **1.21 through 1.21.11**
- **26.1, 26.1.1, 26.1.2, 26.2**

## Building

```bash
./gradlew build
```

1.21-line jars:

```bash
./scripts/build-versions.sh
```

26.x jars (uses `build.gradle.26` + `scripts/port-to-26.py`, then restores `src/`):

```bash
./scripts/build-26.sh
```

Artifacts land in `dist/` as `twoptwotvoice-<mod>+<minecraft>.jar`.

Publish a GitHub release with **every** supported jar (required; do not upload a single target):

```bash
# working tree must be clean; mod_version in gradle.properties is the tag (v1.2.1 → tag v1.2.1)
./scripts/release.sh
```

Options: `--skip-build` (upload existing `dist/` jars only), `--skip-upload` (build/verify only).

## License

MIT. See [LICENSE](LICENSE).
