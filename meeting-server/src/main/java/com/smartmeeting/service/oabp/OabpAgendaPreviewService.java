package com.smartmeeting.service.oabp;

import com.smartmeeting.api.dto.structured.SheetStructuredDto;
import com.smartmeeting.config.oabp.OabpDisplayTemplate;
import com.smartmeeting.config.oabp.OabpDisplayTemplateValidator;
import com.smartmeeting.config.oabp.OabpSheetDisplayMeta;
import com.smartmeeting.config.oabp.OabpSheetTemplateEngine;
import com.smartmeeting.config.oabp.OabpSqlColumnLabelInferer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * oabp 会序资料查询 + 展示模板预览（admin internal 与运行时共用逻辑）。
 */
@Service
public class OabpAgendaPreviewService {

    private final OabpAgendaTaskQueryService queryService;

    public OabpAgendaPreviewService(@Autowired(required = false) OabpAgendaTaskQueryService queryService) {
        this.queryService = queryService;
    }

    public Map<String, Object> probeColumns(String sql) {
        SheetStructuredDto sheet = requireQuery(sql);
        List<String> headers = sheet.getHeaders() != null ? sheet.getHeaders() : List.of();
        Map<String, List<String>> samples = new LinkedHashMap<>();
        for (String h : headers) {
            samples.put(h, sampleColumn(sheet, h, 20));
        }
        Map<String, String> columnLabels = OabpSqlColumnLabelInferer.infer(sql, headers);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("headers", headers);
        out.put("samples", samples);
        out.put("columnLabels", columnLabels);
        return out;
    }

    public Map<String, Object> preview(String sql, OabpDisplayTemplate template, int maxQueryRows) {
        return preview(sql, template, maxQueryRows, false);
    }

    public Map<String, Object> preview(String sql, OabpDisplayTemplate template, int maxQueryRows, boolean sqlStrict) {
        SheetStructuredDto raw = requireQuery(sql);
        List<String> headers = raw.getHeaders() != null ? raw.getHeaders() : List.of();
        List<String> issues = !sqlStrict && template != null && !template.isEmpty()
                ? OabpDisplayTemplateValidator.validate(template, headers, maxQueryRows)
                : List.of();
        SheetStructuredDto rendered = raw;
        if (sqlStrict) {
            rendered.setDisplayMeta(OabpSheetDisplayMeta.builder().sqlStrict(true).build());
        } else if (template != null && !template.isEmpty() && issues.isEmpty()) {
            rendered = OabpSheetDataMapper.toDto(
                    OabpSheetTemplateEngine.apply(OabpSheetDataMapper.fromDto(raw), template));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sheet", rendered);
        out.put("rowCount", rendered.getRows() != null ? rendered.getRows().size() : 0);
        out.put("issues", issues);
        return out;
    }

    private SheetStructuredDto requireQuery(String sql) {
        if (queryService == null) {
            throw new IllegalStateException("oabp 数据源未启用");
        }
        return queryService.queryAsSheet(sql);
    }

    private static List<String> sampleColumn(SheetStructuredDto sheet, String header, int limit) {
        List<String> headers = sheet.getHeaders();
        if (headers == null || sheet.getRows() == null) {
            return List.of();
        }
        int idx = headers.indexOf(header);
        if (idx < 0) {
            return List.of();
        }
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (List<String> row : sheet.getRows()) {
            if (row == null || idx >= row.size()) {
                continue;
            }
            String v = row.get(idx);
            if (v != null && !v.isBlank()) {
                distinct.add(v);
            }
            if (distinct.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(distinct);
    }
}
