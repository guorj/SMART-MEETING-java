package com.smartmeeting.matterprogress.feishu;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 飞书电子表格（sheet）正文拉取：sheets v3 列工作/worksheets + v2 读单元格范围，输出 Markdown 表格。
 */
public final class FeishuSpreadsheetPlainTextFetcher {

    private static final int MAX_SHEETS = 20;
    private static final int DEFAULT_MAX_ROWS = 200;
    private static final int DEFAULT_MAX_COLS = 26;
    private static final int ABS_MAX_ROWS = 500;
    private static final int ABS_MAX_COLS = 50;

    private FeishuSpreadsheetPlainTextFetcher() {
    }

    /**
     * @param spreadsheetToken wiki get_node 返回的 obj_token（obj_type=sheet）
     */
    public static String fetch(RestTemplate restTemplate, String baseUrl, String tenantToken, String spreadsheetToken) {
        if (spreadsheetToken == null || spreadsheetToken.isBlank()) {
            throw new IllegalArgumentException("spreadsheet_token 为空");
        }
        String token = spreadsheetToken.trim();
        String root = normalizeBaseUrl(baseUrl);

        List<JsonNode> sheets = querySheets(restTemplate, root, tenantToken, token);
        if (sheets.isEmpty()) {
            return "【电子表格】\n\n（无工作表）";
        }
        sheets = new ArrayList<>(sheets);
        sheets.sort(Comparator.comparingInt(s -> s.path("index").asInt(0)));

        StringBuilder out = new StringBuilder();
        out.append("【电子表格摘要】\n");
        int count = 0;
        int skipped = 0;
        for (JsonNode sheet : sheets) {
            if (count >= MAX_SHEETS) {
                out.append("\n\n（其余工作表已省略，请在飞书中打开查看）");
                break;
            }
            if (sheet.path("hidden").asBoolean(false)) {
                continue;
            }
            if (!isReadableSheetResource(sheet)) {
                skipped++;
                continue;
            }
            String sheetId = resolveSheetId(sheet);
            if (sheetId.isBlank()) {
                skipped++;
                continue;
            }
            String title = sheet.path("title").asText("未命名工作表");
            JsonNode grid = sheet.path("grid_properties");
            String tableMd = readSheetAsMarkdown(restTemplate, root, tenantToken, token, sheetId, title, grid);
            if (tableMd.isBlank()) {
                skipped++;
                continue;
            }
            if (count > 0) {
                out.append("\n\n");
            }
            out.append(tableMd);
            count++;
        }
        if (count == 0) {
            return "【电子表格】\n\n（工作表为空或不可读；部分工作表 sheetId 无效已跳过，请在飞书中打开查看）";
        }
        if (skipped > 0) {
            out.append("\n\n（已跳过 ").append(skipped).append(" 个不可读工作表）");
        }
        return out.toString().trim();
    }

    /** 仅读取普通网格 sheet；跳过 bitable、仪表盘等 resource_type。 */
    static boolean isReadableSheetResource(JsonNode sheet) {
        if (sheet == null || sheet.isNull()) {
            return false;
        }
        String type = sheet.path("resource_type").asText("").trim().toLowerCase();
        if (type.isEmpty() || "sheet".equals(type) || "sheets".equals(type)) {
            return true;
        }
        return false;
    }

    static String resolveSheetId(JsonNode sheet) {
        String id = sheet.path("sheet_id").asText("").trim();
        if (!id.isEmpty()) {
            return id;
        }
        return sheet.path("sheetId").asText("").trim();
    }

    private static String readSheetAsMarkdown(
            RestTemplate restTemplate,
            String baseUrl,
            String tenantToken,
            String spreadsheetToken,
            String sheetId,
            String title,
            JsonNode grid) {
        String range = buildReadRange(sheetId, grid);
        try {
            JsonNode values = readRangeValues(restTemplate, baseUrl, tenantToken, spreadsheetToken, range);
            return formatValuesAsMarkdownTable(title, values);
        } catch (RuntimeException first) {
            if (!isSheetIdNotFound(first)) {
                throw first;
            }
        }
        String fallbackRange = sheetId + "!A1:" + columnIndexToLetter(DEFAULT_MAX_COLS - 1) + DEFAULT_MAX_ROWS;
        try {
            JsonNode values = readRangeValues(restTemplate, baseUrl, tenantToken, spreadsheetToken, fallbackRange);
            return formatValuesAsMarkdownTable(title, values);
        } catch (RuntimeException second) {
            if (isSheetIdNotFound(second)) {
                return "";
            }
            throw second;
        }
    }

    static boolean isSheetIdNotFound(RuntimeException e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        String msg = e.getMessage();
        return msg.contains("90215") || msg.contains("SheetIdNotFound") || msg.contains("not found sheetId");
    }

    static String buildReadRange(String sheetId, JsonNode gridProperties) {
        int rows = gridProperties != null && gridProperties.isObject()
                ? gridProperties.path("row_count").asInt(DEFAULT_MAX_ROWS)
                : DEFAULT_MAX_ROWS;
        int cols = gridProperties != null && gridProperties.isObject()
                ? gridProperties.path("column_count").asInt(DEFAULT_MAX_COLS)
                : DEFAULT_MAX_COLS;
        rows = Math.max(1, Math.min(rows, ABS_MAX_ROWS));
        cols = Math.max(1, Math.min(cols, ABS_MAX_COLS));
        String endCol = columnIndexToLetter(cols - 1);
        return sheetId + "!A1:" + endCol + rows;
    }

    static String columnIndexToLetter(int index) {
        if (index < 0) {
            return "A";
        }
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n >= 0) {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        }
        return sb.toString();
    }

    static String formatValuesAsMarkdownTable(String sheetTitle, JsonNode valuesNode) {
        if (valuesNode == null || !valuesNode.isArray() || valuesNode.isEmpty()) {
            return "## 工作表：" + escapeMarkdownCell(sheetTitle) + "\n\n（无数据）";
        }
        List<List<String>> rows = new ArrayList<>();
        int maxCols = 0;
        for (JsonNode rowNode : valuesNode) {
            List<String> row = new ArrayList<>();
            if (rowNode != null && rowNode.isArray()) {
                for (JsonNode cell : rowNode) {
                    row.add(cell == null || cell.isNull() ? "" : cell.asText("").trim());
                }
            }
            if (!row.isEmpty()) {
                maxCols = Math.max(maxCols, row.size());
            }
            rows.add(row);
        }
        rows = trimTrailingEmptyRows(rows);
        if (rows.isEmpty()) {
            return "## 工作表：" + escapeMarkdownCell(sheetTitle) + "\n\n（无数据）";
        }
        for (List<String> row : rows) {
            while (row.size() < maxCols) {
                row.add("");
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("## 工作表：").append(escapeMarkdownCell(sheetTitle)).append("\n\n");
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            out.append('|');
            for (String cell : row) {
                out.append(' ').append(escapeMarkdownCell(cell)).append(" |");
            }
            out.append('\n');
            if (r == 0) {
                out.append('|');
                for (int c = 0; c < row.size(); c++) {
                    out.append(" --- |");
                }
                out.append('\n');
            }
        }
        return out.toString().trim();
    }

    private static List<List<String>> trimTrailingEmptyRows(List<List<String>> rows) {
        int last = rows.size() - 1;
        while (last >= 0 && isEmptyRow(rows.get(last))) {
            last--;
        }
        if (last < 0) {
            return List.of();
        }
        List<List<String>> trimmed = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            if (!isEmptyRow(rows.get(i)) || i < last) {
                trimmed.add(rows.get(i));
            }
        }
        return trimmed;
    }

    private static boolean isEmptyRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (String cell : row) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String escapeMarkdownCell(String text) {
        if (text == null || text.isEmpty()) {
            return " ";
        }
        return text.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
    }

    private static List<JsonNode> querySheets(
            RestTemplate restTemplate, String baseUrl, String tenantToken, String spreadsheetToken) {
        String url = baseUrl + "/open-apis/sheets/v3/spreadsheets/" + spreadsheetToken + "/sheets/query";
        JsonNode json = exchangeGet(restTemplate, url, tenantToken);
        int code = json.path("code").asInt(-1);
        if (code != 0) {
            throw new RuntimeException("飞书 sheets query code=" + code + " msg=" + json.path("msg").asText(""));
        }
        JsonNode sheets = json.path("data").path("sheets");
        List<JsonNode> out = new ArrayList<>();
        if (sheets.isArray()) {
            for (JsonNode sheet : sheets) {
                out.add(sheet);
            }
        }
        return out;
    }

    private static JsonNode readRangeValues(
            RestTemplate restTemplate,
            String baseUrl,
            String tenantToken,
            String spreadsheetToken,
            String range) {
        String encodedRange = encodeRangeForUrlPath(range);
        String url = UriComponentsBuilder
                .fromUriString(baseUrl + "/open-apis/sheets/v2/spreadsheets/" + spreadsheetToken + "/values/"
                        + encodedRange)
                .queryParam("valueRenderOption", "ToString")
                .toUriString();
        JsonNode json = exchangeGet(restTemplate, url, tenantToken);
        int code = json.path("code").asInt(-1);
        if (code != 0) {
            throw new RuntimeException("飞书 sheets values code=" + code + " msg=" + json.path("msg").asText(""));
        }
        return json.path("data").path("valueRange").path("values");
    }

    private static JsonNode exchangeGet(RestTemplate restTemplate, String url, String tenantToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tenantToken);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        JsonNode json = response.getBody();
        if (json == null) {
            throw new RuntimeException("飞书 sheets API 响应为空: " + url);
        }
        return json;
    }

    /** v2 values 路径中的 range：保留 {@code !}，对其余特殊字符编码。 */
    static String encodeRangeForUrlPath(String range) {
        if (range == null || range.isBlank()) {
            return "";
        }
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < range.length(); i++) {
            char c = range.charAt(i);
            if (c == '!' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == ':' || c == '.' || c == '_' || c == '-') {
                encoded.append(c);
            } else {
                encoded.append(URLEncoder.encode(String.valueOf(c), StandardCharsets.UTF_8));
            }
        }
        return encoded.toString();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://open.feishu.cn";
        }
        return baseUrl.replaceAll("/$", "");
    }
}
