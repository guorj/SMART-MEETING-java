package com.smartmeeting.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.smartmeeting.service.feishu.FeishuResourceKind;
import com.smartmeeting.service.feishu.FeishuResourceRef;

import java.util.*;

/**
 * 飞书 API 封装（消息/文档/token）
 * 
 * API 文档: https://open.feishu.cn/document
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeishuService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final com.smartmeeting.service.host.MeetingHostFeishuMuteRegistry meetingHostFeishuMuteRegistry;

    @Value("${meeting.feishu.app-id:test}")
    private String appId;

    @Value("${meeting.feishu.app-secret:test}")
    private String appSecret;

    @Value("${meeting.feishu.base-url:https://open.feishu.cn}")
    private String baseUrl;

    private volatile String cachedToken = null;
    private volatile long tokenExpiry = 0;

    // ==================== Token 管理 ====================

    /**
     * 获取 tenant_access_token（带缓存，约2小时有效）
     */
    public String getTenantToken() {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedToken;
        }

        String url = baseUrl + "/open-apis/auth/v3/tenant_access_token/internal";
        Map<String, String> body = Map.of(
                "app_id", appId,
                "app_secret", appSecret
        );

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
            JsonNode json = response.getBody();
            if (json != null && json.has("tenant_access_token")) {
                cachedToken = json.get("tenant_access_token").asText();
                tokenExpiry = System.currentTimeMillis() + 7000_000; // 约2小时
                log.info("Feishu token refreshed");
                return cachedToken;
            }
            log.error("Failed to get feishu token: {}", json);
            throw new RuntimeException("Failed to get feishu tenant_access_token");
        } catch (Exception e) {
            log.error("Exception getting feishu token: {}", e.getMessage());
            throw new RuntimeException("Failed to get feishu tenant_access_token: " + e.getMessage());
        }
    }

    // ==================== 消息发送 ====================

    /**
     * 发送文本消息到飞书聊天
     */
    public boolean sendMessage(String chatId, String text) {
        if (meetingHostFeishuMuteRegistry.isMuted(chatId)) {
            log.warn("Feishu send suppressed (AI host in-session): sendMessage chatId={}", chatId);
            return false;
        }
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/im/v1/messages?receive_id_type=chat_id";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = Map.of(
                "receive_id", chatId,
                "msg_type", "text",
                "content", "{\"text\":\"" + escapeJson(text) + "\"}"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Feishu message sent to chat: {}", chatId);
            return true;
        }

        log.error("Failed to send feishu message: {}", response.getBody());
        return false;
    }

    /**
     * 向用户单聊发送文本（菜单「推送事件」场景下可能无群 chat_id，用于提示用户回到群聊操作）
     */
    public boolean sendMessageToOpenId(String openId, String text) {
        if (openId == null || openId.isBlank()) {
            return false;
        }
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/im/v1/messages?receive_id_type=open_id";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = Map.of(
                "receive_id", openId,
                "msg_type", "text",
                "content", "{\"text\":\"" + escapeJson(text) + "\"}"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Feishu message sent to open_id");
            return true;
        }
        log.error("Failed to send feishu message to open_id: {}", response.getBody());
        return false;
    }

    /**
     * 发送富文本卡片消息到飞书聊天
     */
    public boolean sendCardMessage(String chatId, String title, List<Map<String, String>> elements) {
        if (meetingHostFeishuMuteRegistry.isMuted(chatId)) {
            log.warn("Feishu send suppressed (AI host in-session): sendCardMessage chatId={}", chatId);
            return false;
        }
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/im/v1/messages?receive_id_type=chat_id";

        try {
            ObjectNode card = objectMapper.createObjectNode();
            ObjectNode config = card.putObject("config");
            config.put("wide_screen_mode", true);
            
            ObjectNode header = card.putObject("header");
            // 飞书交互卡片要求 title 为富文本节点对象，不能为裸字符串（否则 200621 parse card json err）
            ObjectNode headerTitle = header.putObject("title");
            headerTitle.put("tag", "plain_text");
            headerTitle.put("content", title != null ? title : "");
            header.put("template", "blue");

            ArrayNode bodyElements = card.putArray("elements");
            for (Map<String, String> elem : elements) {
                ObjectNode elementNode = bodyElements.addObject();
                elementNode.put("tag", "div");
                ObjectNode textNode = elementNode.putObject("text");
                textNode.put("content", elem.get("content"));
                textNode.put("tag", "lark_md");
            }

            // 文档链接按钮：放在 elements 内，与 FeishuCardBuilder 一致（根级 actions 非合法卡片结构）
            String docUrl = elements.stream()
                    .filter(e -> e.containsKey("doc_url"))
                    .map(e -> e.get("doc_url"))
                    .findFirst()
                    .orElse(null);

            if (docUrl != null && !docUrl.isEmpty()) {
                ObjectNode actionWrapper = bodyElements.addObject();
                actionWrapper.put("tag", "action");
                ArrayNode actionsInner = actionWrapper.putArray("actions");
                ObjectNode btnNode = actionsInner.addObject();
                btnNode.put("tag", "button");
                ObjectNode btnText = btnNode.putObject("text");
                btnText.put("tag", "plain_text");
                btnText.put("content", "📄 查看纪要文档");
                btnNode.put("url", docUrl);
                btnNode.put("type", "primary");
            }

            Map<String, Object> body = Map.of(
                    "receive_id", chatId,
                    "msg_type", "interactive",
                    "content", objectMapper.writeValueAsString(card)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Feishu card message sent to chat: {}", chatId);
                return true;
            }
            log.error("Failed to send feishu card: {}", response.getBody());
            return false;
        } catch (Exception e) {
            log.error("Exception sending feishu card: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 用户信息获取 ====================

    /**
     * 从飞书API获取用户姓名
     * 
     * @param openId 飞书用户 open_id
     * @return 用户姓名，失败返回 null
     */
    public String getUserName(String openId) {
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/user/v3/users/" + openId + "?user_id_type=open_id";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);
            JsonNode json = response.getBody();
            if (json != null && json.path("code").asInt() == 0) {
                JsonNode user = json.path("data").path("user");
                String name = user.path("name").asText();
                log.info("获取飞书用户姓名: openId={}, name={}", openId, name);
                return name;
            }
            log.warn("获取用户姓名失败: openId={}, code={}", openId, json != null ? json.path("code").asInt() : -1);
            return null;
        } catch (Exception e) {
            log.error("获取飞书用户姓名异常: openId={}, error={}", openId, e.getMessage());
            return null;
        }
    }

    // ==================== Interactive卡片发送 ====================

    /**
     * 发送Interactive卡片消息（完整卡片JSON）
     * 
     * @param chatId 目标聊天ID
     * @param cardJson 卡片JSON字符串
     */
    public boolean sendInteractiveCard(String chatId, String cardJson) {
        if (meetingHostFeishuMuteRegistry.isMuted(chatId)) {
            log.warn("Feishu send suppressed (AI host in-session): sendInteractiveCard chatId={}", chatId);
            return false;
        }
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/im/v1/messages?receive_id_type=chat_id";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = new HashMap<>();
        body.put("receive_id", chatId);
        body.put("msg_type", "interactive");
        body.put("content", cardJson);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Feishu interactive card sent to chat: {}", chatId);
                return true;
            }
            log.error("Failed to send interactive card: {}", response.getBody());
            return false;
        } catch (Exception e) {
            log.error("Exception sending interactive card: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 文档管理 ====================

    /**
     * 创建飞书文档
     * 
     * @param folderToken 文件夹token（空字符串表示根目录）
     * @param title 文档标题
     * @return Map with docToken and docUrl
     */
    public Map<String, String> createDoc(String folderToken, String title) {
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/docx/v1/documents";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = new HashMap<>();
        if (folderToken != null && !folderToken.isEmpty()) {
            body.put("folder_token", folderToken);
        }
        body.put("title", title);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);

        JsonNode json = response.getBody();
        if (json != null && json.has("data")) {
            JsonNode data = json.get("data");
            String docToken = data.path("document").path("document_id").asText();
            String docUrl = data.path("document").path("url").asText();
            log.info("Feishu doc created: token={}, url={}", docToken, docUrl);
            return Map.of("docToken", docToken, "docUrl", docUrl);
        }

        log.error("Failed to create feishu doc: {}", json);
        throw new RuntimeException("Failed to create feishu document: " + json);
    }

    /**
     * 读取云文档 Docx 的正文为纯文本（遍历 {@code GET .../documents/{id}/blocks} 分页结果，汇总各 Block 内 {@code elements} 的 {@code text_run}）。
     * <p>
     * 需应用具备对该文档的读取权限，并在开放平台开通「查看、评论、编辑和管理云空间中所有文件」或 docx 只读相关能力（以控制台为准）。
     *
     * @param documentId 文档 {@code document_id}（与浏览器地址 {@code .../docx/xxx} 中 xxx 一致）
     * @return 拼接后的纯文本，可能含较多换行
     * @throws RuntimeException 飞书返回 code≠0 或 HTTP 失败时，消息中含错误码与描述
     */
    public String fetchDocxPlainText(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("document_id 为空");
        }
        String tenantToken = getTenantToken();
        String pageToken = null;
        StringBuilder out = new StringBuilder();
        do {
            UriComponentsBuilder ub = UriComponentsBuilder
                    .fromUriString(baseUrl + "/open-apis/docx/v1/documents/" + documentId + "/blocks")
                    .queryParam("page_size", 500)
                    .queryParam("document_revision_id", -1);
            if (pageToken != null && !pageToken.isBlank()) {
                ub.queryParam("page_token", pageToken);
            }
            String url = ub.toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tenantToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);
            JsonNode json = response.getBody();
            if (json == null) {
                throw new RuntimeException("飞书文档 blocks 响应为空");
            }
            int code = json.path("code").asInt(-1);
            if (code != 0) {
                throw new RuntimeException("飞书 docx blocks code=" + code + " msg=" + json.path("msg").asText(""));
            }
            JsonNode data = json.path("data");
            JsonNode items = data.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    appendDocxBlockPlainText(item, out);
                }
            }
            boolean hasMore = data.path("has_more").asBoolean(false);
            String next = data.path("page_token").asText("");
            pageToken = hasMore && !next.isBlank() ? next : null;
            if (pageToken != null) {
                try {
                    Thread.sleep(350);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (pageToken != null);

        return out.toString().trim();
    }

    /**
     * 按资源类型拉取纯文本：docx 块遍历、wiki 转 docx、base 导出表格行（需 URL 含 table=）。
     */
    public String fetchResourcePlainText(FeishuResourceRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("飞书资源引用为空");
        }
        return switch (ref.kind()) {
            case DOCX -> fetchDocxPlainText(ref.primaryToken());
            case WIKI -> fetchWikiPlainText(ref);
            case BASE -> fetchBitablePlainText(ref.primaryToken(), ref.tableId());
            case UNKNOWN -> throw new RuntimeException(
                    "无法识别飞书链接类型，请使用 /docx/、/wiki/ 或 /base/?table= 的完整 HTTPS 链接");
        };
    }

    /**
     * 知识库节点：get_node 取得 obj_token 后，docx 类型走 {@link #fetchDocxPlainText}。
     */
    public String fetchWikiPlainText(FeishuResourceRef ref) {
        if (ref == null || ref.primaryToken() == null || ref.primaryToken().isBlank()) {
            throw new IllegalArgumentException("wiki node_token 为空");
        }
        String wikiNodeToken = ref.primaryToken().trim();
        String tenantToken = getTenantToken();
        String url = baseUrl + "/open-apis/wiki/v2/spaces/get_node?token=" + wikiNodeToken;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);
        JsonNode json = response.getBody();
        if (json == null) {
            throw new RuntimeException("飞书 wiki get_node 响应为空");
        }
        int code = json.path("code").asInt(-1);
        if (code != 0) {
            throw new RuntimeException("飞书 wiki get_node code=" + code + " msg=" + json.path("msg").asText(""));
        }
        JsonNode node = json.path("data").path("node");
        String objType = node.path("obj_type").asText("");
        String objToken = node.path("obj_token").asText("");
        if (objToken.isBlank()) {
            throw new RuntimeException("飞书 wiki 节点无 obj_token");
        }
        if ("docx".equalsIgnoreCase(objType) || objType.isEmpty()) {
            return fetchDocxPlainText(objToken);
        }
        if ("bitable".equalsIgnoreCase(objType)) {
            String tableId = ref.tableId();
            if (tableId == null || tableId.isBlank()) {
                throw new RuntimeException("知识库内嵌多维表格链接须带 ?table=tbl… 参数");
            }
            return fetchBitablePlainText(objToken, tableId);
        }
        throw new RuntimeException("暂不支持在主持页内嵌展示该 Wiki 节点类型: " + objType
                + "，请点击主持页「在飞书中打开」查看");
    }

    /**
     * 多维表格：导出前若干行记录为纯文本（URL 须含 {@code table=tbl...}）。
     */
    public String fetchBitablePlainText(String appToken, String tableId) {
        if (appToken == null || appToken.isBlank()) {
            throw new IllegalArgumentException("base app_token 为空");
        }
        if (tableId == null || tableId.isBlank()) {
            throw new IllegalArgumentException("多维表格链接缺少 table 参数，请使用 .../base/{app}?table=tblXXX");
        }
        String tenantToken = getTenantToken();
        String url = baseUrl + "/open-apis/bitable/v1/apps/" + appToken.trim()
                + "/tables/" + tableId.trim() + "/records/search?page_size=50";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tenantToken);

        Map<String, Object> body = Map.of("automatic_fields", false);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
        JsonNode json = response.getBody();
        if (json == null) {
            throw new RuntimeException("飞书 bitable search 响应为空");
        }
        int code = json.path("code").asInt(-1);
        if (code != 0) {
            throw new RuntimeException("飞书 bitable search code=" + code + " msg=" + json.path("msg").asText(""));
        }
        JsonNode items = json.path("data").path("items");
        StringBuilder out = new StringBuilder();
        out.append("【多维表格摘要，最多 50 条】\n\n");
        if (!items.isArray() || items.isEmpty()) {
            out.append("（无记录）");
            return out.toString().trim();
        }
        int row = 0;
        for (JsonNode item : items) {
            row++;
            out.append("--- 记录 ").append(row).append(" ---\n");
            JsonNode fields = item.path("fields");
            if (fields.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    out.append(e.getKey()).append(": ").append(fieldValueAsText(e.getValue())).append('\n');
                }
            }
            out.append('\n');
        }
        return out.toString().trim();
    }

    private static String fieldValueAsText(JsonNode v) {
        if (v == null || v.isNull()) {
            return "";
        }
        if (v.isTextual()) {
            return v.asText();
        }
        if (v.isNumber() || v.isBoolean()) {
            return v.asText();
        }
        return v.toString();
    }

    /**
     * 从单条 block JSON 中提取可见文字（遍历除元数据字段外、含 {@code elements} 的子对象）。
     */
    private static void appendDocxBlockPlainText(JsonNode block, StringBuilder out) {
        Iterator<Map.Entry<String, JsonNode>> it = block.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if ("block_id".equals(key) || "block_type".equals(key) || "parent_id".equals(key) || "children".equals(key)) {
                continue;
            }
            JsonNode val = e.getValue();
            if (val != null && val.isObject() && val.has("elements") && val.get("elements").isArray()) {
                appendDocxTextElements(val.get("elements"), out);
            }
        }
        out.append('\n');
    }

    private static void appendDocxTextElements(JsonNode elements, StringBuilder out) {
        for (JsonNode el : elements) {
            if (el == null || !el.isObject()) {
                continue;
            }
            if (el.has("text_run")) {
                out.append(el.path("text_run").path("content").asText(""));
            } else if (el.has("equation")) {
                out.append(el.path("equation").path("content").asText(""));
            }
        }
    }

    /**
     * 批量写入飞书文档内容（Block API）
     * 
     * 修复：飞书文档Block写入API的正确URL格式是
     * /open-apis/docx/v1/documents/{doc_id}/blocks/{block_id}/children
     * 其中根block_id等于doc_id
     * 
     * 频率限制：单应用3次/秒，单文档3次/秒
     * API限制：单次最多50个children
     * 
     * @param docToken 文档token（即doc_id）
     * @param blocks 内容块列表
     */
    public boolean updateDocBlocks(String docToken, List<Map<String, Object>> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            log.warn("Empty blocks, skipping update");
            return true;
        }

        String token = getTenantToken();
        // 正确URL: /documents/{doc_id}/blocks/{block_id}/children (根block_id=doc_id)
        String url = baseUrl + "/open-apis/docx/v1/documents/" + docToken + "/blocks/" + docToken + "/children";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // 分批写入（飞书API单次最多50个children）
        int BATCH_SIZE = 50;
        int totalBlocks = blocks.size();
        int successCount = 0;

        for (int i = 0; i < totalBlocks; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, totalBlocks);
            List<Map<String, Object>> batch = blocks.subList(i, end);

            Map<String, Object> body = Map.of("children", batch);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    JsonNode respBody = response.getBody();
                    int code = respBody.path("code").asInt(-1);
                    if (code == 0) {
                        log.info("Feishu doc blocks batch written: docToken={}, batch={}-{}, count={}", 
                            docToken, i, end, batch.size());
                        successCount += batch.size();
                    } else {
                        log.error("Feishu doc blocks batch failed: code={}, msg={}", 
                            code, respBody.path("msg").asText(""));
                        return false;
                    }
                } else {
                    log.warn("Failed to update doc blocks batch: status={}", response.getStatusCode());
                    return false;
                }
            } catch (Exception e) {
                log.error("Exception updating doc blocks batch: {}", e.getMessage());
                return false;
            }

            // 频率控制：避免超过3次/秒（批次间sleep 400ms）
            if (end < totalBlocks) {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        log.info("Feishu doc blocks all written: docToken={}, total={}, success={}", 
            docToken, totalBlocks, successCount);
        return successCount == totalBlocks;
    }
    /**
     * 写入飞书文档内容（文本内容）
     * 简化版本：直接设置文档纯文本内容
     */
    public boolean updateDoc(String docToken, String content) {
        if (docToken == null || docToken.isEmpty()) {
            log.warn("Empty docToken, skipping update");
            return false;
        }

        try {
            // 构建文档内容块
            List<Map<String, Object>> blocks = new ArrayList<>();
            
            // 按行分割内容，每行一个文本块
            String[] lines = content.split("\n");
            for (String line : lines) {
                Map<String, Object> block = new HashMap<>();
                block.put("block_type", 2); // 文本块
                Map<String, Object> textElements = new HashMap<>();
                List<Map<String, Object>> elements = new ArrayList<>();
                Map<String, Object> textElement = new HashMap<>();
                textElement.put("text_run", Map.of("content", line.isEmpty() ? " " : line));
                elements.add(textElement);
                textElements.put("elements", elements);
                block.put("text", textElements);
                blocks.add(block);
            }

            return updateDocBlocks(docToken, blocks);
        } catch (Exception e) {
            log.error("Failed to update doc content: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 设置文档权限（可选，使文档可被团队成员访问）
     */
    public boolean setDocPermission(String docToken, String type, String token) {
        String tenantToken = getTenantToken();
        String url = baseUrl + "/open-apis/drive/v1/permissions/" + docToken + "/members";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tenantToken);

        Map<String, Object> member = Map.of(
                "member_type", type,  // "openid", "unionid", etc.
                "member_id", token,
                "perm", "view"  // 只读权限
        );

        Map<String, Object> body = Map.of("member", member);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Failed to set doc permission: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 工具方法 ====================

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
