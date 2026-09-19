# Changelog

Alle wichtigen Änderungen an GridLock. Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/).

## [1.1.0] – 2026-09-19

### Hinzugefügt
- **Server-Icon** direkt aus dem Plugin: Das GridLock-Icon erscheint in der Serverliste, ohne `server-icon.png` im Server-Ordner.
- Eigenes Icon über `plugins/GridLock/server-icon.png`. Beliebige Größen werden automatisch auf 64×64 skaliert, `/gl reload` lädt es neu.
- Neue Einstellung `motd.server-icon` (GUI → MOTD → Server-Icon).
- **Logo** (Grasblock in roter Border, transparenter Hintergrund) als Server-Icon und in der README.

## [1.0.0] – 2026-09-19

Erste Version für **Paper 26.3** (Java 25).

### Hinzugefügt
- **1x1-Challenge**: Alle starten gemeinsam auf einem 1x1-Feld (ganze Blocksäule), umgeben von einer roten Partikel-Border.
- **Erweitern mit Leveln**: Schleichen + gegen die Border drücken und halten (Fortschrittsbalken, Border färbt sich von rot zu grün) schaltet den Block in Laufrichtung frei.
- Abbauen und Bauen außerhalb des Feldes ist erlaubt, nur Hinüberlaufen nicht. Enderperlen, Chorusfrüchte, Boote, Minecarts und Reittiere werden ebenfalls aufgehalten.
- **Kostenmodell**: Grundkosten, optionaler Aufschlag alle N Blöcke, Multiplikatoren für Nether und End.
- **Bezahlmodi**: „Spieler zahlt“ (eigene Level) oder „Team-Pool“ (gemeinsame Level mit Bossbar, einstellbarer XP-Anteil, `/gl pool einzahlen`).
- **Level-Quellen**: Vanilla-XP (abschaltbar) und Zeit-Level (alle X Minuten Y Level), Start-Level.
- **Lobby** in einer leeren Void-Welt mit Plattform und Hotbar-Items (Spawn-Wahl, Menü, Start).
- **Spawn-Abstimmung** mit 12 Presets und Schwierigkeitsanzeige: Zufall, Neben einem Baum, Dschungel, Kirschblütenhain, Taiga, Ebene, Sumpf, Wüste, Schneeebene, Tafelberge, Berggipfel, Pilzinsel. Bei Gleichstand entscheidet der Zufall.
- „Neben einem Baum“ sucht einen echten Baum (Stamm ab 4 Blöcken Höhe), keine Stümpfe oder umgefallenen Stämme.
- **Dimensionen**: eigenes Feld pro Dimension. Portal-Ankünfte (Nether-Portal, End-Gateway) schalten Ankunftsblock und Portal frei. Die End-Hauptinsel hat keine Border (Radius einstellbar), im äußeren End gilt sie wieder.
- **Timer**: globaler Challenge-Timer in der Actionbar, Spielzeit pro Spieler, `/timer pause|resume|reset`, pausiert standardmäßig ohne Spieler.
- **Scoreboard** mit Status, Zeit, eigener Spielzeit, Feldgröße, Kosten, Level und Spielzeit-Rangliste.
- **Tod-Modi**: Normal (Respawn im Feld) oder Hardcore (einer stirbt → Runde verloren, alle werden Zuschauer).
- **Sieg**: Mit dem Tod des Enderdrachen stoppt der Timer und die Endzeit wird angezeigt.
- **Meilensteine** bei 10, 25, 50, 100, … Blöcken.
- **Einstellungs-GUI** (`/gl`) mit 8 Kategorien, farbigen Status-Feldern und Klicksteuerung (±1 / ±10, Umschalten, Durchschalten), Kategorie-Reset.
- **Befehle**: `/gl config [key] [wert]` mit Tab-Vervollständigung und klickbarer Übersicht, `/gl spawn`, `/gl vote`, `/gl info`, `/gl start`, `/gl reset`, `/gl reload`, `/gl unlock`.
- **Dynamische MOTD**: Logo und Status in Zeile 1, rotierende Live-Stats und Sprüche (in der Config editierbar) in Zeile 2, Stats beim Hover über die Spielerzahl.
- **Neue Runde**: `/gl reset` löscht die Welt beim nächsten Serverstart (unterstützt das Dimensions-Layout von 26.x) und kehrt zur Lobby zurück.
- Speicherung aller Rundendaten in `plugins/GridLock/data.json` (übersteht Neustarts).
