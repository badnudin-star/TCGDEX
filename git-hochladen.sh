#!/usr/bin/env bash
# TCG Sammlung - auf GitHub hochladen (Linux / macOS / Git Bash)
# Start:  bash git-hochladen.sh
set -u
cd "$(dirname "$0")" || exit 1

echo "================================================"
echo "  TCG Sammlung - auf GitHub hochladen"
echo "================================================"
echo

if ! command -v git >/dev/null 2>&1; then
  echo "FEHLER: Git ist nicht installiert."
  echo "Installieren: sudo apt install git   (Fedora: sudo dnf install git)"
  exit 1
fi
if [ ! -f app/build.gradle.kts ]; then
  echo "FEHLER: Dieses Skript muss im Projektordner liegen (neben dem Ordner \"app\")."
  exit 1
fi
if [ ! -f .gitignore ]; then
  echo "FEHLER: Die Datei .gitignore fehlt. Bitte aus git-setup.zip in diesen Ordner kopieren."
  exit 1
fi

if [ ! -d .git ]; then
  echo "[1/5] Lege lokales Repository an ..."
  git init -q
  git symbolic-ref HEAD refs/heads/main
else
  echo "[1/5] Lokales Repository gefunden."
fi

if [ -z "$(git config user.name)" ]; then
  read -r -p "Dein Name für die Commits: " GN
  git config user.name "$GN"
fi
if [ -z "$(git config user.email)" ]; then
  read -r -p "Deine E-Mail (die bei GitHub hinterlegte): " GE
  git config user.email "$GE"
fi

echo "[2/5] Sammle Dateien ..."
git add -A

echo "[3/5] Sicherheitscheck ..."
BAD=$(git ls-files | grep -iE 'keystore\.properties$|\.jks$|\.keystore$|\.p12$|^local\.properties$|\.apk$|\.aab$' || true)
if [ -n "$BAD" ]; then
  echo
  echo "STOPP: Diese geheimen oder unerwünschten Dateien würden hochgeladen:"
  echo "$BAD"
  git reset -q
  echo "Nichts wurde hochgeladen. Prüfe die Datei .gitignore."
  exit 1
fi
BIG=""
while IFS= read -r -d '' f; do
  if [ "$(wc -c < "$f")" -gt 52428800 ]; then BIG="$BIG$f"$'\n'; fi
done < <(git ls-files -z)
if [ -n "$BIG" ]; then
  echo
  echo "STOPP: Diese Dateien sind größer als 50 MB (GitHub erlaubt max. 100 MB):"
  printf '%s' "$BIG"
  git reset -q
  echo "Nichts wurde hochgeladen. Trage die Dateien in .gitignore ein."
  exit 1
fi
CNT=$(git ls-files | wc -l | tr -d ' ')
echo "    OK - $CNT Dateien werden versioniert, keine Geheimnisse gefunden."

echo "[4/5] Speichere Version ..."
if ! git diff --cached --quiet; then
  read -r -p "Kurze Beschreibung der Änderung (Enter = Update): " MSG
  git commit -q -m "${MSG:-Update}"
  echo "    Gespeichert."
else
  echo "    Keine neuen Änderungen zum Speichern."
fi

echo "[5/5] Lade auf GitHub hoch ..."
if ! git remote get-url origin >/dev/null 2>&1; then
  echo
  echo "Lege zuerst auf https://github.com/new ein LEERES Repository an"
  echo "(ohne README, ohne .gitignore, ohne Lizenz)."
  read -r -p "Repository-URL einfügen (https://github.com/NAME/REPO.git): " REPO
  if [ -z "$REPO" ]; then
    echo "Keine URL eingegeben - Upload übersprungen. Starte das Skript später erneut."
    exit 0
  fi
  git remote add origin "$REPO"
fi
if ! git push -u origin main; then
  echo
  echo "FEHLER beim Hochladen. Häufige Ursachen:"
  echo " - Als Passwort wurde das GitHub-Passwort statt eines Tokens eingegeben (siehe Anleitung)"
  echo " - Dem Token fehlt die Berechtigung \"workflow\" (nötig wegen .github/workflows)"
  echo " - Falsche URL: git remote set-url origin NEUE-URL"
  echo " - Repository war nicht leer: auf GitHub ein neues leeres anlegen"
  exit 1
fi
echo
echo "FERTIG. Nächste Schritte:"
echo " 1. Die URL NAME/REPO in SyncRepository.kt und build_catalog.py anpassen (siehe README)"
echo " 2. Auf GitHub: Actions - Build Catalog Pipeline - Run workflow"
