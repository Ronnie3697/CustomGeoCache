package com.customgeocache.app.data.auth

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Cookie jar pro geocaching.com — drží session v paměti a každou změnu shodí přes [onPersist] callback.
 * Konstruktor přijímá `initialSerialized` (serializovaný state z prefs).
 *
 * Formát: jeden cookie per řádek, pole oddělená `\t`:
 *     name \t value \t domain \t path \t expiresAt \t secure \t httpOnly
 */
class PersistentCookieJar(
    initialSerialized: String? = null,
    private val onPersist: (String) -> Unit = {}
) : CookieJar {

    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        initialSerialized?.lines()?.forEach { line ->
            parseLine(line)?.let { (host, cookie) ->
                store.getOrPut(host) { mutableListOf() }.add(cookie)
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val list = store.getOrPut(host) { mutableListOf() }
        for (c in cookies) {
            list.removeAll { it.name == c.name && it.path == c.path }
            list += c
        }
        onPersist(serializeAll())
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store.values.flatten().filter { it.expiresAt > now && it.matches(url) }
    }

    @Synchronized
    fun clear() {
        store.clear()
        onPersist("")
    }

    private fun serializeAll(): String =
        store.flatMap { (_, list) -> list.map(::serialize) }.joinToString("\n")

    private fun serialize(c: Cookie): String = listOf(
        c.name, c.value, c.domain, c.path, c.expiresAt.toString(),
        c.secure.toString(), c.httpOnly.toString()
    ).joinToString("\t")

    private fun parseLine(line: String): Pair<String, Cookie>? {
        if (line.isBlank()) return null
        val parts = line.split("\t")
        if (parts.size < 7) return null
        val builder = Cookie.Builder()
            .name(parts[0])
            .value(parts[1])
            .domain(parts[2])
            .path(parts[3])
            .expiresAt(parts[4].toLongOrNull() ?: return null)
        if (parts[5].toBoolean()) builder.secure()
        if (parts[6].toBoolean()) builder.httpOnly()
        return parts[2] to builder.build()
    }
}
