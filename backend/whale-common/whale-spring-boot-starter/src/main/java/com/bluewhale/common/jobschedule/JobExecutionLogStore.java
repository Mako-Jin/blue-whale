package com.bluewhale.common.jobschedule;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 执行日志采集与落盘。
 *
 * <p>解决的问题：调度中心只拿到 {@code Result<String>} 里的执行摘要，业务方法内部的日志留在
 * 各服务的容器 stdout 里，排障时必须去翻容器日志、还要自己在时间轴上对齐。这里把「一次远程触发」
 * 线程内产生的日志收集成一份文件，调度中心按任务编码即可回捞。</p>
 *
 * <p>采集口径（写清楚边界，避免误以为能拿到全量日志）：</p>
 * <ul>
 *   <li>只采集<b>执行线程内</b>产生的日志——依赖 SLF4J MDC 传递，业务方法里新起的线程/线程池
 *       不继承 MDC，其日志不会进入本次快照；</li>
 *   <li>由 {@link JobLogCaptureAppender} 在 MDC 含 {@value #MDC_RUN_ID} 时写入，
 *       没有该键的日志零成本跳过；</li>
 *   <li>单次上限见 {@code whale.schedule.executor.log-max-file-bytes}，超出后丢弃并标注截断。</li>
 * </ul>
 *
 * <p>落盘结构：{@code {logDir}/{yyyyMMdd}/{code}-{yyyyMMddHHmmssSSS}.log}，按任务编码保留最近
 * {@code log-max-files} 份，超出删最旧的。</p>
 *
 * @author BlueWhale
 */
@Slf4j
public class JobExecutionLogStore {

    /** MDC 键：当前执行批次ID，业务方法内可通过它关联本次执行 */
    public static final String MDC_RUN_ID = "job.run.id";

    /** MDC 键：当前执行的任务编码 */
    public static final String MDC_JOB_CODE = "job.code";

    /** 日志文件后缀 */
    private static final String FILE_SUFFIX = ".log";

    /** 目录名日期格式 */
    private static final DateTimeFormatter DIR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 文件名时间格式 */
    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 内容里的时间格式 */
    private static final DateTimeFormatter TEXT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 截断标记：只写一次，避免被后续日志淹没 */
    private static final String TRUNCATED_MARK = "\n...[日志达到采集上限，后续内容已丢弃]\n";

    /** 回捞时单次读取的硬上限：防止历史大文件把调度中心内存打满 */
    private static final int READ_HARD_LIMIT_BYTES = 1024 * 1024;

    /** 默认保留文件数 */
    private static final int DEFAULT_MAX_FILES = 20;

    /** 默认采集上限（字节） */
    private static final int DEFAULT_MAX_FILE_BYTES = 262144;

    /** 默认目录名 */
    private static final String DEFAULT_DIR_NAME = "whale-job-exec-logs";

    /** 文件名里 runId 后缀长度 */
    private static final int SUFFIX_LENGTH = 8;

    private final Path root;

    private final int maxFileBytes;

    private final int maxFiles;

    /** runId 到采集缓冲的映射：仅存在于执行期间 */
    private final Map<String, Capture> active = new ConcurrentHashMap<>();

    /**
     * 构造采集器。
     *
     * @param config 执行器配置
     */
    public JobExecutionLogStore(JobScheduleProperties.Executor config) {
        String dir = config.getLogDir();
        this.root = Paths.get(dir == null || dir.isBlank()
                ? Paths.get(System.getProperty("java.io.tmpdir"), DEFAULT_DIR_NAME).toString()
                : dir.trim());
        this.maxFileBytes = config.getLogMaxFileBytes() > 0
                ? config.getLogMaxFileBytes() : DEFAULT_MAX_FILE_BYTES;
        this.maxFiles = config.getLogMaxFiles() > 0 ? config.getLogMaxFiles() : DEFAULT_MAX_FILES;
    }

    /**
     * 开始一次采集。
     *
     * @param runId 执行批次ID
     * @param code  任务编码
     */
    public void begin(String runId, String code) {
        if (runId == null || code == null) {
            return;
        }
        active.put(runId, new Capture(code, maxFileBytes));
    }

    /**
     * 追加一段日志（由 appender 调用）。
     *
     * <p>没有对应的活跃采集时直接返回——绝大多数日志走这条路径，开销只是一次 map 查找。</p>
     *
     * @param runId 执行批次ID
     * @param text  日志文本（含换行）
     */
    public void append(String runId, String text) {
        if (runId == null || text == null) {
            return;
        }
        Capture capture = active.get(runId);
        if (capture != null) {
            capture.append(text);
        }
    }

    /**
     * 结束采集并落盘。
     *
     * <p>落盘失败不影响任务本身：日志是排障辅助，不能让它在收尾阶段把一次成功执行翻成失败。</p>
     *
     * @param runId   执行批次ID
     * @param code    任务编码
     * @param outcome 执行结论摘要（成功结果或异常简述）
     */
    public void finish(String runId, String code, String outcome) {
        if (runId == null || code == null) {
            return;
        }
        Capture capture = active.remove(runId);
        if (capture == null) {
            return;
        }
        try {
            Path dir = root.resolve(LocalDateTime.now().format(DIR_FORMAT));
            Files.createDirectories(dir);
            Path file = dir.resolve(code + "-" + LocalDateTime.now().format(FILE_TIME_FORMAT)
                    + "-" + runIdSuffix(runId) + FILE_SUFFIX);
            Files.writeString(file, capture.render(runId, outcome), StandardCharsets.UTF_8);
            retainLatest(code);
        } catch (IOException | RuntimeException e) {
            log.warn("执行日志落盘失败（不影响任务结果）: code={}, runId={}, reason={}",
                    code, runId, e.getMessage());
        }
    }

    /**
     * 读取指定任务最近一次执行的日志。
     *
     * @param code 任务编码
     * @return 日志文本；无历史日志返回 {@code null}
     */
    public String readLatest(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Path latest = findFiles(code).stream()
                .max(Comparator.comparing(JobExecutionLogStore::fileName))
                .orElse(null);
        if (latest == null) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(latest);
            if (bytes.length <= READ_HARD_LIMIT_BYTES) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            String head = new String(bytes, 0, READ_HARD_LIMIT_BYTES, StandardCharsets.UTF_8);
            return head + "\n...[内容超过 1MB 已截断]\n";
        } catch (IOException e) {
            log.warn("读取执行日志失败: code={}, file={}, reason={}", code, latest, e.getMessage());
            return null;
        }
    }

    /**
     * 只保留该任务编码最近 {@code maxFiles} 份日志。
     *
     * @param code 任务编码
     */
    private void retainLatest(String code) {
        List<Path> files = findFiles(code).stream()
                .sorted(Comparator.comparing(JobExecutionLogStore::fileName).reversed())
                .toList();
        for (int i = maxFiles; i < files.size(); i++) {
            try {
                Files.deleteIfExists(files.get(i));
            } catch (IOException e) {
                log.debug("删除过期执行日志失败: file={}, reason={}", files.get(i), e.getMessage());
            }
        }
    }

    /**
     * 查找该任务编码的全部日志文件（跨日期目录）。
     *
     * @param code 任务编码
     * @return 文件列表；目录不存在返回空列表
     */
    private List<Path> findFiles(String code) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        String prefix = code + "-";
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                    .flatMap(dir -> listQuietly(dir).stream())
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(FILE_SUFFIX);
                    })
                    .toList();
        } catch (IOException e) {
            log.debug("扫描执行日志目录失败: root={}, reason={}", root, e.getMessage());
            return List.of();
        }
    }

    /**
     * 列出目录内的普通文件，失败时返回空列表。
     *
     * @param dir 目录
     * @return 文件列表
     */
    private static List<Path> listQuietly(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * 取文件名用于排序。
     *
     * <p>文件名形如 {@code {code}-{yyyyMMddHHmmssSSS}-{runId后8位}.log}，字典序即时间序。
     * 不用文件 mtime 排序：同一毫秒内连续落盘时 mtime 相同，排序不稳定。</p>
     *
     * @param path 文件路径
     * @return 文件名
     */
    private static String fileName(Path path) {
        return path.getFileName().toString();
    }

    /**
     * 取 runId 末尾 8 位作文件名后缀，保证同一毫秒内多次执行不互相覆盖。
     *
     * @param runId 执行批次ID
     * @return 后缀；runId 为空时返回固定串
     */
    private static String runIdSuffix(String runId) {
        if (runId == null || runId.isBlank()) {
            return "unknown";
        }
        return runId.length() <= SUFFIX_LENGTH ? runId : runId.substring(runId.length() - SUFFIX_LENGTH);
    }

    /**
     * 单次执行的采集缓冲：累计到上限后丢弃后续内容并标记一次。
     */
    private static final class Capture {

        private final String code;

        private final int maxBytes;

        private final LocalDateTime startedAt = LocalDateTime.now();

        private final StringBuilder buffer = new StringBuilder();

        private int bytes;

        private boolean truncated;

        /**
         * 构造采集缓冲。
         *
         * @param code     任务编码
         * @param maxBytes 采集上限（字节）
         */
        private Capture(String code, int maxBytes) {
            this.code = code;
            this.maxBytes = maxBytes;
        }

        /**
         * 追加日志，超过上限后只写一次截断标记。
         *
         * @param text 日志文本
         */
        private synchronized void append(String text) {
            if (truncated) {
                return;
            }
            int incoming = text.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + incoming > maxBytes) {
                buffer.append(TRUNCATED_MARK);
                truncated = true;
                return;
            }
            buffer.append(text);
            bytes += incoming;
        }

        /**
         * 渲染成最终落盘内容：带上下文头尾，便于单独打开一份文件就能看懂。
         *
         * @param runId   执行批次ID
         * @param outcome 执行结论摘要
         * @return 文件内容
         */
        private synchronized String render(String runId, String outcome) {
            return "# job code : " + code + System.lineSeparator()
                    + "# run id   : " + runId + System.lineSeparator()
                    + "# started  : " + startedAt.format(TEXT_TIME_FORMAT) + System.lineSeparator()
                    + "# 采集范围 : 本服务执行线程内的日志（新起线程不继承 MDC，不含）" + System.lineSeparator()
                    + System.lineSeparator()
                    + buffer
                    + System.lineSeparator()
                    + "--- 执行结束: " + (outcome == null ? "无摘要" : outcome) + " ---" + System.lineSeparator();
        }
    }
}
