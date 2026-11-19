package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.dto.KnowledgeSyncResultVO;
import com.bluewhale.agent.entity.AgentKnowledgeDoc;
import com.bluewhale.agent.support.index.AgentIndexService;
import com.bluewhale.agent.support.index.DocIndexMeta;
import com.bluewhale.agent.support.ingest.Chunk;
import com.bluewhale.agent.support.ingest.SensitiveContentMasker;
import com.bluewhale.agent.support.knowledge.DocKnowledgeClient;
import com.bluewhale.agent.support.knowledge.FileChunker;
import com.bluewhale.agent.support.knowledge.KnowledgeConstants;
import com.bluewhale.agent.support.knowledge.RemoteDocChange;
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
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文档知识库同步服务：变更拉取 → 快照读取 → 脱敏 → 切块 → 写入 Lucene → 台账落库。
 *
 * <p>只收录「已发布 + 公开空间 + 空间启用 + 未删除」的正文页面；页面撤销发布、归档、
 * 删除或空间转私有/停用时，变更流会下发墓碑，同步侧下线对应索引块。增量游标取台账
 * 最大的 {@code source_update_time}，按 revision 未变不重建。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocKnowledgeSyncService {

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
    private static final String PATH_PREFIX = KnowledgeConstants.DOC_PATH_PREFIX;

    /** 并发保护：同一时刻仅允许一轮同步 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final AgentProperties properties;

    private final DocKnowledgeClient docClient;

    private final TenantDirectoryClient tenantDirectoryClient;

    private final FileChunker fileChunker;

    private final AgentKnowledgeDocService docService;

    private final AgentIndexService indexService;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-doc-knowledge");
        thread.setDaemon(true);
        return thread;
    });

    private final ExecutorService rebuildExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-doc-knowledge-rebuild");
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
        if (!knowledge.isDocEnabled()) {
            return;
        }
        if (knowledge.isDocSyncOnStartup()) {
            executor.submit(this::runOnceSafely);
        }
        int interval = knowledge.getDocSyncIntervalSeconds();
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
            throw new BizException(ErrorCode.PARAM_ERROR, "agent.knowledge-doc.sync-running",
                    "文档知识库同步任务正在执行");
        }
        try {
            return doRun();
        } finally {
            running.set(false);
        }
    }

    /**
     * 异步触发全量重建：清空文档知识索引与台账后按全量变更流重新采集。
     *
     * @return 受理返回 {@code true}；已有任务在执行返回 {@code false}
     */
    public boolean rebuildAsync() {
        if (running.get()) {
            return false;
        }
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            log.warn("缺少租户上下文，拒绝执行文档知识库全量重建");
            return false;
        }
        rebuildExecutor.submit(() -> {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            Long previousTenant = TenantContext.get();
            TenantContext.set(tenantId);
            try {
                indexService.deleteKind(KnowledgeConstants.KIND_DOC_PAGE);
                indexService.commitAndRefresh();
                docService.deleteAllRows();
                doRun();
            } catch (RuntimeException e) {
                log.error("文档知识库全量重建失败", e);
            } finally {
                TenantContext.set(previousTenant);
                running.set(false);
            }
        });
        return true;
    }

    /**
     * 手动重建单个页面（同步执行）。
     *
     * @param pageId 页面ID
     * @return 重建成功返回 {@code true}；页面不可见或跳过返回 {@code false}
     */
    public boolean reindex(Long pageId) {
        if (pageId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "agent.knowledge-doc.page-id-required", "页面ID不能为空");
        }
        RemoteDocChange detail = docClient.detail(pageId);
        if (detail == null) {
            skipMissing(pageId);
            return false;
        }
        SyncContext context = new SyncContext();
        int outcome = syncOne(detail, true, context);
        if (context.dirty) {
            indexService.commitAndRefresh();
        }
        return outcome == OUTCOME_INDEXED;
    }

    /**
     * 启用或停用页面知识块。
     *
     * @param pageId  页面ID
     * @param enabled 是否启用
     * @throws BizException 台账不存在时抛出
     */
    public void setEnabled(Long pageId, boolean enabled) {
        AgentKnowledgeDoc record = docService.find(pageId);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "agent.knowledge-doc.record-not-found",
                    "文档知识台账不存在");
        }
        if (!enabled) {
            record.setEnabled(KnowledgeConstants.ENABLED_NO);
            record.setIndexStatus(KnowledgeConstants.STATUS_SKIPPED);
            record.setChunkCount(0);
            docService.save(record);
            indexService.deletePaths(List.of(pathOf(pageId)));
            indexService.commitAndRefresh();
            return;
        }
        record.setEnabled(KnowledgeConstants.ENABLED_YES);
        record.setIndexStatus(KnowledgeConstants.STATUS_PENDING);
        docService.save(record);
        reindex(pageId);
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
        context.cursor = docService.cursor();
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
        log.info("文档知识库同步完成: {}", lastResult);
        return result;
    }

    /**
     * 按游标分页拉取变更并逐条同步。
     *
     * @param context 本轮上下文
     */
    private void pullChanges(SyncContext context) {
        int pageSize = Math.max(properties.getKnowledge().getPageSize(), 1);
        // 分页期间固定起始游标：文档服务游标为闭区间且 update_time 仅精确到秒，
        // 若随处理进度推进游标，offset 会跳过同秒内的后续页面。
        LocalDateTime since = context.cursor;
        long current = 1;
        while (true) {
            List<RemoteDocChange> records;
            try {
                records = docClient.changes(since, current, pageSize);
            } catch (RuntimeException e) {
                log.warn("拉取文档变更流失败: {}", e.getMessage());
                return;
            }
            if (records.isEmpty()) {
                return;
            }
            for (RemoteDocChange change : records) {
                if (change == null || change.getPageId() == null) {
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
        List<AgentKnowledgeDoc> records = docService.retryCandidates(LocalDateTime.now(), maxAttempts,
                RETRY_BATCH_SIZE);
        for (AgentKnowledgeDoc record : records) {
            RemoteDocChange detail;
            try {
                detail = docClient.detail(record.getPageId());
            } catch (RuntimeException e) {
                detail = null;
            }
            if (detail == null) {
                record.setIndexStatus(KnowledgeConstants.STATUS_SKIPPED);
                record.setChunkCount(0);
                record.setLastError("页面不存在或不可见");
                docService.save(record);
                indexService.deletePaths(List.of(pathOf(record.getPageId())));
                context.dirty = true;
                context.skipped++;
                continue;
            }
            applyOutcome(context, syncOne(detail, true, context));
        }
    }

    /**
     * 同步单个页面。
     *
     * @param change  变更记录（变更流项或快照）
     * @param force   是否强制重建（忽略 revision 与重试上限）
     * @param context 本轮上下文
     * @return 结果码
     */
    private int syncOne(RemoteDocChange change, boolean force, SyncContext context) {
        Long pageId = change.getPageId();
        AgentKnowledgeDoc existing = docService.find(pageId);
        AgentKnowledgeDoc record = existing == null ? new AgentKnowledgeDoc() : existing;
        applyMeta(record, change);
        if (!change.collectable()) {
            removeIndex(pageId, context);
            markSkipped(record, "页面未发布、已删除或空间不可见");
            return OUTCOME_SKIPPED;
        }
        if (!force && record.getEnabled() != null && record.getEnabled() == KnowledgeConstants.ENABLED_NO) {
            removeIndex(pageId, context);
            markSkipped(record, "文档知识已停用");
            return OUTCOME_SKIPPED;
        }
        if (!force && unchanged(existing, change)) {
            docService.save(record);
            return OUTCOME_UNCHANGED;
        }
        if (!force && exhausted(existing)) {
            docService.save(record);
            return OUTCOME_SKIPPED;
        }
        record.setIndexStatus(KnowledgeConstants.STATUS_INDEXING);
        docService.save(record);
        try {
            return index(record, change, context);
        } catch (RuntimeException e) {
            return recordFailure(record, e);
        }
    }

    /**
     * 读取快照并写入索引。
     *
     * @param record  台账
     * @param change  变更记录
     * @param context 本轮上下文
     * @return 结果码
     */
    private int index(AgentKnowledgeDoc record, RemoteDocChange change, SyncContext context) {
        Long pageId = change.getPageId();
        RemoteDocChange detail;
        try {
            detail = docClient.detail(pageId);
        } catch (RuntimeException e) {
            return recordFailure(record, e);
        }
        if (detail == null) {
            removeIndex(pageId, context);
            markSkipped(record, "页面不存在或不可见");
            return OUTCOME_SKIPPED;
        }
        applyMeta(record, detail);
        String text = truncateText(detail.getContentText());
        if (!StringUtils.hasText(text)) {
            removeIndex(pageId, context);
            markSkipped(record, "页面正文为空");
            return OUTCOME_SKIPPED;
        }
        String path = pathOf(pageId);
        String module = detail.getSpaceName() == null ? "" : detail.getSpaceName();
        String title = StringUtils.hasText(detail.getTitle()) ? detail.getTitle() : "文档页面";
        List<Chunk> chunks = fileChunker.chunk(path, module, title, text, KnowledgeConstants.KIND_DOC_PAGE);
        List<Chunk> safeChunks = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            safeChunks.add(mask(chunk));
        }
        String hash = md5Of(detail.getRevision(), text);
        DocIndexMeta meta = new DocIndexMeta(pageId, detail.getSpaceId(), urlOf(detail));
        indexService.deletePaths(List.of(path));
        indexService.addDocChunks(path, hash, safeChunks, meta, permissionCode(), tenantId());
        context.dirty = true;
        record.setIndexStatus(KnowledgeConstants.STATUS_INDEXED);
        record.setChunkCount(safeChunks.size());
        record.setContentHash(hash);
        record.setAttempts(0);
        record.setNextRetryAt(null);
        record.setLastError(null);
        record.setIndexedAt(LocalDateTime.now());
        docService.save(record);
        return OUTCOME_INDEXED;
    }

    /**
     * 记录可重试失败并计算退避时间。
     *
     * @param record 台账
     * @param error  异常
     * @return 结果码
     */
    private int recordFailure(AgentKnowledgeDoc record, RuntimeException error) {
        int attempts = (record.getAttempts() == null ? 0 : record.getAttempts()) + 1;
        int maxAttempts = Math.max(properties.getKnowledge().getMaxAttempts(), 1);
        record.setAttempts(attempts);
        record.setIndexStatus(KnowledgeConstants.STATUS_FAILED);
        record.setLastError(truncate(error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage()));
        record.setNextRetryAt(attempts >= maxAttempts ? null
                : LocalDateTime.now().plusSeconds(FailureBackoff.delaySeconds(attempts,
                properties.getKnowledge().getRetryBaseSeconds())));
        docService.save(record);
        log.warn("文档知识索引失败: pageId={}, attempts={}, error={}",
                record.getPageId(), attempts, record.getLastError());
        return OUTCOME_FAILED;
    }

    /**
     * 判断台账已索引且页面 revision 未变。
     *
     * @param existing 已有台账（可为 {@code null}）
     * @param change   变更记录
     * @return 未变更返回 {@code true}
     */
    private boolean unchanged(AgentKnowledgeDoc existing, RemoteDocChange change) {
        if (existing == null
                || !KnowledgeConstants.STATUS_INDEXED.equals(existing.getIndexStatus())
                || !StringUtils.hasText(existing.getContentHash())) {
            return false;
        }
        return existing.getRevision() != null && existing.getRevision().equals(change.getRevision());
    }

    /**
     * 判断失败次数是否已达上限（不再自动重试）。
     *
     * @param existing 已有台账（可为 {@code null}）
     * @return 已达上限返回 {@code true}
     */
    private boolean exhausted(AgentKnowledgeDoc existing) {
        if (existing == null) {
            return false;
        }
        int attempts = existing.getAttempts() == null ? 0 : existing.getAttempts();
        return KnowledgeConstants.STATUS_FAILED.equals(existing.getIndexStatus())
                && attempts >= Math.max(properties.getKnowledge().getMaxAttempts(), 1);
    }

    /**
     * 回填页面元数据并初始化新增台账。
     *
     * @param record 台账
     * @param change 页面变更/快照
     */
    private void applyMeta(AgentKnowledgeDoc record, RemoteDocChange change) {
        if (record.getPageId() == null) {
            record.setPageId(change.getPageId());
            record.setEnabled(KnowledgeConstants.ENABLED_YES);
            record.setAttempts(0);
            record.setChunkCount(0);
            record.setIndexStatus(KnowledgeConstants.STATUS_PENDING);
        }
        record.setSpaceId(change.getSpaceId());
        record.setTitle(change.getTitle());
        record.setSlug(change.getSlug());
        record.setRevision(change.getRevision());
        record.setUrl(urlOf(change));
        record.setSourceUpdateTime(change.getUpdateTime());
    }

    /**
     * 标记跳过并清空块数。
     *
     * @param record 台账
     * @param reason 跳过原因
     */
    private void markSkipped(AgentKnowledgeDoc record, String reason) {
        record.setIndexStatus(KnowledgeConstants.STATUS_SKIPPED);
        record.setChunkCount(0);
        record.setLastError(truncate(reason));
        record.setNextRetryAt(null);
        docService.save(record);
    }

    /**
     * 页面不可见时下线索引并更新台账。
     *
     * @param pageId 页面ID
     */
    private void skipMissing(Long pageId) {
        AgentKnowledgeDoc record = docService.find(pageId);
        if (record != null) {
            record.setIndexStatus(KnowledgeConstants.STATUS_SKIPPED);
            record.setChunkCount(0);
            record.setLastError("页面不存在或不可见");
            docService.save(record);
        }
        indexService.deletePaths(List.of(pathOf(pageId)));
        indexService.commitAndRefresh();
    }

    /**
     * 移除页面的全部索引块。
     *
     * @param pageId  页面ID
     * @param context 本轮上下文
     */
    private void removeIndex(Long pageId, SyncContext context) {
        indexService.deletePaths(List.of(pathOf(pageId)));
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
     * 截断正文以适配配置上限。
     *
     * @param text 原始正文
     * @return 截断后的正文
     */
    private String truncateText(String text) {
        if (text == null) {
            return null;
        }
        int maxChars = Math.max(properties.getKnowledge().getDocMaxChars(), 1);
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    /**
     * 计算正文哈希（revision + 正文，任一变化触发重建）。
     *
     * @param revision 版本号
     * @param text     正文
     * @return MD5 十六进制字符串
     */
    private static String md5Of(Long revision, String text) {
        String raw = (revision == null ? "0" : String.valueOf(revision)) + "|" + text;
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构造页面地址（前端工作台路由）。
     *
     * @param change 页面变更/快照
     * @return 页面地址
     */
    private static String urlOf(RemoteDocChange change) {
        if (change.getSpaceId() == null || change.getPageId() == null) {
            return null;
        }
        return "/doc/space/" + change.getSpaceId() + "?pageId=" + change.getPageId();
    }

    /**
     * 取当前租户ID；调度线程已绑定上下文，缺失时使用默认租户。
     *
     * @return 租户ID
     */
    private static long tenantId() {
        Long tenantId = TenantContext.get();
        return tenantId == null ? TenantContext.DEFAULT_TENANT_ID : tenantId;
    }

    /**
     * 取文档知识块要求的最低权限码。
     *
     * @return 权限码
     */
    private String permissionCode() {
        String configured = properties.getKnowledge().getDocPermissionCode();
        return StringUtils.hasText(configured) ? configured.trim() : "sys:doc:page:read";
    }

    /**
     * 构造知识块路径。
     *
     * @param pageId 页面ID
     * @return 路径
     */
    private static String pathOf(Long pageId) {
        return PATH_PREFIX + pageId;
    }

    /**
     * 取两个游标中较大者。
     *
     * @param current   当前游标
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
     */
    private void runOnceSafely() {
        for (Long tenantId : resolveTenantIds()) {
            Long previousTenant = TenantContext.get();
            TenantContext.set(tenantId);
            try {
                runOnce();
            } catch (BizException e) {
                log.info("文档知识库同步跳过: tenantId={}, reason={}", tenantId, e.getMessage());
            } catch (RuntimeException e) {
                log.error("文档知识库同步异常: tenantId={}", tenantId, e);
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
        rebuildExecutor.shutdownNow();
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

    /**
     * 失败退避计算（与文件知识同步保持同一规则）。
     *
     * @author BlueWhale
     */
    private static final class FailureBackoff {

        /**
         * 计算指数退避秒数：{@code base * 2^(attempts-1)}。
         *
         * @param attempts    已失败次数（1 起）
         * @param baseSeconds 退避基数（秒）
         * @return 退避秒数
         */
        private static long delaySeconds(int attempts, int baseSeconds) {
            int exponent = Math.min(Math.max(attempts - 1, 0), 10);
            return Math.max(baseSeconds, 1L) * (1L << exponent);
        }
    }
}
