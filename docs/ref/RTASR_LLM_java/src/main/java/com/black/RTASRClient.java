package com.black;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RTASRClient {
    // 配置参数
    private static final String AUDIO_ENCODE = "pcm_s16le";
    private static final String LANG = "autodialect";
    private static final String SAMPLERATE = "16000";
    private static final int AUDIO_FRAME_SIZE = 1280;  // 每帧字节数
    private static final int FRAME_INTERVAL_MS = 40;   // 帧间隔(毫秒)

    // 客户端参数
    private final String appId;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String audioPath;
    private final String baseWsUrl = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1";

    // 状态变量
    private WebSocketClient webSocketClient;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isSendingAudio = new AtomicBoolean(false);
    private String sessionId;
    private long audioFileSize = 0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public RTASRClient(String appId, String accessKeyId, String accessKeySecret, String audioPath) {
        this.appId = appId;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.audioPath = audioPath;
    }

    /**
     * 获取音频文件大小
     */
    private long getAudioFileSize() {
        File file = new File(audioPath);
        if (!file.exists() || !file.isFile()) {
            System.err.println("【获取文件大小失败】文件不存在或不是普通文件");
            return 0;
        }
        return file.length();
    }

    /**
     * 生成鉴权参数并建立WebSocket连接
     */
    public boolean connect() {
        try {
            // 生成鉴权参数
            Map<String, String> authParams = generateAuthParams();
            String paramsStr = buildParamsString(authParams);
            String fullWsUrl = baseWsUrl + "?" + paramsStr;
            System.out.println("【连接信息】完整URL：" + fullWsUrl);

            // 创建WebSocket客户端
            webSocketClient = new WebSocketClient(new URI(fullWsUrl)) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    isConnected.set(true);
                    System.out.println("【连接成功】WebSocket握手完成，等待服务端就绪（1.5秒）...");

                    // 启动接收消息处理线程
                    executor.submit(() -> receiveMessages());

                    // 等待服务端初始化
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public void onMessage(String message) {
                    try {
                        // 使用org.json解析JSON消息
                        JSONObject json = new JSONObject(message);
                        System.out.println("【接收消息】" + json.toString());

                        // 处理会话ID
                        if ("action".equals(json.optString("msg_type"))) {
                            JSONObject data = json.optJSONObject("data");
                            if (data != null && data.has("sessionId")) {
                                sessionId = data.getString("sessionId");
                                System.out.println("【会话信息】已获取sessionId：" + sessionId);
                            }
                        }
                    } catch (Exception e) {
                        int maxLength = Math.min(50, message.length());
                        System.out.println("【接收异常】非JSON文本消息：" + message.substring(0, maxLength) + "...");
                    }
                }

                /*@Override
                public void onMessage(byte[] bytes, int offset, int length) {
                    System.out.println("【接收提示】收到二进制消息（长度：" + length + "字节），忽略");
                }*/

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    isConnected.set(false);
                    System.out.println("【连接关闭】代码：" + code + "，原因：" + reason);
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("【WebSocket错误】" + ex.getMessage());
                    ex.printStackTrace();
                }
            };

            // 连接服务器
            webSocketClient.connectBlocking(15, TimeUnit.SECONDS);
            return isConnected.get();
        } catch (Exception e) {
            System.err.println("【连接失败】" + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 接收并处理服务端消息
     */
    private void receiveMessages() {
        while (isConnected.get() && webSocketClient != null) {
            try {
                // 等待消息（通过onMessage回调处理）
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("【接收异常】" + e.getMessage());
                e.printStackTrace();
                close();
                break;
            }
        }
        System.out.println("【接收线程】连接已关闭，退出接收循环");
    }

    /**
     * 发送音频文件（带精确节奏控制）
     */
    public boolean sendAudio() {
        if (!isConnected.get() || webSocketClient == null || isSendingAudio.get()) {
            System.out.println("【发送失败】WebSocket未连接或已有发送任务");
            return false;
        }

        isSendingAudio.set(true);
        audioFileSize = getAudioFileSize();

        try (FileInputStream fis = new FileInputStream(audioPath)) {
            // 计算总帧数和预估时长
            long totalFrames = audioFileSize / AUDIO_FRAME_SIZE;
            long remainingBytes = audioFileSize % AUDIO_FRAME_SIZE;
            if (remainingBytes > 0) {
                totalFrames++;
            }
            double estimatedDuration = (totalFrames * FRAME_INTERVAL_MS) / 1000.0;
            System.out.printf("【发送配置】音频文件大小：%d字节 | 总帧数：%d | 预估时长：%.1f秒%n", audioFileSize, totalFrames, estimatedDuration);
            System.out.printf("【发送配置】每%dms发送%d字节，严格控制节奏%n", FRAME_INTERVAL_MS, AUDIO_FRAME_SIZE);

            // 发送音频帧
            byte[] buffer = new byte[AUDIO_FRAME_SIZE];
            int bytesRead;
            int frameIndex = 0;
            Long startTime = null;

            while ((bytesRead = fis.read(buffer)) != -1) {
                // 处理实际读取的字节数（最后一帧可能不足AUDIO_FRAME_SIZE）
                byte[] frameData = bytesRead == AUDIO_FRAME_SIZE ? buffer : Arrays.copyOf(buffer, bytesRead);

                // 记录起始时间
                if (startTime == null) {
                    startTime = System.currentTimeMillis();
                    System.out.println("【发送开始】起始时间：" + startTime + "ms（基准时间）");
                }

                // 计算理论发送时间
                long expectedSendTime = startTime + (frameIndex * FRAME_INTERVAL_MS);
                long currentTime = System.currentTimeMillis();
                long timeDiff = expectedSendTime - currentTime;

                // 动态调整休眠时间
                if (timeDiff > 1) {  // 大于1ms才休眠
                    try {
                        Thread.sleep(timeDiff);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }

                // 发送音频帧（二进制消息）
                webSocketClient.send(frameData);

                // 打印节奏控制日志（每10帧）
                if (frameIndex % 10 == 0) {
                    long actualSendTime = System.currentTimeMillis();
                    System.out.printf("【节奏控制】帧%d | 理论时间：%dms | 实际时间：%dms | 误差：%.1fms%n", frameIndex, expectedSendTime, actualSendTime, (actualSendTime - expectedSendTime) * 1.0);
                }

                frameIndex++;
            }

            System.out.println("【发送完成】所有音频帧发送完毕（共" + frameIndex + "帧）");

            // 发送结束标记（使用org.json构建JSON）
            JSONObject endMsg = new JSONObject();
            endMsg.put("end", true);
            if (sessionId != null && !sessionId.isEmpty()) {
                endMsg.put("sessionId", sessionId);
            }
            String endMsgStr = endMsg.toString();
            webSocketClient.send(endMsgStr);
            System.out.println("【发送结束】已发送标准JSON结束标记：" + endMsgStr);

            return true;
        } catch (FileNotFoundException e) {
            System.err.println("【发送失败】音频文件不存在：" + audioPath);
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("【发送异常】文件读取错误：" + e.getMessage());
            e.printStackTrace();
        } finally {
            isSendingAudio.set(false);
        }
        return false;
    }

    /**
     * 生成鉴权参数
     */
    private Map<String, String> generateAuthParams() {
        Map<String, String> params = new TreeMap<>();  // TreeMap保证字典序排序

        // 固定参数
        params.put("audio_encode", AUDIO_ENCODE);
        params.put("lang", LANG);
        params.put("samplerate", SAMPLERATE);

        // 动态参数
        params.put("accessKeyId", accessKeyId);
        params.put("appId", appId);
        params.put("uuid", UUID.randomUUID().toString().replaceAll("-", ""));
        params.put("utc", getUtcTime());

        // 计算签名
        String signature = calculateSignature(params);
        params.put("signature", signature);

        return params;
    }

    /**
     * 生成UTC时间字符串（yyyy-MM-dd'T'HH:mm:ss+0800）
     */
    private String getUtcTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(new Date());
    }

    /**
     * 计算HMAC-SHA1签名
     */
    private String calculateSignature(Map<String, String> params) {
        try {
            // 构建基础字符串
            StringBuilder baseStr = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                // 跳过signature参数
                if ("signature".equals(key)) continue;
                // 过滤空值
                if (value == null || value.trim().isEmpty()) continue;

                if (!first) {
                    baseStr.append("&");
                }
                baseStr.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name())).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
                first = false;
            }

            // HMAC-SHA1计算
            Mac mac = Mac.getInstance("HmacSHA1");
            SecretKeySpec keySpec = new SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] signBytes = mac.doFinal(baseStr.toString().getBytes(StandardCharsets.UTF_8));

            // Base64编码
            return Base64.getEncoder().encodeToString(signBytes);
        } catch (Exception e) {
            throw new RuntimeException("计算签名失败", e);
        }
    }

    /**
     * 构建参数字符串
     */
    private String buildParamsString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            try {
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name())).append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException e) {
                // UTF-8编码总是支持的
                e.printStackTrace();
            }
            first = false;
        }
        return sb.toString();
    }

    /**
     * 关闭连接
     */
    public void close() {
        if (isConnected.get() && webSocketClient != null) {
            isConnected.set(false);
            webSocketClient.close();
            System.out.println("【连接关闭】WebSocket已安全关闭");
        } else {
            System.out.println("【连接关闭】WebSocket已断开或未初始化");
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    public static void main(String[] args) {
        // 替换为实际参数
        String appId = "";	//你的appid
        String accessKeyId = "";	//你的apiKey
        String accessKeySecret = "";	//你的apiSecret
        String audioPath = "src/main/resources/1.pcm";

        RTASRClient client = new RTASRClient(appId, accessKeyId, accessKeySecret, audioPath);
        try {
            // 建立连接
            if (!client.connect()) {
                System.out.println("【程序退出】连接失败");
                return;
            }

            // 发送音频
            if (!client.sendAudio()) {
                System.out.println("【程序退出】音频发送失败");
                return;
            }

            // 等待识别结果
            long estimatedDuration = (client.audioFileSize / AUDIO_FRAME_SIZE) * FRAME_INTERVAL_MS / 1000;
            int waitTime = (int) estimatedDuration + 5;
            System.out.printf("【等待结果】预估识别时长%.1f秒，等待%d秒接收结果...%n", estimatedDuration * 1.0, waitTime);

            // 循环等待并检查连接状态
            for (int i = 0; i < waitTime; i++) {
                if (!client.isConnected.get()) {
                    System.out.println("【等待中断】连接已关闭，提前结束等待");
                    break;
                }
                Thread.sleep(1000);
            }

            System.out.println("【程序结束】识别流程完成");
        } catch (InterruptedException e) {
            System.out.println("【程序退出】用户手动中断");
            Thread.currentThread().interrupt();
        } finally {
            client.close();
        }
    }
}
    