package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.dto.KnowledgeSyncResultVO;
import com.bluewhale.agent.entity.AgentKnowledgeFile;
import com.bluewhale.agent.support.index.AgentIndexService;
import com.bluewhale.agent.support.ingest.Chunk;
import com.bluewhale.agent.support.ingest.SensitiveContentMasker;
import com.bluewhale.agent.support.knowledge.ArchiveSecurityClient;
import com.bluewhale.agent.support.knowledge.FileChunker;
import com.bluewhale.agent.support.knowledge.FileDocumentParser;
import com.bluewhale.agent.support.knowledge.FileKnowledgeClient;
import com.bluewhale.agent.support.knowledge.FileParseException;
import com.bluewhale.agent.support.knowledge.KnowledgeConstants;
import com.bluewhale.agent.support.knowledge.RemoteFileChange;
import com.bluewhale.agent.support.knowledge.TenantDirectoryClient;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件知识库同步服务：变更拉取 → 解析 → 脱敏 → 切块 → 写入 Lucene → 台账落库。
 *
 * <p>增量语义：以台账最大 {@code source_update_time} 为游标，按 {@code update_time,id}
 * 升序分页；{@code md5} 未变不重建；删除/停用/不支持/损坏置 {@code SKIPPED} 并移除索引；
 * 可重试失败按指数退避最多尝试 {@code max-attempts} 次。</p>
 *
 * <p>桶策略为 default-deny：只有命中 {@code included-buckets} 白名单且不在
 * {@code excluded-buckets} 中的桶才会入库，避免个人/部门桶内容进入同租户全员可检索池。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileKnowledgeSyncService {

    /** 结果码：已索引/重建 */
    private static final int OUTCOME_INDEXED = 1;

    /** 结果码：未变更 */
    private static final int OUTCOME_UNCHANGED = 2;

    /** 结果码：跳过 */
    private static final int OUTCOME_SKIPPED = 3;

    /** 结果码：失败 */
    private static final int OUTCOME_FAILED = 4;

    /** 重试单批上限 */
    private static final int RETRY_BATCH_SIZE = 50;

    /** 知识块路径前缀 */
    private static final String PATH_PREFIX = KnowledgeConstants.FILE_PATH_PREFIX;

    /** 档案秘密级阈值：密级 ≥ 3 需秘密级权限码 */
    private static final int SECRET_LEVEL = 3;

    /** 文件状态：归档锁定（原件/附件归档后由 whale-file 置为该状态） */
    private static final int ARCHIVED_STATUS = 2;

    /** 并发保护：同一时刻仅允许一轮同步 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AgentProperties properties;

    private final FileKnowledgeClient fileClient;

    private final TenantDirectoryClient tenantDirectoryClient;

    private final ArchiveSecurityClient archiveSecurityClient;

    private final FileDocumentParser documentParser;

    private final FileChunker fileChunker;

    private final AgentKnowledgeFileService fileService;

    private final AgentIndexService indexService;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-file-knowledge");
        thread.setDaemon(true);
        return thread;
    });

    /** 最近一次同步开始时间（进程内展示） */
    private volatile LocalDateTime lastRunAt;

    /** 最近一次同步结果摘要（进程内展示） */
    private volatile String lastResult;

    /**
     * 启动后注册周期同步（可选启动即同步）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        AgentProperties.Knowledge knowledge = properties.getKnowledge();
        if (!knowledge.isEnabled()) {
            return;
        }
        if (knowledge.isSyncOnStartup()) {
            executor.submit(this::runOnceSafely);
        }
        int interval = knowledge.getSyncIntervalSeconds();
        if (interval > 0) {
            executor.scheduleWithFixedDelay(this::runOnceSafely, interval, interval, TimeUnit.SECONDS);
        }
    }

    /**
     * 执行一轮同步（手动触发）。
     *
     * @return 同步结果
     * @throws BizException 已有同步在运行时抛出
     */
    public KnowledgeSyncResultVO runOnce() {
        if (!running.compareAndSet(false, true)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件知识库同步任务正在执行");
        }
        try {
            return doRun();
        } finally {
            running.set(false);
        }
    }

    /**
     * 异步触发全量重建：清空文件索引与台账后按全量变更流重新采集。
     *
     * @return 受理返回 {@code true}；已有任务在执行返回 {@code false}
     */
    public boolean rebuildAsync() {
        if (running.get()) {
            return false;
        }
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            log.warn("缺少租户上下文，拒绝执行文件知识库全量重建");
            return false;
        }
        executor.submit(() -> {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            Long previousTenant = TenantContext.get();
            TenantContext.set(tenantId);
            try {
                indexService.deleteKind(FileChunker.KIND_FILE);
                indexService.deleteKind(FileChunker.KIND_ARCHIVE);
                indexService.commitAndRefresh();
                fileService.deleteAllRows();
                doRun();
            } catch (RuntimeException e) {
                log.error("文件知识库全量重建失败", e);
            } finally {
                TenantContext.set(previousTenant);
                running.set(false);
            }
        });
        return true;
    }

    /**
     * 手动重建单个文件（同步执行）。
     *
     * @param fileId 文件ID
     * @return 重建成功返回 {@code true}；文件已删除或跳过返回 {@code false}
     */
    public boolean reindex(Long fileId) {
        if (fileId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件ID不能为空");
        }
        RemoteFileChange change = fileClient.detail(fileId);
        if (change == null) {
            AgentKnowledgeFile record = fileService.find(fileId);
            if (record != null) {
                record.setIndexStatus(KnowledgeConstants.STATUS_SKIPPED);
                record.setChunkCount(0);
                record.setLastError("文件不存在或已删除");
                fileService.save(record);
            }
            indexService.deletePaths(List.of(pathOf(fileId)));
            indexService.commitAndRefresh();
            return false;
        }
        SyncContext context = new SyncContext();
        int outcome = syncOne(change, true, context);
        if (context.dirty) {
            indexService.commitAndRefresh();
        }
        return outcome == OUTCOME_INDEXED;
    }

    /**
     * 启用或停用文件知识块。
     *
     * @param fileId 文件ID
     * @param enabled 是否启用
     * @throws BizException 台账不存在时抛出
     */
    public void setEnabled(Long fileId, boolean enabled) {
        AgentKnowledgeFile record = fileService.find(fileId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "文件知识台账不存在");
        }
        if (!enabled) {
            record.setEnabled(KnowledgeConstants.ENABLED_NO);
            record.setIndexStatus(KnowledgeConstants.STATUS_SKIPPED);
            record.setChunkCount(0);
            fileService.save(record);
            indexService.deletePaths(List.of(pathOf(fileId)));
            indexService.commitAndRefresh();
            return;
        }
        record.setEnabled(KnowledgeConstants.ENABLED_YES);
        record.setIndexStatus(KnowledgeConstants.STATUS_PENDING);
        fileService.save(record);
        reindex(fileId);
    }

    /**
     * 读取最近一次同步开始时间。
     *
     * @return 开始时间；未执行返回 {@code null}
     */
    public LocalDateTime lastRunAt() {
        return lastRunAt;
    }

    /**
     * 读取最近一次同步结果摘要。
     *
     * @return 摘要；未执行返回 {@code null}
     */
    public String lastResult() {
        return lastResult;
    }

    /**
     * 执行一轮同步（调用方须已持有 {@code running} 标记）。
     *
     * @return 同步结果
     */
    private KnowledgeSyncResultVO doRun() {
        LocalDateTime startedAt = LocalDateTime.now();
        SyncContext context = new SyncContext();
        context.cursor = fileService.cursor();
        pullChanges(context);
        retryFailed(context);
        if (context.dirty) {
            indexService.commitAndRefresh();
        }
        lastRunAt = startedAt;
        lastResult = summarize(context);
        KnowledgeSyncResultVO result = new KnowledgeSyncResultVO();
        result.setPulled(context.pulled);
        result.setIndexed(context.indexed);
        result.setUnchanged(context.unchanged);
        result.setSkipped(context.skipped);
        result.setFailed(context.failed);
        result.setCursor(context.cursor);
        result.setStartedAt(startedAt);
        result.setFinishedAt(LocalDateTime.now());
        log.info("文件知识库同步完成: {}", lastResult);
        return result;
    }

    /**
     * 按游标分页拉取变更并逐条同步。
     *
     * @param context 本轮上下文
     */
    private void pullChanges(SyncContext context) {
        int pageSize = Math.max(properties.getKnowledge().getPageSize(), 1);
        long current = 1;
        while (true) {
            List<RemoteFileChange> records;
            try {
                records = fileClient.changes(context.cursor, current, pageSize);
            } catch (RuntimeException e) {
                log.warn("拉取文件变更流失败: {}", e.getMessage());
                return;
            }
            if (records.isEmpty()) {
                return;
            }
            for (RemoteFileChange change : records) {
                if (change == null || change.getId() == null) {
                    continue;
                }
                context.pulled++;
                context.cursor = maxCursor(context.cursor, change.getUpdateTime());
                applyOutcome(context, syncOne(change, false, context));
            }
            if (records.size() < pageSize) {
                return;
            }
            current++;
        }
    }

    /**
     * 重试到达退避时间的失败台账。
     *
     * @param context 本轮上下文
     */
    private void retryFailed(SyncContext context) {
        int maxAttempts = Math.max(properties.getKnowledge().getMaxAttempts(), 1);
        List<AgentKnowledgeFile> records = fileService.retryCandidates(LocalDateTime.now(), maxAttempts,
                RETRY_BATCH_SIZE);
        for (AgentKnowledgeFile record : records) {
            RemoteFileChange change;
            try {
                change = fileClient.detail(record.getFileId());
            } catch (RuntimeException e) {
                change = null;
            }
            if (change == null) {
                record.setIndexStatus(KnowledgeConstants.STATUS_SKIPPED);
                record.setChunkCount(0);
                record.setLastError("文件不存在或已删除");
                fileService.save(record);
                indexService.deletePaths(List.of(pathOf(record.getFileId())));
                context.dirty = true;
                context.skipped++;
                continue;
            }
            applyOutcome(context, syncOne(change, true, context));
        }
    }

    /**
     * 同步单个文件。
     *
     * @param change 变更记录
     * @param force 是否强制重建（忽略 md5 与重试上限）
     * @param context 本轮上下文
     * @return 结果码
     */
    private int syncOne(RemoteFileChange change, boolean force, SyncContext context) {
        Long fileId = change.getId();
        AgentKnowledgeFile existing = fileService.find(fileId);
        boolean sameContent = unchanged(existing, change);
        AgentKnowledgeFile record = existing == null ? new AgentKnowledgeFile() : existing;
        applyMeta(record, change);
        if (!included(change.getBucket())) {
            removeIndex(fileId, context);
            markSkipped(record, "文件桶未纳入知识库白名单");
            return OUTCOME_SKIPPED;
        }
        if (excluded(change.getBucket()) || !change.collectable()) {
            removeIndex(fileId, context);
            markSkipped(record, "文件已删除、禁用或桶被排除");
            return OUTCOME_SKIPPED;
        }
        if (!force && record.getEnabled() != null && record.getEnabled() == KnowledgeConstants.ENABLED_NO) {
            removeIndex(fileId, context);
            markSkipped(record, "文件知识已停用");
            return OUTCOME_SKIPPED;
        }
        if (!force && sameContent) {
            fileService.save(record);
            return OUTCOME_UNCHANGED;
        }
        if (!force && exhausted(existing)) {
            fileService.save(record);
            return OUTCOME_SKIPPED;
        }
        record.setIndexStatus(KnowledgeConstants.STATUS_INDEXING);
        fileService.save(record);
        try {
            return index(record, change, context);
        } catch (FileParseException e) {
            if (!e.isRetryable()) {
                removeIndex(fileId, context);
                markSkipped(record, e.getMessage());
                return OUTCOME_SKIPPED;
            }
            return recordFailure(record, e);
        } catch (RuntimeException e) {
            return recordFailure(record, e);
        }
    }

    /**
     * 解析并写入索引。
     *
     * @param record 台账
     * @param change 变更记录
     * @param context 本轮上下文
     * @return 结果码
     */
    private int index(AgentKnowledgeFile record, RemoteFileChange change, SyncContext context) {
        Long fileId = change.getId();
        byte[] content = fileClient.content(fileId);
        if (content == null) {
            removeIndex(fileId, context);
            markSkipped(record, "文件内容不存在");
            return OUTCOME_SKIPPED;
        }
        if (content.length > properties.getKnowledge().getMaxFileSize()) {
            removeIndex(fileId, context);
            markSkipped(record, "文件超出大小上限");
            return OUTCOME_SKIPPED;
        }
        String text = documentParser.parse(content, change.getOriginalName(), change.getContentType());
        if (!StringUtils.hasText(text)) {
            removeIndex(fileId, context);
            markSkipped(record, "抽取文本为空");
            return OUTCOME_SKIPPED;
        }
        String path = pathOf(fileId);
        String module = change.getBucket() == null ? "" : change.getBucket();
        boolean archive = archived(change);
        String permissionCode = null;
        String kind = FileChunker.KIND_FILE;
        if (archive) {
            Integer securityLevel = archiveSecurityClient.securityLevel(fileId);
            if (securityLevel == null) {
                removeIndex(fileId, context);
                markSkipped(record, "档案密级不可用，拒绝入索引");
                return OUTCOME_SKIPPED;
            }
            permissionCode = permissionCodeOf(securityLevel);
            kind = FileChunker.KIND_ARCHIVE;
        }
        List<Chunk> chunks = fileChunker.chunk(path, module, change.getOriginalName(), text, kind);
        List<Chunk> safeChunks = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            safeChunks.add(mask(chunk));
        }
        indexService.deletePaths(List.of(path));
        indexService.addFileChunks(path, change.getMd5(), safeChunks, fileId, permissionCode);
        context.dirty = true;
        record.setIndexStatus(KnowledgeConstants.STATUS_INDEXED);
        record.setChunkCount(safeChunks.size());
        record.setAttempts(0);
        record.setNextRetryAt(null);
        record.setLastError(null);
        record.setIndexedAt(LocalDateTime.now());
        fileService.save(record);
        return OUTCOME_INDEXED;
    }

    /**
     * 记录可重试失败并计算退避时间。
     *
     * @param record 台账
     * @param error 异常
     * @return 结果码
     */
    private int recordFailure(AgentKnowledgeFile record, RuntimeException error) {
        int attempts = (record.getAttempts() == null ? 0 : record.getAttempts()) + 1;
        int maxAttempts = Math.max(properties.getKnowledge().getMaxAttempts(), 1);
        record.setAttempts(attempts);
        record.setIndexStatus(KnowledgeConstants.STATUS_FAILED);
        record.setLastError(truncate(error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage()));
        record.setNextRetryAt(attempts >= maxAttempts ? null
                : LocalDateTime.now().plusSeconds(retryDelaySeconds(attempts,
                properties.getKnowledge().getRetryBaseSeconds())));
        fileService.save(record);
        log.warn("文件知识索引失败: fileId={}, attempts={}, error={}",
                record.getFileId(), attempts, record.getLastError());
        return OUTCOME_FAILED;
    }

    /**
     * 计算指数退避秒数：{@code base * 2^(attempts-1)}。
     *
     * @param attempts 已失败次数（1 起）
     * @param baseSeconds 退避基数（秒）
     * @return 退避秒数
     */
    static long retryDelaySeconds(int attempts, int baseSeconds) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 10);
        return Math.max(baseSeconds, 1L) * (1L << exponent);
    }

    /**
     * 判断 md5 与归档状态均未变化（可直接跳过重建）。
     *
     * @param existing 已有台账（可为 {@code null}）
     * @param change 变更记录
     * @return 未变更返回 {@code true}
     */
    private boolean unchanged(AgentKnowledgeFile existing, RemoteFileChange change) {
        if (existing == null
                || !KnowledgeConstants.STATUS_INDEXED.equals(existing.getIndexStatus())
                || !StringUtils.hasText(existing.getMd5())
                || !existing.getMd5().equals(change.getMd5())) {
            return false;
        }
        String expected = archived(change) ? KnowledgeConstants.SOURCE_ARCHIVE : KnowledgeConstants.SOURCE_FILE;
        String actual = existing.getSource() == null ? KnowledgeConstants.SOURCE_FILE : existing.getSource();
        return expected.equals(actual);
    }

    /**
     * 判断失败次数是否已达上限（不再自动重试）。
     *
     * @param existing 已有台账（可为 {@code null}）
     * @return 已达上限返回 {@code true}
     */
    private boolean exhausted(AgentKnowledgeFile existing) {
        if (existing == null) {
            return false;
        }
        int attempts = existing.getAttempts() == null ? 0 : existing.getAttempts();
        return KnowledgeConstants.STATUS_FAILED.equals(existing.getIndexStatus())
                && attempts >= Math.max(properties.getKnowledge().getMaxAttempts(), 1);
    }

    /**
     * 回填文件元数据并初始化新增台账。
     *
     * @param record 台账
     * @param change 变更记录
     */
    private void applyMeta(AgentKnowledgeFile record, RemoteFileChange change) {
        if (record.getFileId() == null) {
            record.setFileId(change.getId());
            record.setEnabled(KnowledgeConstants.ENABLED_YES);
            record.setAttempts(0);
            record.setChunkCount(0);
            record.setSource(KnowledgeConstants.SOURCE_FILE);
            record.setIndexStatus(KnowledgeConstants.STATUS_PENDING);
        }
        record.setSource(archived(change)
                ? KnowledgeConstants.SOURCE_ARCHIVE : KnowledgeConstants.SOURCE_FILE);
        record.setBucket(change.getBucket());
        record.setOriginalName(change.getOriginalName());
        record.setContentType(change.getContentType());
        record.setFileSize(change.getFileSize());
        record.setMd5(change.getMd5());
        record.setUploaderId(change.getUploaderId());
        record.setSourceUpdateTime(change.getUpdateTime());
    }

    /**
     * 标记跳过并清空块数。
     *
     * @param record 台账
     * @param reason 跳过原因
     */
    private void markSkipped(AgentKnowledgeFile record, String reason) {
        record.setIndexStatus(KnowledgeConstants.STATUS_SKIPPED);
        record.setChunkCount(0);
        record.setLastError(truncate(reason));
        record.setNextRetryAt(null);
        fileService.save(record);
    }

    /**
     * 移除文件的全部索引块。
     *
     * @param fileId 文件ID
     * @param context 本轮上下文
     */
    private void removeIndex(Long fileId, SyncContext context) {
        indexService.deletePaths(List.of(pathOf(fileId)));
        context.dirty = true;
    }

    /**
     * 脱敏知识块正文。
     *
     * @param chunk 原知识块
     * @return 脱敏后的知识块
     */
    private Chunk mask(Chunk chunk) {
        return Chunk.builder()
                .kind(chunk.getKind())
                .module(chunk.getModule())
                .path(chunk.getPath())
                .title(chunk.getTitle())
                .content(SensitiveContentMasker.mask(chunk.getContent()))
                .startLine(chunk.getStartLine())
                .endLine(chunk.getEndLine())
                .wave(chunk.getWave())
                .build();
    }

    /**
     * 判断桶是否在知识库白名单内（default-deny）。
     *
     * <p>白名单按桶名前缀匹配；未配置、配置为空或桶名缺失时均返回 {@code false}，
     * 避免未知桶被静默纳入全员可检索池。</p>
     *
     * @param bucket 桶名
     * @return 在白名单内返回 {@code true}
     */
    private boolean included(String bucket) {
        if (!StringUtils.hasText(bucket)) {
            return false;
        }
        List<String> included = properties.getKnowledge().getIncludedBuckets();
        if (included == null || included.isEmpty()) {
            return false;
        }
        for (String pattern : included) {
            if (StringUtils.hasText(pattern) && bucket.startsWith(pattern.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断桶是否被排除。
     *
     * @param bucket 桶名
     * @return 排除返回 {@code true}
     */
    private boolean excluded(String bucket) {
        if (!StringUtils.hasText(bucket)) {
            return false;
        }
        for (String excluded : properties.getKnowledge().getExcludedBuckets()) {
            if (StringUtils.hasText(excluded) && bucket.startsWith(excluded.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断文件是否已归档：档案桶快照，或被归档锁定的原件/附件。
     *
     * <p>原件与附件归档时仍在原业务桶、仅状态置 {@code 2}，因此不能只按桶判定，
     * 否则归档内容会继续以公开知识块被检索到。</p>
     *
     * @param change 变更记录
     * @return 已归档返回 {@code true}
     */
    private boolean archived(RemoteFileChange change) {
        return isArchiveBucket(change.getBucket())
                || (change.getStatus() != null && change.getStatus() == ARCHIVED_STATUS);
    }

    /**
     * 判断桶是否属于档案桶（按配置前缀匹配）。
     *
     * @param bucket 桶名
     * @return 档案桶返回 {@code true}
     */
    private boolean isArchiveBucket(String bucket) {
        String archiveBucket = properties.getKnowledge().getArchiveBucket();
        return StringUtils.hasText(archiveBucket) && bucket != null && bucket.startsWith(archiveBucket.trim());
    }

    /**
     * 按密级选择最低可见权限码：密级 3 用秘密级码，其余用档案条目读码。
     *
     * @param securityLevel 密级
     * @return 权限码
     */
    private String permissionCodeOf(int securityLevel) {
        AgentProperties.Knowledge knowledge = properties.getKnowledge();
        return securityLevel >= SECRET_LEVEL
                ? knowledge.getArchiveSecretPermissionCode() : knowledge.getArchivePermissionCode();
    }

    /**
     * 构造知识块路径。
     *
     * @param fileId 文件ID
     * @return 路径
     */
    private static String pathOf(Long fileId) {
        return PATH_PREFIX + fileId;
    }

    /**
     * 取两个游标中较大者。
     *
     * @param current 当前游标
     * @param candidate 候选游标
     * @return 较大者
     */
    private static LocalDateTime maxCursor(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    /**
     * 结果码累加到计数器。
     *
     * @param context 本轮上下文
     * @param outcome 结果码
     */
    private static void applyOutcome(SyncContext context, int outcome) {
        if (outcome == OUTCOME_INDEXED) {
            context.indexed++;
        } else if (outcome == OUTCOME_UNCHANGED) {
            context.unchanged++;
        } else if (outcome == OUTCOME_FAILED) {
            context.failed++;
        } else {
            context.skipped++;
        }
    }

    /**
     * 生成结果摘要。
     *
     * @param context 本轮上下文
     * @return 摘要
     */
    private static String summarize(SyncContext context) {
        return String.format(Locale.ROOT, "pulled=%d,indexed=%d,unchanged=%d,skipped=%d,failed=%d",
                context.pulled, context.indexed, context.unchanged, context.skipped, context.failed);
    }

    /**
     * 截断失败原因以适配列宽。
     *
     * @param message 原始消息
     * @return 截断后的消息
     */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= KnowledgeConstants.MAX_ERROR_LENGTH
                ? message : message.substring(0, KnowledgeConstants.MAX_ERROR_LENGTH);
    }

    /**
     * 按租户目录逐租户安全执行一轮同步（异常不外抛，供调度器使用）。
     *
     * <p>租户清单优先取显式配置；未配置时查询租户目录，目录不可用回退默认租户 1。
     * 每个租户独立绑定 {@link TenantContext}，单租户异常不影响其余租户。</p>
     */
    private void runOnceSafely() {
        for (Long tenantId : resolveTenantIds()) {
            Long previousTenant = TenantContext.get();
            TenantContext.set(tenantId);
            try {
                runOnce();
            } catch (BizException e) {
                log.info("文件知识库同步跳过: tenantId={}, reason={}", tenantId, e.getMessage());
            } catch (RuntimeException e) {
                log.error("文件知识库同步异常: tenantId={}", tenantId, e);
            } finally {
                TenantContext.set(previousTenant);
            }
        }
    }

    /**
     * 解析本轮同步的租户清单：显式配置优先，否则查询租户目录，目录为空回退默认租户。
     *
     * @return 租户ID列表（非空）
     */
    private List<Long> resolveTenantIds() {
        AgentProperties.Knowledge knowledge = properties.getKnowledge();
        List<Long> configured = knowledge.getSyncTenantIds();
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        List<Long> enabled = tenantDirectoryClient.listEnabledTenantIds();
        return enabled.isEmpty() ? List.of(TenantContext.DEFAULT_TENANT_ID) : enabled;
    }

    /**
     * 停止调度线程。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 单轮同步上下文。
     *
     * @author BlueWhale
     */
    private static final class SyncContext {

        /** 拉取条数 */
        private long pulled;

        /** 已索引条数 */
        private long indexed;

        /** 未变更条数 */
        private long unchanged;

        /** 跳过条数 */
        private long skipped;

        /** 失败条数 */
        private long failed;

        /** 是否有索引写入待提交 */
        private boolean dirty;

        /** 当前游标 */
        private LocalDateTime cursor;
    }
}
