# Mascot3DAccel

Hardware-accelerated fork of [MascotME](https://github.com/woesss/JL-Mod/) for **JSR-184 (M3G)** phones — **Nokia N95** (PowerVR MBX Lite). Keeps the MascotCapsule v3 API as `com.mascot3daccel.micro3d.v3`; games are patched on a PC, not recompiled on the device.

Software rasterizer → **javax.microedition.m3g** Immediate Mode. MBAC/BMP parsing inherited from MascotME / [JL-Mod](https://github.com/woesss/JL-Mod/).

---

## Для пользователей (релиз)

Нужен только патчер и файл игры. **Java и Python не обязательны**, если вы скачали готовый релиз.

### Что в релизном архиве

```
Mascot3DAccel-Patcher/
  mascot3daccel-patcher.exe   (или jar_patcher.py + mascot3daccel.jar)
  mascot3daccel.jar             предсобранный мост (JSR-184)
  README.txt
```

`mascot3daccel.jar` лежит в папке `tools/` репозитория — это тот же мост, который патчер вшивает в игру.

### Патч игры в один шаг

**Вариант A — готовый .exe (PyInstaller, без Python):**

```text
mascot3daccel-patcher.exe "RallyMasterPro.jar"
```

**Вариант B — Python 3 (только stdlib):**

```bash
python tools/jar_patcher.py "RallyMasterPro.jar"
```

Скрипт в **релизном режиме** (по умолчанию, если рядом нет папки `src/`):

1. Распаковывает игру.
2. Заменяет в `.class` байткод `com/mascotcapsule` → `com/mascot3daccel` (13 символов, безопасно для constant pool).
3. Удаляет старый `com/mascotcapsule/`.
4. Вкладывает содержимое `tools/mascot3daccel.jar`.
5. Собирает `RallyMasterPro_patched_nokia.jar`.

Скопируйте JAR (и `.jad`, если есть) на телефон.

### Сборка .exe для распространения (maintainer)

```bash
pip install pyinstaller
pyinstaller --onefile --name mascot3daccel-patcher tools/jar_patcher.py
```

Положите рядом с `.exe` файл `mascot3daccel.jar` (в ту же папку, что и скрипт — `tools/` в репозитории).

---

## Для разработчиков (Dev-режим)

### Требования

| Компонент | Назначение |
|-----------|------------|
| **OpenJDK 8** ([Adoptium Temurin](https://adoptium.net/)) | `javac` для байткода Java 1.3 |
| **Java ME SDK 3.x** (опционально) | JSR-184 bootclasspath для компиляции M3G |
| Python 3 | Запуск `tools/jar_patcher.py` |

### Настройка окружения

1. Установите **JDK 8**, проверьте: `javac -version`
2. Добавьте в `PATH` или задайте `JAVA_HOME`
3. Для M3G-классов укажите bootclasspath (пример Windows):

```text
set MASCOT3DACCEL_BOOTCLASSPATH=C:\Java_ME_platform_SDK_3.4\lib\jsr184_1.1.jar;C:\Java_ME_platform_SDK_3.4\lib\midp_2.0.jar;C:\Java_ME_platform_SDK_3.4\lib\cldc_1.1.jar
```

Альтернатива: сборка через **NetBeans** + JSR-184 platform (`ant jar`).

### Dev-режим патчера

Активируется флагом `--dev` или **автоматически**, если в корне репозитория есть `src/`:

```bash
python tools/jar_patcher.py --dev "game.jar" -o game_n95.jar
```

Патчер вызывает:

```text
javac -source 1.3 -target 1.3 -encoding UTF-8 -d build/classes src/com/mascot3daccel/micro3d/v3/*.java
```

Затем вшивает `build/classes` в игру (с той же бинарной заменой `mascotcapsule` → `mascot3daccel`).

Принудительный релизный режим (даже при наличии `src/`):

```bash
python tools/jar_patcher.py --release "game.jar"
```

Готовые классы без javac:

```bash
python tools/jar_patcher.py --dev game.jar --classes-dir build/CLDC-1.1-MIDP-2.0/compiled
```

### Структура репозитория

```
src/com/mascot3daccel/micro3d/v3/   исходники MCv3 + M3G
tools/jar_patcher.py                гибридный патчер
tools/mascot3daccel.jar             релизный мост (собирается maintainer'ом)
build/classes/                      выход javac (dev)
dist/                               Mascot3DAccel.jar (NetBeans)
```

### Статус разработки

| Компонент | Состояние |
|-----------|-----------|
| MBAC / BMP parsing | Готово |
| M3G bind / release / textures | Готово |
| Матрицы и камера | Готово |
| Appearance / material | Готово |
| Figure → VertexBuffer | В работе |

### Конфигурация в игре

`mascotme.ini` в корне JAR — см. [INI-CONFIG.md](INI-CONFIG.md).

---

## Screenshots (MascotME, софтверный рендер)

![Screenshot of a Coast Racer](/screenshots/CoastRacer.png) ![Screenshot of a Bomberman 3D](/screenshots/Bomberman3D.png) ![Screenshot of a Blades and Magic](/screenshots/BladesAndMagic.png)

## Special thanks

- [woesss](https://github.com/woesss/) — [JL-Mod](https://github.com/woesss/JL-Mod/)
- Roman Lahin — MascotME
- Yury Kharchenko — MCv3 parsing
- [klaxons1](https://github.com/klaxons1/), [shinovon](https://github.com/shinovon/), [minexew](https://github.com/minexew/)

## License

MIT — [LICENSE](LICENSE). Fork extensions Copyright (c) 2026 Konstantin Zverev.
