package com.smartmeeting.service.oabp;

import com.smartmeeting.api.dto.structured.SheetStructuredDto;
import com.smartmeeting.config.oabp.OabpSheetData;
import com.smartmeeting.config.oabp.OabpSheetDisplayMeta;

/** SheetStructuredDto 与 config-core OabpSheetData 互转。 */
public final class OabpSheetDataMapper {

    private OabpSheetDataMapper() {
    }

    public static OabpSheetData fromDto(SheetStructuredDto dto) {
        if (dto == null) {
            return OabpSheetData.builder().build();
        }
        return OabpSheetData.builder()
                .sheetName(dto.getSheetName())
                .headers(dto.getHeaders())
                .rows(dto.getRows())
                .columnWidths(dto.getColumnWidths())
                .headerRowCount(dto.getHeaderRowCount())
                .displayMeta(dto.getDisplayMeta())
                .build();
    }

    public static SheetStructuredDto toDto(OabpSheetData data) {
        if (data == null) {
            return null;
        }
        return SheetStructuredDto.builder()
                .sheetName(data.getSheetName())
                .headers(data.getHeaders())
                .rows(data.getRows())
                .columnWidths(data.getColumnWidths())
                .headerRowCount(data.getHeaderRowCount())
                .displayMeta(data.getDisplayMeta())
                .build();
    }
}
