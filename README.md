<p align="center"><img src="docs/logo.png" alt="GridLock Logo" width="200"></p>

<h1 align="center">GridLock</h1>

Multiplayer-Challenge für **Paper 26.3**: Alle starten gemeinsam auf einem **1x1-Feld**, umgeben von einer roten Border.
Wer schleichend gegen die Border drückt, kauft für Level den nächsten Block. Ziel: Enderdrache.

## Ablauf

1. **Lobby**: Beim Joinen landet man in einer leeren Void-Welt auf einer Plattform.
   - Kompass → **Spawn-Abstimmung** (Zufall, Neben Baum, Dschungel, Wüste, Pilzinsel … mit Schwierigkeit ★☆☆☆ bis ★★★★)
   - Comparator → **Menü & Einstellungen**
   - Grüner Farbstoff (nur Admins) → **Challenge starten**
2. **Start**: Der Spawn mit den meisten Stimmen wird gesucht (Gleichstand = Zufall, keine Stimmen = Zufall), danach 5-Sekunden-Countdown und alle werden auf das 1x1 teleportiert.
3. **Spielen**: Schleichen + gegen die rote Border laufen, ca. 1 Sekunde halten (Fortschrittsbalken, Border färbt sich grün) → Block frei, Level weg.
   Abbauen und Bauen **außerhalb** des Feldes ist erlaubt, nur hinüberlaufen nicht.
4. **Ende**: Enderdrache tot → gewonnen (Timer stoppt). Im Hardcore-Modus: einer stirbt → verloren.
5. **Neue Runde**: `/gl reset` löscht Oberwelt/Nether/End und fährt den Server herunter. Beim nächsten Start gibt es eine frische Welt und es geht zurück in die Lobby.

## Dimensionen

| Dimension | Regel |
|---|---|
| Oberwelt | Startet mit dem 1x1 am Spawn |
| Nether | Eigenes Feld. Beim Durchgehen eines Portals werden Ankunftsblock + Portal automatisch freigeschaltet |
| End | **Keine Border** auf der Drachen-Insel (Radius einstellbar, Standard 200). Im äußeren End gilt wieder die Border; End-Gateways schalten den Ankunftsblock frei |

Jedes Portal, das an einer neuen Stelle ankommt, öffnet dort ein neues 1x1.

## Befehle

| Befehl | Wer | Was |
|---|---|---|
| `/gl` | alle | Hauptmenü (GUI) |
| `/gl settings [kategorie]` | alle (ändern: Admin) | Einstellungs-GUI |
| `/gl config [key] [wert]` | alle (ändern: Admin) | Einstellungen per Befehl, mit Tab-Vervollständigung |
| `/gl spawn`, `/gl vote [spawn]` | alle | Spawn-Abstimmung |
| `/gl info` | alle | Feldgrößen, Kosten, Timer |
| `/gl pool einzahlen [n]` | alle | Eigene Level in den Team-Pool |
| `/timer` · `/timer pause\|resume\|reset` | alle · Admin | Timer anzeigen / steuern |
| `/gl start` · `/gl reset` · `/gl reload` · `/gl unlock` | Admin | Runde starten, neue Runde, Config neu laden, Block unter dir freischalten |

Aliase: `/gridlock`, `/gl`, `/grid`. Permission: `gridlock.admin` (Standard: OP).

## Einstellungen

Alles ist per GUI **und** per `/gl config` änderbar und wird in `plugins/GridLock/config.yml` gespeichert.

- **Erweitern**: Haltezeit, Grundkosten, Kostenaufschlag (+X Level alle N Blöcke), Meilenstein-Nachrichten
- **Level**: Bezahlmodus (Spieler zahlt / Team-Pool mit Bossbar), Vanilla-XP an/aus, Pool-Anteil in %, Zeit-Level (alle X Minuten Y Level), Start-Level
- **Timer**: Actionbar, Scoreboard, läuft ohne Spieler
- **Dimensionen**: Kosten-Multiplikator Nether/End, Radius der freien Drachen-Insel
- **Tod**: Normal / Hardcore
- **Border**: Farbe, Sichtweite, Partikeldichte, Monster dürfen ins Feld
- **Spawn**: Suchradius
- **MOTD**: an/aus, Stats in der Rotation, Wechselintervall, Hover-Infos, Server-Icon. Die Sprüche stehen unter `motd.slogans` in der config.yml (MiniMessage-Format).

## Server-Icon

Das Plugin bringt ein eigenes Icon für die Serverliste mit, eine `server-icon.png` im Server-Ordner ist nicht nötig.
Eigenes Icon: `plugins/GridLock/server-icon.png` ersetzen (beliebige Größe, wird auf 64×64 skaliert) und `/gl reload`.

## Server-Anforderungen

- Paper **26.3**, Java **25**
- Für `/gl reset` sollte der Server per Restart-Skript automatisch neu starten (die meisten Hoster machen das).

## Bauen

```bash
./gradlew build
```

Das Plugin liegt danach in `build/libs/GridLock-<version>.jar`. Gradle lädt JDK 25 automatisch, falls keins installiert ist.
