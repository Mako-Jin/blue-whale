package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.support.index.AgentIndexService;
import com.bluewhale.agent.support.index.IndexStats;
import com.bluewhale.agent.support.ingest.Chunk;
import com.bluewhale.agent.support.ingest.ChunkerRegistry;
import com.bluewhale.agent.support.ingest.ChunkSupport;
import com.bluewhale.agent.support.ingest.DocumentChunker;
import com.bluewhale.agent.support.ingest.RepositoryScanner;
import com.bluewhale.agent.support.ingest.ScannedFile;
import com.bluewhale.agent.support.ingest.SensitiveContentMasker;
import com.bluewhale.agent.support.knowledge.KnowledgeConstants;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识采集服务：扫描 → 切分 → 脱敏 → 写入 Lucene → 落库索引元数据。
 *
 * <p>启动后按 {@code whale.agent.ingest.build-on-startup} 构建一次，并按
 * {@code scan-interval-seconds} 周期做增量扫描（仅处理哈希变化的文件，删除已移除文件）；
 * 单线程执行器保证同一时刻只有一个构建任务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIngestService {

    /** 索引版本时间格式 */
    private static final DateTimeFormatter VERSION_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AgentProperties properties;

    private final RepositoryScanner scanner;

    private final ChunkerRegistry chunkerRegistry;

    private final AgentIndexService indexService;

    private final AgentIndexMetaService metaService;

    private final FactStoreService factStoreService;

    private final EmbeddingService embeddingService;

    private final AgentKnowledgeFileService knowledgeFileService;

    private final AgentKnowledgeDocService knowledgeDocService;

    private final AtomicBoolean building = new AtomicBoolean(false);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-indexer");
        thread.setDaemon(true);
        return thread;
    });

    private volatile int indexedFileCount;

    /**
     * 启动后触发首次构建并注册周期增量扫描。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (properties.getIngest().isBuildOnStartup()) {
            executor.submit(this::rebuildSafely);
        }
        int interval = properties.getIngest().getScanIntervalSeconds();
        if (interval > 0) {
            executor.scheduleWithFixedDelay(this::incrementalSafely, interval, interval, TimeUnit.SECONDS);
        }
    }

    /**
     * 异步触发全量重建。
     *
     * @return 已受理返回 {@code true}；已有构建任务返回 {@code false}
     */
    public boolean rebuildAsync() {
        if (building.get()) {
            return false;
        }
        executor.submit(this::rebuildSafely);
        return true;
    }

    /**
     * 全量重建索引（同步执行，供内部与测试调用）。
     */
    public void rebuild() {
        if (!building.compareAndSet(false, true)) {
            log.info("已有索引构建任务在执行，跳过本次重建");
            return;
        }
        try {
            runFullRebuild();
        } finally {
            building.set(false);
        }
    }

    /**
     * 执行全量重建（调用方须已持有 {@code building} 标记）。
     */
    private void runFullRebuild() {
        long start = System.currentTimeMillis();
        indexService.markBuildStart();
        try {
            List<ScannedFile> files = scanner.scan();
            indexService.deleteAll();
            int chunks = 0;
            for (ScannedFile file : files) {
                chunks += indexFile(file);
            }
            indexService.commitAndRefresh();
            indexedFileCount = files.size();
            long millis = System.currentTimeMillis() - start;
            indexService.markBuildSuccess(millis);
            saveMeta();
            log.info("知识索引全量重建完成：文件={}，块={}，耗时={}ms，向量维度={}",
                    files.size(), chunks, millis, indexService.indexedDimension());
            rebuildFacts();
            // deleteAll 会同时清空文件/档案知识块：重置台账为 PENDING，由下一轮文件知识同步自愈重建
            int reset = knowledgeFileService.resetIndexedToPending();
            if (reset > 0) {
                log.info("文件知识台账已重置待处理：{} 条，等待下一轮同步重建", reset);
            }
            int docReset = knowledgeDocService.resetIndexedToPending();
            if (docReset > 0) {
                log.info("文档知识台账已重置待处理：{} 条，等待下一轮同步重建", docReset);
            }
        } catch (RuntimeException e) {
            indexService.markBuildFailed();
            log.error("知识索引全量重建失败", e);
        }
    }

    /**
     * 增量扫描：仅重建哈希变化文件，删除已移除文件；embedding 签名变化时回落全量重建。
     */
    public void incrementalScan() {
        if (!building.compareAndSet(false, true)) {
            return;
        }
        try {
            if (embeddingChanged()) {
                log.info("embedding 签名变更（索引={}，期望={}），改为全量重建",
                        indexService.indexedSignature(), embeddingService.signature());
                runFullRebuild();
                return;
            }
            runIncremental();
        } finally {
            building.set(false);
        }
    }

    /**
     * 判断索引内的 embedding 签名是否与当前配置不一致（需全量重建）。
     *
     * @return 不一致返回 {@code true}
     */
    private boolean embeddingChanged() {
        return indexService.count() > 0
                && !embeddingService.signature().equals(indexService.indexedSignature());
    }

    /**
     * 执行增量扫描（调用方须已持有 {@code building} 标记）。
     */
    private void runIncremental() {
        long start = System.currentTimeMillis();
        indexService.markBuildStart();
        try {
            Map<String, String> existing = indexService.listFileHashes();
            List<ScannedFile> files = scanner.scan();
            Set<String> currentPaths = new HashSet<>();
            List<String> stalePaths = new ArrayList<>();
            List<ScannedFile> changedFiles = new ArrayList<>();
            for (ScannedFile file : files) {
                currentPaths.add(file.getRelativePath());
                String oldHash = existing.get(file.getRelativePath());
                if (oldHash == null || !oldHash.equals(file.getHash())) {
                    stalePaths.add(file.getRelativePath());
                    changedFiles.add(file);
                }
            }
            List<String> removedPaths = new ArrayList<>();
            for (String path : existing.keySet()) {
                // 文件知识块（file: 前缀）由 FileKnowledgeSyncService 独立管理生命周期，
                // 文档知识块（docpage: 前缀）由 DocKnowledgeSyncService 独立管理生命周期，
                // 均不参与仓库扫描比对，严禁在此误删
                if (path.startsWith(KnowledgeConstants.FILE_PATH_PREFIX)
                        || path.startsWith(KnowledgeConstants.DOC_PATH_PREFIX)) {
                    continue;
                }
                if (!currentPaths.contains(path)) {
                    removedPaths.add(path);
                }
            }
            if (stalePaths.isEmpty() && removedPaths.isEmpty()) {
                indexService.markBuildSuccess(System.currentTimeMillis() - start);
                return;
            }
            indexService.deletePaths(stalePaths);
            indexService.deletePaths(removedPaths);
            int chunks = 0;
            for (ScannedFile file : changedFiles) {
                chunks += indexFile(file);
            }
            indexService.commitAndRefresh();
            indexedFileCount = currentPaths.size();
            indexService.markBuildSuccess(System.currentTimeMillis() - start);
            saveMeta();
            log.info("知识索引增量更新完成：变更文件={}，移除文件={}，新增块={}",
                    changedFiles.size(), removedPaths.size(), chunks);
            rebuildFacts();
        } catch (RuntimeException e) {
            indexService.markBuildFailed();
            log.error("知识索引增量更新失败", e);
        }
    }

    /**
     * 当前索引统计。
     *
     * @return 索引统计
     */
    public IndexStats stats() {
        int fileCount = indexedFileCount > 0 ? indexedFileCount : indexService.listFileHashes().size();
        return indexService.stats(fileCount);
    }

    /**
     * 索引单个文件并返回块数。
     *
     * @param file 扫描结果
     * @return 写入块数
     */
    private int indexFile(ScannedFile file) {
        DocumentChunker chunker = chunkerRegistry.resolve(file.getRelativePath());
        if (chunker == null) {
            return 0;
        }
        try {
            String text = scanner.readText(file);
            List<Chunk> chunks = chunker.chunk(file.getRelativePath(),
                    ChunkSupport.moduleOf(file.getRelativePath()), text);
            List<Chunk> safeChunks = new ArrayList<>(chunks.size());
            for (Chunk chunk : chunks) {
                safeChunks.add(mask(chunk));
            }
            indexService.addFileChunks(file.getRelativePath(), file.getHash(), safeChunks);
            return safeChunks.size();
        } catch (RuntimeException e) {
            log.warn("索引文件失败，跳过: {}", file.getRelativePath(), e);
            return 0;
        }
    }

    /**
     * 对知识块正文做敏感内容脱敏。
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
     * 重建结构化事实（失败不影响索引结果）。
     */
    private void rebuildFacts() {
        try {
            factStoreService.rebuild();
        } catch (RuntimeException e) {
            log.error("结构化事实重建失败（索引不受影响）", e);
        }
    }

    /**
     * 落库索引元数据。
     */
    private void saveMeta() {
        String version = LocalDateTime.now().format(VERSION_FORMAT);
        metaService.save(stats(), version);
    }

    /**
     * 安全重建（异常不外抛，供调度器使用）。
     */
    private void rebuildSafely() {
        try {
            rebuild();
        } catch (RuntimeException e) {
            log.error("调度重建异常", e);
        }
    }

    /**
     * 安全增量扫描（异常不外抛，供调度器使用）。
     */
    private void incrementalSafely() {
        try {
            incrementalScan();
        } catch (RuntimeException e) {
            log.error("调度增量扫描异常", e);
        }
    }

    /**
     * 停止调度线程。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
