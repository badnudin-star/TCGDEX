#!/usr/bin/env bash
# Meldet Klassen/Objekte, die nirgends referenziert werden (tote Code-Dateien).
# Hauptkomponenten, die nur über das AndroidManifest bzw. den Launcher starten,
# werden nicht als verwaist gemeldet.
set -u

SRC=app/src/main
BODY='(@[A-Za-z]+ )*(internal |public )?((data|sealed|enum|abstract|open) )*(class|object|interface|fun) +(<[^>]*> *)?([A-Za-z0-9_]+\.)?'

# Nur über das Manifest referenziert (Application/Activity) – kein toter Code.
MANIFEST_ONLY=("MainActivity" "TCGApp")

is_manifest_only() {
  local name="$1"
  local entry
  for entry in "${MANIFEST_ONLY[@]}"; do
    if [ "$entry" = "$name" ]; then
      return 0
    fi
  done
  return 1
}

find "$SRC" -name '*.kt' | while read -r f; do
  grep -E "^${BODY}[A-Za-z0-9_]+" "$f" \
  | sed -E "s/^${BODY}([A-Za-z0-9_]+).*/\8/" | sort -u | while read -r name; do
      [ -z "$name" ] && continue
      if is_manifest_only "$name"; then
        continue
      fi
      uses=$(grep -rnw --include='*.kt' -e "$name" "$SRC" | grep -v -E ":[0-9]+:${BODY}${name}\b" | wc -l)
      [ "$uses" -eq 0 ] && echo "UNREFERENZIERT: $name ($f)"
    done
done | sort
