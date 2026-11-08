package com.cuso.voice.data

data class User(
    val id: Int,
    val username: String,
    val nickname: String,
    val avatarUrl: String?,
    val createdAt: Long,
)

data class AuthResponse(
    val token: String,
    val user: User,
)

data class Channel(
    val id: Int,
    val name: String,
    val inviteCode: String,
    val creatorId: Int,
    val createdAt: Long,
    val memberCount: Int,
    val isMember: Boolean,
)

data class ChannelMember(
    val userId: Int,
    val username: String,
    val nickname: String,
    val avatarUrl: String?,
    val joinedAt: Long = 0L,
)

data class ChannelDetail(
    val channel: Channel,
    val members: List<ChannelMember>,
    val creator: ChannelMember?,
)

data class RtcToken(
    val appId: String,
    val channelName: String,
    val uid: Int,
    val token: String,
)
