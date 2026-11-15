package com.bluewhale.file.infrastructure.storage;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.domain.storage.ChunkUploadMetadata;
import com.bluewhale.file.domain.storage.ChunkUploadSessionId;
import com.bluewhale.file.domain.storage.ChunkUploadStore;
import com.bluewhale.file.domain.storage.MergedChunkUpload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * 分片上传暂存端口的本机磁盘实现：按 {@code ${whale.file.storage.root}/.chunk-upload/${uploadId}}
 * 组织会话目录。暂存目录名以点开头（同时不是合法 S3 桶名），避免与 {@code ${root}/${bucket}}
 * 的本地存储桶命名空间冲突。
 *
 * <p>会话目录内保存分片（{@code part-<index>}）、合并文件（{@code merged.bin}）与会话元数据
 * （{@code meta.properties}）。所有路径均基于已校验的 UUID 会话标识归一化，并做根目录越界防护。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class LocalChunkUploadStore implements ChunkUploadStore {

    /** 分片临时目录名（位于存储根目录下） */
    private static final String TMP_DIR_NAME = ".chunk-upload";

    /** 分片文件名前缀，分片文件名为 {@code part-<index>} */
    private static final String PART_FILE_PREFIX = "part-";

    /** 合并后临时文件名 */
    private static final String MERGED_FILE_NAME = "merged.bin";

    /** 上传会话元数据文件名 */
    private static final String META_FILE_NAME = "meta.properties";

    /** 元数据键：MIME 类型 */
    private static final String META_KEY_CONTENT_TYPE = "contentType";

    /** 元数据键：文件总字节数 */
    private static final String META_KEY_SIZE = "size";

    /** 元数据键：原始文件名 */
    private static final String META_KEY_FILE_NAME = "fileName";

    /** 元数据键：初始化时生效的单文件上限（G1 大文件上传策略） */
    private static final String META_KEY_MAX_SIZE_BYTES = "maxSizeBytes";

    /** 元数据键：初始化时生效的个人配额（G1 大文件上传策略；{@code <=0} 表示不限） */
    private static final String META_KEY_QUOTA_BYTES = "quotaBytes";

    /** 毫秒/小时：用于换算会话保留时长 */
    private static final long MILLIS_PER_HOUR = 3_600_000L;

    /** 过期会话清理的最小间隔（毫秒）：避免每次初始化都全量扫描暂存目录 */
    private static final long PURGE_INTERVAL_MILLIS = MILLIS_PER_HOUR;

    /** 暂存根目录（归一化后的绝对路径） */
    private final Path root;

    /** 会话保留时长（毫秒）；{@code <=0} 表示不清理 */
    private final long sessionTtlMillis;

    /** 上次清理时间戳（毫秒），用于限频扫描；CAS 更新保证并发下只有一个线程执行清理 */
    private final AtomicLong lastPurgeAt = new AtomicLong();

    /**
     * 以文件服务配置构造本机分片暂存实现
     *
     * <p>构造时即校验存储根目录可创建、可写，配置错误在启动阶段暴露而非首次上传时才失败。</p>
     *
     * @param fileProperties 文件服务配置（读取 {@code whale.file.storage.root} 与
     *                       {@code whale.file.chunk-upload-ttl-hours}）
     * @throws BizException 根目录创建失败或不可写时抛出 {@code SYSTEM_ERROR}
     */
    public LocalChunkUploadStore(FileProperties fileProperties) {
        this.root = Paths.get(fileProperties.getStorage().getRoot()).toAbsolutePath().normalize();
        this.sessionTtlMillis = Math.max(fileProperties.getChunkUploadTtlHours(), 0L) * MILLIS_PER_HOUR;
        ensureWritableRoot(root);
    }

    /**
     * 确保存储根目录存在且可写
     *
     * @param root 存储根目录
     * @throws BizException 目录创建失败或不可写时抛出 {@code SYSTEM_ERROR}
     */
    private static void ensureWritableRoot(Path root) {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "文件存储根目录创建失败: " + root);
        }
        if (!Files.isWritable(root)) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "文件存储根目录不可写: " + root);
        }
    }

    @Override
    public void initialize(ChunkUploadSessionId sessionId, ChunkUploadMetadata metadata) {
        purgeExpiredQuietly();
        Path dir = resolve(sessionId);
        try {
            Files.createDirectories(dir);
            writeMeta(dir, metadata);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public boolean exists(ChunkUploadSessionId sessionId) {
        return Files.isDirectory(resolve(sessionId));
    }

    @Override
    public ChunkUploadMetadata metadata(ChunkUploadSessionId sessionId) {
        Path dir = resolve(sessionId);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        Properties meta = readMeta(dir);
        if (meta == null) {
            return null;
        }
        return new ChunkUploadMetadata(meta.getProperty(META_KEY_FILE_NAME),
                textOrNull(meta.getProperty(META_KEY_CONTENT_TYPE)),
                parseLong(meta.getProperty(META_KEY_SIZE), 0L),
                parseLong(meta.getProperty(META_KEY_MAX_SIZE_BYTES), ChunkUploadMetadata.LIMITS_NOT_RECORDED),
                parseLong(meta.getProperty(META_KEY_QUOTA_BYTES), ChunkUploadMetadata.LIMITS_NOT_RECORDED));
    }

    @Override
    public void storePart(ChunkUploadSessionId sessionId, int index, InputStream content, long size) {
        Path dir = requireSession(sessionId);
        Path target = dir.resolve(PART_FILE_PREFIX + index);
        try {
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public List<Integer> uploadedPartIndexes(ChunkUploadSessionId sessionId) {
        Path dir = resolve(sessionId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            List<Integer> indexes = new ArrayList<>();
            paths.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(PART_FILE_PREFIX))
                    .map(name -> name.substring(PART_FILE_PREFIX.length()))
                    .filter(LocalChunkUploadStore::isNumeric)
                    .forEach(name -> indexes.add(Integer.valueOf(name)));
            indexes.sort(Comparator.naturalOrder());
            return indexes;
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    public MergedChunkUpload merge(ChunkUploadSessionId sessionId, int partCount) {
        Path dir = requireSession(sessionId);
        Path merged = dir.resolve(MERGED_FILE_NAME);
        try {
            writeMerged(dir, merged, partCount);
            // 流打开后不可再调用可失败逻辑：先取元数据/摘要/大小（失败时无流需要回收），
            // Files.newInputStream 必须是最后一个可失败调用，否则会泄漏未关闭的流
            String contentType = readMetaContentType(dir);
            String md5 = md5Of(merged);
            long size = Files.size(merged);
            return new MergedChunkUpload(Files.newInputStream(merged), size, contentType, md5);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 顺序拼接分片写入合并文件
     *
     * <p>失败（缺片或 IO 异常）时删除半成品，避免不完整的 {@code merged.bin} 长期占用磁盘。</p>
     *
     * @param dir 会话目录
     * @param merged 合并文件路径
     * @param partCount 分片总数
     * @throws IOException 写入失败时抛出
     */
    private static void writeMerged(Path dir, Path merged, int partCount) throws IOException {
        try {
            try (OutputStream out = Files.newOutputStream(merged)) {
                for (int i = 0; i < partCount; i++) {
                    Path part = dir.resolve(PART_FILE_PREFIX + i);
                    if (!Files.exists(part)) {
                        throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "缺少分片索引: " + i);
                    }
                    Files.copy(part, out);
                }
            }
        } catch (IOException | RuntimeException e) {
            deleteQuietly(merged);
            throw e;
        }
    }

    @Override
    public void discard(ChunkUploadSessionId sessionId) {
        Path dir = resolve(sessionId);
        if (!Files.exists(dir)) {
            return;
        }
        try {
            deleteTree(dir);
        } catch (IOException e) {
            log.warn("清理分片上传暂存目录失败: session={}", sessionId.value(), e);
        }
    }

    @Override
    public boolean writable() {
        return Files.isWritable(root);
    }

    @Override
    public long sessionCount() {
        Path tmpRoot = root.resolve(TMP_DIR_NAME);
        if (!Files.isDirectory(tmpRoot)) {
            return 0L;
        }
        try (Stream<Path> dirs = Files.list(tmpRoot)) {
            return dirs.filter(Files::isDirectory).count();
        } catch (IOException e) {
            // 指标采集失败不得影响业务与健康端点，按 0 上报
            return 0L;
        }
    }

    /**
     * 清理超过保留期的暂存会话：限频执行，失败仅告警，不影响新会话初始化。
     *
     * <p>清理动作挂在初始化入口，避免引入定时任务依赖；会话目录与元数据文件的最近修改时间
     * 用于判断活跃度，读取失败时保守跳过，避免误删正在续传的会话。</p>
     */
    private void purgeExpiredQuietly() {
        if (sessionTtlMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastPurgeAt.get();
        if (now - previous < PURGE_INTERVAL_MILLIS) {
            return;
        }
        // CAS 抢占本轮清理权：并发初始化时只有一个线程真正扫描/删除，其余线程直接返回
        if (!lastPurgeAt.compareAndSet(previous, now)) {
            return;
        }
        Path tmpRoot = root.resolve(TMP_DIR_NAME);
        if (!Files.isDirectory(tmpRoot)) {
            return;
        }
        long expireBefore = now - sessionTtlMillis;
        try (Stream<Path> dirs = Files.list(tmpRoot)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> lastActiveAt(dir) < expireBefore)
                    .forEach(this::discardQuietly);
        } catch (IOException e) {
            log.warn("扫描分片上传暂存目录失败: root={}", tmpRoot, e);
        }
    }

    /**
     * 尽力删除过期会话目录
     *
     * @param dir 会话目录
     */
    private void discardQuietly(Path dir) {
        try {
            deleteTree(dir);
            log.info("已清理过期分片上传会话: {}", dir.getFileName());
        } catch (IOException e) {
            log.warn("清理过期分片上传会话失败: dir={}", dir, e);
        }
    }

    /**
     * 删除目录树（含子文件）；目录已被并发清理时静默忽略
     *
     * @param dir 目标目录
     * @throws IOException 删除失败时抛出
     */
    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (NoSuchFileException e) {
            // discard 与过期清理可能并发删除同一会话目录：目录已不存在视为删除成功
        }
    }

    /**
     * 尽力删除文件：失败仅告警
     *
     * @param path 目标文件
     */
    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理分片合并半成品失败: path={}", path, e);
        }
    }

    /**
     * 计算会话最近活跃时间：取会话目录与元数据文件修改时间的较大值
     *
     * @param dir 会话目录
     * @return 最近活跃时间戳（毫秒）；读取失败时返回 {@link Long#MAX_VALUE} 表示不清理
     */
    private static long lastActiveAt(Path dir) {
        try {
            long dirTime = Files.getLastModifiedTime(dir).toMillis();
            Path meta = dir.resolve(META_FILE_NAME);
            long metaTime = Files.exists(meta) ? Files.getLastModifiedTime(meta).toMillis() : dirTime;
            return Math.max(dirTime, metaTime);
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 解析会话暂存目录 {@code ${root}/.chunk-upload/${uploadId}}，并做根目录越界防护
     *
     * @param sessionId 会话标识
     * @return 归一化后的会话目录
     * @throws BizException 归一化后越出根目录时抛出 {@code PARAM_ERROR}
     */
    private Path resolve(ChunkUploadSessionId sessionId) {
        Path dir = root.resolve(TMP_DIR_NAME).resolve(sessionId.value()).normalize();
        if (!dir.startsWith(root)) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        return dir;
    }

    /**
     * 获取已存在的会话暂存目录，不存在时抛出参数错误
     *
     * @param sessionId 会话标识
     * @return 会话目录
     * @throws BizException 会话不存在或已失效时抛出 {@code PARAM_ERROR}
     */
    private Path requireSession(ChunkUploadSessionId sessionId) {
        Path dir = resolve(sessionId);
        if (!Files.isDirectory(dir)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "上传会话不存在或已失效");
        }
        return dir;
    }

    /**
     * 写入会话元数据（文件名、MIME 类型、总大小与生效上传上限）
     *
     * @param dir 会话目录
     * @param metadata 会话元数据
     * @throws IOException 写文件失败时抛出
     */
    private static void writeMeta(Path dir, ChunkUploadMetadata metadata) throws IOException {
        Properties meta = new Properties();
        meta.setProperty(META_KEY_FILE_NAME, metadata.fileName());
        meta.setProperty(META_KEY_CONTENT_TYPE, metadata.contentType() == null ? "" : metadata.contentType());
        meta.setProperty(META_KEY_SIZE, String.valueOf(metadata.size()));
        if (metadata.hasRecordedLimits()) {
            meta.setProperty(META_KEY_MAX_SIZE_BYTES, String.valueOf(metadata.maxSizeBytes()));
            meta.setProperty(META_KEY_QUOTA_BYTES, String.valueOf(metadata.quotaBytes()));
        }
        try (OutputStream out = Files.newOutputStream(dir.resolve(META_FILE_NAME))) {
            meta.store(out, "chunk upload metadata");
        }
    }

    /**
     * 读取会话元数据文件
     *
     * @param dir 会话目录
     * @return 元数据属性；文件缺失时返回 {@code null}
     * @throws BizException 读取失败时抛出 {@code SYSTEM_ERROR}
     */
    private static Properties readMeta(Path dir) {
        Path metaFile = dir.resolve(META_FILE_NAME);
        if (!Files.exists(metaFile)) {
            return null;
        }
        Properties meta = new Properties();
        try (InputStream in = Files.newInputStream(metaFile)) {
            meta.load(in);
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
        return meta;
    }

    /**
     * 读取会话元数据中的 MIME 类型
     *
     * @param dir 会话目录
     * @return MIME 类型；元数据缺失或为空时返回 {@code null}
     */
    private static String readMetaContentType(Path dir) {
        Properties meta = readMeta(dir);
        return meta == null ? null : textOrNull(meta.getProperty(META_KEY_CONTENT_TYPE));
    }

    /**
     * 文本去空白：无有效内容时返回 {@code null}
     *
     * @param text 原始文本
     * @return 去除首尾空白后的文本；无有效内容时返回 {@code null}
     */
    private static String textOrNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    /**
     * 解析长整型属性，缺失或非法时回落默认值
     *
     * @param text 属性文本
     * @param defaultValue 默认值
     * @return 解析结果
     */
    private static long parseLong(String text, long defaultValue) {
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 计算文件 MD5 摘要
     *
     * @param path 文件路径
     * @return 十六进制小写 MD5
     * @throws IOException 读取失败时抛出
     */
    private static String md5Of(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return DigestUtils.md5DigestAsHex(in);
        }
    }

    /**
     * 判断字符串是否全部由数字组成
     *
     * @param text 待校验字符串
     * @return 全部为数字时返回 {@code true}
     */
    private static boolean isNumeric(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
