package com.bluewhale.agent.support.knowledge;

import com.bluewhale.agent.config.AgentProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 文档文本抽取器：基于 Apache Tika，支持 PDF / DOCX / XLSX / PPTX / TXT / MD / HTML / CSV。
 *
 * <p>安全与稳定性约束：MIME 白名单、单文件大小上限、抽取字符上限与解析超时；
 * Tika 默认关闭 XML 外部实体，且 OOXML 解析不展开外部资源，避免 XXE 与 zip bomb。
 * 解析在独立线程池中执行，超时即中断，不阻塞采集队列。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileDocumentParser {

    /** Tika MIME 探测实例（线程安全） */
    private static final Tika TIKA = new Tika();

    /** 白名单：PDF */
    private static final String MIME_PDF = "application/pdf";

    /** 白名单：DOCX */
    private static final String MIME_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** 白名单：XLSX */
    private static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 白名单：PPTX */
    private static final String MIME_PPTX =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    /** 白名单：JSON（档案快照） */
    private static final String MIME_JSON = "application/json";

    /** 允许的 MIME 类型 */
    private static final Set<String> ALLOWED_MIME = Set.of(
            MIME_PDF, MIME_DOCX, MIME_XLSX, MIME_PPTX,
            "text/plain", "text/markdown", "text/x-web-markdown", "text/html",
            "application/xhtml+xml", "text/csv", "application/csv", "text/x-csv",
            MIME_JSON, "text/json");

    /** 允许的扩展名（MIME 探测失败时的兜底） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "docx", "xlsx", "pptx", "txt", "md", "markdown", "html", "htm", "csv", "json");

    /** 解析线程池：固定两线程，避免并发解析拖垮内存 */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "agent-file-parser");
        thread.setDaemon(true);
        return thread;
    });

    private final AgentProperties properties;

    /**
     * 判断文件是否在白名单内（扩展名或 MIME 命中即视为支持）。
     *
     * @param fileName 原始文件名
     * @param contentType MIME 类型（可为空）
     * @return 支持返回 {@code true}
     */
    public boolean supported(String fileName, String contentType) {
        if (extensionAllowed(fileName)) {
            return true;
        }
        return contentType != null && ALLOWED_MIME.contains(normalizeMime(contentType));
    }

    /**
     * 抽取文档文本。
     *
     * @param content 文件字节内容
     * @param fileName 原始文件名
     * @param contentType 记录的 MIME 类型（可为空）
     * @return 抽取文本（已按字符上限截断保护）
     * @throws FileParseException 不支持、损坏或超时时抛出
     */
    public String parse(byte[] content, String fileName, String contentType) {
        if (content == null || content.length == 0) {
            throw new FileParseException(false, "文件内容为空");
        }
        long maxSize = properties.getKnowledge().getMaxFileSize();
        if (content.length > maxSize) {
            throw new FileParseException(false, "文件超出大小上限: " + content.length + " > " + maxSize);
        }
        String mime = resolveMime(content, fileName, contentType);
        if (!ALLOWED_MIME.contains(mime)) {
            throw new FileParseException(false, "不支持的文件类型: " + mime);
        }
        if (MIME_JSON.equals(mime) || "text/json".equals(mime)) {
            return jsonText(content, properties.getKnowledge().getMaxChars());
        }
        Future<String> future = executor.submit(() -> extract(content, fileName));
        try {
            return future.get(properties.getKnowledge().getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new FileParseException(true, "解析超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FileParseException(true, "解析被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FileParseException parseException) {
                throw parseException;
            }
            throw new FileParseException(false, "文档解析失败: " + messageOf(cause), cause);
        }
    }

    /**
     * 读取 JSON 文本：档案快照为标准 JSON，直接按 UTF-8 解码，避免 Tika 探测差异。
     *
     * @param content 文件字节内容
     * @param maxChars 字符上限
     * @return UTF-8 文本（超限截断）
     */
    private String jsonText(byte[] content, int maxChars) {
        String text = new String(content, StandardCharsets.UTF_8);
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    /**
     * 执行一次 Tika 抽取（在独立线程中运行）。
     *
     * @param content 文件字节内容
     * @param fileName 原始文件名
     * @return 抽取文本
     */
    private String extract(byte[] content, String fileName) {
        int maxChars = properties.getKnowledge().getMaxChars();
        BodyContentHandler handler = new BodyContentHandler(new WriteOutContentHandler(maxChars));
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        ParseContext context = new ParseContext();
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            new AutoDetectParser().parse(input, handler, metadata, context);
            return handler.toString();
        } catch (SAXException e) {
            throw new FileParseException(false, "文档解析失败（可能损坏）: " + messageOf(e), e);
        } catch (TikaException e) {
            throw new FileParseException(false, "文档解析失败（格式不支持或损坏）: " + messageOf(e), e);
        } catch (IOException e) {
            throw new FileParseException(true, "读取文档失败: " + messageOf(e), e);
        }
    }

    /**
     * 解析生效 MIME：优先使用白名单内的记录值，否则用 Tika 探测。
     *
     * @param content 文件字节内容
     * @param fileName 原始文件名
     * @param contentType 记录的 MIME 类型
     * @return 归一化后的 MIME
     */
    private String resolveMime(byte[] content, String fileName, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            String normalized = normalizeMime(contentType);
            if (ALLOWED_MIME.contains(normalized)) {
                return normalized;
            }
        }
        try {
            return normalizeMime(TIKA.detect(content, fileName));
        } catch (RuntimeException e) {
            log.warn("MIME 探测失败，按扩展名判定: fileName={}", fileName);
            return extensionAllowed(fileName) ? normalizeMime(contentType) : "application/octet-stream";
        }
    }

    /**
     * 判断扩展名是否在白名单内。
     *
     * @param fileName 原始文件名
     * @return 白名单内返回 {@code true}
     */
    private boolean extensionAllowed(String fileName) {
        if (fileName == null) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * 归一化 MIME：去参数、转小写。
     *
     * @param mime 原始 MIME
     * @return 归一化结果
     */
    private String normalizeMime(String mime) {
        if (mime == null) {
            return "";
        }
        int semicolon = mime.indexOf(';');
        String value = semicolon >= 0 ? mime.substring(0, semicolon) : mime;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 提取异常消息（空时回落类名）。
     *
     * @param throwable 异常
     * @return 消息
     */
    private String messageOf(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    /**
     * 停止解析线程池。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
