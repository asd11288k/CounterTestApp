#!/usr/bin/env python3
"""Apply the KM product identity to the pinned Operit Android source tree.

This is a build-time overlay: upstream source remains an unmodified submodule.
"""
from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path

repo_root = Path(__file__).resolve().parents[1]
operit = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else repo_root / "vendor" / "operit"
gradle = operit / "app" / "build.gradle.kts"
strings_root = operit / "app" / "src" / "main" / "res"
icon_source = repo_root / "app" / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.jpg"

if not gradle.is_file():
    raise SystemExit(f"Operit Gradle file missing: {gradle}")
if not icon_source.is_file():
    raise SystemExit(f"User-provided KM launcher image missing: {icon_source}")

text = gradle.read_text(encoding="utf-8")
text, app_id_count = re.subn(
    r'(?m)^(\s*applicationId\s*=\s*)"com\.ai\.assistance\.operit"(\s*)$',
    r'\1"com.km.agent.mobile"\2',
    text,
    count=1,
)
if app_id_count != 1:
    raise SystemExit(f"Expected exactly one upstream applicationId declaration; changed {app_id_count}")

clone_block = re.search(r'(?ms)^\s*create\("clone"\)\s*\{.*?^\s*\}', text)
if not clone_block:
    raise SystemExit("Upstream clone build type was not found")
block = clone_block.group(0)
block_new, suffix_count = re.subn(
    r'applicationIdSuffix\s*=\s*"\.clone"',
    'applicationIdSuffix = ""',
    block,
    count=1,
)
if suffix_count != 1:
    raise SystemExit("Could not clear the clone applicationId suffix")
block_new, label_count = re.subn(
    r'resValue\("string",\s*"app_name",\s*"[^"]*"\)',
    'resValue("string", "app_name", "km")',
    block_new,
    count=1,
)
if label_count != 1:
    raise SystemExit("Could not set the clone application label")
text = text[:clone_block.start()] + block_new + text[clone_block.end():]
gradle.write_text(text, encoding="utf-8")

# Keep the launcher label consistent in every packaged locale.
for strings_file in strings_root.glob("values*/strings.xml"):
    source = strings_file.read_text(encoding="utf-8")
    updated, count = re.subn(
        r'(<string\s+name="app_name"[^>]*>).*?(</string>)',
        r'\1km\2',
        source,
        count=1,
        flags=re.DOTALL,
    )
    if count:
        strings_file.write_text(updated, encoding="utf-8")

# Use the original image supplied by the user, not an online substitute.
drawable_dir = strings_root / "drawable-nodpi"
drawable_dir.mkdir(parents=True, exist_ok=True)
shutil.copy2(icon_source, drawable_dir / "km_launcher.jpg")
foreground = strings_root / "drawable" / "ic_launcher_simple_foreground.xml"
foreground.write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<bitmap xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    android:src="@drawable/km_launcher"\n'
    '    android:gravity="fill" />\n',
    encoding="utf-8",
)

# Fail closed if the overlay did not produce the intended package identity.
assert 'applicationId = "com.km.agent.mobile"' in gradle.read_text(encoding="utf-8")
assert (drawable_dir / "km_launcher.jpg").stat().st_size == icon_source.stat().st_size
print("Applied KM package name, launcher label, and user-provided adaptive icon.")
