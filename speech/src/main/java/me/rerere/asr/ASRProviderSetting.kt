/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.asr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
sealed class ASRProviderSetting {
    abstract val id: Uuid
    abstract val name: String

    abstract fun copyProvider(
        id: Uuid = this.id,
        name: String = this.name,
    ): ASRProviderSetting

    @Serializable
    @SerialName("openai_realtime")
    data class OpenAIRealtime(
        override val id: Uuid = Uuid.random(),
        override val name: String = "OpenAI Realtime ASR",
        val apiKey: String = "",
        val websocketUrl: String = "wss://api.openai.com/v1/realtime?intent=transcription",
        val model: String = "gpt-4o-transcribe",
        val language: String = "",
        val prompt: String = "",
        val sampleRate: Int = 24000,
        val vadThreshold: Float = 0.3f,
        val prefixPaddingMs: Int = 200,
        val silenceDurationMs: Int = 300,
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    @Serializable
    @SerialName("siliconflow")
    data class SiliconFlow(
        override val id: Uuid = Uuid.random(),
        override val name: String = "SiliconFlow ASR",
        val apiKey: String = "",
        val baseUrl: String = "https://api.siliconflow.cn/v1/audio/transcriptions",
        val model: String = "FunAudioLLM/Spirit-tiny",
        val language: String = "",
        val sampleRate: Int = 16000,
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * 火山引擎 (豆包) 大模型流式语音识别。
     *
     * 鉴权分两代, 依据填写了哪组凭证决定发什么 header:
     * - 新版控制台单个 API Key -> 只发 `X-Api-Key`
     * - 旧版控制台 App ID + Access Token -> 发 `X-Api-App-Key` + `X-Api-Access-Key`
     *
     * 两代凭证混用 (例如把 App ID 填进 [apiKey]) 会在握手阶段被拒, 服务端返回 403。
     * [resourceId] 还必须与账号实际开通的服务一致, 否则同样 403:
     * - 2.0 小时版 `volc.seedasr.sauc.duration` / 2.0 并发版 `volc.seedasr.sauc.concurrent`
     * - 1.0 小时版 `volc.bigasr.sauc.duration` / 1.0 并发版 `volc.bigasr.sauc.concurrent`
     *
     * 官方文档: https://docs.volcengine.com/docs/6561/1354869
     */
    @Serializable
    @SerialName("volcengine")
    data class Volcengine(
        override val id: Uuid = Uuid.random(),
        override val name: String = "Volcengine ASR",
        val apiKey: String = "",
        // 旧版控制台凭证; 二者都非空时改用 X-Api-App-Key / X-Api-Access-Key 鉴权
        val appKey: String = "",
        val accessKey: String = "",
        val websocketUrl: String = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel",
        val resourceId: String = "volc.seedasr.sauc.duration",
        val language: String = "",
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * 小米 MiMo ASR (mimo-v2.5-asr)。
     *
     * 与 OpenAIRealtime / Volcengine 等流式 WebSocket 接口不同, MiMo ASR 是基于 OpenAI 兼容
     * chat/completions 的 HTTP 一次性识别接口。客户端在录音期间按 [segmentDurationSec] 分段,
     * 把每段 PCM 转成 WAV 后 base64 内嵌到 messages[].content[].input_audio.data 字段,
     * POST 到 {baseUrl}/chat/completions, 返回结果在 choices[0].message.content。
     *
     * 官方文档: https://platform.xiaomimimo.com/docs/zh-CN/api/audio/Speech-Recognition
     */
    @Serializable
    @SerialName("mimo")
    data class MiMo(
        override val id: Uuid = Uuid.random(),
        override val name: String = "MiMo ASR",
        val apiKey: String = "",
        val baseUrl: String = "https://api.xiaomimimo.com/v1",
        val model: String = "mimo-v2.5-asr",
        // auto | zh | en; 留空时不下发 asr_options, 服务端默认 auto
        val language: String = "auto",
        val sampleRate: Int = 16000,
        // 每多少秒自动 flush 一次当前缓冲区 (上传识别)。设为 0 表示禁用自动分段,
        // 仅在用户主动 stop() 时整体上传 (注意 MiMo 单次请求 raw 上限约 7.5MB,
        // 16kHz/16bit/mono 下约 234 秒)。
        val segmentDurationSec: Int = 30,
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    /**
     * 腾讯混元语音识别 (Hy-ASR-3.0-preview)。
     *
     * 混元 ASR 目前只有两个入口, 都不能套用 Whisper 那套 `multipart + model/file` 的
     * `/v1/audio/transcriptions` 协议 (套用会被服务端判为参数非法, 返回 400):
     * - TokenHub 同步识别: POST JSON `{model, data | input_url, source, voice_encode_format}`,
     *   鉴权用 `Authorization: Bearer <API Key>`, 识别结果在 `output.text` (本 Provider 走这条)
     * - 腾讯云实时语音识别 WebSocket: 传 `engine_model_type=Hy-ASR-3.0-preview`, 需要
     *   AppID/SecretID/SecretKey 签名, 仅支持 16k 单声道 PCM 且限 1 分钟以内
     *
     * 客户端在录音期间按 [segmentDurationSec] 分段, 每段 PCM 包成 WAV 后 base64 放进 `data`
     * 字段上传, 返回文本按段拼接后回调。音频只能通过 `input_url` 或 `data` 二选一传入,
     * 手上没有可公网访问的音频地址, 所以走 base64。
     *
     * 官方文档 (TokenHub 同步识别): https://cloud.tencent.com/document/product/1823/135791
     * 官方文档 (混元 ASR 内测版): https://cloud.tencent.com/document/product/1093/135476
     */
    @Serializable
    @SerialName("hunyuan")
    data class Hunyuan(
        override val id: Uuid = Uuid.random(),
        override val name: String = "腾讯混元 ASR",
        val apiKey: String = "",
        val baseUrl: String = "https://tokenhub.tencentmaas.com/v1/wand/asrproxy/sync_transcribe",
        val model: String = "hy-asr-3.0-preview",
        // 识别语种 (source): zh / en 等, 留空由服务端自动检测
        val language: String = "",
        val sampleRate: Int = 16000,
        // 每多少秒自动 flush 一次当前缓冲区 (上传识别)。设为 0 表示禁用自动分段,
        // 仅在用户主动 stop() 时整体上传。
        val segmentDurationSec: Int = 30,
    ) : ASRProviderSetting() {
        override fun copyProvider(
            id: Uuid,
            name: String,
        ): ASRProviderSetting {
            return this.copy(
                id = id,
                name = name,
            )
        }
    }

    companion object {
        val Types by lazy {
            listOf(
                OpenAIRealtime::class,
                SiliconFlow::class,
                Volcengine::class,
                MiMo::class,
                Hunyuan::class,
            )
        }
    }
}
