# 第三方 SDK 参考代码

本目录存放讯飞等厂商 Demo 源码，供对接 ASR / 声纹时对照，**不是**本项目编译单元。

| 子目录 | 用途 |
|--------|------|
| `RTASR_LLM_java/` | 实时转写 WebSocket 客户端示例 |
| `voiceprint_recognition_java_demo/` | 声纹注册/检索 HTTP 示例 |

请勿在此目录提交 IDE 配置（`.idea`、`*.iml`、Eclipse `.project`）。业务实现见 `meeting-server/src/main/java/com/smartmeeting/asr` 与 `.../service/Voiceprint*`。
