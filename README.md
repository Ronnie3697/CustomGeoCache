# CustomGeoCache

Moderní Android klient pro geocaching.com. Postavené v Jetpack Compose + Material 3, mapa přes
MapLibre GL Native s vector tiles z [Mapy.com](https://developer.mapy.com).

> Inspirováno open-source projektem [c:geo](https://github.com/cgeo/cgeo) — login flow byl
> portován z jejich Java implementace, zbytek je psaný od nuly.

## Features (MVP)

- Setup wizard na první spuštění (výběr složky přes SAF, Mapy.com API klíč, login na geocaching.com)
- Vector mapa Mapy.com (Basic / Outdoor + raster Aerial / Winter)
- Vyhledávání kešek v okolí
- Detail kešky (popis, hint, attributy, posledních 10 logů)
- Offline cache databáze (Room)

## Build

1. Naklonuj repozitář.
2. Zkopíruj `local.properties.example` jako `local.properties` a doplň `sdk.dir`.
3. Volitelně doplň `MAPY_CZ_API_KEY=...` do `local.properties`, pokud chceš mít klíč
   zabudovaný v buildu (uživatelé ho potom nemusí zadávat). Bez něj se appka klíče
   zeptá v setup wizardu.
4. Otevři v Android Studio (Ladybug+) a syncni Gradle.
5. Spusť na zařízení s Androidem 8.0+ (API 26+).

## Mapy.com API klíč

Klíč si zaregistruj zdarma na [developer.mapy.com](https://developer.mapy.com) — máš
250 000 dlaždic / měsíc. Klíč můžeš uživateli přednastavit přes `local.properties`
a nebo ho nechat zadat v setup wizardu / nastavení.

## Stack

- Kotlin 2.0 + Jetpack Compose + Material 3
- Navigation Compose
- DataStore Preferences (settings)
- Room (offline DB)
- OkHttp + Jsoup (login na geocaching.com)
- Retrofit + Moshi (REST volání)
- MapLibre GL Native 11.x (mapa)
- Coroutines + Flow
