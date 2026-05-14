package com.aiproofreader.controller;

import com.aiproofreader.service.FileParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/parse")
public class ParseController {

    private static final Logger log = LoggerFactory.getLogger(ParseController.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "docx", "pdf");

    private final FileParseService fileParseService;

    public ParseController(FileParseService fileParseService) {
        this.fileParseService = fileParseService;
    }

    @PostMapping
    public ResponseEntity<?> parseFile(@RequestParam("file") MultipartFile file) {
        // Check if file is present
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请上传文件"));
        }

        // Check file size
        long maxSize = fileParseService.getMaxFileSize();
        if (file.getSize() > maxSize) {
            double sizeMb = file.getSize() / (1024.0 * 1024.0);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", String.format("文件过大（%.1fMB），最大支持 20MB", sizeMb)
            ));
        }

        // Check file extension
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件名无效"));
        }

        String ext = getExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "不支持的文件格式: ." + ext + "，仅支持 .txt、.docx、.pdf"
            ));
        }

        try {
            byte[] buffer = file.getBytes();

            // Magic bytes validation
            if (!fileParseService.validateFileType(buffer, filename)) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件内容与扩展名不匹配"));
            }

            // Parse file
            String text = fileParseService.parseFile(buffer, filename);

            // Check for empty content
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "文件内容为空"));
            }

            return ResponseEntity.ok(Map.of("text", text, "filename", filename));

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("文件解析超时: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "文件解析超时，请稍后重试"));
        } catch (Exception e) {
            log.error("文件解析失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "文件解析失败，请检查文件格式后重试"));
        }
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0) return "";
        return filename.substring(lastDot + 1);
    }
}
