# 多音源音频标准化（离线 ASR / ISV）

## 背景

离线转写（讯飞 IST）与声纹识别（ISV）固定消费 **16kHz / mono / s16le PCM**。

若原始输入为 **48kHz 双声道混音**、云端 M4A/WAV 等，直接按 16k mono 读取会导致：

- 时长被放大（例如 48k 双声道误读为 16k mono 时约 6 倍）
- 声道交错被误解析
- ISV 切片时间轴错位
- 讯飞返回 `failType=6` 等处理失败

## 架构

```text
原始音频 (多种格式)
  → MeetingAudioMaterializerService (本地/云端物化)
  → AudioNormalizeService (标准化 + 资产入库)
  → AudioQualityProbe (质量检测)
  → 标准 PCM (*.normalized.16k-mono.pcm)
  → OfflineTranscriptVoiceprintService / OfflineSpeakerLabeler
```

## 数据表

`int_meeting_audio_asset`（见 `schema-upgrade/v0.24-meeting-audio-asset.sql`）：

| 字段 | 说明 |
|------|------|
| `asset_role` | `ORIGINAL` 原始 / `NORMALIZED` 标准化后 |
| `source_type` | `MICROPHONE` / `STEREO_MIX` / `UPLOAD` / `CLOUD` |
| `quality_status` | `OK` / `LOW_VOLUME` / `SILENT` / `FORMAT_UNKNOWN` / `CONVERT_FAILED` / `TOO_SHORT` |

## 原始 PCM 元信息

Raw PCM 无文件头，**必须**通过以下之一提供格式：

1. 旁路文件 `{audio}.meta.json`
2. 调用方显式传入 `AudioFormatDescriptor`
3. 默认使用 `meeting.audio.*`（浏览器 WebSocket 录音：16k/mono/16bit）

### sidecar 示例（48k 立体声混音）

与 `955ae3e7-....pcm` 同目录创建 `955ae3e7-....pcm.meta.json`：

```json
{
  "encoding": "pcm_s16le",
  "sampleRate": 48000,
  "channels": 2,
  "bitDepth": 16,
  "sourceType": "STEREO_MIX"
}
```

## 配置项（application.yml）

```yaml
meeting:
  audio:
    normalize-enabled: true
    normalized-suffix: ".normalized.16k-mono.pcm"
    ffmpeg-path: ffmpeg
    quality-min-duration-ms: 1000
    quality-min-absmax: 100
    quality-min-rms: 10.0
```

## 质量阻断

标准化后若检测为 `SILENT` / `LOW_VOLUME` / `TOO_SHORT` / `FORMAT_UNKNOWN` / `CONVERT_FAILED`，**不会**送讯飞离线 ASR。

日志示例：

```text
Offline ASR skipped due to audio quality: meetingId=..., status=SILENT, message=近似静音，无法可靠转写
```

## 运维：已有库升级

```bash
cd meeting-server
mvn test-compile exec:java -Dexec.mainClass=com.smartmeeting.tools.ProdSchemaMigrate \
  -Dexec.classpathScope=test \
  -Dexec.args="--apply src/main/resources/schema-upgrade/v0.24-meeting-audio-asset.sql"
```

## 验收

- 48k/2ch/16bit + sidecar → 生成 `.normalized.16k-mono.pcm`，时长与原始一致（约 1s 误差内）
- 原生 16k/mono → 复制为标准资产，ASR 行为不变
- 近似静音 → 提前阻断，不写转写分段
