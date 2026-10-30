#!/usr/bin/env bash
SRC=app/src/main
BODY='(@[A-Za-z]+ )*(internal |public )?((data|sealed|enum|abstract|open) )*(class|object|interface|fun) +(<[^>]*> *)?([A-Za-z0-9_]+\.)?'
find "$SRC" -name '*.kt' | while read -r f; do
  grep -E "^${BODY}[A-Za-z0-9_]+" "$f" \
  | sed -E "s/^${BODY}([A-Za-z0-9_]+).*/\8/" | sort -u | while read -r name; do
      [ -z "$name" ] && continue
      uses=$(grep -rnw --include='*.kt' -e "$name" "$SRC" | grep -v -E ":[0-9]+:${BODY}${name}\b" | wc -l)
      [ "$uses" -eq 0 ] && echo "UNREFERENZIERT: $name ($f)"
    done
done | sort
