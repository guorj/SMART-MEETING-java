package com.smartmeeting.service.structured;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvStructuredExporterTest {

    @Test
    void parsesQuotedCsv() throws Exception {
        Path temp = Files.createTempFile("test-", ".csv");
        Files.writeString(temp, "a,b\n\"1,2\",3\n");
        var dto = CsvStructuredExporter.export(temp);
        assertEquals(2, dto.getHeaders().size());
        assertEquals("1,2", dto.getRows().get(0).get(0));
        assertEquals("3", dto.getRows().get(0).get(1));
        Files.deleteIfExists(temp);
    }
}
