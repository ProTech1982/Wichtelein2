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

## Prüfung
- JVM-Tests des Kerns laufen lokal (kotlinc) und in CI. Emulator-Tests nur in CI.

## Offen
- Siehe Antwort an den Nutzer (Ehrliche Liste der Vereinfachungen).
