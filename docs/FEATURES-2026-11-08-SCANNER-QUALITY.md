# Scanner: bessere Scan-Qualität und Kartenerkennung 2026-11-08 (Runde 11)

Ziel: OCR-Kartenscan über die Kamera zuverlässiger machen — sowohl die Bildqualität
als auch die Textauswertung. Alle Änderungen ohne neue Abhängigkeiten (ML Kit
Texterkennung + CameraX bleiben, kein material-icons-extended nötig).

## Kamera/Bildqualität (`CardScannerScreen.kt`)

1. **Höhere Analyse-Auflösung**: längere Kante ~1280 px → **~1920 px**. Die Kartennummer
   ist eines der kleinsten Textelemente — mehr Pixel erkennen sie deutlich stabiler.
   Der Frame-Gate (max. eine laufende OCR-Aufgabe) hält die Framerate im Rahmen.
2. **Zoom-Steuerung (+/−, 1,0x–4,0x in 0,5er-Schritten)**: Digital-Zoom beschneidet den
   Sensor, bevor auf die Analyse-Auflösung skaliert wird — die Karte erhält also
   **echte zusätzliche Pixel**, statt nur nachträglich hochskaliert zu werden.
3. **Taschenlampe an/aus**: ML Kit braucht Licht; in dunklen Räumen macht der Torch
   kleine Nummern erst lesbar.
4. **Tippen zum Fokussieren** (Tap-to-Focus): Fokus+Belichtungsmessung auf die
   angetippte Stelle (MeteringPointFactory der PreviewView rechnet Ansichts- in
   Sensor-Koordinaten um, inkl. FILL_CENTER); ein kurzer Ring zeigt die Tipp-Position.
   Zusätzlich Hinweiszeile „Karte in den Rahmen halten · Tippen fokussiert".
5. Beim Kamera-Neustart (nach Kamerafehler) werden Torch/Zoom zurückgesetzt und beim
   Binden sofort wieder auf die neue Kamera angewendet.

## Textauswertung

6. **Größte Texte zuerst** (`sizeOrderedText`): ML Kit liefert Bounding-Boxes pro Zeile;
   die Zeilen werden nach Schrifthöhe sortiert. Der Kartenname ist das größte
   Textelement, Regel-/Angriffstext und Fußnoten sind klein — die Namenssuche
   (`extractOcrSearchTokens` nimmt die ersten Tokens) trifft damit den Kartennamen
   statt zufälligen Beifang aus dem Regeltext.
7. **OCR-Verwechslungszeichen in Nummern** (`CardNumberMatcher.kt`): ML Kit liest
   gedruckte Ziffern gelegentlich als Buchstaben. In nummernförmigen Fragmenten werden
   Lookalikes normalisiert (O/o/Q→0, I/l/i→1, Z/z→2, S/s→5, G→6, B→8, g/q→9):
   „12/1O2" → „12/102", „SV-P OO1" → „SV-P001". Schutz vor Fehlalarmen:
   - Der Set-Präfix selbst wird nie normalisiert („SWSH" bleibt „SWSH", nicht „5W5H").
   - Ein Kandidat ohne eine einzige echten Ziffer wird verworfen („I/O", „S/B").
   - Ein einziger Lookalike-Buchstabe nach Präfix wird verworfen (Setname „SVI" ≠ „SV1").
   - Brüche müssen plausibel sein: n/m mit 0 < n ≤ m („5/2" ist Rauschen).
8. **Stabilitätsbestätigung über Frames** (`ScannerSubmitGate`, neu): Eine lesbare
   Kartennummer muss jetzt in **2 aufeinanderfolgenden** Frames erkannt werden, bevor
   die Katalogsuche startet (vorher: sofort beim ersten Frame — einzelne Misreads
   konnten Fehlsuchen auslösen). Die unscharfe Namens-Suche braucht weiterhin 3
   identische Frames. Bereits gesendete Fingerprints feuern nicht erneut; ein Wechsel
   (andere Karte) startet neue Zählungen. Die Logik ist als synchronisierte, reine
   Klasse ausgelagert und vollständig getestet.

## Tests

Lokaler kotlinc-JVM-Harness: **56/56 grün** (neu: 5 OCR-Normalisierungsfälle in
`CardNumberMatcherTest`, 6 Fälle in `ScannerSubmitGateTest`; `ScannerLookupTest`
läuft weiter in der CI, da coroutines-abhängig). Der Harness hat dabei einen echten
Fehler abgefangen (Präfix-Mutation „SWSH"→„5W5H"), der vor dem Commit behoben wurde.

Keine Schema-/DAO-Änderungen; `CardScannerScreen`-Aufrufstelle (MainActivity) blieb
unverändert.
