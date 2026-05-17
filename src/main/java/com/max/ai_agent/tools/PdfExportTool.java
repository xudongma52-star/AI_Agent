package com.max.ai_agent.tools;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PdfExportTool {

    /** 内存存储 PDF 字节，key 为下载 UUID */
    private static final ConcurrentHashMap<String, PdfEntry> PDF_STORE = new ConcurrentHashMap<>();

    private static final String[] CLASSPATH_FONT_PATHS = {
            "fonts/SimHei.ttf",
            "fonts/SimSun.ttf"
    };

    private static final String[][] SYSTEM_FONT_FALLBACKS = {
            { "C:/Windows/Fonts/simhei.ttf", "SimHei" },
            { "C:/Windows/Fonts/simsun.ttc", "SimSun" },
            { "C:/Windows/Fonts/msyh.ttc", "Microsoft YaHei" },
            { "C:/Windows/Fonts/msyhbd.ttc", "Microsoft YaHei" },
            { "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf", "DroidSans" },
            { "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", "NotoSansCJK" },
            { "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc", "WenQuanYi" },
            { "/System/Library/Fonts/PingFang.ttc", "PingFang" },
            { "/System/Library/Fonts/STHeiti Light.ttc", "STHeiti" },
            { "/Library/Fonts/Arial Unicode.ttf", "ArialUnicode" },
    };

    /** PDF 条目 */
    public record PdfEntry(byte[] bytes, String fileName, Instant createdAt) {}

    /**
     * 根据下载 ID 获取 PDF 数据，获取后自动移除（一次性下载）。
     */
    public static PdfEntry takePdf(String downloadId) {
        return PDF_STORE.remove(downloadId);
    }

    /**
     * 定时清理超过 30 分钟未被下载的 PDF，每 10 分钟执行一次。
     */
    @Scheduled(fixedRate = 600_000)
    public void cleanExpiredPdfs() {
        Instant cutoff = Instant.now().minusSeconds(1800);
        PDF_STORE.entrySet().removeIf(entry -> {
            if (entry.getValue().createdAt().isBefore(cutoff)) {
                log.info("清理过期 PDF: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    @Tool(description = "当用户要求将对话内容、心得体会、总结导出为PDF文档，或者生成报告时，调用此工具。需要提供标题和Markdown格式的正文内容。")
    public String exportToPdf(
            @ToolParam(description = "PDF文档的标题，例如：'张三的心得体会'") String title,
            @ToolParam(description = "文档正文内容，必须是Markdown格式") String markdownContent) {

        log.info("PDF 导出请求：{}", title);
        try {
            // 1. Markdown → HTML
            Parser parser = Parser.builder().build();
            Node document = parser.parse(markdownContent);
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String htmlBody = renderer.render(document);
            String fullHtml = buildHtml(title, htmlBody);

            // 2. 生成文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeName = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = safeName + "_" + timestamp + ".pdf";

            // 3. 渲染 PDF 到内存
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withHtmlContent(fullHtml, null);
            builder.toStream(baos);
            builder.run();
            byte[] pdfBytes = baos.toByteArray();

            // 4. 存入内存，返回下载标记
            String downloadId = UUID.randomUUID().toString();
            PDF_STORE.put(downloadId, new PdfEntry(pdfBytes, fileName, Instant.now()));

            log.info("PDF 生成成功，下载ID: {}, 文件名: {}, 大小: {} bytes",
                    downloadId, fileName, pdfBytes.length);

            return "{{PDF_DOWNLOAD:" + downloadId + ":" + fileName + "}}";

        } catch (Exception e) {
            log.error("PDF生成失败", e);
            return "PDF生成失败: " + e.getMessage()
                    + "。请确保系统有中文字体，或将字体文件放入 src/main/resources/fonts/ 目录。";
        }
    }

    private void registerFonts(PdfRendererBuilder builder) {
        boolean fontLoaded = false;

        for (String path : CLASSPATH_FONT_PATHS) {
            try {
                ClassPathResource resource = new ClassPathResource(path);
                if (resource.exists() && resource.contentLength() > 0) {
                    File fontFile = resolveFontFile(resource);
                    if (fontFile != null) {
                        String family = path.contains("Hei") ? "SimHei" : "SimSun";
                        builder.useFont(fontFile, family);
                        log.info("从 classpath 加载字体：{} → {}", path, family);
                        fontLoaded = true;
                    }
                }
            } catch (Exception e) {
                log.debug("classpath 字体 {} 加载失败: {}", path, e.getMessage());
            }
        }

        if (!fontLoaded) {
            for (String[] entry : SYSTEM_FONT_FALLBACKS) {
                File fontFile = new File(entry[0]);
                if (fontFile.exists() && fontFile.length() > 0) {
                    builder.useFont(fontFile, entry[1]);
                    log.info("从系统加载字体：{} → {}", entry[0], entry[1]);
                    fontLoaded = true;
                    break;
                }
            }
        }

        if (!fontLoaded) {
            log.warn("未找到中文字体，PDF 中文可能显示为乱码。" +
                    "请将 SimHei.ttf / SimSun.ttf 放入 src/main/resources/fonts/ 目录。");
        }
    }

    private File resolveFontFile(ClassPathResource resource) throws IOException {
        try {
            File file = resource.getFile();
            if (file.exists()) return file;
        } catch (IOException e) {
            // JAR 部署时走 temp 文件逻辑
        }

        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "ai-agent-fonts");
        Files.createDirectories(tmpDir);
        String filename = resource.getFilename();
        Path tmpFile = tmpDir.resolve(filename);
        if (!Files.exists(tmpFile) || Files.size(tmpFile) != resource.contentLength()) {
            try (InputStream in = resource.getInputStream();
                 OutputStream out = Files.newOutputStream(tmpFile)) {
                in.transferTo(out);
            }
        }
        return tmpFile.toFile();
    }

    private String buildHtml(String title, String htmlBody) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8"/>
                    <style>
                        @page { margin: 2cm; }
                        body {
                            font-family: SimHei, SimSun, 'Microsoft YaHei', 'PingFang SC', 'Noto Sans CJK SC', sans-serif;
                            font-size: 14px;
                            line-height: 1.8;
                            color: #333;
                        }
                        h1 {
                            text-align: center;
                            font-size: 22px;
                            margin-bottom: 24px;
                            padding-bottom: 12px;
                            border-bottom: 2px solid #ddd;
                        }
                        h2 { font-size: 18px; margin-top: 20px; }
                        h3 { font-size: 16px; margin-top: 16px; }
                        blockquote {
                            border-left: 3px solid #bbb;
                            margin: 12px 0;
                            padding: 8px 16px;
                            color: #555;
                            background: #f9f9f9;
                        }
                        p { margin-bottom: 10px; }
                        code { background: #f0f0f0; padding: 1px 4px; border-radius: 3px; }
                        pre { background: #f5f5f5; padding: 12px; border-radius: 4px; overflow-x: auto; }
                        ul, ol { margin-left: 20px; }
                    </style>
                </head>
                <body>
                    <h1>%s</h1>
                    %s
                </body>
                </html>
                """.formatted(title, htmlBody);
    }
}
