# CustomGeoCache

Moderní Android klient pro **geocaching.com**, postavený od nuly v **Jetpack Compose + Material 3**. Mapy z [Mapy.com](https://developer.mapy.com) přes MapLibre GL Native, login flow a API endpointy reverzně portované z open-source [c:geo](https://github.com/cgeo/cgeo).

> Vyvíjeno jako osobní projekt — nejede přes Google Play, debug APK najdeš v [Releases](https://github.com/Ronnie3697/CustomGeoCache/releases).

## Funkce

### 🚀 Setup wizard na první spuštění
- Welcome → výběr složky přes SAF → zadání Mapy.com API klíče (s návodem) → login na geocaching.com

### 🗺 Mapa
- Mapy.com tiles ve 4 vrstvách: Basic / Outdoor / Letecká / Zimní
- GPU-accelerated pan, zoom, rotace přes MapLibre GL
- Markery kešek na mapě, barevně rozlišené dle typu (Traditional, Multi, Mystery, Earth, Event, Virtual, Wherigo)
- **„Hledat keše tady"** — stáhne keše v aktuálním viewportu z geocaching.com search v2 endpointu
- Klepnutí na marker → bottom sheet preview s rychlým náhledem + tlačítky **Detail / Naviguj / Log**

### 📄 Detail keše
- Stažení kompletního detailu z geocaching.com
- Popis (HTML rendering)
- **Hint v ROT13** s klepnutím odhalit
- Atributy, owner, D/T/size
- Posledních 25 logů (autor, datum, typ, text)
- Tlačítko **„Otevřít v prohlížeči"** jako fallback

### 🧭 Kompas
- Šipka ukazující k aktivní keši (Rotation Vector sensor)
- Reálná vzdálenost (Haversine) + bearing v stupních
- Animovaná rotace, real-time fused location

### ✏️ Logování
- Typ logu: Found it / DNF / Write note / Needs maintenance / Needs archived
- Pro Found it: toggle „Použít favorite point" (pro premium členy)
- POST přes geocaching.com `/api/live/v1/logs/...` endpoint s OAuth + CSRF tokeny

### ⚙️ Nastavení
- Změna Mapy.com API klíče
- Odhlášení / přihlášení na geocaching.com
- Změna datové složky
- Verze appky

## Stack

| Vrstva | Knihovna |
|---|---|
| UI | Jetpack Compose 2025.04 + Material 3 1.3.2 |
| Navigation | Navigation Compose 2.8.9 |
| State | Coroutines + Flow + DataStore Preferences |
| DB | Room 2.6.1 |
| HTTP | OkHttp 4.12 + Jsoup 1.18 + Retrofit 2.11 + Moshi 1.15 |
| Mapa | MapLibre GL Native 11.8.0 |
| Sensors | Android SensorManager + Fused Location Provider |
| Min SDK | 26 (Android 8) |
| Target SDK | 35 (Android 15) |

## Build

1. Naklonuj repozitář.
2. Zkopíruj `local.properties.example` jako `local.properties`, doplň `sdk.dir`.
3. Volitelně doplň `MAPY_CZ_API_KEY=...` pro build-time klíč; jinak se appka klíče zeptá v setup wizardu.
4. Otevři v Android Studio Ladybug+, sync Gradle.
5. Run na zařízení s Androidem 8.0+.

## Mapy.com API klíč

Zaregistruj zdarma na [developer.mapy.com](https://developer.mapy.com) — 250 000 dlaždic / měsíc. Klíč se ukládá lokálně v zašifrovaném DataStore na zařízení uživatele.

## Geocaching.com API

Login a všechny endpointy jsou **reverzně analyzované** z webové verze geocaching.com — Groundspeak nemá oficiální Premium API. Workflow:

1. Username/heslo → POST `/account/signin` s `__RequestVerificationToken` → session cookie
2. Cookie session → GET `/account/oauth/token` → Bearer token (cachuje se 80 % expires_in)
3. Bearer token autorizuje:
   - GET `/api/proxy/web/search/v2` (search v bbox)
   - POST `/api/live/v1/logs/{GC}/geocacheLog` (log POST)
4. HTML scraping detail stránky `/geocache/GCxxx` přes Jsoup pro description / hint / attributes
5. POST `/seek/geocache.logbook` s `userToken` z detail page pro JSON logbook

## Struktura projektu

```
app/src/main/java/com/customgeocache/app/
├── data/
│   ├── api/         — GcSearchApi, GcDetailApi, GcLogApi, GcMappings
│   ├── auth/        — GCLogin, PersistentCookieJar
│   ├── db/          — Room (CacheEntity, LogEntity, CacheDao, AppDatabase)
│   ├── network/     — OkHttp factory, GcAuth (OAuth), DTOs
│   ├── parser/      — GcDetailParser (Jsoup + regex z c:geo)
│   ├── prefs/       — DataStore preferences
│   ├── repo/        — CacheRepository (single source of truth)
│   ├── state/       — ActiveCacheStore (sdílená aktivní keš)
│   └── AppContainer — manuální DI
├── ui/
│   ├── caches/      — Cache list, detail, ViewModel
│   ├── compass/     — Kompas + sensors + location flow
│   ├── log/         — Log entry screen + ViewModel
│   ├── map/         — MapLibre wrapper + markery + style
│   ├── nav/         — Navigation graph
│   ├── settings/    — Settings screen
│   ├── setup/       — 4-step wizard
│   └── theme/       — Material 3 theme
└── util/            — GeoUtils (Haversine, bearing), Rot13
```

## Inspirace

Projekt jsem začal jako experiment, jak rychle se dá udělat moderně vypadající geocaching klient s Compose + Material 3. Reverse-engineered API endpointy a HTML selektory jsou portované z **c:geo** (GPL-2.0). Děkuju c:geo komunitě za 15 let crowd-sourced reverse engineering geocaching.com webu — bez nich by tahle appka nebyla možná.

## License

Projekt zatím nemá licenci. Kód portovaný z c:geo (GCLogin, parsovací regexy v GcDetailParser) podléhá GPL-2.0 licenci původního projektu.
