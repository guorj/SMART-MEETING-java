package com.smartmeeting.service.oabp;

import com.smartmeeting.api.dto.structured.SheetStructuredDto;
import com.smartmeeting.config.datasource.OabpDataSourceProperties;
import com.smartmeeting.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
 * 在 oabp 库执行会序配置的只读 SQL，映射为 {@link SheetStructuredDto} 供主持页表格展示。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "meeting.datasource.external.oabp", name = "enabled", havingValue = "true")
public class OabpAgendaTaskQueryService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final OabpDataSourceProperties properties;

    /**
     * @param oabpDataSource oabp Hikari 连接池
     * @param properties     oabp 连接与查询限制配置
     */
    public OabpAgendaTaskQueryService(
            @Qualifier("oabpDataSource") DataSource oabpDataSource,
            OabpDataSourceProperties properties) {
        this.jdbc = new JdbcTemplate(oabpDataSource);
        this.properties = properties;
        int timeout = properties.getQueryTimeoutSeconds();
        if (timeout > 0) {
            this.jdbc.setQueryTimeout(timeout);
        }
    }

    /**
     * 执行已校验的 SELECT，将结果集转为 sheet_cells 结构化 DTO。
     *
     * @param sql 经 {@link OabpReadOnlySqlValidator} 校验后的 SQL
     * @return 表头 + 行数据；无行时仍返回 headers
     * @throws BusinessException 执行失败时
     */
    public SheetStructuredDto queryAsSheet(String sql) {
        int maxRows = Math.max(1, properties.getMaxQueryRows());
        try {
            String normalized = OabpReadOnlySqlValidator.validateAndNormalize(sql);
            return jdbc.query(normalized, rs -> {
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
                return SheetStructuredDto.builder()
                        .sheetName("项目任务")
                        .headers(headers)
                        .rows(rows)
                        .headerRowCount(1)
                        .build();
            });
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, e.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String detail = describeError(e);
            log.warn("oabp agenda query failed: {}", detail, e);
            throw new BusinessException(502, "oabp 查询失败: " + detail);
        }
    }

    private static String describeError(Throwable e) {
        if (e == null) {
            return "unknown";
        }
        if (e.getMessage() != null && !e.getMessage().isBlank()) {
            return e.getMessage();
        }
        if (e.getCause() != null && e.getCause() != e) {
            String nested = describeError(e.getCause());
            if (!"unknown".equals(nested)) {
                return nested;
            }
        }
        if (e instanceof java.sql.SQLException sqlEx) {
            if (sqlEx.getSQLState() != null && !sqlEx.getSQLState().isBlank()) {
                return sqlEx.getSQLState()
                        + (sqlEx.getErrorCode() != 0 ? " (#" + sqlEx.getErrorCode() + ")" : "");
            }
        }
        return e.getClass().getSimpleName();
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
}
