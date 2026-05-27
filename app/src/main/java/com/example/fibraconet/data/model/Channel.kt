package com.example.fibraconet.data.model

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val groupTitle: String = "",
    val tvgId: String = "",
    val tvgName: String = ""
)

data class ChannelGroup(
    val name: String,
    val channels: List<Channel>
)

data class LoginCredentials(
    val serverUrl: String,
    val username: String,
    val password: String
)
