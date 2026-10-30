@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title TCG Sammlung - auf GitHub hochladen
echo ================================================
echo   TCG Sammlung - auf GitHub hochladen
echo ================================================
echo.

where git >nul 2>nul
if errorlevel 1 (
  echo FEHLER: Git ist nicht installiert.
  echo Installieren: https://git-scm.com/download/win
  echo Danach diese Datei noch einmal starten.
  goto :ende
)
if not exist "app\build.gradle.kts" (
  echo FEHLER: Diese Datei muss im Projektordner liegen ^(neben dem Ordner "app"^).
  goto :ende
)
if not exist ".gitignore" (
  echo FEHLER: Die Datei .gitignore fehlt. Bitte aus git-setup.zip in diesen Ordner kopieren.
  goto :ende
)

if not exist ".git" (
  echo [1/5] Lege lokales Repository an ...
  git init
  git symbolic-ref HEAD refs/heads/main
) else (
  echo [1/5] Lokales Repository gefunden.
)

set "GN="
set "GE="
for /f "delims=" %%i in ('git config user.name') do set "GN=%%i"
for /f "delims=" %%i in ('git config user.email') do set "GE=%%i"
if "!GN!"=="" (
  set /p GN=Dein Name fuer die Commits: 
  git config user.name "!GN!"
)
if "!GE!"=="" (
  set /p GE=Deine E-Mail ^(die bei GitHub hinterlegte^): 
  git config user.email "!GE!"
)

echo [2/5] Sammle Dateien ...
git add -A

echo [3/5] Sicherheitscheck ...
git ls-files | findstr /i /r "keystore\.properties$ \.jks$ \.keystore$ \.p12$ ^local\.properties$ \.apk$ \.aab$" > "%TEMP%\tcg_git_check.txt"
set "BAD=0"
for %%A in ("%TEMP%\tcg_git_check.txt") do if %%~zA GTR 0 set "BAD=1"
if "!BAD!"=="1" (
  echo.
  echo STOPP: Diese geheimen oder unerwuenschten Dateien wuerden hochgeladen:
  type "%TEMP%\tcg_git_check.txt"
  git reset >nul 2>nul
  echo Nichts wurde hochgeladen. Pruefe die Datei .gitignore.
  goto :ende
)
powershell -NoProfile -Command "git ls-files | ForEach-Object { if ((Get-Item -LiteralPath $_).Length -gt 50MB) { $_ } }" > "%TEMP%\tcg_git_big.txt"
set "BIG=0"
for %%A in ("%TEMP%\tcg_git_big.txt") do if %%~zA GTR 0 set "BIG=1"
if "!BIG!"=="1" (
  echo.
  echo STOPP: Diese Dateien sind groesser als 50 MB ^(GitHub erlaubt max. 100 MB^):
  type "%TEMP%\tcg_git_big.txt"
  git reset >nul 2>nul
  echo Nichts wurde hochgeladen. Trage die Dateien in .gitignore ein.
  goto :ende
)
for /f %%c in ('git ls-files ^| find /c /v ""') do set "CNT=%%c"
echo     OK - !CNT! Dateien werden versioniert, keine Geheimnisse gefunden.

echo [4/5] Speichere Version ...
git diff --cached --quiet
if errorlevel 1 (
  set "MSG="
  set /p MSG=Kurze Beschreibung der Aenderung ^(Enter = Update^): 
  if "!MSG!"=="" set "MSG=Update"
  git commit -m "!MSG!"
) else (
  echo     Keine neuen Aenderungen zum Speichern.
)

echo [5/5] Lade auf GitHub hoch ...
git remote get-url origin >nul 2>nul
if errorlevel 1 (
  echo.
  echo Lege zuerst auf https://github.com/new ein LEERES Repository an
  echo ^(ohne README, ohne .gitignore, ohne Lizenz^).
  set "REPO="
  set /p REPO=Repository-URL einfuegen ^(https://github.com/NAME/REPO.git^): 
  if "!REPO!"=="" (
    echo Keine URL eingegeben - Upload uebersprungen. Starte die Datei spaeter erneut.
    goto :ende
  )
  git remote add origin "!REPO!"
)
git push -u origin main
if errorlevel 1 (
  echo.
  echo FEHLER beim Hochladen. Haeufige Ursachen:
  echo  - Anmeldung im Browser-Fenster abgebrochen: Datei erneut starten
  echo  - Falsche URL: git remote set-url origin NEUE-URL
  echo  - Repository war nicht leer: auf GitHub ein neues leeres anlegen
  goto :ende
)
echo.
echo FERTIG. Naechste Schritte:
echo  1. Die URL NAME/REPO in SyncRepository.kt und build_catalog.py anpassen ^(siehe README^)
echo  2. Auf GitHub: Actions - Build Catalog Pipeline - Run workflow

:ende
echo.
pause
endlocal
