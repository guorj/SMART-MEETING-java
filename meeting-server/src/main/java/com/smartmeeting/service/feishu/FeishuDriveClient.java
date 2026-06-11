package com.smartmeeting.service.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 飞书云盘下载与异步导出（file / slides 等 Wiki 节点）。
 */
public final class FeishuDriveClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FeishuDriveClient() {
    }

    public static byte[] downloadFile(
            RestTemplate restTemplate, String baseUrl, String tenantToken, String fileToken) {
        if (fileToken == null || fileToken.isBlank()) {
            throw new IllegalArgumentException("file_token 为空");
        }
        String root = normalizeBaseUrl(baseUrl);
        String url = root + "/open-apis/drive/v1/files/" + fileToken.trim() + "/download";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("飞书文件下载失败: HTTP " + response.getStatusCode());
        }
        return response.getBody();
    }

    /**
     * 创建导出任务并轮询，返回导出产物字节（如 slides → pdf）。
     */
    public static byte[] exportDocument(
            RestTemplate restTemplate,
            String baseUrl,
            String tenantToken,
            String docToken,
            String docType,
            String fileExtension,
            int maxWaitMs) {
        if (docToken == null || docToken.isBlank()) {
            throw new IllegalArgumentException("doc_token 为空");
        }
        String root = normalizeBaseUrl(baseUrl);
        String createUrl = root + "/open-apis/drive/v1/export_tasks";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        headers.set("Content-Type", "application/json; charset=utf-8");
        try {
            String body = MAPPER.writeValueAsString(java.util.Map.of(
                    "token", docToken.trim(),
                    "type", docType,
                    "file_extension", fileExtension));
            ResponseEntity<JsonNode> createResp = restTemplate.exchange(
                    createUrl, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode createJson = createResp.getBody();
            int code = createJson != null ? createJson.path("code").asInt(-1) : -1;
            if (code != 0) {
                throw new RuntimeException("飞书 export_tasks 创建失败 code=" + code
                        + " msg=" + (createJson != null ? createJson.path("msg").asText("") : ""));
            }
            String ticket = createJson.path("data").path("ticket").asText("");
            if (ticket.isBlank()) {
                throw new RuntimeException("飞书 export_tasks 未返回 ticket");
            }
            long deadline = System.currentTimeMillis() + Math.max(3000, maxWaitMs);
            while (System.currentTimeMillis() < deadline) {
                String pollUrl = root + "/open-apis/drive/v1/export_tasks/" + ticket
                        + "?token=" + docToken.trim();
                ResponseEntity<JsonNode> pollResp = restTemplate.exchange(
                        pollUrl, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
                JsonNode pollJson = pollResp.getBody();
                int pollCode = pollJson != null ? pollJson.path("code").asInt(-1) : -1;
                if (pollCode != 0) {
                    throw new RuntimeException("飞书 export_tasks 查询失败 code=" + pollCode);
                }
                JsonNode result = pollJson.path("data").path("result");
                String exportFileToken = result.path("file_token").asText("");
                if (!exportFileToken.isBlank()) {
                    return downloadExportFile(restTemplate, root, tenantToken, exportFileToken);
                }
                Thread.sleep(800);
            }
            throw new RuntimeException("飞书导出超时（" + maxWaitMs + "ms）");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("飞书导出被中断", e);
        } catch (java.io.IOException e) {
            throw new RuntimeException("飞书 export_tasks 请求序列化失败", e);
        }
    }

    private static byte[] downloadExportFile(
            RestTemplate restTemplate, String baseUrl, String tenantToken, String fileToken) {
        String url = baseUrl + "/open-apis/drive/v1/export_tasks/file/" + fileToken.trim() + "/download";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("飞书导出文件下载失败: HTTP " + response.getStatusCode());
        }
        return response.getBody();
    }

    public static Path writeTempFile(byte[] data, String suffix) throws java.io.IOException {
        Path path = Files.createTempFile("feishu-cloud-", suffix);
        Files.write(path, data);
        return path;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://open.feishu.cn";
        }
        return baseUrl.replaceAll("/$", "");
    }
}
