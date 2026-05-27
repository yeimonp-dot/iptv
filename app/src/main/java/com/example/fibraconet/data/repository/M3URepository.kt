package com.example.fibraconet.data.repository

import com.example.fibraconet.data.model.Channel
import com.example.fibraconet.data.model.ChannelGroup
import com.example.fibraconet.data.model.LoginCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class M3URepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private fun friendlyError(e: Exception): String = when (e) {
        is SocketTimeoutException ->
            "El servidor no responde. Verifica que la URL sea correcta y el servidor esté encendido."
        is UnknownHostException ->
            "No se encontró el servidor. Verifica la dirección URL."
        else -> e.message?.let { msg ->
            when {
                msg.contains("CLEARTEXT", ignoreCase = true) ->
                    "La URL usa HTTP pero el dispositivo requiere HTTPS."
                msg.contains("Unable to resolve", ignoreCase = true) ->
                    "No se pudo resolver el nombre del servidor. Verifica la URL."
                msg.contains("refused", ignoreCase = true) ->
                    "Conexión rechazada. El servidor no acepta conexiones en ese puerto."
                else -> "Error de conexión: $msg"
            }
        } ?: "Error desconocido al conectar con el servidor."
    }

    suspend fun fetchPlaylist(credentials: LoginCredentials): Result<List<Channel>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildM3UUrl(credentials)
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error del servidor: ${response.code}"))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("El servidor no envió respuesta"))

                if (!body.trimStart().startsWith("#EXTM3U")) {
                    return@withContext Result.failure(Exception("Respuesta inválida del servidor (no es M3U)"))
                }

                Result.success(parseM3U(body))
            } catch (e: Exception) {
                Result.failure(Exception(friendlyError(e)))
            }
        }
    }

    suspend fun fetchFromUrl(rawUrl: String): Result<List<Channel>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = normalizeUrl(rawUrl)

                // 1. Verificar si la extensión de la URL es claramente de un stream de video directo
                if (isDirectStreamExtension(url)) {
                    val channelName = getFileNameFromUrl(url) ?: "Enlace Directo"
                    return@withContext Result.success(listOf(
                        Channel(
                            id = "direct_stream",
                            name = channelName,
                            streamUrl = url,
                            logoUrl = "",
                            groupTitle = "Enlaces Directos"
                        )
                    ))
                }

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Error del servidor: ${response.code}"))
                }

                // 2. Verificar el Content-Type para evitar descargar archivos de video gigantes a memoria
                val contentType = response.body?.contentType()?.toString()?.lowercase() ?: ""
                if (contentType.startsWith("video/") || contentType.contains("video/mp2t") || contentType.contains("video/mp4")) {
                    val channelName = getFileNameFromUrl(url) ?: "Enlace Directo"
                    return@withContext Result.success(listOf(
                        Channel(
                            id = "direct_stream",
                            name = channelName,
                            streamUrl = url,
                            logoUrl = "",
                            groupTitle = "Enlaces Directos"
                        )
                    ))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("El servidor no envió respuesta"))

                // 3. Si no comienza con #EXTM3U o no contiene #EXTINF, pero es un formato reproducible (como HLS/DASH en texto)
                if (!body.trimStart().startsWith("#EXTM3U") || !body.contains("#EXTINF")) {
                    val channelName = getFileNameFromUrl(url) ?: "Enlace Directo"
                    return@withContext Result.success(listOf(
                        Channel(
                            id = "direct_stream",
                            name = channelName,
                            streamUrl = url,
                            logoUrl = "",
                            groupTitle = "Enlaces Directos"
                        )
                    ))
                }

                Result.success(parseM3U(body))
            } catch (e: Exception) {
                Result.failure(Exception(friendlyError(e)))
            }
        }
    }

    private fun isDirectStreamExtension(url: String): Boolean {
        val cleanUrl = url.lowercase().split("?")[0]
        return cleanUrl.endsWith(".mp4") ||
               cleanUrl.endsWith(".mkv") ||
               cleanUrl.endsWith(".ts") ||
               cleanUrl.endsWith(".avi") ||
               cleanUrl.endsWith(".mov") ||
               cleanUrl.endsWith(".flv") ||
               cleanUrl.endsWith(".mp3")
    }

    private fun getFileNameFromUrl(url: String): String? {
        try {
            val cleanUrl = url.split("?")[0]
            val parts = cleanUrl.split("/")
            val lastPart = parts.lastOrNull()
            if (!lastPart.isNullOrBlank() && lastPart.contains(".")) {
                return lastPart
            }
        } catch (_: Exception) {}
        return null
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }
    }

    private fun buildM3UUrl(credentials: LoginCredentials): String {
        val base = normalizeUrl(credentials.serverUrl).trimEnd('/')
        return "$base/get.php?username=${credentials.username}&password=${credentials.password}&type=m3u_plus&output=ts"
    }

    fun parseM3U(content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var i = 0
        var channelIndex = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("#EXTINF")) {
                val extinf = line
                val streamUrl = findNextStreamUrl(lines, i + 1)

                if (streamUrl != null) {
                    val name = extractName(extinf)
                    val logo = extractAttribute(extinf, "tvg-logo")
                    val group = extractAttribute(extinf, "group-title")
                    val tvgId = extractAttribute(extinf, "tvg-id")
                    val tvgName = extractAttribute(extinf, "tvg-name")

                    channels.add(
                        Channel(
                            id = channelIndex.toString(),
                            name = name,
                            streamUrl = streamUrl,
                            logoUrl = logo,
                            groupTitle = group.ifEmpty { "Sin grupo" },
                            tvgId = tvgId,
                            tvgName = tvgName
                        )
                    )
                    channelIndex++
                }
            }
            i++
        }

        return channels
    }

    private fun findNextStreamUrl(lines: List<String>, startIndex: Int): String? {
        for (i in startIndex until minOf(startIndex + 3, lines.size)) {
            val line = lines[i].trim()
            if (line.startsWith("http://") || line.startsWith("https://") || line.startsWith("rtmp://") || line.startsWith("rtsp://")) {
                return line
            }
        }
        return null
    }

    private fun extractName(extinf: String): String {
        val commaIndex = extinf.lastIndexOf(',')
        return if (commaIndex >= 0 && commaIndex < extinf.length - 1) {
            extinf.substring(commaIndex + 1).trim()
        } else {
            "Canal sin nombre"
        }
    }

    private fun extractAttribute(extinf: String, attr: String): String {
        val pattern = Regex("""$attr="([^"]*)"""")
        return pattern.find(extinf)?.groupValues?.get(1) ?: ""
    }

    fun groupChannels(channels: List<Channel>): List<ChannelGroup> {
        return channels
            .groupBy { it.groupTitle }
            .map { (group, channels) -> ChannelGroup(group, channels) }
            .sortedBy { it.name }
    }
}
