package com.drivelingo.core.progress

import android.content.Context

/** 저장소 추상화. 기기에서는 SharedPreferences, 테스트에서는 메모리. */
interface KeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun clear()
}

class SharedPrefsStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("drivelingo", Context.MODE_PRIVATE)
    override fun read(key: String): String? = prefs.getString(key, null)
    override fun write(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun clear() = prefs.edit().clear().apply()
}

class InMemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun read(key: String): String? = map[key]
    override fun write(key: String, value: String) { map[key] = value }
    override fun clear() = map.clear()
}
