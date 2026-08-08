# 2p2t Voice

Fabric client mod for [2p2t](https://2p2t.org) proximity and channel voice chat. It talks to the same backend as [voice.2p2t.org](https://voice.2p2t.org), so browser and in-game users can hear each other.

Jar id stays `twoptwotvoice` for compatibility with existing configs and the server plugin bridge.

## Requirements

- Fabric Loader
- Fabric API
- A supported Minecraft release (see [Releases](https://github.com/WaffleStealz/2p2t-voice/releases))
- Joining `2p2t.org` (or a server running the matching 2p2tCore voice bridge)

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
- Settings show the last update time and whether it was automatic or manual.

## Channels

Global, proximity, spawn (within 1000 blocks of 0,0), staff (permission gated), and player groups. Spawn range checks are enforced by the server as well as the client.

## Building

```bash
./gradlew build
```

Versioned jars are produced with:

```bash
./scripts/build-versions.sh
```

Artifacts land in `dist/` as `twoptwotvoice-<mod>+<minecraft>.jar`.

## License

MIT. See [LICENSE](LICENSE).
