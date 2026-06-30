# Mascot3DAccel

Mascot3DAccel is a hardware-accelerated fork of [MascotME](https://github.com/woesss/JL-Mod/) for **JSR-184 (M3G)** devices such as the **Nokia N95** (PowerVR MBX Lite). It keeps the MascotCapsule v3 API (`com.mascot3daccel.micro3d.v3`) so existing games can run without recompilation on the phone — the bridge is injected on a PC.

The original software rasterizer is replaced with **javax.microedition.m3g** Immediate Mode rendering. Parsing of `.mbac` models, animations, and BMP textures is inherited from MascotME / [JL-Mod](https://github.com/woesss/JL-Mod/).

## Requirements

| Layer | Requirement |
|-------|-------------|
| Phone / target | MIDP 2.0, CLDC 1.1, **JSR-184 1.1** |
| Dev build | Java ME SDK 3.x or NetBeans with JSR-184 platform |
| Patching games | **Python 3** (stdlib only) |

## Quick start — patch any game (one command)

1. Build this project in NetBeans (**Clean and Build**) or run `ant jar`.
2. Patch the game JAR:

```bash
python tools/jar_patcher.py "RallyMasterPro.jar"
```

Output: `RallyMasterPro_patched_nokia.jar` in the current directory.

Custom output path and explicit classes directory:

```bash
python tools/jar_patcher.py game.jar -o game_n95.jar --classes-dir build/CLDC-1.1-MIDP-2.0/compiled
```

The patcher will:

1. Unpack the game JAR.
2. Binary-replace `com/mascotcapsule` → `com/mascot3daccel` in every `.class` (same 13-character package name — safe for the constant pool).
3. Remove the old `com/mascotcapsule` classes from the archive.
4. Inject compiled `com/mascot3daccel/**` from `build/.../compiled` or `dist/.../Mascot3DAccel.jar`.
5. Repack a ready-to-install JAR.

Copy the patched JAR (and `.jad` if needed) to the phone and run.

## Manual integration

Copy the `com/mascot3daccel` folder from `dist/.../Mascot3DAccel.jar` into the game JAR and rename package references in game classes (the Python script does this automatically). See [INTEGRATION-NOTES.md](INTEGRATION-NOTES.md) for emulator integration.

## Configuration

Compatibility and debug flags use `mascotme.ini` in the JAR root (same format as MascotME). See [INI-CONFIG.md](INI-CONFIG.md).

Example for Nokia N95 testing:

```ini
showFPS=1
doNotClear=0
```

## Project layout

```
src/com/mascot3daccel/micro3d/v3/   MCv3 API + M3G renderer
tools/jar_patcher.py                AOT JAR patcher (PC)
build/                              Compiled .class (after build)
dist/                               Mascot3DAccel.jar
```

## Status

| Component | State |
|-----------|--------|
| MBAC / BMP parsing | Ported from MascotME |
| M3G bind / release / textures | Done |
| Matrix & camera mapping | Done |
| Appearance / material mapping | Done |
| Figure `VertexBuffer` rendering | In progress |

## Screenshots (MascotME software renderer)

![Screenshot of a Coast Racer](/screenshots/CoastRacer.png) ![Screenshot of a Bomberman 3D](/screenshots/Bomberman3D.png) ![Screenshot of a Blades and Magic](/screenshots/BladesAndMagic.png)

## Special thanks

- [woesss](https://github.com/woesss/) — MascotCapsule v3 implementation in [JL-Mod](https://github.com/woesss/JL-Mod/)
- [Roman Lahin](https://github.com/) — MascotME
- [Yury Kharchenko](https://github.com/) — original MCv3 parsing code
- [klaxons1](https://github.com/klaxons1/), [shinovon](https://github.com/shinovon/), [minexew](https://github.com/minexew/) — testing and [MascotCapsule Archaeology](https://github.com/j2me-preservation/MascotCapsule/)

## License

MIT License — see [LICENSE](LICENSE). Fork extensions Copyright (c) 2026 Konstantin Zverev.
