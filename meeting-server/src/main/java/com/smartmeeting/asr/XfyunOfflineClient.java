package com.smartmeeting.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmeeting.config.MeetingAsrProperties;
import com.smartmeeting.entity.TranscriptSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞办公离线语音转写（IST v2）HTTP 客户端。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XfyunOfflineClient {

    @Value("${meeting.asr.xfyun.app-id:}")
    private String appId;

    @Value("${meeting.asr.xfyun.api-key:}")
    private String accessKeyId;

    @Value("${meeting.asr.xfyun.api-secret:}")
    private String accessKeySecret;

    private static final String BASE_URL = "https://office-api-ist-dx.iflyaisol.com";
    private static final ZoneId XFYUN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter XFYUN_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final ObjectMapper objectMapper;
    private final MeetingAsrProperties asrProperties;

    private static final RestTemplate IST_HTTP = createIstRestTemplate();

    private static RestTemplate createIstRestTemplate() {
        RestTemplate rt = new RestTemplate();
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory();
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        rt.setUriTemplateHandler(factory);
        return rt;
    }

    /**
     * 默认盲分/自动参数转写（无参会人声纹 hint）。
     */
    public List<TranscriptSegment> transcribe(String audioPath) {
        OfflineIstOptions options = OfflineIstParamBuilder.resolve(asrProperties, List.of(), 0);
        return transcribe(audioPath, options);
    }

    /**
     * 按 IST 说话人参数转写；roleType=3 上传失败时自动回退 roleType=1 重试一次。
     */
    public List<TranscriptSegment> transcribe(String audioPath, OfflineIstOptions istOptions) {
        try {
            byte[] audioData = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(audioPath));
            String filename = java.nio.file.Paths.get(audioPath).getFileName().toString();
            return transcribe(audioData, filename, istOptions);
        } catch (Exception e) {
            log.error("Failed to read audio file: {}", audioPath, e);
            return List.of();
        }
    }

    /**
     * 上传音频并离线转写，返回带时间戳的分段列表。
     */
    public List<TranscriptSegment> transcribe(byte[] audioData, String filename) {
        OfflineIstOptions options = OfflineIstParamBuilder.resolve(asrProperties, List.of(), 0);
        return transcribe(audioData, filename, options);
    }

    public List<TranscriptSegment> transcribe(byte[] audioData, String filename, OfflineIstOptions istOptions) {
        log.info("【离线ASR】Starting: {} bytes, filename={}, roleType={}, roleNum={}",
                audioData.length, filename, istOptions.roleType(), istOptions.roleNum());

        List<TranscriptSegment> result = doTranscribe(audioData, filename, istOptions);
        if (!result.isEmpty() || istOptions.roleType() != 3) {
            return result;
        }

        log.warn("【离线ASR】roleType=3 upload/transcribe failed, falling back to blind separation");
        OfflineIstOptions fallback = OfflineIstOptions.blind(istOptions.roleNum());
        return doTranscribe(audioData, filename, fallback);
    }

    private List<TranscriptSegment> doTranscribe(byte[] audioData, String filename, OfflineIstOptions istOptions) {
        try {
            String dateTime = generateDateTime();
            String signatureRandom = generateRandomString();
            int duration = estimateDuration(audioData);

            Map<String, String> params = buildUploadParams(
                    appId, accessKeyId, dateTime, signatureRandom,
                    audioData.length, filename, duration, istOptions);
            String signature = generateSignature(params);

            log.info("【离线ASR】roleType={}, roleNum={}, featureIds={}",
                    istOptions.roleType(), istOptions.roleNum(),
                    istOptions.featureIdsCsv() != null ? "present" : "none");

            UploadResult upload = uploadAudio(audioData, params, signature);
            if (upload.orderId() == null) {
                log.error("【离线ASR】Upload failed: code={}", upload.errorCode());
                return List.of();
            }

            String result = pollResult(upload.orderId(), dateTime, signatureRandom);
            if (result == null) {
                log.error("【离线ASR】Poll failed for order: {}", upload.orderId());
                return List.of();
            }
            return parseResult(result);
        } catch (Exception e) {
            log.error("【离线ASR】Failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 构建 IST upload 签名参数（供单测断言）。
     */
    static Map<String, String> buildUploadParams(String appId,
                                                 String accessKeyId,
                                                 String dateTime,
                                                 String signatureRandom,
                                                 int fileSize,
                                                 String filename,
                                                 int duration,
                                                 OfflineIstOptions istOptions) {
        Map<String, String> params = new TreeMap<>();
        params.put("appId", appId);
        params.put("accessKeyId", accessKeyId);
        params.put("dateTime", dateTime);
        params.put("signatureRandom", signatureRandom);
        params.put("fileSize", String.valueOf(fileSize));
        params.put("fileName", filename);
        params.put("language", "autodialect");
        params.put("duration", String.valueOf(duration));

        int roleType = istOptions != null ? istOptions.roleType() : 1;
        params.put("roleType", String.valueOf(roleType));

        if (istOptions != null && roleType != 0) {
            params.put("roleNum", String.valueOf(Math.max(0, istOptions.roleNum())));
        }
        if (istOptions != null && roleType == 3
                && istOptions.featureIdsCsv() != null && !istOptions.featureIdsCsv().isBlank()) {
            params.put("featureIds", istOptions.featureIdsCsv());
        }
        return params;
    }

    static String generateDateTime() {
        return OffsetDateTime.now(XFYUN_ZONE).format(XFYUN_DATE_TIME);
    }

    private String generateRandomString() {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        return uuid.substring(0, 16);
    }

    private int estimateDuration(byte[] audioData) {
        return (int) (audioData.length / 32000.0 * 1000);
    }

    static String generateSignature(Map<String, String> params, String accessKeySecret) throws Exception {
        TreeMap<String, String> treeMap = new TreeMap<>(params);
        treeMap.remove("signature");

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : treeMap.entrySet()) {
            String value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
                builder.append(entry.getKey()).append("=").append(encodedValue).append("&");
            }
        }
        if (builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        javax.crypto.spec.SecretKeySpec keySpec =
                new javax.crypto.spec.SecretKeySpec(accessKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(keySpec);
        byte[] signBytes = mac.doFinal(builder.toString().getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signBytes);
    }

    private String generateSignature(Map<String, String> params) throws Exception {
        return generateSignature(params, accessKeySecret);
    }

    record UploadResult(String orderId, String errorCode) {
    }

    private UploadResult uploadAudio(byte[] audioData, Map<String, String> params, String signature) throws Exception {
        String url = buildIstRequestUrl("/v2/upload", params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("signature", signature);

        HttpEntity<byte[]> request = new HttpEntity<>(audioData, headers);
        ResponseEntity<String> response = IST_HTTP.postForEntity(url, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            return new UploadResult(null, "HTTP_" + response.getStatusCode().value());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String code = root.path("code").asText("");
        if (!code.equals("000000")) {
            return new UploadResult(null, code);
        }

        String orderId = root.path("content").path("orderId").asText(null);
        return new UploadResult(orderId, code);
    }

    private String pollResult(String orderId, String uploadDateTime, String uploadSignatureRandom) throws Exception {
        String dateTime = generateDateTime();
        String signatureRandom = uploadSignatureRandom;

        Map<String, String> params = new TreeMap<>();
        params.put("accessKeyId", accessKeyId);
        params.put("dateTime", dateTime);
        params.put("signatureRandom", signatureRandom);
        params.put("orderId", orderId);
        params.put("resultType", "transfer");

        String signature = generateSignature(params);
        String url = buildIstRequestUrl("/v2/getResult", params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("signature", signature);

        for (int i = 0; i < pollMaxRetries(); i++) {
            HttpEntity<String> request = new HttpEntity<>("{}", headers);
            ResponseEntity<String> response = IST_HTTP.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                TimeUnit.MILLISECONDS.sleep(pollIntervalMs());
                continue;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String code = root.path("code").asText("");
            if (!code.equals("000000")) {
                return null;
            }

            int status = root.path("content").path("orderInfo").path("status").asInt(-1);
            if (status == 4) {
                String orderResult = root.path("content").path("orderResult").asText("");
                return orderResult.isEmpty() ? null : orderResult;
            } else if (status == -1) {
                return null;
            }
            TimeUnit.MILLISECONDS.sleep(pollIntervalMs());
        }
        return null;
    }

    private int pollMaxRetries() {
        if (asrProperties == null) {
            return 60;
        }
        return Math.max(1, asrProperties.getOfflinePollMaxRetries());
    }

    private int pollIntervalMs() {
        if (asrProperties == null) {
            return 5000;
        }
        return Math.max(1000, asrProperties.getOfflinePollIntervalMs());
    }

    static String encodeQueryParam(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String buildEncodedQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(encodeQueryParam(entry.getValue()));
        }
        return sb.toString();
    }

    private String buildIstRequestUrl(String path, Map<String, String> params) {
        return BASE_URL + path + "?" + buildEncodedQueryString(params);
    }

    private List<TranscriptSegment> parseResult(String orderResult) {
        return OfflineAsrLatticeParser.parse(orderResult);
    }
}
