package com.aiproofreader.service;

import jakarta.annotation.PreDestroy;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.ooxml.POIXMLException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class FileParseService {

    private static final Logger log = LoggerFactory.getLogger(FileParseService.class);
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024; // 20MB
    private static final long PARSE_TIMEOUT_MS = 30_000; // 30 seconds

    private final ExecutorService executor = new ThreadPoolExecutor(
            1, 10, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(20), r -> {
        Thread t = new Thread(r, "file-parse");
        t.setDaemon(true);
        return t;
    }, new ThreadPoolExecutor.AbortPolicy());

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭 FileParseService 线程池...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Validate file type using magic bytes (file signatures).
     */
    public boolean validateFileType(byte[] buffer, String filename) {
        if (buffer == null || buffer.length == 0 || filename == null) return false;

        String ext = getExtension(filename).toLowerCase();

        return switch (ext) {
            case "pdf" -> validatePdf(buffer);
            case "docx" -> validateDocx(buffer);
            case "txt" -> validateTxt(buffer);
            default -> false;
        };
    }

    /**
     * Parse file content to plain text with timeout.
     */
    public String parseFile(byte[] buffer, String filename) throws Exception {
        String ext = getExtension(filename).toLowerCase();

        Future<String> future = executor.submit(() -> switch (ext) {
            case "txt" -> new String(buffer, StandardCharsets.UTF_8);
            case "docx" -> parseDocx(buffer);
            case "pdf" -> parsePdf(buffer);
            default -> throw new IOException("不支持的文件格式: ." + ext);
        });

        try {
            return future.get(PARSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("文件解析超时（30秒）");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioEx) throw ioEx;
            if (cause instanceof RuntimeException rte) throw rte;
            throw new RuntimeException(cause != null ? cause : e);
        }
    }

    public long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }

    private boolean validatePdf(byte[] buffer) {
        if (buffer.length < 4) return false;
        return buffer[0] == '%' && buffer[1] == 'P' && buffer[2] == 'D' && buffer[3] == 'F';
    }

    private boolean validateDocx(byte[] buffer) {
        if (buffer.length < 2) return false;
        // DOCX is a ZIP file, check for PK signature
        return buffer[0] == 0x50 && buffer[1] == 0x4B;
    }

    private boolean validateTxt(byte[] buffer) {
        // Check first 8KB for null bytes (binary indicator)
        int checkLen = Math.min(buffer.length, 8192);
        for (int i = 0; i < checkLen; i++) {
            if (buffer[i] == 0) return false;
        }

        // Check for known binary signatures in first 4 bytes
        if (buffer.length >= 4) {
            // ELF executable: 0x7F 0x45 0x4C 0x46
            if (buffer[0] == 0x7F && buffer[1] == 0x45 && buffer[2] == 0x4C && buffer[3] == 0x46) return false;
            // PE executable (MZ): 0x4D 0x5A
            if (buffer[0] == 0x4D && buffer[1] == 0x5A) return false;
            // Java class file: 0xCA 0xFE 0xBA 0xBE
            if (buffer[0] == (byte) 0xCA && buffer[1] == (byte) 0xFE
                    && buffer[2] == (byte) 0xBA && buffer[3] == (byte) 0xBE) return false;
        }

        return true;
    }

    private String parseDocx(byte[] buffer) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
             XWPFDocument document = new XWPFDocument(bais)) {

            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isEmpty()) {
                    if (!sb.isEmpty()) sb.append("\n");
                    sb.append(text);
                }
            }
            return sb.toString();
        } catch (NotOfficeXmlFileException e) {
            log.warn("DOCX 文件格式校验失败: {}", e.getMessage());
            throw new IOException("不是有效的 DOCX 文件", e);
        } catch (POIXMLException e) {
            log.warn("DOCX 解析异常: {}", e.getMessage(), e);
            throw new IOException("DOCX 文件解析失败", e);
        }
    }

    private String parsePdf(byte[] buffer) throws IOException {
        // PDFBox 3.x 语法：直接使用 Loader.loadPDF 解析 byte 数组
        try (PDDocument document = Loader.loadPDF(buffer)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0) return "";
        return filename.substring(lastDot + 1);
    }
}