package com.example.voice

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EdgeTtsSynthesizer(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "EdgeTtsSynthesizer"
        private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EA6549818C624523F9342531"
        private const val BASE_WSS_URL = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
    }

    suspend fun synthesizeToMp3(
        text: String,
        voice: TtsVoice,
        speedRate: Float = 1.0f,
        pitchRate: Float = 1.0f,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val result = withTimeoutOrNull(25_000L) {
            try {
                executeWebSocketSynthesis(text, voice, speedRate, pitchRate, destinationFile)
                Result.success(destinationFile)
            } catch (e: Exception) {
                Log.w(TAG, "Synthesis failed for voice ${voice.id}: ${e.message}")
                Result.failure(e)
            }
        }
        result ?: Result.failure(IllegalStateException("TTS synthesis timed out"))
    }

    private suspend fun executeWebSocketSynthesis(
        text: String,
        voice: TtsVoice,
        speedRate: Float,
        pitchRate: Float,
        destinationFile: File
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val connectionId = UUID.randomUUID().toString().replace("-", "")
        val requestId = UUID.randomUUID().toString().replace("-", "")
        val wssUrl = "$BASE_WSS_URL?TrustedClientToken=$TRUSTED_CLIENT_TOKEN&ConnectionId=$connectionId"

        val request = Request.Builder()
            .url(wssUrl)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp_${System.currentTimeMillis()}")
        var outputStream: FileOutputStream? = null
        var hasReceivedAudio = false
        var activeWebSocket: WebSocket? = null

        try {
            outputStream = FileOutputStream(tempFile)
        } catch (e: Exception) {
            continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                activeWebSocket = webSocket
                val timestamp = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)", Locale.US).format(Date())

                // 1. Send speech config message
                val configPayload = "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}"
                val configMsg = "Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n$configPayload"
                webSocket.send(configMsg)

                // 2. Format rate and pitch
                val ratePercent = ((speedRate - 1.0f) * 100).toInt()
                val rateStr = if (ratePercent >= 0) "+$ratePercent%" else "$ratePercent%"

                val pitchPercent = ((pitchRate - 1.0f) * 50).toInt()
                val pitchStr = if (pitchPercent >= 0) "+${pitchPercent}Hz" else "${pitchPercent}Hz"

                val escapedText = text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;")

                // 3. Send SSML payload
                val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xmlns:mstts='https://www.w3.org/2001/mstts' xml:lang='${voice.localeTag}'><voice name='${voice.id}'><prosody pitch='$pitchStr' rate='$rateStr'>$escapedText</prosody></voice></speak>"
                val ssmlMsg = "X-RequestId:$requestId\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:$timestamp\r\nPath:ssml\r\n\r\n$ssml"
                webSocket.send(ssmlMsg)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (bytes.size < 2) return
                try {
                    val byteBuffer = bytes.asByteBuffer()
                    val headerLen = byteBuffer.short.toInt() and 0xFFFF
                    if (bytes.size >= 2 + headerLen) {
                        val audioBytes = bytes.substring(2 + headerLen).toByteArray()
                        if (audioBytes.isNotEmpty()) {
                            outputStream?.write(audioBytes)
                            hasReceivedAudio = true
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error writing audio chunk: ${e.message}")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("Path:turn.end")) {
                    finishSuccessfully(webSocket)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (hasReceivedAudio && tempFile.length() > 0) {
                    finishSuccessfully(null)
                } else if (continuation.isActive) {
                    cleanup()
                    continuation.resumeWithException(IllegalStateException("WebSocket closed without audio: $reason ($code)"))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (continuation.isActive) {
                    cleanup()
                    continuation.resumeWithException(t)
                }
            }

            private fun finishSuccessfully(ws: WebSocket?) {
                try {
                    outputStream?.flush()
                    outputStream?.close()
                    outputStream = null
                    ws?.close(1000, "Completed")
                } catch (_: Exception) {}

                if (tempFile.length() > 0) {
                    if (destinationFile.exists()) destinationFile.delete()
                    val renamed = tempFile.renameTo(destinationFile)
                    if (renamed && continuation.isActive) {
                        continuation.resume(Unit)
                    } else if (continuation.isActive) {
                        tempFile.copyTo(destinationFile, overwrite = true)
                        tempFile.delete()
                        continuation.resume(Unit)
                    }
                } else if (continuation.isActive) {
                    cleanup()
                    continuation.resumeWithException(IllegalStateException("Generated audio file is empty"))
                }
            }

            private fun cleanup() {
                try {
                    outputStream?.close()
                    outputStream = null
                    if (tempFile.exists()) tempFile.delete()
                } catch (_: Exception) {}
            }
        }

        activeWebSocket = client.newWebSocket(request, listener)

        continuation.invokeOnCancellation {
            try {
                activeWebSocket?.cancel()
                outputStream?.close()
                if (tempFile.exists()) tempFile.delete()
            } catch (_: Exception) {}
        }
    }
}
