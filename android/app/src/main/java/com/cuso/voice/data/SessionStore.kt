package com.cuso.voice.data

import android.content.Context
import org.json.JSONObject

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("cuso_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        private set(value) {
            prefs.edit().putString("token", value).apply()
        }

    var user: User?
        get() {
            val raw = prefs.getString("user", null) ?: return null
            return runCatching {
                val j = JSONObject(raw)
                User(
                    id = j.getInt("id"),
                    username = j.getString("username"),
                    nickname = j.optString("nickname", j.getString("username")),
                    avatarUrl = if (j.isNull("avatarUrl")) null else j.optString("avatarUrl"),
                    createdAt = j.optLong("createdAt", 0L),
                )
            }.getOrNull()
        }
        private set(value) {
            if (value == null) {
                prefs.edit().remove("user").apply()
            } else {
                val j = JSONObject()
                    .put("id", value.id)
                    .put("username", value.username)
                    .put("nickname", value.nickname)
                    .put("avatarUrl", value.avatarUrl)
                    .put("createdAt", value.createdAt)
                prefs.edit().putString("user", j.toString()).apply()
            }
        }

    fun save(token: String, user: User) {
        this.token = token
        this.user = user
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
