package com.smartmeeting.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.entity.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞办公离线语音转写（IST v2）HTTP 客户端。
 * <p>
 * 协作组件：{@link RestTemplate} 上传与轮询、{@link ObjectMapper} 解析 lattice 结果，
 * 输出 {@link com.smartmeeting.entity.TranscriptSegment} 列表供纪要/回放等业务使用。
 * </p>
 * <p>
 * 流程：{@code POST /v2/upload} 上传二进制 PCM → 获得 orderId → {@code POST /v2/getResult}
 * 轮询直至 status=4 → 解析 orderResult。
 * </p>
 * <p>
 * 文档参考：<a href="https://www.xfyun.cn/doc/spark/asr_llm/Ifasr_llm.html">Ifasr_llm</a>。
 * 注意查询参数中的 dateTime 在 URL 上不可整体 URL 编码；签名计算时对键值单独编码。
 * </p>
 *
 * @see TranscriptSegment
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XfyunOfflineClient {

    @Value("${meeting.asr.xfyun.app-id:}")
    private String appId;

    @Value("${meeting.asr.xfyun.api-key:}")
    private String accessKeyId;  // 文档中叫 accessKeyId

    @Value("${meeting.asr.xfyun.api-secret:}")
    private String accessKeySecret;  // 文档中叫 accessKeySecret

    @Value("${meeting.asr.offline-role-enabled:true}")
    private boolean offlineRoleEnabled;

    private static final String BASE_URL = "https://office-api-ist-dx.iflyaisol.com";
    private static final int MAX_RETRIES = 60;
    private static final int POLL_INTERVAL_MS = 5000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 上传音频并离线转写，返回带时间戳的分段列表。
     *
     * @param audioData PCM，16 kHz、16 bit、单声道
     * @param filename  上传时使用的文件名（参与签名与 fileName 参数）
     * @return 转写分段；上传失败、轮询超时或解析失败时返回空列表
     */
    public List<TranscriptSegment> transcribe(byte[] audioData, String filename) {
        log.info("【离线ASR】Starting: {} bytes, filename={}", audioData.length, filename);

        try {
            // 生成请求参数
            String dateTime = generateDateTime();
            String signatureRandom = generateRandomString();
            int duration = estimateDuration(audioData);  // 估算音频时长（毫秒）

            // 构建参数Map（用于签名）
            Map<String, String> params = new TreeMap<>();
            params.put("appId", appId);
            params.put("accessKeyId", accessKeyId);
            params.put("dateTime", dateTime);
            params.put("signatureRandom", signatureRandom);
            params.put("fileSize", String.valueOf(audioData.length));
            params.put("fileName", filename);
            params.put("language", "autodialect");  // 中英+方言免切
            params.put("duration", String.valueOf(duration));
            params.put("roleType", offlineRoleEnabled ? "1" : "0");

            // 生成签名
            String signature = generateSignature(params);
            
            log.info("【离线ASR】签名参数: dateTime={}, signatureRandom={}", dateTime, signatureRandom);
            log.info("【离线ASR】签名结果: {}", signature.substring(0, Math.min(20, signature.length())));

            // Step 1: 上传音频（二进制流）
            String orderId = uploadAudio(audioData, params, signature);
            if (orderId == null) {
                log.error("【离线ASR】Upload failed: no orderId");
                return List.of();
            }

            log.info("【离线ASR】Upload success: orderId={}, duration={}ms", orderId, duration);

            // Step 2: 轮询获取结果
            String result = pollResult(orderId, dateTime, signatureRandom);
            if (result == null) {
                log.error("【离线ASR】Poll failed for order: {}", orderId);
                return List.of();
            }

            // Step 3: 解析转写结果
            return parseResult(result);

        } catch (Exception e) {
            log.error("【离线ASR】Failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 从本地文件路径读取音频后转写（兼容旧调用方）。
     *
     * @param audioPath 本地音频文件绝对或相对路径
     * @return 转写分段；读文件失败时返回空列表
     */
    public List<TranscriptSegment> transcribe(String audioPath) {
        log.warn("Using deprecated file path method: {}", audioPath);
        try {
            byte[] audioData = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(audioPath));
            String filename = java.nio.file.Paths.get(audioPath).getFileName().toString();
            return transcribe(audioData, filename);
        } catch (Exception e) {
            log.error("Failed to read audio file: {}", audioPath, e);
            return List.of();
        }
    }

    /**
     * 生成讯飞要求的 dateTime（东八区，无冒号时区后缀）。
     *
     * @return 形如 {@code 2025-09-08T22:58:29+0800}
     */
    private String generateDateTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        String base = sdf.format(new java.util.Date());
        return base + "+0800";  // 手动添加时区（确保无冒号）
    }

    /**
     * 生成 16 位 signatureRandom（UUID 去横线后截取）。
     *
     * @return 随机串
     */
    private String generateRandomString() {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        return uuid.substring(0, 16);
    }

    /**
     * 按 PCM 16 kHz/16 bit/单声道估算时长（毫秒），用于 upload 的 duration 参数。
     *
     * @param audioData 原始 PCM 字节
     * @return 时长毫秒数
     */
    private int estimateDuration(byte[] audioData) {
        return (int) (audioData.length / 32000.0 * 1000);
    }

    /**
     * 按讯飞 v2 规则生成 HMAC-SHA1 签名（Base64）。
     *
     * @param params 参与签名的查询参数（不含 signature 字段本身）
     * @return Base64 签名串
     * @throws Exception 加密或编码异常
     */
    private String generateSignature(Map<String, String> params) throws Exception {
        TreeMap<String, String> treeMap = new TreeMap<>(params);
        treeMap.remove("signature");  // 排除signature字段

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : treeMap.entrySet()) {
            String value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                // 签名计算时需要URL编码
                String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
                builder.append(entry.getKey()).append("=").append(encodedValue).append("&");
            }
        }
        
        if (builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);  // 删除最后的&
        }
        
        String baseString = builder.toString();
        log.debug("【离线ASR】baseString: {}", baseString.substring(0, Math.min(100, baseString.length())));

        // HMAC-SHA1签名
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        javax.crypto.spec.SecretKeySpec keySpec = 
            new javax.crypto.spec.SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(keySpec);
        byte[] signBytes = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
        
        return Base64.getEncoder().encodeToString(signBytes);
    }

    /**
     * 以二进制流上传音频至 {@code /v2/upload}，URL 查询参数不整体编码。
     *
     * @param audioData  PCM 正文
     * @param params     与签名一致的查询参数 Map
     * @param signature  请求头 signature 值
     * @return 成功时的 orderId；失败返回 null
     * @throws Exception HTTP 或 JSON 解析异常
     */
    private String uploadAudio(byte[] audioData, Map<String, String> params, String signature) throws Exception {
        // 构建URL查询参数（不编码）
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/v2/upload?");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            // 不编码参数值（讯飞不接受编码后的dateTime）
            urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        urlBuilder.deleteCharAt(urlBuilder.length() - 1);  // 删除最后的&
        
        String url = urlBuilder.toString();
        log.info("【离线ASR】Upload URL: {}", url.substring(0, Math.min(150, url.length())));

        // 设置Headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);  // 二进制流
        headers.set("signature", signature);

        // 发送二进制音频数据
        HttpEntity<byte[]> request = new HttpEntity<>(audioData, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        log.info("【离线ASR】Upload response: code={}, body={}", response.getStatusCode(), 
            response.getBody().length() > 200 ? response.getBody().substring(0, 200) + "..." : response.getBody());

        if (!response.getStatusCode().is2xxSuccessful()) {
            log.error("【离线ASR】Upload failed: {}", response.getStatusCode());
            return null;
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String code = root.path("code").asText("");
        if (!code.equals("000000")) {
            log.error("【离线ASR】Upload error: code={}, desc={}", code, root.path("descInfo").asText());
            return null;
        }

        String orderId = root.path("content").path("orderId").asText(null);
        int taskEstimateTime = root.path("content").path("taskEstimateTime").asInt(0);
        log.info("【离线ASR】Upload success: orderId={}, estimateTime={}ms", orderId, taskEstimateTime);
        return orderId;
    }

    /**
     * 轮询 {@code /v2/getResult} 直至订单完成或失败/超时。
     *
     * @param orderId                 上传返回的订单号
     * @param uploadDateTime          上传时的 dateTime（日志用，轮询会重新生成）
     * @param uploadSignatureRandom   与上传一致的 signatureRandom
     * @return 完成时的 orderResult JSON 字符串；失败或超时返回 null
     * @throws Exception 睡眠中断或 HTTP 异常
     */
    private String pollResult(String orderId, String uploadDateTime, String uploadSignatureRandom) throws Exception {
        // 每次轮询需要重新生成dateTime（文档要求）
        String dateTime = generateDateTime();
        String signatureRandom = uploadSignatureRandom;  // 文档说使用相同的random

        Map<String, String> params = new TreeMap<>();
        params.put("accessKeyId", accessKeyId);
        params.put("dateTime", dateTime);
        params.put("signatureRandom", signatureRandom);
        params.put("orderId", orderId);
        params.put("resultType", "transfer");  // 转写结果

        String signature = generateSignature(params);

        // 构建URL（不编码）
        StringBuilder urlBuilder = new StringBuilder(BASE_URL + "/v2/getResult?");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            // 不编码参数值（讯飞不接受编码后的dateTime）
            urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        urlBuilder.deleteCharAt(urlBuilder.length() - 1);
        
        String url = urlBuilder.toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("signature", signature);

        for (int i = 0; i < MAX_RETRIES; i++) {
            // 请求体为空JSON对象
            HttpEntity<String> request = new HttpEntity<>("{}", headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("【离线ASR】Get result failed: {}", response.getStatusCode());
                TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);
                continue;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String code = root.path("code").asText("");
            
            if (!code.equals("000000")) {
                log.error("【离线ASR】Get result error: code={}, desc={}", code, root.path("descInfo").asText());
                return null;
            }

            int status = root.path("content").path("orderInfo").path("status").asInt(-1);
            
            if (status == 4) {
                // 转写完成
                log.info("【离线ASR】Completed for order {}", orderId);
                String orderResult = root.path("content").path("orderResult").asText("");
                if (orderResult.isEmpty()) {
                    log.warn("【离线ASR】Empty orderResult");
                    return null;
                }
                return orderResult;
            } else if (status == 3) {
                // 处理中
                log.info("【离线ASR】Processing... ({}/{})", i + 1, MAX_RETRIES);
            } else if (status == 0) {
                // 订单已创建
                log.info("【离线ASR】Created... ({}/{})", i + 1, MAX_RETRIES);
            } else if (status == -1) {
                // 失败
                int failType = root.path("content").path("orderInfo").path("failType").asInt(99);
                log.error("【离线ASR】Order failed: failType={}", failType);
                return null;
            }

            TimeUnit.MILLISECONDS.sleep(POLL_INTERVAL_MS);
        }

        log.error("【离线ASR】Poll timeout for order {}", orderId);
        return null;
    }

    /**
     * 解析 lattice / json_1best 结构为 {@link TranscriptSegment} 列表；失败时降级为单段全文。
     *
     * @param orderResult getResult 返回的 orderResult 字段内容
     * @return 分段列表，至少可能含一段降级文本
     */
    private List<TranscriptSegment> parseResult(String orderResult) {
        return OfflineAsrLatticeParser.parse(orderResult);
    }
}