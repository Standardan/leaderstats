# LeaderStats

Tracks player stats — kills, deaths, mob kills, blocks mined, playtime — with in-game
leaderboards, **floating leaderboard holograms**, and **PlaceholderAPI** placeholders.
For Paper 1.21+.

## Download

**[Download the latest release »](https://github.com/Standardan/leaderstats/releases/latest)**

Drop the `.jar` into your server's `plugins/` folder and restart. Requires Paper 1.21+ (Java 21).
PlaceholderAPI is optional — placeholders activate automatically if it's installed.

## Features

- Tracks **kills, deaths, mob kills, blocks mined, playtime**, persisted in SQLite
- `/stats` — see your own stats; `/stats top <stat>` — see the leaderboard
- **Hologram leaderboards** — `/stats hologram <stat>` drops a live top-10 board in the world
- **PlaceholderAPI support** — e.g. `%leaderstats_kills%`, `%leaderstats_top_kills_1_name%`

## Commands & permissions

| Command | Description |
|---|---|
| `/stats` | View your own stats |
| `/stats top <stat>` | View a leaderboard |
| `/stats hologram <stat>` | Place a leaderboard hologram (admin) |
| `/stats hologram remove` | Remove nearby holograms (admin) |
| `/stats reload` | Reload config (admin) |

Stat keys: `kills`, `deaths`, `mobkills`, `blocksmined`, `playtime`. Permissions:
`leaderstats.use` (default: all), `leaderstats.admin` (op).

## PlaceholderAPI

| Placeholder | Returns |
|---|---|
| `%leaderstats_<stat>%` | the player's own value |
| `%leaderstats_top_<stat>_<rank>_name%` | the name at that rank |
| `%leaderstats_top_<stat>_<rank>_value%` | the value at that rank |

## Design notes

- **Stats live in an in-memory cache** while a player is online (so increments and placeholder
  lookups are instant), flushed to SQLite on a timer and on quit.
- **Leaderboards are a periodically-rebuilt snapshot.** PlaceholderAPI resolves placeholders on
  the main thread and must return instantly, so it reads the cached snapshot — never a live query.
- **PlaceholderAPI is isolated.** All of its API lives in one class (`StatExpansion`) that's only
  loaded after confirming the plugin is installed — so LeaderStats runs fine without it.
- **Holograms use TextDisplay entities** (no holograms-plugin dependency), re-spawned from config
  on start so they never orphan.

## Building

JDK 21 + Maven. `mvn clean package` → `target/leaderstats-1.0.0.jar`.
