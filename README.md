# Mascot3DAccel

Hardware-accelerated fork of [MascotME](https://github.com/woesss/JL-Mod/) for **JSR-184 (M3G)** phones — **Nokia N95** (PowerVR MBX Lite). Keeps the MascotCapsule v3 API as `com.mascot3daccel.micro3d.v3`; games are patched on a PC, not recompiled on the device.

Software rasterizer → **javax.microedition.m3g** Immediate Mode. MBAC/BMP parsing inherited from MascotME / [JL-Mod](https://github.com/woesss/JL-Mod/).

---

## For end users (release)

You only need the patcher and a game JAR. **Java and Python are not required** if you download a prebuilt release.

### Release bundle layout

```
Mascot3DAccel-Patcher/
  mascot3daccel-patcher.exe   (or jar_patcher.py + mascot3daccel.jar)
  mascot3daccel.jar             prebuilt bridge (JSR-184)
  README.txt
```

`mascot3daccel.jar` lives in `tools/` in this repository — the same bridge the patcher injects into games.

### Patch a game in one step

**Option A — standalone .exe (PyInstaller, no Python):**

```text
mascot3daccel-patcher.exe "RallyMasterPro.jar"
```

**Option B — Python 3 (stdlib only):**

```bash
python tools/jar_patcher.py "RallyMasterPro.jar"
```

In **release mode** (default when there is no `src/` folder next to the script):

1. Unpacks the game JAR.
2. Binary-replaces `com/mascotcapsule` → `com/mascot3daccel` in `.class` files (13 characters, safe for the constant pool).
3. Removes the old `com/mascotcapsule/` tree.
4. Injects `tools/mascot3daccel.jar`.
5. Writes `RallyMasterPro_patched_nokia.jar`.

Copy the patched JAR (and `.jad` if present) to the phone.

### Building the .exe for distribution (maintainers)

```bash
pip install pyinstaller
pyinstaller --onefile --name mascot3daccel-patcher tools/jar_patcher.py
```

Place `mascot3daccel.jar` next to the `.exe` (same folder as the script — `tools/` in the repo).

---

## For developers (dev mode)

### Requirements

| Component | Purpose |
|-----------|---------|
| **OpenJDK 8** ([Adoptium Temurin](https://adoptium.net/)) | `javac` for Java 1.3 bytecode |
| **`lib/jsr184_api.jar`** | JSR-184 / MIDP API stubs for dev compilation (see below) |
| Python 3 | Running `tools/jar_patcher.py` |

### JSR-184 API for dev compilation

Stock `javac` does not know `javax.microedition.m3g` or `javax.microedition.lcdui`. For dev mode you need a J2ME API stub JAR:

1. Copy **`api.jar`** from **KEmulator** (full J2ME API stub with MIDP and M3G).
2. Save it in the repo as:

```text
lib/jsr184_api.jar
```

If your `api.jar` lacks `javax.microedition.lcdui`, also add a MIDP stub as `lib/midp_api.jar`.

These files are not committed (see `.gitignore`); each developer keeps them locally.

The patcher automatically prepends JDK 8 `rt.jar` to `-bootclasspath` (required because `-bootclasspath` replaces the default bootstrap classes).

Override: set `MASCOT3DACCEL_BOOTCLASSPATH` to a full JAR list from Java ME SDK 3.x.

### Environment setup

1. Install **JDK 8** and verify: `javac -version`
2. Add it to `PATH` or set `JAVA_HOME`
3. Place `lib/jsr184_api.jar` (see above)

Alternative without the patcher: build with **NetBeans** + JSR-184 platform (`ant jar`).

### Patcher dev mode

Enabled with `--dev`, or **automatically** when `src/` exists at the repo root:

```bash
python tools/jar_patcher.py --dev "game.jar" -o game_n95.jar
```

The patcher runs:

```text
javac -source 1.3 -target 1.3 -bootclasspath lib/jsr184_api.jar -d build/classes src/com/mascot3daccel/micro3d/v3/*.java
```

Then injects `build/classes` into the game (same binary `mascotcapsule` → `mascot3daccel` replacement).

Force release mode even when `src/` is present:

```bash
python tools/jar_patcher.py --release "game.jar"
```

Use prebuilt classes without javac:

```bash
python tools/jar_patcher.py --dev game.jar --classes-dir build/CLDC-1.1-MIDP-2.0/compiled
```

### Repository layout

```
src/com/mascot3daccel/micro3d/v3/   MCv3 + M3G sources
lib/jsr184_api.jar                  JSR-184 API (KEmulator api.jar, local only)
tools/jar_patcher.py                hybrid patcher
tools/mascot3daccel.jar             release bridge (built by maintainers)
build/classes/                      javac output (dev)
dist/                               Mascot3DAccel.jar (NetBeans)
```

### Development status

| Component | Status |
|-----------|--------|
| MBAC / BMP parsing | Done |
| M3G bind / release / textures | Done |
| Matrices and camera | Done |
| Appearance / material | Done |
| Figure → VertexBuffer | In progress |

### In-game configuration

`mascotme.ini` at the JAR root — see [INI-CONFIG.md](INI-CONFIG.md).

---

## Screenshots (MascotME, software renderer)

![Screenshot of a Coast Racer](/screenshots/CoastRacer.png) ![Screenshot of a Bomberman 3D](/screenshots/Bomberman3D.png) ![Screenshot of a Blades and Magic](/screenshots/BladesAndMagic.png)

## Special thanks

- [woesss](https://github.com/woesss/) — [JL-Mod](https://github.com/woesss/JL-Mod/)
- Roman Lahin — MascotME
- Yury Kharchenko — MCv3 parsing
- [klaxons1](https://github.com/klaxons1/), [shinovon](https://github.com/shinovon/), [minexew](https://github.com/minexew/)

## License

MIT — [LICENSE](LICENSE). Fork extensions Copyright (c) 2026 Konstantin Zverev.
