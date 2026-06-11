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
import com.smartmeeting.config.MatterProgressFetchProperties;
import com.smartmeeting.config.feishu.FeishuResourceKind;
import com.smartmeeting.config.feishu.FeishuResourceRef;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import com.smartmeeting.service.structured.DocxBlockStructuredExporter;
import com.smartmeeting.service.structured.BitableStructuredExporter;
import com.smartmeeting.service.structured.SheetStructuredExporter;
import com.smartmeeting.service.structured.ExcelStructuredExporter;
import com.smartmeeting.service.structured.TaskListStructuredExporter;
import com.smartmeeting.service.structured.PptxSlideImageExporter;
import com.smartmeeting.service.structured.PdfPageImageExporter;
import com.smartmeeting.service.feishu.BitableTableIdResolver;
import com.smartmeeting.service.feishu.DocxEmbeddedBitableResolver;
import com.smartmeeting.service.feishu.DocxEmbeddedBitableResolver.EmbeddedBitableRef;
import com.smartmeeting.service.feishu.FeishuDriveClient;
import com.smartmeeting.api.dto.structured.*;

import java.nio.file.Files;
import java.nio.file.Path;

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
    private final MatterProgressFetchProperties matterProgressFetchProperties;

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
     * 向用户 user_id 发送纯文本消息。
     *
     * @param userId 用户 user_id
     * @param text   消息正文
     * @return 发送成功返回 {@code true}
     */
    public boolean sendMessageToUserId(String userId, String text) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/im/v1/messages?receive_id_type=user_id";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        Map<String, Object> body = Map.of(
                "receive_id", userId,
                "msg_type", "text",
                "content", "{\"text\":\"" + escapeJson(text) + "\"}"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Feishu message sent to user_id");
            return true;
        }
        log.error("Failed to send feishu message to user_id: {}", response.getBody());
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
     * 通过飞书 user_id 获取用户姓名。
     *
     * @param feishuUserId 飞书 user_id
     * @return 用户姓名；API 失败时返回 {@code null}
     */
    public String getUserNameByUserId(String feishuUserId) {
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/contact/v3/users/" + feishuUserId + "?user_id_type=user_id";

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
                log.info("获取飞书用户姓名: feishuUserId={}, name={}", feishuUserId, name);
                return name;
            }
            log.warn("获取用户姓名失败: feishuUserId={}, code={}", feishuUserId, json != null ? json.path("code").asInt() : -1);
            return null;
        } catch (Exception e) {
            log.error("获取飞书用户姓名异常: feishuUserId={}, error={}", feishuUserId, e.getMessage());
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
     * 向用户单聊发送 Interactive 卡片（receive_id_type=user_id）。
     *
     * @param userId   飞书 user_id
     * @param cardJson 卡片 JSON 字符串
     * @return 发送成功返回 {@code true}；userId 为空或 HTTP 失败时返回 {@code false}
     */
    public boolean sendInteractiveCardToUserId(String userId, String cardJson) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return sendInteractiveCardToReceiveId("user_id", userId, cardJson);
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

    public record CalendarCreateResult(boolean success, String eventId, String message) {}

    public record TaskCreateResult(boolean success, String taskId, String message) {}

    /**
     * 创建飞书日历事件（简化封装）。
     * <p>
     * 说明：若当前应用未开通 calendar scope，会返回失败但不抛异常，供上层做降级提示。
     */
    public CalendarCreateResult createCalendarEvent(String summary, LocalDateTime startAt, String roomHint, String chatId) {
        try {
            String token = getTenantToken();
            String url = baseUrl + "/open-apis/calendar/v4/calendars/primary/events";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);

            long startTs = toEpochSeconds(startAt != null ? startAt : LocalDateTime.now().plusMinutes(5));
            long endTs = toEpochSeconds((startAt != null ? startAt : LocalDateTime.now().plusMinutes(5)).plusMinutes(60));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("summary", summary == null || summary.isBlank() ? "会议邀约" : summary);
            body.put("description", roomHint == null || roomHint.isBlank() ? "" : ("会议室建议：" + roomHint));
            body.put("start_time", Map.of("timestamp", String.valueOf(startTs)));
            body.put("end_time", Map.of("timestamp", String.valueOf(endTs)));

            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode data = resp.getBody() == null ? null : resp.getBody().path("data");
            String eventId = data == null ? "" : data.path("event").path("event_id").asText("");
            if (eventId.isBlank()) {
                return new CalendarCreateResult(false, "", "empty_event_id");
            }
            if (chatId != null && !chatId.isBlank()) {
                sendMessage(chatId, "日历邀约已创建：" + summary);
            }
            return new CalendarCreateResult(true, eventId, "ok");
        } catch (Exception e) {
            log.warn("createCalendarEvent failed: {}", e.getMessage());
            return new CalendarCreateResult(false, "", e.getMessage());
        }
    }

    /**
     * 创建飞书任务（若任务 API 不可用则回退为文本提醒，保证流程不中断）。
     */
    public TaskCreateResult createTask(String title, String assigneeUserId, LocalDateTime dueAt, String externalRef) {
        if (assigneeUserId == null || assigneeUserId.isBlank()) {
            return new TaskCreateResult(false, "", "assignee missing");
        }
        try {
            String token = getTenantToken();
            String url = baseUrl + "/open-apis/task/v2/tasks";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("summary", title == null || title.isBlank() ? "会议待办" : title);
            body.put("description", externalRef == null ? "" : externalRef);
            body.put("origin", Map.of("platform_i18n_name", Map.of("zh_cn", "智能会议系统")));
            body.put("assignees", List.of(Map.of("id", assigneeUserId, "type", "user_id")));
            if (dueAt != null) {
                body.put("due", Map.of("timestamp", String.valueOf(toEpochSeconds(dueAt))));
            }
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            JsonNode data = resp.getBody() == null ? null : resp.getBody().path("data");
            String taskId = data == null ? "" : data.path("task").path("id").asText("");
            if (taskId.isBlank()) {
                sendMessageToUserId(assigneeUserId, "待办同步（降级文本）：" + (title == null ? "会议待办" : title));
                return new TaskCreateResult(false, "", "task_api_empty_id");
            }
            return new TaskCreateResult(true, taskId, "ok");
        } catch (Exception e) {
            log.warn("createTask failed, fallback to text: {}", e.getMessage());
            sendMessageToUserId(assigneeUserId, "待办同步（降级文本）：" + (title == null ? "会议待办" : title));
            return new TaskCreateResult(false, "", e.getMessage());
        }
    }

    private long toEpochSeconds(LocalDateTime time) {
        return time.atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond();
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
            case SHEET -> fetchSheetPlainText(ref.primaryToken());
            case TASKLIST -> fetchTaskListPlainText(ref.primaryToken());
            case UNKNOWN -> throw new RuntimeException(
                    "无法识别飞书链接类型，请使用 /docx/、/wiki/、/sheets/、/base/ 或任务清单 AppLink 的完整 HTTPS 链接");
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
            EmbeddedBitableRef embedded = resolveWikiDocxEmbeddedBitable(objToken, ref);
            if (embedded != null) {
                return fetchBitablePlainText(embedded.appToken(), embedded.tableId(), bitableMode(ref));
            }
            return fetchDocxPlainText(objToken);
        }
        if ("bitable".equalsIgnoreCase(objType)) {
            return fetchBitablePlainText(objToken, ref.tableId(), bitableMode(ref));
        }
        if ("sheet".equalsIgnoreCase(objType)) {
            return fetchSheetPlainText(objToken);
        }
        if ("file".equalsIgnoreCase(objType)) {
            return fetchWikiFilePlainText(objToken);
        }
        if ("slides".equalsIgnoreCase(objType)) {
            return fetchWikiSlidesPlainText(objToken);
        }
        if ("mindnote".equalsIgnoreCase(objType)) {
            return fetchWikiMindnotePlainText(node);
        }
        if ("doc".equalsIgnoreCase(objType)) {
            return fetchLegacyDocPlainText(objToken);
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
                restTemplate, baseUrl, getTenantToken(), spreadsheetToken, matterProgressFetchProperties.toLimits());
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
        return searchBitableRecords(app, tableId.trim(), null, effective);
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
        ResolvedBitableTable resolved = fetchBitableTableData(appToken, tableId);
        String label = tableName != null && !tableName.isBlank()
                ? tableName.trim()
                : resolved.tableName();
        String title = "【多维表格·" + label + "，共 " + resolved.items().size() + " 条】";
        return BitablePlainTextExporter.export(resolved.items(), title, mode);
    }

    private record ResolvedBitableTable(String tableId, String tableName, List<JsonNode> items) {
    }

    private ResolvedBitableTable fetchBitableTableData(String appToken, String configuredTableId) {
        String table = configuredTableId.trim();
        try {
            List<JsonNode> items = searchBitableRecordItems(appToken, table);
            return new ResolvedBitableTable(table, lookupBitableTableName(appToken, table), items);
        } catch (RuntimeException first) {
            if (!BitableTableIdResolver.isWrongTableIdError(first)) {
                throw first;
            }
            List<BitableTableInfo> availableTables = listBitableTables(appToken);
            List<String> available = availableTables.stream().map(BitableTableInfo::tableId).toList();
            String corrected = BitableTableIdResolver.resolveFromListing(table, available);
            if (corrected != null && !corrected.equals(table)) {
                log.warn("Bitable WrongTableId: retry app={} table {} -> {}", appToken, table, corrected);
                String name = availableTables.stream()
                        .filter(t -> corrected.equals(t.tableId()))
                        .map(BitableTableInfo::tableName)
                        .findFirst()
                        .orElse(corrected);
                return new ResolvedBitableTable(
                        corrected, name, searchBitableRecordItems(appToken, corrected));
            }
            throw new RuntimeException(formatWrongTableIdHint(appToken, table, available), first);
        }
    }

    private String lookupBitableTableName(String appToken, String tableId) {
        try {
            for (BitableTableInfo t : listBitableTables(appToken)) {
                if (tableId.equals(t.tableId())) {
                    return t.tableName();
                }
            }
        } catch (Exception ignored) {
        }
        return tableId;
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

    /**
     * 按资源类型返回主持页结构化 contentType（无结构化能力时返回 null）。
     */
    public String resolveStructuredContentType(FeishuResourceRef ref) {
        if (ref == null) {
            return null;
        }
        return switch (ref.kind()) {
            case DOCX -> "docx_blocks";
            case BASE -> "bitable_records";
            case SHEET -> "sheet_cells";
            case TASKLIST -> "task_list";
            case WIKI -> resolveWikiStructuredContentType(ref);
            case UNKNOWN -> null;
        };
    }

    /**
     * 按资源类型拉取结构化 JSON（供主持页内嵌渲染；失败时由调用方回退 plainText）。
     */
    public Object fetchResourceStructuredContent(FeishuResourceRef ref) {
        if (ref == null) {
            return null;
        }
        return switch (ref.kind()) {
            case DOCX -> fetchDocxBlocks(ref.primaryToken());
            case BASE -> fetchBitableStructured(ref.primaryToken(), ref.tableId(), bitableMode(ref));
            case SHEET -> fetchSheetStructuredContent(ref.primaryToken());
            case TASKLIST -> fetchTaskListStructured(ref.primaryToken(), ref.defaultOpenUrl());
            case WIKI -> fetchWikiStructuredContent(ref);
            case UNKNOWN -> null;
        };
    }

    private String resolveWikiStructuredContentType(FeishuResourceRef ref) {
        JsonNode node = fetchWikiNode(ref);
        if (node == null) {
            return null;
        }
        String objType = node.path("obj_type").asText("");
        if ("docx".equalsIgnoreCase(objType) || objType.isEmpty()) {
            String objToken = node.path("obj_token").asText("");
            if (!objToken.isBlank() && resolveWikiDocxEmbeddedBitable(objToken, ref) != null) {
                return "bitable_records";
            }
            return "docx_blocks";
        }
        if ("bitable".equalsIgnoreCase(objType)) {
            return "bitable_records";
        }
        if ("sheet".equalsIgnoreCase(objType)) {
            return "sheet_cells";
        }
        if ("file".equalsIgnoreCase(objType)) {
            return resolveWikiFileStructuredContentType(node.path("obj_token").asText(""));
        }
        if ("slides".equalsIgnoreCase(objType)) {
            return "pdf_pages";
        }
        if ("mindnote".equalsIgnoreCase(objType)) {
            return "docx_blocks";
        }
        if ("doc".equalsIgnoreCase(objType)) {
            return "docx_blocks";
        }
        return null;
    }

    private Object fetchWikiStructuredContent(FeishuResourceRef ref) {
        JsonNode node = fetchWikiNode(ref);
        if (node == null) {
            return null;
        }
        String objType = node.path("obj_type").asText("");
        String objToken = node.path("obj_token").asText("");
        if (objToken.isBlank()) {
            return null;
        }
        if ("docx".equalsIgnoreCase(objType) || objType.isEmpty()) {
            EmbeddedBitableRef embedded = resolveWikiDocxEmbeddedBitable(objToken, ref);
            if (embedded != null) {
                return fetchBitableStructured(embedded.appToken(), embedded.tableId(), bitableMode(ref));
            }
            return fetchDocxBlocks(objToken);
        }
        if ("bitable".equalsIgnoreCase(objType)) {
            return fetchBitableStructured(objToken, ref.tableId(), bitableMode(ref));
        }
        if ("sheet".equalsIgnoreCase(objType)) {
            return fetchSheetStructuredContent(objToken);
        }
        if ("file".equalsIgnoreCase(objType)) {
            return fetchWikiFileStructured(objToken);
        }
        if ("slides".equalsIgnoreCase(objType)) {
            return fetchWikiSlidesStructured(objToken);
        }
        if ("mindnote".equalsIgnoreCase(objType)) {
            return fetchWikiMindnoteBlocks(node);
        }
        if ("doc".equalsIgnoreCase(objType)) {
            EmbeddedBitableRef embedded = resolveWikiDocxEmbeddedBitable(objToken, ref);
            if (embedded != null) {
                return fetchBitableStructured(embedded.appToken(), embedded.tableId(), bitableMode(ref));
            }
            return fetchDocxBlocks(objToken);
        }
        return null;
    }

    private EmbeddedBitableRef resolveWikiDocxEmbeddedBitable(String documentId, FeishuResourceRef ref) {
        if (documentId == null || documentId.isBlank()) {
            return null;
        }
        String configuredTable = ref != null ? ref.tableId() : null;
        boolean hasTable = configuredTable != null && !configuredTable.isBlank();
        JsonNode items = fetchDocxBlockItems(documentId);
        EmbeddedBitableRef found = DocxEmbeddedBitableResolver.findEmbeddedBitable(items, configuredTable);
        if (found != null) {
            return found;
        }
        List<EmbeddedBitableRef> all = DocxEmbeddedBitableResolver.listEmbeddedBitables(items);
        if (all.size() == 1) {
            EmbeddedBitableRef only = all.get(0);
            if (hasTable && !configuredTable.trim().equals(only.tableId())) {
                log.warn("wiki docx embedded bitable table mismatch: configured={} actual={}, using sole embed",
                        configuredTable, only.tableId());
            }
            return only;
        }
        if (hasTable && !all.isEmpty()) {
            log.warn("wiki docx ?table={} not matched among {} embedded bitable blocks, docId={}",
                    configuredTable, all.size(), documentId);
            return null;
        }
        if (!all.isEmpty()) {
            log.warn("wiki docx has {} embedded bitable blocks without ?table=, using first embed docId={}",
                    all.size(), documentId);
            return all.get(0);
        }
        return null;
    }

    private JsonNode fetchWikiNode(FeishuResourceRef ref) {
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
        return json.path("data").path("node");
    }

    /**
     * 飞书 Docx 结构化拉取：返回 block 列表（保留富文本 runs、image key 等结构信息）。
     */
    public java.util.List<DocxBlockDto> fetchDocxBlocks(String documentId) {
        return DocxBlockStructuredExporter.exportBlocks(fetchDocxBlockItems(documentId));
    }

    JsonNode fetchDocxBlockItems(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("document_id 为空");
        }
        String tenantToken = getTenantToken();
        String pageToken = null;
        var allItems = objectMapper.createArrayNode();
        do {
            org.springframework.web.util.UriComponentsBuilder ub = org.springframework.web.util.UriComponentsBuilder
                    .fromUriString(baseUrl + "/open-apis/docx/v1/documents/" + documentId + "/blocks")
                    .queryParam("page_size", 500)
                    .queryParam("document_revision_id", -1);
            if (pageToken != null && !pageToken.isBlank()) {
                ub.queryParam("page_token", pageToken);
            }
            String url = ub.toUriString();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(tenantToken);
            org.springframework.http.HttpEntity<Void> request = new org.springframework.http.HttpEntity<>(headers);
            try {
                org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = restTemplate.exchange(
                        url, org.springframework.http.HttpMethod.GET, request, com.fasterxml.jackson.databind.JsonNode.class);
                com.fasterxml.jackson.databind.JsonNode json = response.getBody();
                if (json == null) {
                    break;
                }
                com.fasterxml.jackson.databind.JsonNode items = json.path("data").path("items");
                if (items.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : items) {
                        allItems.add(item);
                    }
                }
                pageToken = json.path("data").path("page_token").asText(null);
                if (pageToken == null || pageToken.isBlank()) {
                    break;
                }
            } catch (Exception e) {
                log.warn("fetchDocxBlocks page failed docId={}: {}", documentId, e.getMessage());
                break;
            }
        } while (true);
        return allItems;
    }

    /**
     * 飞书 Bitable 结构化拉取：返回含字段类型和记录的结构化数据。
     */
    public BitableStructuredDto fetchBitableStructured(String appToken, String tableId) {
        return fetchBitableStructured(appToken, tableId, BitableDisplayMode.GROUPED);
    }

    public BitableStructuredDto fetchBitableStructured(String appToken, String tableId, BitableDisplayMode mode) {
        if (appToken == null || appToken.isBlank()) {
            throw new IllegalArgumentException("base app_token 为空");
        }
        String app = appToken.trim();
        BitableDisplayMode effective = mode != null ? mode : BitableDisplayMode.GROUPED;
        if (tableId == null || tableId.isBlank()) {
            return fetchAllBitableTablesStructured(app, effective);
        }
        ResolvedBitableTable resolved = fetchBitableTableData(app, tableId);
        return BitableStructuredExporter.export(
                resolved.items(), resolved.tableName(), effective);
    }

    private BitableStructuredDto fetchAllBitableTablesStructured(String appToken, BitableDisplayMode mode) {
        List<BitableTableInfo> tables = listBitableTables(appToken);
        if (tables.isEmpty()) {
            return BitableStructuredDto.builder().tableName("无数据表").columns(List.of())
                    .records(List.of()).groups(List.of()).tables(List.of()).totalRecords(0).build();
        }
        List<BitablePlainTextExporter.BitableTableSlice> slices = new ArrayList<>();
        for (BitableTableInfo table : tables) {
            List<JsonNode> items = searchBitableRecordItems(appToken, table.tableId());
            slices.add(new BitablePlainTextExporter.BitableTableSlice(table.tableId(), table.tableName(), items));
        }
        return BitableStructuredExporter.exportMultiTable(slices, mode);
    }

    /**
     * 飞书任务清单：拉取清单详情与全部任务（Task v2 API，需 {@code task:tasklist:read} 权限）。
     */
    public TaskListStructuredDto fetchTaskListStructured(String tasklistGuid) {
        return fetchTaskListStructured(tasklistGuid, null);
    }

    public TaskListStructuredDto fetchTaskListStructured(String tasklistGuid, String openUrl) {
        if (tasklistGuid == null || tasklistGuid.isBlank()) {
            throw new IllegalArgumentException("tasklist guid 为空");
        }
        String guid = tasklistGuid.trim();
        JsonNode tasklist = fetchTaskListDetail(guid);
        List<JsonNode> tasks = fetchTaskListTaskItems(guid);
        String link = openUrl != null && !openUrl.isBlank() ? openUrl.trim() : tasklist.path("url").asText("");
        if (link.isBlank()) {
            link = "https://applink.feishu.cn/client/todo/task_list?guid=" + guid;
        }
        return TaskListStructuredExporter.export(tasklist, tasks, link);
    }

    public String fetchTaskListPlainText(String tasklistGuid) {
        return TaskListStructuredExporter.exportPlainText(fetchTaskListStructured(tasklistGuid));
    }

    private JsonNode fetchTaskListDetail(String tasklistGuid) {
        String tenantToken = getTenantToken();
        String url = baseUrl + "/open-apis/task/v2/tasklists/" + tasklistGuid;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode json = response.getBody();
        if (json == null) {
            throw new RuntimeException("飞书 tasklist get 响应为空");
        }
        int code = json.path("code").asInt(-1);
        if (code != 0) {
            throw new RuntimeException("飞书 tasklist get code=" + code + " msg=" + json.path("msg").asText("")
                    + "（请确认应用已开通 task:tasklist:read 且已加入清单协作成员）");
        }
        JsonNode tasklist = json.path("data").path("tasklist");
        if (tasklist.isMissingNode() || tasklist.isNull()) {
            throw new RuntimeException("飞书 tasklist get 无 tasklist 数据");
        }
        return tasklist;
    }

    private List<JsonNode> fetchTaskListTaskItems(String tasklistGuid) {
        String tenantToken = getTenantToken();
        int pageSize = 50;
        String pageToken = null;
        boolean hasMore = true;
        List<JsonNode> all = new ArrayList<>();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        while (hasMore) {
            UriComponentsBuilder ub = UriComponentsBuilder
                    .fromUriString(baseUrl + "/open-apis/task/v2/tasklists/" + tasklistGuid + "/tasks")
                    .queryParam("page_size", pageSize);
            if (pageToken != null && !pageToken.isBlank()) {
                ub.queryParam("page_token", pageToken);
            }
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    ub.toUriString(), HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode json = response.getBody();
            if (json == null) {
                throw new RuntimeException("飞书 tasklist tasks 响应为空");
            }
            int code = json.path("code").asInt(-1);
            if (code != 0) {
                throw new RuntimeException("飞书 tasklist tasks code=" + code + " msg=" + json.path("msg").asText(""));
            }
            JsonNode data = json.path("data");
            JsonNode items = data.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    all.add(item);
                }
            }
            hasMore = data.path("has_more").asBoolean(false);
            pageToken = data.path("page_token").asText(null);
            if (hasMore && (pageToken == null || pageToken.isBlank())) {
                break;
            }
        }
        return all;
    }

    /**
     * 飞书电子表格结构化拉取：返回含合并范围的结构化数据。
     */
    public SheetStructuredDto fetchSheetStructured(String spreadsheetToken) {
        Object content = fetchSheetStructuredContent(spreadsheetToken);
        if (content instanceof SheetStructuredDto dto) {
            return dto;
        }
        if (content instanceof ExcelWorkbookDto wb && wb.getSheets() != null && !wb.getSheets().isEmpty()) {
            ExcelSheetDto first = wb.getSheets().get(0);
            return SheetStructuredDto.builder()
                    .sheetName(first.getSheetName())
                    .headers(first.getHeaders())
                    .rows(first.getRows())
                    .mergedRanges(first.getMergedRanges())
                    .columnWidths(first.getColumnWidths())
                    .headerRowCount(first.getHeaderRowCount())
                    .build();
        }
        return emptySheetStructured();
    }

    /**
     * 飞书电子表格结构化：单表返回 {@link SheetStructuredDto}，多表返回 {@link ExcelWorkbookDto}。
     */
    public Object fetchSheetStructuredContent(String spreadsheetToken) {
        if (spreadsheetToken == null || spreadsheetToken.isBlank()) {
            throw new IllegalArgumentException("spreadsheet_token 为空");
        }
        List<FeishuSpreadsheetPlainTextFetcher.SheetValuesSlice> slices =
                FeishuSpreadsheetPlainTextFetcher.fetchSheetValueSlices(
                        restTemplate,
                        baseUrl,
                        getTenantToken(),
                        spreadsheetToken.trim(),
                        matterProgressFetchProperties.toLimits());
        if (slices.isEmpty()) {
            return emptySheetStructured();
        }
        if (slices.size() == 1) {
            FeishuSpreadsheetPlainTextFetcher.SheetValuesSlice slice = slices.get(0);
            return SheetStructuredExporter.export(slice.sheetTitle(), slice.values(), slice.mergedRanges());
        }
        List<ExcelSheetDto> sheets = new ArrayList<>();
        for (int i = 0; i < slices.size(); i++) {
            FeishuSpreadsheetPlainTextFetcher.SheetValuesSlice slice = slices.get(i);
            SheetStructuredDto one = SheetStructuredExporter.export(
                    slice.sheetTitle(), slice.values(), slice.mergedRanges());
            sheets.add(ExcelSheetDto.builder()
                    .sheetName(one.getSheetName())
                    .sheetIndex(i)
                    .headers(one.getHeaders())
                    .rows(one.getRows())
                    .mergedRanges(one.getMergedRanges())
                    .columnWidths(one.getColumnWidths())
                    .headerRowCount(one.getHeaderRowCount())
                    .build());
        }
        return ExcelWorkbookDto.builder().sheets(sheets).build();
    }

    private static SheetStructuredDto emptySheetStructured() {
        return SheetStructuredDto.builder()
                .sheetName("无数据")
                .headers(List.of())
                .rows(List.of())
                .mergedRanges(List.of())
                .columnWidths(List.of())
                .headerRowCount(1)
                .build();
    }

    /**
     * 代理下载飞书图片：返回图片二进制数据。
     */
    public byte[] downloadImage(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            throw new IllegalArgumentException("image_key 为空");
        }
        String token = getTenantToken();
        String url = baseUrl + "/open-apis/im/v1/images/" + imageKey;
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        org.springframework.http.HttpEntity<Void> request = new org.springframework.http.HttpEntity<>(headers);
        try {
            org.springframework.http.ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, org.springframework.http.HttpMethod.GET, request, byte[].class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            throw new RuntimeException("飞书图片下载失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            throw new RuntimeException("飞书图片下载失败: " + e.getMessage(), e);
        }
    }

    private String fetchWikiMindnotePlainText(JsonNode node) {
        String title = node.path("title").asText("思维导图");
        return "【思维导图】" + title + "\n\n飞书 Open API 暂不支持导出思维导图正文，请点击下方链接在飞书中查看。";
    }

    private List<DocxBlockDto> fetchWikiMindnoteBlocks(JsonNode node) {
        String title = node.path("title").asText("思维导图");
        return List.of(DocxBlockDto.builder()
                .type("heading2")
                .text(title)
                .build(),
                DocxBlockDto.builder()
                        .type("paragraph")
                        .text("飞书 Open API 暂不支持导出思维导图正文，请点击下方链接在飞书中查看。")
                        .build());
    }

    private String fetchWikiFilePlainText(String fileToken) {
        try {
            byte[] data = FeishuDriveClient.downloadFile(restTemplate, baseUrl, getTenantToken(), fileToken);
            return describeCloudFilePlainText(data, ".bin");
        } catch (Exception e) {
            throw new RuntimeException("飞书附件拉取失败: " + e.getMessage(), e);
        }
    }

    private String fetchWikiSlidesPlainText(String slidesToken) {
        try {
            Object structured = fetchWikiSlidesStructured(slidesToken);
            if (structured instanceof PdfDocumentDto doc) {
                return "【飞书幻灯片】共 " + doc.getTotalPages() + " 页（主持页可翻页预览）";
            }
            return "【飞书幻灯片】请在飞书中打开查看";
        } catch (Exception e) {
            throw new RuntimeException("飞书幻灯片拉取失败: " + e.getMessage(), e);
        }
    }

    private String fetchLegacyDocPlainText(String docToken) {
        try {
            return fetchDocxPlainText(docToken);
        } catch (Exception first) {
            log.warn("legacy doc as docx failed, try export: {}", first.getMessage());
            try {
                byte[] pdf = FeishuDriveClient.exportDocument(
                        restTemplate, baseUrl, getTenantToken(), docToken, "doc", "pdf", 20_000);
                Path temp = FeishuDriveClient.writeTempFile(pdf, ".pdf");
                PdfDocumentDto doc = PdfPageImageExporter.export(temp, temp.getParent());
                Files.deleteIfExists(temp);
                return "【旧版飞书文档】共 " + doc.getTotalPages() + " 页（PDF 预览）";
            } catch (Exception second) {
                throw new RuntimeException("旧版飞书文档拉取失败: " + second.getMessage(), second);
            }
        }
    }

    private String resolveWikiFileStructuredContentType(String fileToken) {
        try {
            byte[] head = FeishuDriveClient.downloadFile(restTemplate, baseUrl, getTenantToken(), fileToken);
            if (isPdfBytes(head)) {
                return "pdf_pages";
            }
            if (isPptBytes(head)) {
                return "ppt_slides";
            }
            if (isExcelBytes(head)) {
                return "excel_workbook";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Object fetchWikiFileStructured(String fileToken) {
        try {
            byte[] data = FeishuDriveClient.downloadFile(restTemplate, baseUrl, getTenantToken(), fileToken);
            Path temp = FeishuDriveClient.writeTempFile(data, guessSuffix(data));
            Path genDir = temp.getParent().resolve("feishu-file-" + fileToken);
            Files.createDirectories(genDir);
            try {
                if (isPdfBytes(data)) {
                    return PdfPageImageExporter.export(temp, genDir);
                }
                try {
                    return ExcelStructuredExporter.export(temp);
                } catch (Exception excelMiss) {
                    return PptxSlideImageExporter.export(temp, genDir);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (Exception e) {
            log.warn("wiki file structured export failed: {}", e.getMessage());
        }
        return null;
    }

    private Object fetchWikiSlidesStructured(String slidesToken) {
        try {
            byte[] pdf = FeishuDriveClient.exportDocument(
                    restTemplate, baseUrl, getTenantToken(), slidesToken, "slides", "pdf", 25_000);
            Path temp = FeishuDriveClient.writeTempFile(pdf, ".pdf");
            Path genDir = temp.getParent().resolve("feishu-slides-" + slidesToken);
            Files.createDirectories(genDir);
            try {
                return PdfPageImageExporter.export(temp, genDir);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (Exception e) {
            throw new RuntimeException("飞书幻灯片结构化导出失败: " + e.getMessage(), e);
        }
    }

    private static String describeCloudFilePlainText(byte[] data, String suffix) {
        if (isPdfBytes(data)) {
            return "【飞书附件 PDF】请在主持页查看分页预览";
        }
        if (isPptBytes(data)) {
            return "【飞书附件幻灯片】请在主持页查看翻页预览";
        }
        if (isExcelBytes(data)) {
            return "【飞书附件表格】请在主持页查看表格预览";
        }
        return "【飞书附件】" + suffix + "（" + data.length + " 字节）请点击下方链接在飞书中打开";
    }

    private static boolean isPdfBytes(byte[] data) {
        return data != null && data.length >= 4 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F';
    }

    private static boolean isPptBytes(byte[] data) {
        return data != null && data.length >= 2 && data[0] == 'P' && data[1] == 'K';
    }

    private static boolean isExcelBytes(byte[] data) {
        return isPptBytes(data);
    }

    private static String guessSuffix(byte[] data) {
        if (isPdfBytes(data)) {
            return ".pdf";
        }
        if (isPptBytes(data)) {
            return ".pptx";
        }
        return ".bin";
    }

}
