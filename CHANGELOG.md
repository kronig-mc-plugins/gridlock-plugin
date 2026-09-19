# Changelog

Alle wichtigen Änderungen an GridLock. Format angelehnt an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionierung nach [SemVer](https://semver.org/lang/de/).

## [1.6.1] – 2026-09-20

### Behoben
- **Eigene Scoreboards in Tab-Liste und unter dem Namen wurden nicht angezeigt** (z. B. ein Death-Counter per `/scoreboard objectives setdisplay list deaths`). Ursache: Jeder Spieler hat wegen des GridLock-Fensters ein eigenes Scoreboard. Die Anzeigen des Server-Scoreboards werden jetzt dorthin gespiegelt.
- Ist die `data.json` unlesbar, wird sie jetzt als `data.json.kaputt-…` gesichert und der Fehler deutlich geloggt, statt die Runde stillschweigend auf „Lobby“ zurückzusetzen (wodurch Inventare geleert würden).

### Geändert
- Die Border-Wand reicht in der Oberwelt und im End jetzt **immer bis zur Oberfläche**. Wer unten in einem Loch steht, sieht die Wand bis ganz nach oben, und von oben erkennt man am Lochrand, wo die Border verläuft. Im Nether bleibt sie auf Spielerhöhe, weil die „Oberfläche“ dort die Bedrock-Decke ist.

## [1.6.0] – 2026-09-20

### Hinzugefügt
- **Harter Stopp an der Border über eine persönliche Vanilla-Border.** Jeder Spieler bekommt eine eigene, riesige Worldborder, deren eine Seite exakt auf der Feldkante liegt, auf die er gerade zuläuft (an Ecken zwei Seiten). Der Client prallt selbst ab, ohne Zurücksetzen, ohne Blöcke und ohne Entities. Das Feld bleibt frei formbar, weil der Rest der Border tausende Blöcke entfernt ist.
- Die Wand wird schon bis zu 3 Blöcke vor der Kante gesetzt und berücksichtigt auch Schwung ohne Tastendruck (Sprung, Eis, Rückstoß).
- Die Wand erscheint im Vanilla-Look in Rot.
- Einstellung `border.hard-stop` (Standard: an). Aus = der Server setzt wie bisher zurück.

### Bekannte Einschränkung
- Minecraft lässt hinter der eigenen Worldborder nicht abbauen oder platzieren. Deshalb ist die Wand nur aktiv, solange man auf die Kante zuläuft. Wer stehen bleibt oder sich wegbewegt, kann außerhalb normal abbauen.

## [1.5.2] – 2026-09-20

### Entfernt
- **Shulker-Pfosten an der Border (aus 1.5.0) komplett entfernt.** Shulker rasten in Minecraft immer auf die Blockmitte ein. Die Pfosten standen deshalb nicht auf der Grenze, sondern mitten im Feld, und blockierten Laufen, Klicken und Platzieren. An der Border hält wieder der Server den Spieler auf.

### Behoben
- Alle übrig gebliebenen Pfosten werden beim Serverstart und beim Laden jedes Chunks automatisch gelöscht. Zusätzlich gibt es `/gl cleanup` (entfernt sie sofort und speichert die Welten).
- Die Pfosten ließen sich nicht mit `/kill` entfernen, weil das Plugin jeden Schaden an ihnen abbrach.
- Hinweis: Eine zwischenzeitlich gebaute Datei `GridLock-1.4.0.jar` enthielt bereits den Shulker-Code. Bitte nur noch 1.5.2 oder neuer verwenden.

## [1.5.1] – 2026-09-19

### Hinzugefügt
- Das Aussehen der Border lässt sich jetzt ingame feinjustieren: `border.glow-height` (Höhe des Schimmers), `border.glow-strength` (Deckkraft) und `border.line-width` (Dicke der Linien) – im GUI unter Border oder per `/gl config`.

### Geändert
- Der Schimmer ist dezenter voreingestellt (28 % Deckkraft, 2,6 Blöcke hoch) und blendet nach oben aus, näher an der Optik der Vanilla-Border.

## [1.5.0] – 2026-09-19

### Hinzugefügt
- **Harter Stopp an der Border, ohne Zurücksetzen und ohne Barrier-Blöcke**: Auf der Grenzfläche um jeden Spieler steht ein Gitter aus winzigen, unsichtbaren Shulkern (1/16 Block). Shulker sind die einzigen Mobs, mit denen der Client selbst kollidiert, also stoppt dein eigenes Minecraft dich sofort und hart. Die Pfosten sind zu klein, um Klicks zu stören: Abbauen, Bauen und Aufsammeln außerhalb gehen weiter.
- Die Pfosten stehen in vier Höhen bis 2,7 Blöcke über den Füßen und wandern mit, auch im Sprung. Damit kommt man nicht mehr über die Kante springen.
- Der Server-Stopp bleibt nur noch als Notfall-Sicherung (Lag, Fahrzeuge, Kolben).

### Geändert
- **Border wird jetzt auf Spielerhöhe gezeichnet**: Wer sich runtergräbt oder in einer Höhle steht, sieht die Border dort, statt nur an der Oberfläche über sich.

## [1.4.0] – 2026-09-19

### Hinzugefügt
- **Neue Lobby**: eine schwebende Insel mit leuchtend rotem Raster (Grasfeld in der Mitte, dunkle „gesperrte“ Kacheln außen), Unterseite mit Erzen und Tropfstein. Dazu kommen vier Deko-Inseln (Wald, Wüste, Nether mit Portal, End mit Drachenei) und Nachtstimmung.
- In der Mitte ein **rotierender Grasblock im roten Glaskäfig**, darüber der Schriftzug **GRIDLOCK**.
- **Abstimm-Säulen**: für jeden Spawn eine Säule mit schwebendem Icon, Schwierigkeit und Live-Stimmen. Klick auf die Säule stimmt ab.
- **Bereit-System**: Die Challenge startet erst, wenn alle online Spieler bereit sind (grüne Säule, Hotbar-Item, Menü oder `/gl ready`). Admins können mit `/gl forcestart` sofort starten.
- Info-Tafel „So geht's“ und Status-Tafel (Spieler, Bereit, Favorit) als Hologramme.
- **Border-Look wie ein Laser-Vorhang**: halbdurchsichtiger Schimmer direkt über dem Boden, der nach oben ausblendet, und dünne scharfe Linien am Geländeprofil (Oberkante, Boden, Stufen, Ecken). Höheres Gelände außerhalb wird rötlich eingefärbt.
- Beim Erweitern färbt sich die **komplette Border** der Welt rot → orange → gelb, blitzt beim neuen Block kurz **grün** auf und wird dann wieder rot.

### Geändert
- **Nether-Portal**: Bei der Ankunft wird das ganze Portal plus ein Ring von einem Block drumherum freigeschaltet (statt nur ein Block davor).
- **Border-Stopp ohne Barrieren**: Die unsichtbaren Barrier-Blöcke aus 1.2.0 sind entfernt. Der Server hält den Spieler an der Kante fest, Klicken, Abbauen und Aufsammeln außerhalb funktionieren wieder uneingeschränkt.
- `/gl start` heißt jetzt „bereit machen“. Von der Konsole aus erzwingt es den Start.

### Entfernt
- Einstellung `border.solid` (feste Wand über Barrieren).

### Behoben
- Das Plugin startete nicht, wenn die Lobby beim Laden Hologramm-Texte füllen wollte, bevor der Spielablauf bereit war.

## [1.3.2] – 2026-09-19

### Geändert
- Zeit-Level gibt es nur noch, solange mindestens ein Spieler online ist. Der Team-Pool wächst also nicht mehr auf einem leeren Server, auch wenn „Timer läuft ohne Spieler“ an ist. Der Challenge-Timer selbst zählt mit dieser Einstellung weiterhin durch.

## [1.3.1] – 2026-09-19

### Hinzugefügt
- Das Scoreboard zeigt in den spielerbasierten Bezahlmodi (Jeder für sich / Überweisen) neben jedem Spieler auch dessen Level an. Im Team-Pool-Modus bleibt es bei der Spielzeit, weil dort alle dieselben Level haben.

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
