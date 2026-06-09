package com.smartmeeting.matterprogress.feishu;

import com.smartmeeting.matterprogress.config.SpreadsheetFetchLimits;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书多类型读写客户端（docx / wiki / base），供 bot 使用。
 * <p>
 * 读取能力参照 meeting-server {@code FeishuService.fetchResourcePlainText}：
 * <ul>
 *   <li>DOCX：分页遍历 blocks API，提取 text_run + equation</li>
     *   <li>WIKI：get_node 解析 obj_type/obj_token 后递归到 docx、bitable 或 sheet</li>
 *   <li>BASE：bitable records/search 分页导出全部记录，含 WrongTableId 自动纠正</li>
 * </ul>
 * 写入仍为 docx 创建 + block 追加。
 */
public class RestFeishuDocClient implements FeishuDocClient {

    private static final Logger log = LoggerFactory.getLogger(RestFeishuDocClient.class);

    private static final Pattern DOCX_PATH = Pattern.compile("/docx/([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIKI_PATH = Pattern.compile("/wiki/([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE_PATH = Pattern.compile("/base/([a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final String appId;
    private final String appSecret;
    private final String baseUrl;
    private int docBlockBatchSize = 50;
    private int docBlockBatchSleepMs = 400;
    private SpreadsheetFetchLimits spreadsheetFetchLimits = SpreadsheetFetchLimits.DEFAULT;

    public RestFeishuDocClient(RestTemplate restTemplate, String appId, String appSecret, String baseUrl) {
        this.restTemplate = restTemplate;
        this.appId = appId;
        this.appSecret = appSecret;
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.replaceAll("/$", "") : "https://open.feishu.cn";
    }

    public void setDocBlockBatchSize(int docBlockBatchSize) {
        this.docBlockBatchSize = Math.max(1, docBlockBatchSize);
    }

    public void setDocBlockBatchSleepMs(int docBlockBatchSleepMs) {
        this.docBlockBatchSleepMs = Math.max(0, docBlockBatchSleepMs);
    }

    public void setSpreadsheetFetchLimits(SpreadsheetFetchLimits spreadsheetFetchLimits) {
        this.spreadsheetFetchLimits = spreadsheetFetchLimits != null
                ? spreadsheetFetchLimits : SpreadsheetFetchLimits.DEFAULT;
    }

    // ---------------------------------------------------------------
    // 读取：按 URL 自动分派 docx / wiki / base
    // ---------------------------------------------------------------

    /**
     * 拉取飞书链接对应纯文本，按 URL 路径自动识别 docx / wiki / base 类型。
     * <ul>
     *   <li>/docx/{id} — 直接读 blocks</li>
     *   <li>/wiki/{id} — get_node 解析后递归</li>
     *   <li>/base/{id}?table=tbl… — bitable records/search</li>
     * </ul>
     * 无法识别的 URL 返回空字符串（不再返回占位文本，由调用方决定降级策略）。
     */
    @Override
    public String fetchPlainText(String feishuDocUrl) {
        if (feishuDocUrl == null || feishuDocUrl.isBlank()) {
            return "";
        }
        String url = feishuDocUrl.trim();

        Matcher docxM = DOCX_PATH.matcher(url);
        if (docxM.find()) {
            try {
                return fetchDocxPlainText(docxM.group(1));
            } catch (Exception e) {
                logFetchFailed("docx", url, e);
                return "";
            }
        }

        Matcher wikiM = WIKI_PATH.matcher(url);
        if (wikiM.find()) {
            String nodeToken = wikiM.group(1);
            String tableId = queryParam(url, "table");
            String viewId = queryParam(url, "view");
            try {
                return fetchWikiPlainText(nodeToken, tableId, viewId);
            } catch (Exception e) {
                logFetchFailed("wiki", url, e);
                return "";
            }
        }

        Matcher baseM = BASE_PATH.matcher(url);
        if (baseM.find()) {
            String appToken = baseM.group(1);
            String tableId = queryParam(url, "table");
            if (tableId == null || tableId.isBlank()) {
                log.warn("base URL missing ?table= param, cannot fetch records: {}", url);
                return "";
            }
            try {
                return fetchBitablePlainText(appToken, tableId);
            } catch (Exception e) {
                logFetchFailed("base", url, e);
                return "";
            }
        }

        log.warn("unrecognized feishu URL pattern: {}", url);
        return "";
    }

    // ---------------------------------------------------------------
    // 写入：创建 Docx + 追加 block
    // ---------------------------------------------------------------

    @Override
    public String createAndWriteMarkdown(String folderToken, String title, String markdown) {
        String body = markdown != null ? markdown : "";
        Map<String, String> created = createDoc(folderToken, title);
        String docToken = created.get("docToken");
        String docUrl = created.get("docUrl");
        if (!updateDoc(docToken, body)) {
            throw new IllegalStateException("飞书文档正文写入失败");
        }
        return docUrl;
    }

    // ---------------------------------------------------------------
    // DOCX 读取（分页 blocks → text_run + equation）
    // ---------------------------------------------------------------

    private String fetchDocxPlainText(String documentId) {
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
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tenantToken);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    ub.toUriString(), HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
            JsonNode json = response.getBody();
            if (json == null) {
                throw new RuntimeException("飞书 docx blocks 响应为空");
            }
            int code = json.path("code").asInt(-1);
            if (code != 0) {
                throw new RuntimeException("飞书 docx blocks code=" + code + " msg=" + json.path("msg").asText(""));
            }
            JsonNode items = json.path("data").path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    appendDocxBlockPlainText(item, out);
                }
            }
            boolean hasMore = json.path("data").path("has_more").asBoolean(false);
            String next = json.path("data").path("page_token").asText("");
            pageToken = hasMore && !next.isBlank() ? next : null;
            if (pageToken != null) {
                try { Thread.sleep(350); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        } while (pageToken != null);
        return out.toString().trim();
    }

    /** 从单条 block JSON 导出为 Markdown 结构纯文本（标题/列表保留层级）。 */
    private static void appendDocxBlockPlainText(JsonNode block, StringBuilder out) {
        DocxBlockMarkdownExporter.appendBlock(block, out);
    }

    // ---------------------------------------------------------------
    // WIKI 读取（get_node → obj_type/obj_token → 递归 docx 或 bitable）
    // ---------------------------------------------------------------

    /**
     * 知识库节点：get_node 取得 obj_token 后，docx 类型走 {@link #fetchDocxPlainText}，bitable 走 {@link #fetchBitablePlainText}。
     *
     * @param nodeToken wiki node_token（URL 中 /wiki/ 后的部分）
     * @param tableId   URL 中 ?table= 参数（可选；wiki 内嵌 bitable 无 table 时拉取全部数据表）
     * @param viewId    URL 中 ?view= 参数（可选）
     */
    private String fetchWikiPlainText(String nodeToken, String tableId, String viewId) {
        if (nodeToken == null || nodeToken.isBlank()) {
            throw new IllegalArgumentException("wiki node_token 为空");
        }
        String tenantToken = getTenantToken();
        String url = baseUrl + "/open-apis/wiki/v2/spaces/get_node?token=" + nodeToken.trim();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
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
            return fetchBitablePlainText(objToken, tableId);
        }
        if ("sheet".equalsIgnoreCase(objType)) {
            return FeishuSpreadsheetPlainTextFetcher.fetch(
                    restTemplate, baseUrl, getTenantToken(), objToken, spreadsheetFetchLimits);
        }
        throw new RuntimeException("暂不支持的 Wiki 节点类型: " + objType);
    }

    // ---------------------------------------------------------------
    // BASE / Bitable 读取（records/search + WrongTableId 自动纠正）
    // ---------------------------------------------------------------

    /**
     * 多维表格：导出前 50 行记录为纯文本。
     *
     * @param appToken 多维表格 app_token（URL 中 /base/ 后的部分）
     * @param tableId  数据表 table_id（URL 中 ?table= 参数）
     */
    private String fetchBitablePlainText(String appToken, String tableId) {
        if (appToken == null || appToken.isBlank()) {
            throw new IllegalArgumentException("base app_token 为空");
        }
        String app = appToken.trim();
        if (tableId == null || tableId.isBlank()) {
            return fetchAllBitableTablesPlainText(app);
        }
        String table = tableId.trim();
        try {
            return searchBitableRecords(app, table, null);
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
                return searchBitableRecords(app, corrected, name);
            }
            throw new RuntimeException(formatWrongTableIdHint(app, table, available), first);
        }
    }

    private String fetchAllBitableTablesPlainText(String appToken) {
        List<BitableTableInfo> tables = listBitableTables(appToken);
        if (tables.isEmpty()) {
            return "【多维表格摘要，共 0 条 · 0 个数据表】\n\n（无数据表）";
        }
        List<BitablePlainTextExporter.BitableTableSlice> slices = new ArrayList<>();
        int totalRows = 0;
        for (BitableTableInfo table : tables) {
            List<JsonNode> items = searchBitableRecordItems(appToken, table.tableId());
            totalRows += items.size();
            slices.add(new BitablePlainTextExporter.BitableTableSlice(
                    table.tableId(), table.tableName(), items));
        }
        String title = "【多维表格摘要，共 " + totalRows + " 条 · " + tables.size() + " 个数据表】";
        return BitablePlainTextExporter.exportMultiTable(slices, title);
    }

    /** 调用 bitable records/search API 分页拉取全部记录（单页最多 500 条）。 */
    private String searchBitableRecords(String appToken, String tableId, String tableName) {
        List<JsonNode> items = searchBitableRecordItems(appToken, tableId);
        String label = tableName != null && !tableName.isBlank() ? tableName.trim() : tableId;
        String title = "【多维表格·" + label + "，共 " + items.size() + " 条】";
        return BitablePlainTextExporter.export(items, title);
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

    List<BitableTableInfo> listBitableTables(String appToken) {
        String tenantToken = getTenantToken();
        String url = baseUrl + "/open-apis/bitable/v1/apps/" + appToken.trim() + "/tables?page_size=100";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
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

    List<String> listBitableTableIds(String appToken) {
        return listBitableTables(appToken).stream().map(BitableTableInfo::tableId).toList();
    }

    /**
     * 记录拉取失败；91403 表示开放平台 scope 已开但文档未授权给应用（需「添加文档应用」）。
     */
    private void logFetchFailed(String resourceType, String url, Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (msg.contains("91403") || (msg.contains("Forbidden") && msg.contains("403"))) {
            log.warn(
                    "fetch {} failed appId={} url={}: {} — 文档级权限不足：请在飞书多维表格/云文档右上角 "
                            + "「...」→「添加文档应用」，选择会前对比应用并授予「可阅读」或「可管理」"
                            + "（开启高级权限时需可管理）；与 99991672（未开 API scope）不同",
                    resourceType, appId, url, msg);
        } else {
            log.warn("fetch {} failed appId={} url={}: {}", resourceType, appId, url, msg);
        }
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
                .append("或更新 int_matter_progress_doc_config。");
        if (available != null && !available.isEmpty()) {
            sb.append(" 当前应用下可用 table_id：").append(String.join(", ", available));
        }
        return sb.toString();
    }

    private static String fieldValueAsText(JsonNode v) {
        return BitableFieldFormatter.format(v, null);
    }

    // ---------------------------------------------------------------
    // DOCX 写入
    // ---------------------------------------------------------------

    private Map<String, String> createDoc(String folderToken, String title) {
        String token = getTenantToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        Map<String, Object> body = new HashMap<>();
        if (folderToken != null && !folderToken.isBlank()) {
            body.put("folder_token", folderToken);
        }
        body.put("title", title != null ? title : "事项对比通报");
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/open-apis/docx/v1/documents",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class);
        JsonNode json = response.getBody();
        if (json != null && json.has("data")) {
            JsonNode doc = json.path("data").path("document");
            return Map.of(
                    "docToken", doc.path("document_id").asText(),
                    "docUrl", doc.path("url").asText());
        }
        throw new RuntimeException("createDoc failed: " + json);
    }

    /**
     * 按行写入 Docx；URL 须含根 block_id（与 document_id 相同），见 meeting-server {@code FeishuService.updateDocBlocks}。
     */
    private boolean updateDoc(String docToken, String content) {
        if (docToken == null || docToken.isBlank()) {
            return false;
        }
        String markdownBody = content != null ? content : "";
        List<Map<String, Object>> blocks = new ArrayList<>();
        for (String line : markdownBody.split("\n", -1)) {
            Map<String, Object> block = new HashMap<>();
            block.put("block_type", 2);
            Map<String, Object> text = new HashMap<>();
            text.put("elements", List.of(Map.of("text_run", Map.of("content", line.isEmpty() ? " " : line))));
            block.put("text", text);
            blocks.add(block);
        }
        if (blocks.isEmpty()) {
            return true;
        }
        return updateDocBlocks(docToken, blocks);
    }

    /** POST .../documents/{doc_id}/blocks/{block_id}/children（根 block_id = doc_id） */
    private boolean updateDocBlocks(String docToken, List<Map<String, Object>> blocks) {
        String tenantToken = getTenantToken();
        String url = baseUrl + "/open-apis/docx/v1/documents/" + docToken + "/blocks/" + docToken + "/children";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tenantToken);

        final int batchSize = docBlockBatchSize;
        for (int i = 0; i < blocks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, blocks.size());
            List<Map<String, Object>> batch = blocks.subList(i, end);
            Map<String, Object> body = Map.of("children", batch);
            try {
                ResponseEntity<JsonNode> response = restTemplate.exchange(
                        url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
                JsonNode json = response.getBody();
                int code = json != null ? json.path("code").asInt(-1) : -1;
                if (!response.getStatusCode().is2xxSuccessful() || code != 0) {
                    log.warn("updateDoc blocks failed appId={} docToken={} batch={}-{} code={} msg={}",
                            appId, docToken, i, end, code,
                            json != null ? json.path("msg").asText("") : response.getStatusCode());
                    return false;
                }
                if (end < blocks.size() && docBlockBatchSleepMs > 0) {
                    Thread.sleep(docBlockBatchSleepMs);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.warn("updateDoc failed appId={} docToken={} url={}: {}", appId, docToken, url, e.getMessage());
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------
    // 鉴权
    // ---------------------------------------------------------------

    private String getTenantToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of("app_id", appId, "app_secret", appSecret);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                baseUrl + "/open-apis/auth/v3/tenant_access_token/internal",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class);
        JsonNode json = response.getBody();
        if (json != null && json.path("code").asInt(-1) == 0) {
            return json.path("tenant_access_token").asText();
        }
        throw new RuntimeException("tenant_access_token failed: " + json);
    }

    // ---------------------------------------------------------------
    // URL 工具
    // ---------------------------------------------------------------

    /** 从 URL query 字符串提取指定参数值。 */
    private static String queryParam(String url, String name) {
        try {
            String q = URI.create(url).getRawQuery();
            if (q == null || q.isBlank()) {
                return null;
            }
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                if (name.equalsIgnoreCase(part.substring(0, eq).trim())) {
                    String raw = part.substring(eq + 1).trim();
                    try {
                        return URLDecoder.decode(raw, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {
                        return raw;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
