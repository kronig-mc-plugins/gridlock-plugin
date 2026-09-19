# Changelog

Alle wichtigen Änderungen an GridLock. Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/).

## [1.3.0] – 2026-09-19

### Hinzugefügt
- **Drei Bezahlmodi**:
  - *Jeder für sich*: eigene Level (wie bisher)
  - *Team-Pool*: alle teilen sich eine XP-Leiste, die unten bei jedem Spieler dieselben Team-Level zeigt. Verzaubern, Amboss und Erweitern ziehen vom Team ab. Beim Tod geht der Pool nicht verloren.
  - *Jeder für sich + Überweisen*: eigene Level, die man anderen per `/gl pay [spieler] [level]` oder über ein Menü mit Spielerköpfen schicken kann (Links 1, Rechts 5, Shift 10)
- **Admin-Befehle für alle Werte**:
  - `/gl level [spieler|pool|alle] [set|add|remove] [n]`
  - `/gl playtime [spieler] [set|add|remove] [zeit]`
  - `/timer set|add|remove [zeit]` (Zeit z. B. `1:30:00`, `45m`, `2h`)
  - `/gl unlock|lock [radius]` zum Freischalten und Sperren von Feld-Blöcken
- **Scoreboard pro Spieler ein-/ausblendbar** im Menü oder per `/gl scoreboard`
- **Neuer Spawn „Normaler Worldspawn“**: Start am Spawnpunkt, den Minecraft selbst gesetzt hat
- **Nether-Portal**: Bei der Ankunft wird zusätzlich ein Block vor dem Portal freigeschaltet, damit man ohne Level wieder zurückkommt

### Geändert
- **„Zufall“** lost jetzt einen der anderen Spawns aus (vom Baum bis zur Pilzinsel) und sagt an, welcher es geworden ist.
- **Border gilt auch im Kreativmodus**, nur Zuschauer sind ausgenommen.
- **Fahrzeuge an der Border**: Minecart, Boot und Pferd fahren weiter, der Spieler wird an der Kante abgesetzt und bleibt im Feld.
- Team-Level stehen nicht mehr in Bossbar und Scoreboard, sondern in der XP-Leiste.

### Entfernt
- `/gl pool einzahlen` und die Einstellung „Pool-Anteil“ (im Team-Pool-Modus fließt jetzt alle XP in den Pool).

### Behoben
- Die Versionsnummer in der `plugin.yml` wurde beim Bauen nicht aktualisiert (der Server zeigte 1.2.0 statt der echten Version).

## [1.2.0] – 2026-09-19

### Hinzugefügt
- **Border als Linie am Boden**: eine durchgehende, statisch leuchtende Linie (Block-Displays) auf dem Boden entlang der Feldkante statt flackernder Partikel. Beim Erweitern färbt sich die gedrückte Kante orange → gelb → grün.
- **Feste Wand**: Man läuft gegen die Border wie gegen eine Mauer und wird nicht mehr vom Server zurückgesetzt. Umgesetzt mit unsichtbaren Barrieren, die nur der jeweilige Spieler direkt an der Kante sieht.
- Erweitern wird jetzt über die Bewegungstasten erkannt (Schleichen + Richtung zur Kante), damit es auch an der festen Wand funktioniert.
- Neue Einstellungen: `border.style` (Linie am Boden / Partikel-Wand / Beides) und `border.solid` (feste Wand an/aus).

### Geändert
- Auswahl-Einstellungen akzeptieren im Befehl auch den Anfang des deutschen Namens, z. B. `/gl config border.style linie`.
- Die Partikel-Wand ist nur noch eine optionale Darstellung. Standard ist die Linie am Boden.

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
