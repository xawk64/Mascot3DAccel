#!/usr/bin/env python3
# Copyright (c) 2026 Konstantin Zverev. All rights reserved.
#
# Hybrid AOT patcher for Sony Ericsson J2ME games -> Nokia N95 (JSR-184 / Mascot3DAccel).
# Dev mode  : compile src/ with javac 1.3, inject into game JAR.
# Release   : inject prebuilt tools/mascot3daccel.jar (no JDK required).

import argparse
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

TOOLS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(TOOLS_DIR)

BRIDGE_JAR_NAME = "mascot3daccel.jar"
SRC_GLOB = os.path.join(REPO_ROOT, "src", "com", "mascot3daccel", "micro3d", "v3", "*.java")
BUILD_CLASSES = os.path.join(REPO_ROOT, "build", "classes")

OLD_PKG_SLASH = b"com/mascotcapsule"
NEW_PKG_SLASH = b"com/mascot3daccel"
OLD_PKG_NAME = b"mascotcapsule"
NEW_PKG_NAME = b"mascot3daccel"

REPLACEMENTS = (
    (OLD_PKG_SLASH, NEW_PKG_SLASH),
    (OLD_PKG_NAME, NEW_PKG_NAME),
)

MODE_DEV = "dev"
MODE_RELEASE = "release"


def log(msg):
    sys.stdout.write(msg + "\n")
    sys.stdout.flush()


def log_step(step, total, msg):
    log("[%d/%d] %s" % (step, total, msg))


def patch_class_bytes(data):
    total = 0
    for old, new in REPLACEMENTS:
        count = data.count(old)
        if count:
            data = data.replace(old, new)
            total += count
    return data, total


def has_src_tree():
    src = os.path.join(REPO_ROOT, "src")
    pkg = os.path.join(src, "com", "mascot3daccel", "micro3d", "v3")
    return os.path.isdir(pkg)


def resolve_mode(dev_flag, release_flag):
    if dev_flag and release_flag:
        raise SystemExit("Use either --dev or --release, not both.")
    if dev_flag:
        return MODE_DEV
    if release_flag:
        return MODE_RELEASE
    if has_src_tree():
        return MODE_DEV
    return MODE_RELEASE


def find_javac():
    javac = os.environ.get("JAVAC")
    if javac and os.path.isfile(javac):
        return javac
    javac = shutil.which("javac")
    if javac:
        return javac
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidate = os.path.join(java_home, "bin", "javac")
        if os.path.isfile(candidate):
            return candidate
        if sys.platform == "win32":
            candidate = os.path.join(java_home, "bin", "javac.exe")
            if os.path.isfile(candidate):
                return candidate
    raise SystemExit(
        "javac not found. Install OpenJDK 8 (Adoptium Temurin) and add it to PATH,\n"
        "or set JAVA_HOME / JAVAC environment variable."
    )


def find_bootclasspath():
    env = os.environ.get("MASCOT3DACCEL_BOOTCLASSPATH")
    if env and os.path.exists(env.split(os.pathsep)[0]):
        return env

    candidates = []
    sdk_home = os.environ.get("JAVA_ME_SDK_HOME")
    if sdk_home:
        candidates.append(os.path.join(sdk_home, "lib"))

    patterns = [
        r"C:\Java_ME_platform_SDK_3.4\lib\*.jar",
        r"C:\Program Files\Java_ME_platform_SDK_3.4\lib\*.jar",
        os.path.expanduser("~/Java_ME_platform_SDK_3.4/lib/*.jar"),
    ]
    for pattern in patterns:
        jars = glob.glob(pattern)
        if jars:
            return os.pathsep.join(sorted(jars))

    return None


def compile_dev_sources():
    sources = sorted(glob.glob(SRC_GLOB))
    if not sources:
        raise SystemExit("Dev mode: no sources at %s" % SRC_GLOB)

    javac = find_javac()
    bootclasspath = find_bootclasspath()

    if os.path.isdir(BUILD_CLASSES):
        shutil.rmtree(BUILD_CLASSES)
    os.makedirs(BUILD_CLASSES)

    cmd = [
        javac,
        "-source", "1.3",
        "-target", "1.3",
        "-encoding", "UTF-8",
        "-d", BUILD_CLASSES,
    ]
    if bootclasspath:
        cmd.extend(["-bootclasspath", bootclasspath])
        log("  bootclasspath: %s" % bootclasspath)
    else:
        log("  WARNING: no JSR-184 bootclasspath (set MASCOT3DACCEL_BOOTCLASSPATH)")
    cmd.extend(sources)

    log("  Running: %s" % " ".join(cmd))
    try:
        subprocess.check_call(cmd)
    except subprocess.CalledProcessError:
        raise SystemExit("javac failed. Check JDK 8 and MASCOT3DACCEL_BOOTCLASSPATH.")
    except OSError as e:
        raise SystemExit("Cannot run javac: %s" % e)

    count = 0
    for _root, _dirs, files in os.walk(BUILD_CLASSES):
        for name in files:
            if name.endswith(".class"):
                count += 1
    log("  Compiled %d .class files -> %s" % (count, BUILD_CLASSES))
    return BUILD_CLASSES


def bridge_jar_path():
    return os.path.join(TOOLS_DIR, BRIDGE_JAR_NAME)


def prepare_bridge_source(mode, classes_dir):
    if classes_dir:
        path = os.path.abspath(classes_dir)
        accel = os.path.join(path, "com", "mascot3daccel")
        if os.path.isdir(accel):
            return ("classes", path)
        raise SystemExit("Classes dir not found: %s" % path)

    if mode == MODE_DEV:
        if classes_dir:
            log("  Mode: DEVELOPMENT (prebuilt classes)")
            return ("classes", os.path.abspath(classes_dir))
        log("  Mode: DEVELOPMENT (javac 1.3)")
        return ("classes", compile_dev_sources())

    jar_path = bridge_jar_path()
    if not os.path.isfile(jar_path):
        raise SystemExit(
            "Release mode: %s not found.\n"
            "Place a prebuilt bridge JAR next to this script, or run from repo with --dev."
            % jar_path
        )
    log("  Mode: RELEASE (prebuilt %s)" % BRIDGE_JAR_NAME)
    return ("jar", jar_path)


def inject_bridge(bridge_kind, bridge_path, dest_root):
    if bridge_kind == "classes":
        src_accel = os.path.join(bridge_path, "com", "mascot3daccel")
        dest_accel = os.path.join(dest_root, "com", "mascot3daccel")
        if not os.path.isdir(src_accel):
            raise SystemExit("Bridge package missing: %s" % src_accel)
        if os.path.isdir(dest_accel):
            shutil.rmtree(dest_accel)
        shutil.copytree(src_accel, dest_accel)
        count = 0
        for _root, _dirs, files in os.walk(dest_accel):
            for name in files:
                if name.endswith(".class"):
                    count += 1
        return count

    count = 0
    with zipfile.ZipFile(bridge_path, "r") as zf:
        for name in zf.namelist():
            if name.endswith("/"):
                continue
            dest = os.path.join(dest_root, name.replace("/", os.sep))
            parent = os.path.dirname(dest)
            if not os.path.isdir(parent):
                os.makedirs(parent)
            with zf.open(name) as src, open(dest, "wb") as out:
                out.write(src.read())
            if name.endswith(".class"):
                count += 1
    return count


def patch_jar(input_jar, output_jar, mode, classes_dir, keep_capsule):
    if not os.path.isfile(input_jar):
        raise SystemExit("Input JAR not found: %s" % input_jar)

    work_dir = tempfile.mkdtemp(prefix="mascot3daccel_patch_")
    total_steps = 6
    step = 1

    try:
        log_step(step, total_steps, "Unpacking %s" % input_jar)
        with zipfile.ZipFile(input_jar, "r") as zf:
            zf.extractall(work_dir)
        step += 1

        log_step(step, total_steps, "Scanning .class files for mascotcapsule references")
        class_files = []
        for root, _dirs, files in os.walk(work_dir):
            for name in files:
                if name.endswith(".class"):
                    class_files.append(os.path.join(root, name))

        total_files = len(class_files)
        patched_files = 0
        total_replacements = 0

        for idx, path in enumerate(class_files, 1):
            rel = os.path.relpath(path, work_dir).replace(os.sep, "/")
            with open(path, "rb") as f:
                original = f.read()

            patched, count = patch_class_bytes(original)
            if count:
                with open(path, "wb") as f:
                    f.write(patched)
                patched_files += 1
                total_replacements += count
                log("  [patch] %s (%d replacements)" % (rel, count))
            elif idx % 50 == 0 or idx == total_files:
                pct = int(100.0 * idx / total_files) if total_files else 100
                log("  [scan] %d/%d (%d%%)" % (idx, total_files, pct))

        log("  Patched %d/%d class files, %d total replacements" % (
            patched_files, total_files, total_replacements))
        step += 1

        if not keep_capsule:
            old_pkg = os.path.join(work_dir, "com", "mascotcapsule")
            if os.path.isdir(old_pkg):
                log_step(step, total_steps, "Removing original com/mascotcapsule")
                shutil.rmtree(old_pkg)
            else:
                log_step(step, total_steps, "No com/mascotcapsule in JAR (skip removal)")
        else:
            log_step(step, total_steps, "Keeping original com/mascotcapsule (--keep-capsule)")
        step += 1

        log_step(step, total_steps, "Preparing Mascot3DAccel bridge")
        bridge_kind, bridge_path = prepare_bridge_source(mode, classes_dir)
        step += 1

        log_step(step, total_steps, "Injecting bridge into game JAR")
        injected = inject_bridge(bridge_kind, bridge_path, work_dir)
        log("  Injected %d bridge .class files" % injected)
        step += 1

        log_step(step, total_steps, "Repacking %s" % output_jar)
        if os.path.isfile(output_jar):
            os.remove(output_jar)

        with zipfile.ZipFile(output_jar, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            for root, _dirs, files in os.walk(work_dir):
                for name in files:
                    full = os.path.join(root, name)
                    arc = os.path.relpath(full, work_dir).replace(os.sep, "/")
                    zf.write(full, arc)

        size_kb = os.path.getsize(output_jar) / 1024.0
        log("")
        log("Done: %s (%.1f KB)" % (output_jar, size_kb))
        log("  Build mode           : %s" % mode.upper())
        log("  Game classes patched : %d" % patched_files)
        log("  String replacements  : %d" % total_replacements)
        log("  Bridge classes added : %d" % injected)

    finally:
        shutil.rmtree(work_dir, ignore_errors=True)


def default_output_name(input_jar):
    base, ext = os.path.splitext(os.path.basename(input_jar))
    if not ext:
        ext = ".jar"
    return base + "_patched_nokia" + ext


def main():
    parser = argparse.ArgumentParser(
        description="Patch J2ME game JARs: mascotcapsule -> mascot3daccel + inject Mascot3DAccel."
    )
    parser.add_argument("input_jar", help="Original game .jar (e.g. Rally Master Pro)")
    parser.add_argument(
        "-o", "--output",
        help="Output .jar path (default: <game>_patched_nokia.jar)"
    )
    parser.add_argument(
        "--dev",
        action="store_true",
        help="Dev mode: compile src/ with javac 1.3 (auto if src/ exists)"
    )
    parser.add_argument(
        "--release",
        action="store_true",
        help="Release mode: use tools/mascot3daccel.jar (default without src/)"
    )
    parser.add_argument(
        "--classes-dir",
        help="Use existing compiled classes (skip javac / bridge jar)"
    )
    parser.add_argument(
        "--keep-capsule",
        action="store_true",
        help="Do not delete com/mascotcapsule from the JAR (debug only)"
    )
    args = parser.parse_args()

    output = args.output
    if not output:
        output = default_output_name(args.input_jar)

    mode = resolve_mode(args.dev, args.release)

    log("Mascot3DAccel JAR Patcher")
    log("Copyright (c) 2026 Konstantin Zverev")
    log("=" * 40)
    patch_jar(args.input_jar, output, mode, args.classes_dir, args.keep_capsule)


if __name__ == "__main__":
    main()
