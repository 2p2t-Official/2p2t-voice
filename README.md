# 2p2t Voice

Fabric client mod for [2p2t](https://2p2t.org) proximity and channel voice chat. It talks to the same backend as [voice.2p2t.org](https://voice.2p2t.org), so browser and in-game users can hear each other.

## Requirements

- Fabric Loader
- Fabric API
- A supported Minecraft release (see [Releases](https://github.com/2p2t-Official/2p2t-voice/releases))
- Joining `2p2t.org`

## Install (recommended)

1. Install Fabric for your game version.
2. Install Fabric API for that same version.
3. Drop **`2p2tvoice-0-loader-….jar`** into `.minecraft/mods` (one jar for every Minecraft version).
4. Launch the game once — the loader downloads the matching voice build into `config/twoptwotvoice/payload/` and starts it in the same launch.
5. Join the server and open the voice menu (Controls → 2p2t Voice Chat).

First launch needs network access. Later launches reuse the cached build (offline OK until an update).

### Direct install (optional)

You can still drop a versioned `2p2tvoice-…+<mc>.jar` into `mods` instead of the loader. If both are present, the full jar wins and the loader stands down.

## Updates

- With the **loader**: updates refresh the cached payload; restart once to run the new build.
- With a **direct jar**: the mod can replace the jar in `mods`; restart to load it.
- Auto-update can be toggled in Voice Settings (`Check updates` / `Update now` are there too).

## Channels

Global, proximity, spawn (within 1000 blocks of 0,0), staff (permission gated), and player groups. Spawn range checks are enforced by the server as well as the client.

## Supported game versions

Release assets cover:

- **Loader:** one jar for all supported versions
- **Payloads:** 1.21 through 1.21.11, plus 26.1 / 26.1.1 / 26.1.2 / 26.2

## Building from source

### Loader

```bash
./scripts/build-loader.sh
```

Output: `dist/2p2tvoice-0-loader-<mod>.jar`

### One Minecraft version (payload)

**Needs**

- Git
- JDK **21** for any `1.21.x` build
- JDK **25** for any `26.x` build

```bash
./scripts/build-one.sh 1.21.11
```

Output: `dist/2p2tvoice-<mod>+<minecraft>.jar`

## License

MIT. See [LICENSE](LICENSE).
