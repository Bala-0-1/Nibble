package com.nibble.app.core.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

actual suspend fun httpPost(url: String, body: String, contentType: String): HttpTextResponse =
    withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", contentType)
        }
        try {
            conn.outputStream.use { it.write(body.encodeToByteArray()) }
            val status = conn.responseCode
            // 4xx/5xx bodies arrive on errorStream — they carry the Firebase error JSON.
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpTextResponse(status, text)
        } catch (e: java.io.IOException) {
            throw HttpUnreachable(e.message ?: "network unreachable")
        } finally {
            conn.disconnect()
        }
    }
