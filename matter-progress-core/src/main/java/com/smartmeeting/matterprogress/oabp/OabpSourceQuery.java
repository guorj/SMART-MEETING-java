package com.smartmeeting.matterprogress.oabp;

import com.smartmeeting.config.oabp.OabpReadOnlySqlValidator;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 在 oabp 库执行 SOURCE 只读 SQL，格式化为 Markdown 表格（Legacy weekly-comparison 路径）。
 */
public class OabpSourceQuery {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final int maxRows;

    public OabpSourceQuery(DataSource dataSource, int maxRows, int queryTimeoutSeconds) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.maxRows = Math.max(1, maxRows);
        if (queryTimeoutSeconds > 0) {
            this.jdbc.setQueryTimeout(queryTimeoutSeconds);
        }
    }

    /**
     * @param sql 经 {@link OabpReadOnlySqlValidator} 校验的 SELECT
     * @return Markdown 表格文本
     */
    public String queryAsMarkdown(String sql) {
        String normalized = OabpReadOnlySqlValidator.validateAndNormalize(sql);
        QueryResult result = jdbc.query(normalized, rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<String> headers = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                String label = meta.getColumnLabel(i);
                headers.add(label != null && !label.isBlank() ? label : "col" + i);
            }
            List<List<String>> rows = new ArrayList<>();
            while (rs.next()) {
                List<String> row = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    row.add(formatCell(rs.getObject(i)));
                }
                rows.add(row);
                if (rows.size() >= maxRows) {
                    break;
                }
            }
            return new QueryResult(headers, rows);
        });
        return toMarkdownTable(result);
    }

    static String toMarkdownTable(QueryResult result) {
        if (result.headers().isEmpty()) {
            return "_（查询无列）_\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", result.headers())).append(" |\n");
        sb.append("|");
        for (int i = 0; i < result.headers().size(); i++) {
            sb.append(" --- |");
        }
        sb.append("\n");
        if (result.rows().isEmpty()) {
            sb.append("| _无数据_");
            for (int i = 1; i < result.headers().size(); i++) {
                sb.append(" | ");
            }
            sb.append(" |\n");
            return sb.toString();
        }
        for (List<String> row : result.rows()) {
            sb.append("| ");
            for (int i = 0; i < result.headers().size(); i++) {
                if (i > 0) {
                    sb.append(" | ");
                }
                String cell = i < row.size() ? row.get(i) : "";
                sb.append(escapeMarkdownCell(cell));
            }
            sb.append(" |\n");
        }
        return sb.toString();
    }

    private static String escapeMarkdownCell(String cell) {
        if (cell == null || cell.isEmpty()) {
            return "";
        }
        return cell.replace("|", "\\|").replace("\n", " ").replace("\r", "");
    }

    static String formatCell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(DT);
        }
        if (value instanceof LocalDate ld) {
            return ld.toString();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().toString();
        }
        if (value instanceof Time sqlTime) {
            return sqlTime.toLocalTime().toString();
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DT);
        }
        if (value instanceof Date d) {
            return DT.format(d.toInstant().atZone(ZoneId.of("Asia/Shanghai")));
        }
        if (value instanceof Boolean b) {
            return b ? "1" : "0";
        }
        if (value instanceof byte[] bytes) {
            if (bytes.length == 1) {
                return Integer.toString(bytes[0] & 0xFF);
            }
            return "[binary " + bytes.length + " bytes]";
        }
        return String.valueOf(value);
    }

    record QueryResult(List<String> headers, List<List<String>> rows) {
    }
}
