/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.asr.ASRProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput

@Composable
fun ASRProviderConfigure(
    setting: ASRProviderSetting,
    modifier: Modifier = Modifier,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        FormItem(
            label = { Text(stringResource(R.string.setting_asr_configure_provider_type)) },
            description = { Text(stringResource(R.string.setting_asr_configure_provider_type_desc)) }
        ) {
            OutlinedTextField(
                value = when (setting) {
                    is ASRProviderSetting.OpenAIRealtime -> "OpenAI Realtime"
                    is ASRProviderSetting.SiliconFlow -> "SiliconFlow"
                    is ASRProviderSetting.Volcengine -> "Volcengine"
                    is ASRProviderSetting.MiMo -> "MiMo"
                    is ASRProviderSetting.Hunyuan -> "腾讯混元 (Hy-ASR)"
                },
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        FormItem(
            label = { Text(stringResource(R.string.setting_asr_configure_name)) },
            description = { Text(stringResource(R.string.setting_asr_configure_name_desc)) }
        ) {
            OutlinedTextField(
                value = setting.name,
                onValueChange = { onValueChange(setting.copyProvider(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("OpenAI Realtime") }
            )
        }

        when (setting) {
            is ASRProviderSetting.OpenAIRealtime -> OpenAIRealtimeASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.SiliconFlow -> SiliconFlowASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.Volcengine -> VolcengineASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.MiMo -> MiMoASRConfiguration(setting, onValueChange)
            is ASRProviderSetting.Hunyuan -> HunyuanASRConfiguration(setting, onValueChange)
        }
    }
}

@Composable
private fun OpenAIRealtimeASRConfiguration(
    setting: ASRProviderSetting.OpenAIRealtime,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_openai_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_websocket_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_openai_websocket_desc)) }
    ) {
        OutlinedTextField(
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("wss://api.openai.com/v1/realtime?intent=transcription") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text(stringResource(R.string.setting_asr_configure_model_desc)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("gpt-4o-transcribe") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_language_iso_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_prompt)) },
        description = { Text(stringResource(R.string.setting_asr_configure_prompt_desc)) }
    ) {
        OutlinedTextField(
            value = setting.prompt,
            onValueChange = { onValueChange(setting.copy(prompt = it)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("Optional") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_vad_threshold)) },
        description = { Text(stringResource(R.string.setting_asr_configure_vad_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.vadThreshold,
            onValueChange = { value ->
                if (value in 0.0f..1.0f) {
                    onValueChange(setting.copy(vadThreshold = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "VAD Threshold"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_prefix_padding)) },
        description = { Text(stringResource(R.string.setting_asr_configure_prefix_padding_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.prefixPaddingMs,
            onValueChange = { value ->
                if (value in 0..2000) {
                    onValueChange(setting.copy(prefixPaddingMs = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Prefix Padding"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_silence_duration)) },
        description = { Text(stringResource(R.string.setting_asr_configure_silence_duration_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.silenceDurationMs,
            onValueChange = { value ->
                if (value in 100..5000) {
                    onValueChange(setting.copy(silenceDurationMs = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Silence Duration"
        )
    }
}

@Composable
private fun SiliconFlowASRConfiguration(
    setting: ASRProviderSetting.SiliconFlow,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text("SiliconFlow API Key") }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...") }
        )
    }

    FormItem(
        label = { Text("Base URL") },
        description = { Text("SiliconFlow 语音识别 API 地址") }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.siliconflow.cn/v1/audio/transcriptions") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text(stringResource(R.string.setting_asr_configure_model_desc)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("FunAudioLLM/Spirit-tiny") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_language_iso_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("zh") }
        )
    }
}

@Composable
private fun VolcengineASRConfiguration(
    setting: ASRProviderSetting.Volcengine,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_volcengine_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("your-api-key") }
        )
    }

    // 旧版控制台只发 App ID + Access Token, 不认 X-Api-Key。留空则回落到上面的 API Key 鉴权。
    FormItem(
        label = { Text("App ID (旧版控制台, 可选)") },
        description = { Text("火山引擎语音控制台旧版的 App ID, 与 Access Token 配对使用；新版控制台请留空") }
    ) {
        OutlinedTextField(
            value = setting.appKey,
            onValueChange = { onValueChange(setting.copy(appKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("123456789") }
        )
    }

    FormItem(
        label = { Text("Access Token (旧版控制台, 可选)") },
        description = { Text("旧版控制台的 Access Token, 与 App ID 同时填写后改用 X-Api-App-Key / X-Api-Access-Key 鉴权") }
    ) {
        OutlinedTextField(
            value = setting.accessKey,
            onValueChange = { onValueChange(setting.copy(accessKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("your-access-key") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_websocket_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_volcengine_websocket_desc)) }
    ) {
        OutlinedTextField(
            value = setting.websocketUrl,
            onValueChange = { onValueChange(setting.copy(websocketUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("wss://openspeech.bytedance.com/api/v3/sauc/bigmodel") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_resource_id)) },
        description = {
            Text(
                "必须与账号已开通的服务一致, 填错握手会返回 403。" +
                    "2.0 小时版 volc.seedasr.sauc.duration / 2.0 并发版 volc.seedasr.sauc.concurrent；" +
                    "1.0 小时版 volc.bigasr.sauc.duration / 1.0 并发版 volc.bigasr.sauc.concurrent"
            )
        }
    ) {
        OutlinedTextField(
            value = setting.resourceId,
            onValueChange = { onValueChange(setting.copy(resourceId = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("volc.seedasr.sauc.duration") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_language_code_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") }
        )
    }
}

@Composable
private fun MiMoASRConfiguration(
    setting: ASRProviderSetting.MiMo,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_api_key_desc)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-... or tp-...") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_base_url)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_base_url_desc)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://api.xiaomimimo.com/v1") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_model_desc)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("mimo-v2.5-asr") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_language_desc)) }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_sample_rate)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_sample_rate_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Sample Rate"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_segment_duration)) },
        description = { Text(stringResource(R.string.setting_asr_configure_mimo_segment_desc)) }
    ) {
        OutlinedNumberInput(
            value = setting.segmentDurationSec,
            onValueChange = { value ->
                if (value in 0..300) {
                    onValueChange(setting.copy(segmentDurationSec = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Segment Duration (s)"
        )
    }
}

@Composable
private fun HunyuanASRConfiguration(
    setting: ASRProviderSetting.Hunyuan,
    onValueChange: (ASRProviderSetting) -> Unit
) {
    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_api_key)) },
        description = { Text("TokenHub 的 API Key, 以 Authorization: Bearer 方式鉴权") }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { onValueChange(setting.copy(apiKey = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("your-api-key") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_base_url)) },
        description = { Text("Hy 同步语音识别接口地址, 注意不是 /v1/audio/transcriptions") }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { onValueChange(setting.copy(baseUrl = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://tokenhub.tencentmaas.com/v1/wand/asrproxy/sync_transcribe") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_model)) },
        description = { Text("混元语音识别模型名, 例如 hy-asr-3.0-preview") }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { onValueChange(setting.copy(model = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("hy-asr-3.0-preview") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_language)) },
        description = { Text("识别语种 source: zh / en 等, 留空由服务端自动检测") }
    ) {
        OutlinedTextField(
            value = setting.language,
            onValueChange = { onValueChange(setting.copy(language = it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("auto") }
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_sample_rate)) },
        description = { Text("PCM 录音采样率, ASR 推荐 16000") }
    ) {
        OutlinedNumberInput(
            value = setting.sampleRate,
            onValueChange = { value ->
                if (value in 8000..48000) {
                    onValueChange(setting.copy(sampleRate = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Sample Rate"
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_asr_configure_segment_duration)) },
        description = { Text("录音期间每 N 秒自动上传一次识别; 0 表示只在停止录音时整体上传") }
    ) {
        OutlinedNumberInput(
            value = setting.segmentDurationSec,
            onValueChange = { value ->
                if (value in 0..300) {
                    onValueChange(setting.copy(segmentDurationSec = value))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "Segment Duration (s)"
        )
    }
}
