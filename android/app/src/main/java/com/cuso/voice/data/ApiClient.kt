package com.cuso.voice.data

import com.cuso.voice.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String) : Exception(message)

class ApiClient(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val tokenProvider: () -> String? = { null },
) {

    suspend fun login(username: String, password: String): AuthResponse = postAuth(
        "/api/auth/login",
        JSONObject().put("username", username).put("password", password),
    )

    suspend fun register(username: String, password: String, nickname: String?): AuthResponse = postAuth(
        "/api/auth/register",
        JSONObject().put("username", username).put("password", password).apply {
            if (!nickname.isNullOrBlank()) put("nickname", nickname)
        },
    )

    suspend fun listMine(): List<Channel> =
        request("GET", "/api/channels/mine").getJSONArray("channels").toChannels()

    suspend fun listPublic(): List<Channel> =
        request("GET", "/api/channels/public").getJSONArray("channels").toChannels()

    suspend fun createChannel(name: String): Channel =
        request("POST", "/api/channels", JSONObject().put("name", name))
            .getJSONObject("channel")
            .toChannel()

    suspend fun joinChannel(channelId: Int? = null, inviteCode: String? = null): Channel {
        val body = JSONObject()
        if (channelId != null) body.put("channelId", channelId)
        if (!inviteCode.isNullOrBlank()) body.put("inviteCode", inviteCode.trim().uppercase())
        return request("POST", "/api/channels/join", body)
            .getJSONObject("channel")
            .toChannel()
    }

    suspend fun channelDetail(id: Int): ChannelDetail {
        val json = request("GET", "/api/channels/$id")
        val membersJson = json.getJSONArray("members")
        val members = buildList {
            for (i in 0 until membersJson.length()) add(membersJson.getJSONObject(i).toMember())
        }
        return ChannelDetail(
            channel = json.getJSONObject("channel").toChannel(),
            members = members,
            creator = if (json.isNull("creator")) null else json.getJSONObject("creator").toMember(),
        )
    }

    suspend fun leaveChannel(id: Int) {
        request("POST", "/api/channels/$id/leave")
    }

    suspend fun deleteChannel(id: Int) {
        request("DELETE", "/api/channels/$id")
    }

    suspend fun kickMember(channelId: Int, userId: Int) {
        request("DELETE", "/api/channels/$channelId/members/$userId")
    }

    suspend fun rtcToken(channelId: Int): RtcToken {
        val json = request("POST", "/api/rtc/token", JSONObject().put("channelId", channelId))
        return RtcToken(
            appId = json.getString("appId"),
            channelName = json.getString("channelName"),
            uid = json.getInt("uid"),
            token = json.getString("token"),
        )
    }

    private suspend fun postAuth(path: String, body: JSONObject): AuthResponse {
        val json = request("POST", path, body)
        return AuthResponse(json.getString("token"), json.getJSONObject("user").toUser())
    }

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                tokenProvider()?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) {
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code == 401) {
                    throw ApiException("登录已过期，请退出后重新登录")
                }
                if (code !in 200..299) {
                    val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "请求失败（$code）"
                    throw ApiException(message)
                }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (e: ApiException) {
                throw e
            } catch (e: java.io.IOException) {
                throw ApiException("网络连接失败，请检查后端服务")
            } catch (e: Exception) {
                throw ApiException("数据解析失败，请稍后重试")
            } finally {
                conn.disconnect()
            }
        }

    private fun JSONObject.toUser(): User = User(
        id = getInt("id"),
        username = getString("username"),
        nickname = optString("nickname", getString("username")),
        avatarUrl = if (isNull("avatarUrl")) null else optString("avatarUrl"),
        createdAt = optLong("createdAt", 0L),
    )

    private fun JSONObject.toChannel(): Channel = Channel(
        id = getInt("id"),
        name = getString("name"),
        inviteCode = getString("inviteCode"),
        creatorId = getInt("creatorId"),
        createdAt = optLong("createdAt", 0L),
        memberCount = optInt("memberCount", 0),
        isMember = optBoolean("isMember", false),
    )

    private fun JSONObject.toMember(): ChannelMember = ChannelMember(
        userId = getInt("userId"),
        username = getString("username"),
        nickname = optString("nickname", getString("username")),
        avatarUrl = if (isNull("avatarUrl")) null else optString("avatarUrl"),
        joinedAt = optLong("joinedAt", 0L),
    )

    private fun JSONArray.toChannels(): List<Channel> = buildList {
        for (i in 0 until length()) add(getJSONObject(i).toChannel())
    }
}
