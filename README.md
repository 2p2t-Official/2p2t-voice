# 2p2t Voice

Fabric client mod for [2p2t](https://2p2t.org) proximity and channel voice chat. It talks to the same backend as [voice.2p2t.org](https://voice.2p2t.org), so browser and in-game users can hear each other.

## Requirements

- Fabric Loader
- Fabric API
- A supported Minecraft release (see [Releases](https://github.com/2p2t-Official/2p2t-voice/releases))
- Joining `2p2t.org`

## Install

1. Install Fabric for your game version.
2. Drop the matching `2p2tvoice-…+<mc>.jar` into `.minecraft/mods`.
3. Install Fabric API for that same version.
4. Join the server and open the voice menu with the keybind (default: check Controls → 2p2t Voice).

Most players should just download a jar from [Releases](https://github.com/2p2t-Official/2p2t-voice/releases). Building from source is optional.

## Updates

The mod checks [GitHub Releases](https://github.com/2p2t-Official/2p2t-voice/releases) for a jar built for your Minecraft version.

- Auto-update can be toggled in Voice Settings.
- **Check updates** and **Update now** are also there if you want to run it by hand.
- After an update installs, restart the game so Fabric loads the new jar.

## Channels

Global, proximity, spawn (within 1000 blocks of 0,0), staff (permission gated), and player groups. Spawn range checks are enforced by the server as well as the client.

## Supported game versions

Release assets cover:

- **1.21 through 1.21.11**
- **26.1, 26.1.1, 26.1.2, 26.2**

## Building one version

If you want to compile the mod yourself, build **only the Minecraft version you play**. You do not need every jar.

**Needs**

- Git
- JDK **21** for any `1.21.x` build
- JDK **25** for any `26.x` build

**Steps**

```bash
git clone https://github.com/2p2t-Official/2p2t-voice.git
cd 2p2t-voice
./scripts/build-one.sh 1.21.11
```

Replace `1.21.11` with your game version (`1.21.4`, `26.2`, etc.).

The finished jar is written to:

`dist/2p2tvoice-<mod>+<minecraft>.jar`

Copy that into `.minecraft/mods` (with matching Fabric API).

Examples:

```bash
./scripts/build-one.sh 1.21.8
./scripts/build-one.sh 26.2
```

## License

MIT. See [LICENSE](LICENSE).
