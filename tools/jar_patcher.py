#!/usr/bin/env python3
# Copyright (c) 2026 Konstantin Zverev. All rights reserved.
#
# AOT static patcher: injects Mascot3DAccel (JSR-184) into Sony Ericsson J2ME game JARs.
# Replaces com/mascotcapsule string references with com/mascot3daccel (same byte length).

import argparse
import glob
import os
import shutil
import sys
import tempfile
import zipfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

OLD_PKG_SLASH = b"com/mascotcapsule"
NEW_PKG_SLASH = b"com/mascot3daccel"
OLD_PKG_NAME = b"mascotcapsule"
NEW_PKG_NAME = b"mascot3daccel"

REPLACEMENTS = (
    (OLD_PKG_SLASH, NEW_PKG_SLASH),
    (OLD_PKG_NAME, NEW_PKG_NAME),
)


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


def find_classes_source(explicit_dir):
    if explicit_dir:
        path = os.path.abspath(explicit_dir)
        accel = os.path.join(path, "com", "mascot3daccel")
        if os.path.isdir(accel):
            return path
        if os.path.basename(path) == "mascot3daccel" and os.path.isdir(path):
            return os.path.dirname(os.path.dirname(path))
        raise SystemExit("Classes dir not found: %s" % path)

    patterns = [
        os.path.join(REPO_ROOT, "build", "*", "compiled"),
        os.path.join(REPO_ROOT, "dist", "*"),
    ]

    for pattern in patterns:
        for base in sorted(glob.glob(pattern), reverse=True):
            accel = os.path.join(base, "com", "mascot3daccel")
            if os.path.isdir(accel):
                log("  Using compiled classes: %s" % base)
                return base

    for pattern in glob.glob(os.path.join(REPO_ROOT, "dist", "*", "Mascot3DAccel.jar")):
        log("  Extracting bridge classes from: %s" % pattern)
        tmp = tempfile.mkdtemp(prefix="mascot3daccel_classes_")
        with zipfile.ZipFile(pattern, "r") as zf:
            for name in zf.namelist():
                if name.startswith("com/mascot3daccel/") and name.endswith(".class"):
                    dest = os.path.join(tmp, name.replace("/", os.sep))
                    parent = os.path.dirname(dest)
                    if not os.path.isdir(parent):
                        os.makedirs(parent)
                    with zf.open(name) as src, open(dest, "wb") as out:
                        out.write(src.read())
        return tmp

    raise SystemExit(
        "Cannot find Mascot3DAccel .class files.\n"
        "Build the project first (NetBeans / ant jar) or pass --classes-dir."
    )


def remove_tree(path):
    if os.path.isdir(path):
        shutil.rmtree(path)


def copy_bridge_classes(src_root, dest_root):
    src_accel = os.path.join(src_root, "com", "mascot3daccel")
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


def patch_jar(input_jar, output_jar, classes_dir, keep_capsule):
    if not os.path.isfile(input_jar):
        raise SystemExit("Input JAR not found: %s" % input_jar)

    work_dir = tempfile.mkdtemp(prefix="mascot3daccel_patch_")
    extracted_from_jar = False

    try:
        log_step(1, 6, "Unpacking %s" % input_jar)
        with zipfile.ZipFile(input_jar, "r") as zf:
            zf.extractall(work_dir)

        log_step(2, 6, "Scanning .class files for mascotcapsule references")
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

        if not keep_capsule:
            old_pkg = os.path.join(work_dir, "com", "mascotcapsule")
            if os.path.isdir(old_pkg):
                log_step(3, 6, "Removing original com/mascotcapsule")
                shutil.rmtree(old_pkg)
            else:
                log_step(3, 6, "No com/mascotcapsule in JAR (skip removal)")
        else:
            log_step(3, 6, "Keeping original com/mascotcapsule (--keep-capsule)")

        log_step(4, 6, "Locating Mascot3DAccel bridge classes")
        bridge_root = find_classes_source(classes_dir)
        if bridge_root != classes_dir and classes_dir is None:
            if "mascot3daccel_classes_" in bridge_root:
                extracted_from_jar = True

        log_step(5, 6, "Injecting com/mascot3daccel")
        injected = copy_bridge_classes(bridge_root, work_dir)
        log("  Injected %d bridge .class files" % injected)

        log_step(6, 6, "Repacking %s" % output_jar)
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
        log("  Game classes patched : %d" % patched_files)
        log("  String replacements  : %d" % total_replacements)
        log("  Bridge classes added : %d" % injected)

    finally:
        shutil.rmtree(work_dir, ignore_errors=True)
        if extracted_from_jar:
            shutil.rmtree(bridge_root, ignore_errors=True)


def default_output_name(input_jar):
    base, ext = os.path.splitext(os.path.basename(input_jar))
    if not ext:
        ext = ".jar"
    return base + "_patched_nokia" + ext


def main():
    parser = argparse.ArgumentParser(
        description="Patch a J2ME game JAR: mascotcapsule -> mascot3daccel + inject Mascot3DAccel bridge."
    )
    parser.add_argument("input_jar", help="Original game .jar (e.g. Rally Master Pro)")
    parser.add_argument(
        "-o", "--output",
        help="Output .jar path (default: <game>_patched_nokia.jar in cwd)"
    )
    parser.add_argument(
        "--classes-dir",
        help="Directory containing com/mascot3daccel/ (default: auto-detect from build/ or dist/)"
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

    log("Mascot3DAccel JAR Patcher")
    log("=" * 40)
    patch_jar(args.input_jar, output, args.classes_dir, args.keep_capsule)


if __name__ == "__main__":
    main()
