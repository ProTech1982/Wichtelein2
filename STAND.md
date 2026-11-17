# Stand (Arbeitsmerker für Agenten, aktueller Zustand wird ersetzt)

Auftrag: Wichtelein2 als komplett neues Projekt (Version 0.1.0), 3D-Wichtel + Besen aus Blender, Tür, Wegfindung, 3 Aktionen,
Farben, Design, Update. Details siehe README.md.

## Version 0.1.0
- Kern (`core/`): `Plan` (Foto, Ansicht seitlich/oben, Horizont, Tür, Gegenstände, Schritte), `NavGrid` (A* mit Standflächen,
  Gras-Kosten, Horizont), `Simulator` (30 Bilder/s: Tür auf, herauskommen, Schritte, wieder hinein; Schleichen/Rennen/Fegen/Erschrecken/Ducken),
  `Recolor` (Maskenfarben), `EditOps` (Pfeile, Größe, Drehen, Griff, Treffer), `PlanJson`, `UpdateCheck`.
- Bilder aus Blender (`blender/`): 7 Bewegungen x 2 Ansichten (Elevation 10° / 68°) x 8 Richtungen, Farbmasken, Tür (3 Formen, 11 Winkel),
  2 Innenräume, Symbol.
- Wichtel-Höhe: Tür-Höhe x `wichtelRatio`; seitlich skaliert mit (y - Horizont).
- Verdeckung: Gegenstand-Ausschnitt aus dem Foto wird über den Wichtel gezeichnet, wenn er dahinter steht (seitlich: Fuß über Unterkante;
  oben: nur Gegenstände mit `hideTop`, wenn der Fuß darin liegt).
- Video: `Exporter` (MediaCodec Surface, eigene Zeitstempel), Speichern in Filme/Wichtelein2.

## Prüfung (Stand 17.11.2026, Zweig v0.1.0-entwicklung)
- 46 JVM-Tests des Kerns (lokal mit kotlinc und in CI) und 22 Emulator-Tests (CI, API 30): Rendern aller Bewegungen/Richtungen/Ansichten, Tür (3 Formen, Angeln, Aufschlag, Innenräume),
  Verdeckung, Umfärben, Video-Export (MP4 wird mit MediaMetadataRetriever geprüft), Abbruch, Bedienung (Pfeile, Ziehen, Chips, Einstellungen, Update-Fenster), Kontaktbögen des ganzen Ablaufs.
- Compose-Test-Falle: `delay` in `LaunchedEffect` läuft in Tests mit virtueller Uhr; beim Warten auf das Autospeichern `rule.mainClock.advanceTimeBy(...)` aufrufen.
- Nicht geprüft: echtes Handy (Encoder-Verhalten, Kamera-App), Update-Download (es gibt noch kein Release).

## Offen
- Release-APK: Secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` im Repo setzen (`tools/secrets-setzen.ps1`), dann `gh workflow run android.yml`.
- Vereinfachungen: vorgerenderte Sprites (8 Richtungen, 2 Kamerawinkel) statt Echtzeit-3D; Verdeckung durch markierte Ovale/Rechtecke (keine Objekterkennung);
  Tür-Angeln/Aufschlag stellt der Nutzer ein (keine Erkennung im Foto); Hände am Besen per Mini-IK (bei starkem Schwung bis ca. 10 cm daneben); Leiter nur Hindernis.
