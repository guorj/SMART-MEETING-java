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
import com.smartmeeting.matterprogress.feishu.BitableDisplayMode;
import com.smartmeeting.matterprogress.feishu.BitableFieldFormatter;
import com.smartmeeting.matterprogress.feishu.BitablePlainTextExporter;
import com.smartmeeting.matterprogress.feishu.DocxBlockMarkdownExporter;
import com.smartmeeting.matterprogress.feishu.BitableTableInfo;
import com.smartmeeting.matterprogress.feishu.FeishuSpreadsheetPlainTextFetcher;
import com.smartmeeting.config.feishu.FeishuResourceKind;
import com.smartmeeting.config.feishu.FeishuResourceRef;

import java.util.*;

/**
 * 飞书开放平台 API 封装：Tenant Token 管理、消息发送、云文档读写与资源正文拉取。
 * <p>
 * API 文档：<a href="https://open.feishu.cn/document">飞书开放平台</a>
 * <p>
 * 主要协作组件：{@link RestTemplate}、{@link ObjectMapper}、
 * {@link com.smartmeeting.service.host.MeetingHostFeishuMuteRegistry}（AI 主持期间抑制推送）。
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
     * 获取 tenant_access_token（带内存缓存，约 2 小时有效）。
     *
     * @return 有效的 tenant_access_token
     * @throws RuntimeException 飞书 API 返回异常或 HTTP 请求失败时
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
     * 向群聊发送纯文本消息。
     *
     * @param chatId 群 chat_id
     * @param text   消息正文
     * @return 发送成功返回 {@code true}；被静音或 HTTP 失败时返回 {@code false}
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
     * 向用户单聊发送纯文本消息（菜单「推送事件」等无群 chat_id 场景）。
     *
     * @param openId 用户 open_id
     * @param text   消息正文
     * @return 发送成功返回 {@code true}；openId 为空或 HTTP 失败时返回 {@code false}
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
     * 向群聊发送富文本卡片消息（lark_md 正文 + 可选文档按钮）。
     *
     * @param chatId   群 chat_id
     * @param title    卡片标题
     * @param elements 卡片元素列表（含 content、可选 doc_url）
     * @return 发送成功返回 {@code true}；被静音、序列化异常或 HTTP 失败时返回 {@code false}
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
     * 从飞书 API 获取用户姓名。
     *
     * @param openId 飞书用户 open_id
     * @return 用户姓名；API 失败时返回 {@code null}
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
     * 向群聊发送 Interactive 卡片消息（完整卡片 JSON）。
     *
     * @param chatId   群 chat_id
     * @param cardJson 卡片 JSON 字符串
     * @return 发送成功返回 {@code true}；被静音或 HTTP 失败时返回 {@code false}
     */
    public boolean sendInteractiveCard(String chatId, String cardJson) {
        if (meetingHostFeishuMuteRegistry.isMuted(chatId)) {
            log.warn("Feishu send suppressed (AI host in-session): sendInteractiveCard chatId={}", chatId);
            return false;
        }
        return sendInteractiveCardToReceiveId("chat_id", chatId, cardJson);
    }

    /**
     * 向用户单聊发送 Interactive 卡片（如线上个人入会链接）。
     *
     * @param openId   用户 open_id
     * @param cardJson 卡片 JSON 字符串
     * @return 发送成功返回 {@code true}；openId 为空或 HTTP 失败时返回 {@code false}
     */
    public boolean sendInteractiveCardToOpenId(String openId, String cardJson) {
        if (openId == null || openId.isBlank()) {
            return false;
        }
        return sendInteractiveCardToReceiveId("open_id", openId, cardJson);
    }

    /** 向指定 receive_id_type 的接收方发送 Interactive 卡片。 */
    private boolean sendInteractiveCardToReceiveId(String receiveIdType, String receiveId, String cardJson) {
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/im/v1/messages?receive_id_type=" + receiveIdType;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = new HashMap<>();
        body.put("receive_id", receiveId);
        body.put("msg_type", "interactive");
        body.put("content", cardJson);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Feishu interactive card sent to {}={}", receiveIdType, receiveId);
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
     * 创建飞书 Docx 云文档。
     *
     * @param folderToken 文件夹 token（空字符串表示根目录）
     * @param title       文档标题
     * @return 含 {@code docToken} 与 {@code docUrl} 的 Map
     * @throws RuntimeException 飞书 API 返回异常时
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
     *
     * @param ref 解析后的飞书资源引用
     * @return 拼接后的纯文本
     * @throws IllegalArgumentException 资源引用为空或类型无法识别时
     * @throws RuntimeException         飞书 API 返回异常时
     */
    public String fetchResourcePlainText(FeishuResourceRef ref) {
        if (ref == null) {
            throw new IllegalArgumentException("飞书资源引用为空");
        }
        return switch (ref.kind()) {
            case DOCX -> fetchDocxPlainText(ref.primaryToken());
            case WIKI -> fetchWikiPlainText(ref);
            case BASE -> fetchBitablePlainText(ref.primaryToken(), ref.tableId(), bitableMode(ref));
            case UNKNOWN -> throw new RuntimeException(
                    "无法识别飞书链接类型，请使用 /docx/、/wiki/ 或 /base/?table= 的完整 HTTPS 链接");
        };
    }

    /**
     * 知识库节点：get_node 取得 obj_token 后，docx / bitable / sheet 分别拉取正文。
     * bitable 无 {@code table} 时与 {@code /base/} 相同，拉取全部数据表。
     *
     * @param ref 含 wiki node_token 的资源引用
     * @return 拼接后的纯文本
     * @throws IllegalArgumentException node_token 为空时
     * @throws RuntimeException         飞书 API 返回异常或不支持的节点类型时
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
            return fetchBitablePlainText(objToken, ref.tableId(), bitableMode(ref));
        }
        if ("sheet".equalsIgnoreCase(objType)) {
            return fetchSheetPlainText(objToken);
        }
        throw new RuntimeException("暂不支持在主持页内嵌展示该 Wiki 节点类型: " + objType
                + "，请点击主持页「在飞书中打开」查看");
    }

    /**
     * 电子表格：列出工作表并读取单元格，格式化为 Markdown 表格（供主持页 WIKI/sheet 内嵌展示）。
     *
     * @param spreadsheetToken wiki get_node 返回的 obj_token（obj_type=sheet）
     */
    public String fetchSheetPlainText(String spreadsheetToken) {
        return FeishuSpreadsheetPlainTextFetcher.fetch(
                restTemplate, baseUrl, getTenantToken(), spreadsheetToken);
    }

    /**
     * 多维表格：分页导出记录为纯文本。
     * <ul>
     *   <li>带 {@code table=tbl…}：仅拉取该数据表（sheet）</li>
     *   <li>不带 table：拉取 base 下全部数据表并分表展示</li>
     * </ul>
     *
     * @param appToken 多维表格 app_token
     * @param tableId  数据表 table_id，可为空表示全部数据表
     * @return 表格记录摘要文本
     * @throws IllegalArgumentException appToken 为空时
     * @throws RuntimeException         飞书 API 返回异常时
     */
    public String fetchBitablePlainText(String appToken, String tableId) {
        return fetchBitablePlainText(appToken, tableId, BitableDisplayMode.GROUPED);
    }

    public String fetchBitablePlainText(String appToken, String tableId, BitableDisplayMode mode) {
        if (appToken == null || appToken.isBlank()) {
            throw new IllegalArgumentException("base app_token 为空");
        }
        BitableDisplayMode effective = mode != null ? mode : BitableDisplayMode.GROUPED;
        String app = appToken.trim();
        if (tableId == null || tableId.isBlank()) {
            return fetchAllBitableTablesPlainText(app, effective);
        }
        String table = tableId.trim();
        try {
            return searchBitableRecords(app, table, null, effective);
        } catch (RuntimeException first) {
            if (!isWrongTableIdError(first)) {
                throw first;
            }
            List<BitableTableInfo> availableTables = listBitableTables(app);
            List<String> available = availableTables.stream().map(BitableTableInfo::tableId).toList();
            String corrected = resolveTableIdFromListing(table, available);
            if (corrected != null && !corrected.equals(table)) {
                log.warn("Bitable WrongTableId: retry app={} table {} -> {}", app, table, corrected);
                String name = availableTables.stream()
                        .filter(t -> corrected.equals(t.tableId()))
                        .map(BitableTableInfo::tableName)
                        .findFirst()
                        .orElse(null);
                return searchBitableRecords(app, corrected, name, effective);
            }
            throw new RuntimeException(formatWrongTableIdHint(app, table, available), first);
        }
    }

    /**
     * 拉取 base 下全部数据表（多 sheet），每表独立分区导出。
     */
    public String fetchAllBitableTablesPlainText(String appToken) {
        return fetchAllBitableTablesPlainText(appToken, BitableDisplayMode.GROUPED);
    }

    public String fetchAllBitableTablesPlainText(String appToken, BitableDisplayMode mode) {
        List<BitableTableInfo> tables = listBitableTables(appToken);
        if (tables.isEmpty()) {
            return "【多维表格摘要，共 0 条 · 0 个数据表】\n\n（无数据表）";
        }
        BitableDisplayMode effective = mode != null ? mode : BitableDisplayMode.GROUPED;
        List<BitablePlainTextExporter.BitableTableSlice> slices = new ArrayList<>();
        int totalRows = 0;
        for (BitableTableInfo table : tables) {
            List<JsonNode> items = searchBitableRecordItems(appToken, table.tableId());
            totalRows += items.size();
            slices.add(new BitablePlainTextExporter.BitableTableSlice(
                    table.tableId(), table.tableName(), items));
        }
        String title = "【多维表格摘要，共 " + totalRows + " 条 · " + tables.size() + " 个数据表】";
        return BitablePlainTextExporter.exportMultiTable(slices, title, effective);
    }

    private static BitableDisplayMode bitableMode(FeishuResourceRef ref) {
        return BitableDisplayMode.from(ref != null ? ref.bitableDisplayMode() : null);
    }

    private String searchBitableRecords(String appToken, String tableId, String tableName,
            BitableDisplayMode mode) {
        List<JsonNode> items = searchBitableRecordItems(appToken, tableId);
        String label = tableName != null && !tableName.isBlank() ? tableName.trim() : tableId;
        String title = "【多维表格·" + label + "，共 " + items.size() + " 条】";
        return BitablePlainTextExporter.export(items, title, mode);
    }

    private List<JsonNode> searchBitableRecordItems(String appToken, String tableId) {
        String tenantToken = getTenantToken();
        int pageSize = 500;
        String pageToken = null;
        boolean hasMore = true;
        List<JsonNode> allItems = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tenantToken);
        Map<String, Object> body = Map.of("automatic_fields", false);

        while (hasMore) {
            UriComponentsBuilder ub = UriComponentsBuilder
                    .fromUriString(baseUrl + "/open-apis/bitable/v1/apps/" + appToken
                            + "/tables/" + tableId + "/records/search")
                    .queryParam("page_size", pageSize);
            if (pageToken != null && !pageToken.isBlank()) {
                ub.queryParam("page_token", pageToken);
            }
            String url = ub.toUriString();
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
            JsonNode data = json.path("data");
            JsonNode items = data.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    allItems.add(item);
                }
            }
            hasMore = data.path("has_more").asBoolean(false);
            pageToken = data.path("page_token").asText(null);
            if (hasMore && (pageToken == null || pageToken.isBlank())) {
                break;
            }
            if (hasMore) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return allItems;
    }

    /**
     * 列出多维表格 base 下全部数据表（sheet）。
     */
    List<BitableTableInfo> listBitableTables(String appToken) {
        String tenantToken = getTenantToken();
        String url = baseUrl + "/open-apis/bitable/v1/apps/" + appToken.trim() + "/tables?page_size=100";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, request, JsonNode.class);
            JsonNode json = response.getBody();
            if (json == null || json.path("code").asInt(-1) != 0) {
                return List.of();
            }
            JsonNode items = json.path("data").path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<BitableTableInfo> tables = new ArrayList<>();
            for (JsonNode item : items) {
                String id = item.path("table_id").asText("").trim();
                if (id.isEmpty()) {
                    continue;
                }
                String name = item.path("name").asText("").trim();
                tables.add(new BitableTableInfo(id, name.isEmpty() ? id : name));
            }
            return tables;
        } catch (Exception e) {
            log.warn("listBitableTables failed app={}: {}", appToken, e.getMessage());
            return List.of();
        }
    }

    /** @deprecated 请用 {@link #listBitableTables} */
    List<String> listBitableTableIds(String appToken) {
        return listBitableTables(appToken).stream().map(BitableTableInfo::tableId).toList();
    }

    private static boolean isWrongTableIdError(Throwable e) {
        String msg = e != null ? e.getMessage() : null;
        return msg != null && msg.contains("1254004") && msg.contains("WrongTableId");
    }

    /** 配置 table 与 API 列表仅大小写不一致时自动纠正（飞书 table_id 区分大小写）。 */
    private static String resolveTableIdFromListing(String configured, List<String> available) {
        if (configured == null || configured.isBlank() || available == null || available.isEmpty()) {
            return null;
        }
        if (available.contains(configured)) {
            return configured;
        }
        String want = configured.toLowerCase(Locale.ROOT);
        String match = null;
        for (String id : available) {
            if (id != null && id.toLowerCase(Locale.ROOT).equals(want)) {
                if (match != null) {
                    return null;
                }
                match = id;
            }
        }
        return match;
    }

    private static String formatWrongTableIdHint(String appToken, String tableId, List<String> available) {
        StringBuilder sb = new StringBuilder();
        sb.append("飞书 bitable table_id 无效（1254004 WrongTableId）。")
                .append(" app_token=").append(appToken)
                .append(" 配置的 table=").append(tableId)
                .append("。请从浏览器重新复制完整链接（须含 ?table=tbl...），")
                .append("或更新 int_meeting_type_preset.host_agenda / int_matter_progress_doc_config。");
        if (available != null && !available.isEmpty()) {
            sb.append(" 当前应用下可用 table_id：").append(String.join(", ", available));
        }
        return sb.toString();
    }

    private static String fieldValueAsText(JsonNode v) {
        return BitableFieldFormatter.format(v, null);
    }

    /**
     * 从单条 block JSON 中提取可见文字（遍历除元数据字段外、含 {@code elements} 的子对象）。
     */
    private static void appendDocxBlockPlainText(JsonNode block, StringBuilder out) {
        DocxBlockMarkdownExporter.appendBlock(block, out);
    }

    /**
     * 批量写入飞书文档内容块（Block API，分批最多 50 个/次，含频率控制）。
     *
     * @param docToken 文档 token（即 document_id，根 block_id 同 doc_id）
     * @param blocks   内容块列表
     * @return 全部批次写入成功返回 {@code true}；任一批次失败返回 {@code false}
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
     * 将纯文本按行写入飞书 Docx 文档（内部转换为 Block 并调用 {@link #updateDocBlocks}）。
     *
     * @param docToken 文档 token
     * @param content  纪要或正文纯文本
     * @return 写入成功返回 {@code true}；docToken 为空或写入失败时返回 {@code false}
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
     * 为文档添加成员只读权限。
     *
     * @param docToken 文档 token
     * @param type     成员类型（如 openid）
     * @param token    成员 ID
     * @return HTTP 2xx 时返回 {@code true}；失败时返回 {@code false}
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
