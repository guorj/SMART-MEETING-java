package com.smartmeeting.config.agenda;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgendaMaterialFileSupportTest {

    @Test
    void validateAndNormalizeMime_acceptsPptxWithOctetStream() {
        String mime = AgendaMaterialFileSupport.validateAndNormalizeMime(
                "application/octet-stream", "report.pptx");
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", mime);
    }

    @Test
    void validateAndNormalizeMime_acceptsPdfWithOctetStream() {
        String mime = AgendaMaterialFileSupport.validateAndNormalizeMime(
                "application/octet-stream", "slides.pdf");
        assertEquals("application/pdf", mime);
    }

    @Test
    void validateAndNormalizeMime_rejectsUnknownExtension() {
        assertNull(AgendaMaterialFileSupport.validateAndNormalizeMime(
                "application/octet-stream", "archive.zip"));
    }

    @Test
    void validateAndNormalizeMime_rejectsMimeExtensionMismatch() {
        assertNull(AgendaMaterialFileSupport.validateAndNormalizeMime(
                "application/pdf", "report.pptx"));
    }
}
