#!/usr/bin/env python3
"""Static accessibility guardrails for the Android Compose UI."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "app/src/main/java"
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"

errors: list[str] = []
notes: list[str] = []

manifest = MANIFEST.read_text(encoding="utf-8")
for attr in ("icon", "roundIcon"):
    if not re.search(rf'android:{attr}="@mipmap/ic_launcher(?:_round)?"', manifest):
        errors.append(f"Manifest: android:{attr} fehlt oder verweist nicht auf das Launcher-Icon")

kotlin_files = sorted(JAVA_ROOT.rglob("*.kt"))
for path in kotlin_files:
    source = path.read_text(encoding="utf-8")
    rel = path.relative_to(ROOT).as_posix()
    for match in re.finditer(r'Text\s*\(\s*"[^"\n]*[A-Za-zÄÖÜäöü][^"\n]*"', source):
        line = source.count("\n", 0, match.start()) + 1
        errors.append(f"{rel}:{line}: fest kodierter UI-Text in Text(...)")

    # Find each TcgImage call with balanced parentheses; nested stringResource calls are valid.
    if path.name != "TcgImage.kt":
        for match in re.finditer(r'\bTcgImage\s*\(', source):
            start = match.end()
            depth = 1
            i = start
            in_string = False
            escaped = False
            while i < len(source) and depth:
                ch = source[i]
                if in_string:
                    if escaped:
                        escaped = False
                    elif ch == "\\":
                        escaped = True
                    elif ch == '"':
                        in_string = False
                else:
                    if ch == '"':
                        in_string = True
                    elif ch == '(':
                        depth += 1
                    elif ch == ')':
                        depth -= 1
                i += 1
            call = source[start:i - 1]
            line = source.count("\n", 0, match.start()) + 1
            if "contentDescription" not in call:
                errors.append(f"{rel}:{line}: TcgImage ohne contentDescription")

# Null descriptions are allowed only for decorative icons whose meaning is already conveyed by adjacent text.
allowed_null_icons = {
    ("WishlistScreen.kt", "Edit"): 2,
    ("WishlistScreen.kt", "ShoppingCart"): 1,
    ("PokemonDetailScreen.kt", "Star"): 1,
    ("PokemonDetailScreen.kt", "Add"): 1,
    ("SetDetailScreen.kt", "Check"): 1,
}
found_null_icons: dict[tuple[str, str], int] = {}
for path in kotlin_files:
    source = path.read_text(encoding="utf-8")
    rel = path.relative_to(ROOT).as_posix()
    for match in re.finditer(r'Icon\s*\(\s*Icons\.Default\.(\w+)\s*,\s*contentDescription\s*=\s*null', source):
        icon_name = match.group(1)
        key = (path.name, icon_name)
        found_null_icons[key] = found_null_icons.get(key, 0) + 1
        line = source.count("\n", 0, match.start()) + 1
        if key not in allowed_null_icons:
            errors.append(f"{rel}:{line}: contentDescription=null nicht in der Dekorativ-Icon-Allowlist")
        else:
            notes.append(f"DEKORATIV: {rel}:{line} Icon {icon_name} (Textlabel daneben vorhanden)")

for key, expected in allowed_null_icons.items():
    actual = found_null_icons.get(key, 0)
    if actual != expected:
        errors.append(f"Allowlist {key[0]} / {key[1]}: erwartet {expected}, gefunden {actual}")

if errors:
    print("ACCESSIBILITY-CHECK: FEHLER")
    for item in errors:
        print(f"FEHLER: {item}")
    sys.exit(1)

print("ACCESSIBILITY-CHECK: OK")
print(f"Kotlin-Dateien geprüft: {len(kotlin_files)}")
print("Launcher-Icons: icon und roundIcon vorhanden")
print("Fest kodierte Text(...)-Beschriftungen: 0")
print("TcgImage-Aufrufe ohne contentDescription: 0")
print("contentDescription=null nur für freigegebene dekorative Icons:")
for note in notes:
    print(f"  {note}")
