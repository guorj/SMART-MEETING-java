package com.smartmeeting.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

public class XfyunSignatureUtil {

    public static String getRFC2616Date() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new java.util.Date());
    }

    public static String hmacSHA256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    public static String hmacSHA1(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    /**
     * 生成讯飞离线 ASR API 签名
     * @param apiKey 讯飞 API Key
     * @param apiSecret 讯飞 API Secret
     * @param params 请求参数 Map
     * @return 签名字符串
     */
    public static String generateSignature(String apiKey, String apiSecret, Map<String, String> params) {
        try {
            // 1. 参数按 key 排序
            List<String> keys = new ArrayList<>(params.keySet());
            Collections.sort(keys);

            // 2. 拼接参数
            StringBuilder sb = new StringBuilder();
            for (String key : keys) {
                sb.append(key).append("=").append(params.get(key));
            }
            sb.append(apiSecret);

            // 3. MD5 摘要
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * 生成讯飞 ISV API 签名（支持复杂 JSON body）
     * @param apiKey 讯飞 API Key
     * @param apiSecret 讯飞 API Secret
     * @param params 请求参数 Map（支持嵌套对象）
     * @return 签名字符串
     */
    public static String generateSignatureForObject(String apiKey, String apiSecret, Map<String, Object> params) {
        try {
            // 将 Map 转成 JSON 字符串
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonStr = objectMapper.writeValueAsString(params);
            
            // 拼接 apiSecret
            String signStr = jsonStr + apiSecret;
            
            // MD5 摘要
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(signStr.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * 生成北京时间字符串（格式：yyyy-MM-dd'T'HH:mm:ss+0800）
     * 用于讯飞 office-api-ast 实时ASR鉴权
     */
    public static String getBeijingTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(new java.util.Date());
    }

    /**
     * 生成讯飞 office-api-ast 实时 ASR WebSocket 鉴权 URL
     * 参考 RTASR_LLM_java Demo
     * 
     * @param baseUrl WebSocket 基础 URL
     * @param appId 讯飞 App ID
     * @param apiKey 讯飞 API Key（accessKeyId）
     * @param apiSecret 讯飞 API Secret
     * @param lang 语言（autodialect）
     * @param roleType 角色类型（2=说话人分离）
     * @param featureIds 特性ID列表（声纹匹配用）
     * @return 完整的鉴权 URL
     */
    public static String buildOfficeApiAuthUrl(String baseUrl, String appId, String apiKey, String apiSecret,
                                                  String lang, int roleType, List<String> featureIds) throws Exception {
        String utc = getBeijingTime();
        // UUID去掉横线（参考Demo）
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        
        // 使用 TreeMap 保证字典序排序
        TreeMap<String, String> params = new TreeMap<>();
        
        // 固定参数
        params.put("audio_encode", "pcm_s16le");
        params.put("lang", lang);
        params.put("samplerate", "16000");
        
        // 动态参数
        params.put("accessKeyId", apiKey);
        params.put("appId", appId);
        params.put("uuid", uuid);
        params.put("utc", utc);
        params.put("role_type", String.valueOf(roleType));
        
        if (featureIds != null && !featureIds.isEmpty()) {
            params.put("feature_ids", String.join(",", featureIds));
            params.put("eng_spk_match", "1");
        }
        
        // 计算签名（跳过空值，跳过 signature 参数）
        StringBuilder baseString = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("signature".equals(key)) continue;
            if (value == null || value.trim().isEmpty()) continue;
            
            if (!first) baseString.append("&");
            baseString.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name()));
            baseString.append("=");
            baseString.append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
            first = false;
        }
        
        // HMAC-SHA1 签名
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec keySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(keySpec);
        byte[] signBytes = mac.doFinal(baseString.toString().getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signBytes);
        
        params.put("signature", signature);
        
        // 构建完整 URL
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?");
        boolean firstParam = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!firstParam) urlBuilder.append("&");
            urlBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
            urlBuilder.append("=");
            urlBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
            firstParam = false;
        }
        
        return urlBuilder.toString();
    }

    /**
     * 生成讯飞 ASR WebSocket 鉴权 URL（旧版通用）
     * @param baseUrl wss://host/path 格式的基础 URL
     * @param apiKey 讯飞 API Key
     * @param apiSecret 讯飞 API Secret
     * @return 完整的鉴权 URL
     */
    public static String assembleAuthUrl(String baseUrl, String apiKey, String apiSecret) throws Exception {
        // 解析 URL
        String scheme = baseUrl.startsWith("wss://") ? "wss" : "ws";
        String hostPath = baseUrl.replaceFirst("^wss?://", "");
        String[] parts = hostPath.split("/", 2);
        String host = parts[0];
        String path = parts.length > 1 ? "/" + parts[1] : "/";

        String date = getRFC2616Date();

        // 签名原始串
        String signatureOrigin = String.format("host: %s\ndate: %s\nGET %s HTTP/1.1", host, date, path);

        // HMAC-SHA256 签名
        String signature = hmacSHA256(signatureOrigin, apiSecret);

        // 组装 authorization
        String authorizationOrigin = String.format(
                "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                apiKey, signature);
        String authorization = Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

        // 组装最终 URL
        return String.format("%s://%s%s?authorization=%s&date=%s&host=%s",
                scheme,
                host,
                path,
                URLEncoder.encode(authorization, StandardCharsets.UTF_8),
                URLEncoder.encode(date, StandardCharsets.UTF_8),
                URLEncoder.encode(host, StandardCharsets.UTF_8));
    }
}
