package com.smartmeeting.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 讯飞（Xfyun）API 签名与鉴权 URL 构建工具类。
 * <p>
 * 支持离线 ASR、ISV API、office-api-ast 实时 ASR WebSocket 等多种签名算法。
 */
public class XfyunSignatureUtil {

    /**
     * 获取 RFC 2616 格式的 GMT 日期字符串。
     *
     * @return 如 {@code Wed, 20 May 2026 08:00:00 GMT} 格式的日期
     */
    public static String getRFC2616Date() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new java.util.Date());
    }

    /**
     * 使用 HMAC-SHA256 算法计算签名。
     *
     * @param data 待签名数据
     * @param key  密钥
     * @return Base64 编码的签名字符串
     * @throws Exception 加密算法不可用时抛出
     */
    public static String hmacSHA256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    /**
     * 使用 HMAC-SHA1 算法计算签名。
     *
     * @param data 待签名数据
     * @param key  密钥
     * @return Base64 编码的签名字符串
     * @throws Exception 加密算法不可用时抛出
     */
    public static String hmacSHA1(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    /**
     * 生成讯飞离线 ASR API 签名（参数按 key 排序后 MD5）。
     *
     * @param apiKey    讯飞 API Key
     * @param apiSecret 讯飞 API Secret
     * @param params    请求参数 Map
     * @return 十六进制 MD5 签名字符串
     * @throws RuntimeException 签名计算失败时包装抛出
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
     * 生成讯飞 ISV API 签名（支持复杂 JSON body，JSON + apiSecret 后 MD5）。
     *
     * @param apiKey    讯飞 API Key
     * @param apiSecret 讯飞 API Secret
     * @param params    请求参数 Map（支持嵌套对象）
     * @return 十六进制 MD5 签名字符串
     * @throws RuntimeException 签名计算失败时包装抛出
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
     * 生成北京时间字符串（格式：yyyy-MM-dd'T'HH:mm:ss+0800）。
     * <p>
     * 用于讯飞 office-api-ast 实时 ASR 鉴权。
     *
     * @return 东八区 ISO 8601 格式时间字符串
     */
    public static String getBeijingTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(new java.util.Date());
    }

    /**
     * 生成讯飞 office-api-ast 实时 ASR WebSocket 鉴权 URL。
     * <p>
     * 参考 RTASR_LLM_java Demo，使用 HMAC-SHA1 签名。
     *
     * @param baseUrl    WebSocket 基础 URL
     * @param appId      讯飞 App ID
     * @param apiKey     讯飞 API Key（accessKeyId）
     * @param apiSecret  讯飞 API Secret
     * @param lang       语言（autodialect）
     * @param roleType   角色类型（2=说话人分离）
     * @param featureIds 声纹特征 ID 列表（声纹匹配用，可为 null）
     * @return 含签名参数的完整鉴权 URL
     * @throws Exception 签名或 URL 编码失败时抛出
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
     * 生成讯飞 ASR WebSocket 鉴权 URL（旧版通用 HMAC-SHA256 方案）。
     *
     * @param baseUrl   {@code wss://host/path} 格式的基础 URL
     * @param apiKey    讯飞 API Key
     * @param apiSecret 讯飞 API Secret
     * @return 含 authorization、date、host 查询参数的完整鉴权 URL
     * @throws Exception 签名或 URL 编码失败时抛出
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
                encodeQueryParam(authorization),
                encodeQueryParam(date),
                encodeQueryParam(host));
    }

    /**
     * 讯飞 ISV / 私有化 api.xf-yun.com POST 鉴权 URL（HMAC-SHA256，date 为 GMT RFC1123）。
     *
     * @param requestUrl 如 {@code https://api.xf-yun.com/v1/private/s1aa729d0}
     * @param apiKey     控制台 APIKey
     * @param apiSecret  控制台 APISecret
     * @return 含 authorization/date/host 查询参数的完整 URL 及签名用 date 字符串
     */
    public static IsvAuthContext buildIsvPostAuthUrl(String requestUrl, String apiKey, String apiSecret) throws Exception {
        URI endpoint = URI.create(requestUrl.trim());
        String scheme = endpoint.getScheme() == null ? "https" : endpoint.getScheme();
        String host = endpoint.getHost();
        String path = endpoint.getRawPath();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("invalid ISV request url: " + requestUrl);
        }
        if (path == null || path.isBlank()) {
            path = "/";
        }

        String date = getRFC2616Date();
        String requestLine = "POST " + path + " HTTP/1.1";
        String signatureOrigin = "host: " + host + "\n" + "date: " + date + "\n" + requestLine;
        String signature = hmacSHA256(signatureOrigin, apiSecret);
        String authorizationOrigin = String.format(
                "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                apiKey, signature);
        String authorization = Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

        Map<String, String> query = new LinkedHashMap<>();
        query.put("authorization", authorization);
        query.put("host", host);
        query.put("date", date);
        String signedUrl = scheme + "://" + host + path + "?" + buildEncodedQueryString(query);
        return new IsvAuthContext(date, URI.create(signedUrl));
    }

    /**
     * 查询参数值编码：空格用 {@code %20}，避免 RestTemplate/网关将 {@code +} 解析为空格导致 date 失效。
     */
    public static String encodeQueryParam(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String buildEncodedQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(entry.getKey()).append('=').append(encodeQueryParam(entry.getValue()));
        }
        return sb.toString();
    }

    /** ISV 鉴权上下文：HTTP Date 头与已签名的请求 URI。 */
    public record IsvAuthContext(String date, URI signedUri) {
    }
}
