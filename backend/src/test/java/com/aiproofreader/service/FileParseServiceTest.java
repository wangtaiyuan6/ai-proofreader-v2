package com.aiproofreader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FileParseServiceTest {

    private FileParseService service;

    @BeforeEach
    void setUp() {
        service = new FileParseService();
    }

    // ==================== validateFileType() ====================

    @Test
    void validateFileType_pdf_validMagicBytes() {
        byte[] buffer = "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8);
        assertThat(service.validateFileType(buffer, "test.pdf")).isTrue();
    }

    @Test
    void validateFileType_pdf_invalidMagicBytes() {
        byte[] buffer = "not a pdf".getBytes(StandardCharsets.UTF_8);
        assertThat(service.validateFileType(buffer, "test.pdf")).isFalse();
    }

    @Test
    void validateFileType_docx_validMagicBytes() {
        // PK magic bytes (ZIP format)
        byte[] buffer = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00};
        assertThat(service.validateFileType(buffer, "test.docx")).isTrue();
    }

    @Test
    void validateFileType_docx_invalidMagicBytes() {
        byte[] buffer = "not a docx".getBytes(StandardCharsets.UTF_8);
        assertThat(service.validateFileType(buffer, "test.docx")).isFalse();
    }

    @Test
    void validateFileType_txt_normalText() {
        byte[] buffer = "这是一段正常的中文文本".getBytes(StandardCharsets.UTF_8);
        assertThat(service.validateFileType(buffer, "test.txt")).isTrue();
    }

    @Test
    void validateFileType_txt_binaryContent() {
        byte[] buffer = {0x00, 0x01, 0x02, 0x03};
        assertThat(service.validateFileType(buffer, "test.txt")).isFalse();
    }

    @Test
    void validateFileType_txt_elfExecutable() {
        byte[] buffer = {0x7F, 0x45, 0x4C, 0x46, 0x00, 0x00};
        assertThat(service.validateFileType(buffer, "test.txt")).isFalse();
    }

    @Test
    void validateFileType_txt_peExecutable() {
        byte[] buffer = {0x4D, 0x5A, 0x00, 0x00};
        assertThat(service.validateFileType(buffer, "test.txt")).isFalse();
    }

    @Test
    void validateFileType_txt_javaClassFile() {
        byte[] buffer = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0x00};
        assertThat(service.validateFileType(buffer, "test.txt")).isFalse();
    }

    @Test
    void validateFileType_nullBuffer() {
        assertThat(service.validateFileType(null, "test.txt")).isFalse();
    }

    @Test
    void validateFileType_nullFilename() {
        byte[] buffer = "test".getBytes(StandardCharsets.UTF_8);
        assertThat(service.validateFileType(buffer, null)).isFalse();
    }

    @Test
    void validateFileType_unknownExtension() {
        byte[] buffer = "test".getBytes(StandardCharsets.UTF_8);
        assertThat(service.validateFileType(buffer, "test.xyz")).isFalse();
    }

    // ==================== parseFile() ====================

    @Test
    void parseFile_txt_returnsContent() throws Exception {
        byte[] buffer = "Hello 你好世界".getBytes(StandardCharsets.UTF_8);
        String result = service.parseFile(buffer, "test.txt");
        assertThat(result).isEqualTo("Hello 你好世界");
    }

    @Test
    void parseFile_unsupportedExtension_throwsException() {
        byte[] buffer = "test".getBytes(StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> service.parseFile(buffer, "test.xyz"));
    }

    // ==================== getMaxFileSize() ====================

    @Test
    void getMaxFileSize_returns20MB() {
        assertThat(service.getMaxFileSize()).isEqualTo(20L * 1024 * 1024);
    }

    // ==================== shutdown() ====================

    @Test
    void shutdown_completesWithoutException() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> service.shutdown());
    }
}
