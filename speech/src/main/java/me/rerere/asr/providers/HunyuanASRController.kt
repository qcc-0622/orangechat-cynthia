/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.asr.providers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import me.rerere.asr.calculateRmsAmplitude
import me.rerere.asr.stripTrailingEmoji
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Collections

private const val TAG = "HunyuanASR"

// 分段兜底阈值: 即使 segmentDurationSec 设为 0 (只在 stop 时整体上传), 缓冲区涨到
// 6MB 也强制 flush 一次, 避免 base64 后请求体过大被服务端拒绝。
private const val MAX_SEGMENT_BYTES = 6 * 1024 * 1024

/**
 * 腾讯混元 ASR (Hy-ASR-3.0-preview) Controller。
 *
 * 走 TokenHub 同步识别接口: POST {baseUrl}, JSON body
 * `{model, data, voice_encode_format, source?}`, 鉴权 `Authorization: Bearer <apiKey>`,
 * 结果在 `output.text`。注意这不是 Whisper 那套 `/v1/audio/transcriptions` 的
 * multipart 协议, 用 multipart 请求会被判为参数非法 (400)。
 *
 * 录PCM -> 按 segmentDurationSec 分段 -> 每段包 WAV 头 -> base64 -> 上传,
 * 返回文本累加后通过 onTranscriptChange 回调。stop() 时把剩余 PCM 做最后一次 flush。
 *
 * 官方文档: https://cloud.tencent.com/document/product/1823/135791
 */
class HunyuanASRController(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val provider: ASRProviderSetting.Hunyuan
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(ASRState(isAvailable = true))
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recorderJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var onTranscriptChange: ((String) -> Unit)? = null

    // 同一时刻只允许一个 flush 协程在跑, 避免乱序拼结果
    private var flushJob: Job? = null

    private val bufferLock = Any()
    private var currentBuffer = ByteArrayOutputStream()
    private var segmentStartElapsedMs = 0L
    private val completedTranscripts = Collections.synchronizedList(mutableListOf<String>())

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        synchronized(bufferLock) {
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
        }
        completedTranscripts.clear()
        flushJob = null

        // 同步识别接口没有连接阶段, 直接进入 Listening
        _state.update {
            ASRState(
                status = ASRStatus.Listening,
                isAvailable = true
            )
        }
        startRecorder()
    }

    override fun stop() {
        recorderJob?.cancel()
        releaseRecorder()
        _state.update { it.copy(status = ASRStatus.Stopping) }

        // 把剩余 PCM 做最后一次 flush, 完成后切回 Idle
        scope.launch(Dispatchers.IO) {
            try {
                // 等当前正在跑的 flushJob 完成, 避免并发 flush 导致结果乱序
                flushJob?.join()
                flushSegment()
            } catch (e: Exception) {
                Log.e(TAG, "Final flush failed", e)
                setError(e.message ?: "Hunyuan ASR final flush failed")
            } finally {
                _state.update { it.copy(status = ASRStatus.Idle) }
            }
        }
    }

    override fun dispose() {
        recorderJob?.cancel()
        flushJob?.cancel()
        releaseRecorder()
        scope.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recorderJob?.cancel()
        recorderJob = scope.launch(Dispatchers.IO) {
            val sampleRate = provider.sampleRate
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBufferSize
                .coerceAtLeast(sampleRate / 10 * 2)
                .coerceAtLeast(4096)

            val recorder: AudioRecord
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                )
                audioRecord = recorder
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException(
                        "AudioRecord 初始化失败, state=${recorder.state}, 请检查录音权限或音频参数"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord 构造/初始化失败", e)
                setError(e.message ?: "麦克风初始化失败")
                return@launch
            }

            try {
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                val segmentMs = provider.segmentDurationSec.coerceAtLeast(0) * 1000L
                while (isActive) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val amplitude = calculateRmsAmplitude(buffer, read)
                        _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(amplitude)) }

                        val shouldFlush = synchronized(bufferLock) {
                            currentBuffer.write(buffer, 0, read)
                            if (segmentMs <= 0) {
                                currentBuffer.size() >= MAX_SEGMENT_BYTES
                            } else {
                                val elapsed = SystemClock.elapsedRealtime() - segmentStartElapsedMs
                                currentBuffer.size() >= MAX_SEGMENT_BYTES || elapsed >= segmentMs
                            }
                        }

                        if (shouldFlush) {
                            // 用单独协程异步 flush, 不阻塞录音主循环
                            triggerFlush()
                        }
                    } else if (read < 0) {
                        throw IllegalStateException("AudioRecord read error: $read")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
                setError(e.message ?: "Audio recording failed")
            } finally {
                releaseRecorder()
            }
        }
    }

    private fun triggerFlush() {
        // 同一时刻只跑一个 flush, 避免后发先至导致结果乱序
        if (flushJob?.isActive == true) return
        flushJob = scope.launch(Dispatchers.IO) {
            runCatching { flushSegment() }
                .onFailure { Log.e(TAG, "Segment flush failed", it) }
        }
    }

    /**
     * 取出当前缓冲区里的 PCM, 包成 WAV 后 base64 上传; 把识别结果加到 completedTranscripts。
     * 在 bufferLock 内拷贝出 PCM 并立刻重置缓冲区, 不持有锁等待网络, 避免阻塞录音写。
     */
    private suspend fun flushSegment() {
        val pcmBytes = synchronized(bufferLock) {
            if (currentBuffer.size() == 0) return
            val bytes = currentBuffer.toByteArray()
            currentBuffer = ByteArrayOutputStream()
            segmentStartElapsedMs = SystemClock.elapsedRealtime()
            bytes
        }

        val wavBytes = pcm16ToWav(
            pcm = pcmBytes,
            sampleRate = provider.sampleRate,
            channels = 1,
            bitsPerSample = 16
        )

        val body = JSONObject()
            .put("model", provider.model)
            .put("data", Base64.encodeToString(wavBytes, Base64.NO_WRAP))
            .put("voice_encode_format", "wav")
        // source: 留空时不下发, 由服务端自动检测语种
        if (provider.language.isNotBlank()) {
            body.put("source", provider.language)
        }

        val request = Request.Builder()
            .url(provider.baseUrl.trim())
            .addHeader("Authorization", "Bearer ${provider.apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val text = withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { resp ->
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IOException("Hunyuan ASR HTTP ${resp.code}: $respBody")
                }
                val json = runCatching { JSONObject(respBody) }.getOrElse {
                    throw IOException("Hunyuan ASR response is not valid JSON: $respBody")
                }
                json.optJSONObject("error")?.let { error ->
                    throw IOException(
                        "Hunyuan ASR error: ${error.optString("message", error.toString())}"
                    )
                }
                val status = json.optString("status", "")
                if (status.isNotBlank() && status != "completed") {
                    throw IOException("Hunyuan ASR task not completed: status=$status $respBody")
                }
                json.optJSONObject("output")
                    ?.optString("text", "")
                    ?.trim()
                    .orEmpty()
            }
        }.stripTrailingEmoji()

        if (text.isNotEmpty()) {
            completedTranscripts.add(text)
            publishTranscript()
        }
    }

    private fun publishTranscript() {
        val transcript = completedTranscripts
            .filter { it.isNotBlank() }
            .joinToString(" ")
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        scope.launch { onTranscriptChange?.invoke(transcript) }
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                errorMessage = message
            )
        }
    }

    private fun releaseRecorder() {
        recorderJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * 把 raw PCM16 little-endian 数据封装成最小 WAV (RIFF/WAVE/fmt/data)。
         * 混元只接受 wav/mp3/pcm/ogg 这类编码格式, AudioRecord 输出的是裸 PCM,
         * 用 WAV 封装并配合 voice_encode_format=wav 最稳妥。
         */
        private fun pcm16ToWav(
            pcm: ByteArray,
            sampleRate: Int,
            channels: Int,
            bitsPerSample: Int
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize = pcm.size
            val out = ByteArrayOutputStream(44 + dataSize)

            // RIFF header
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeIntLE(out, 36 + dataSize) // chunk size = file size - 8
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            // fmt chunk
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            writeIntLE(out, 16)            // PCM fmt chunk size
            writeShortLE(out, 1)           // audio format = PCM
            writeShortLE(out, channels)
            writeIntLE(out, sampleRate)
            writeIntLE(out, byteRate)
            writeShortLE(out, blockAlign)
            writeShortLE(out, bitsPerSample)
            // data chunk
            out.write("data".toByteArray(Charsets.US_ASCII))
            writeIntLE(out, dataSize)
            out.write(pcm)
            return out.toByteArray()
        }

        private fun writeIntLE(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }

        private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
    }
}
