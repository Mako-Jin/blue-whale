package com.bluewhale.agent.controller;

import com.bluewhale.agent.dto.AgentKnowledgeDocVO;
import com.bluewhale.agent.dto.AgentKnowledgeFileVO;
import com.bluewhale.agent.dto.AgentKnowledgeStatsVO;
import com.bluewhale.agent.dto.KnowledgeSyncResultVO;
import com.bluewhale.agent.service.AgentKnowledgeDocService;
import com.bluewhale.agent.service.AgentKnowledgeFileService;
import com.bluewhale.agent.service.DocKnowledgeSyncService;
import com.bluewhale.agent.service.FileKnowledgeSyncService;
import com.bluewhale.agent.support.knowledge.KnowledgeConstants;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库管理接口（文件知识 Wave 5.53 M4 + 文档知识 Wiki M4）。
 *
 * <p>路径前缀 {@code /agent/knowledge}；查询需 {@code sys:agent:knowledge:list}，
 * 重建/重索引/启停需 {@code sys:agent:knowledge:manage}，管理动作落审计。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/agent/knowledge")
@RequiredArgsConstructor
public class AgentKnowledgeController {

    /** 审计对象类型：文件知识台账 */
    private static final String TARGET_TYPE = "agent_knowledge_file";

    /** 审计对象类型：文档知识台账 */
    private static final String TARGET_TYPE_DOC = "agent_knowledge_doc";

    private final AgentKnowledgeFileService fileService;

    private final FileKnowledgeSyncService syncService;

    private final AgentKnowledgeDocService docService;

    private final DocKnowledgeSyncService docSyncService;

    /**
     * 分页查询文件采集台账。
     *
     * @param keyword 文件名关键词
     * @param status 状态过滤
     * @param source 来源过滤
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    @GetMapping("/files/page")
    @RequirePermission("sys:agent:knowledge:list")
    public Result<PageResult<AgentKnowledgeFileVO>> page(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String source,
                                                         @RequestParam(defaultValue = "1") long current,
                                                         @RequestParam(defaultValue = "20") long size) {
        PageResult<AgentKnowledgeFileVO> page = fileService.pageVo(keyword, status, source, current, size);
        return Result.ok(page);
    }

    /**
     * 重建单个文件的索引。
     *
     * @param fileId 文件ID
     * @return 重建成功返回 {@code true}
     */
    @PostMapping("/files/{fileId}/reindex")
    @RequirePermission("sys:agent:knowledge:manage")
    @AuditLog(operation = "重建文件知识索引", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#fileId")
    public Result<Boolean> reindex(@PathVariable Long fileId) {
        return Result.ok(syncService.reindex(fileId));
    }

    /**
     * 启用或停用文件知识。
     *
     * @param fileId 文件ID
     * @param enabled 是否启用
     * @return 空结果
     */
    @PutMapping("/files/{fileId}/enabled")
    @RequirePermission("sys:agent:knowledge:manage")
    @AuditLog(operation = "启停文件知识", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#fileId")
    public Result<Void> setEnabled(@PathVariable Long fileId,
                                   @RequestParam(defaultValue = "true") boolean enabled) {
        syncService.setEnabled(fileId, enabled);
        return Result.ok();
    }

    /**
     * 触发全量重建（异步）。
     *
     * @return 受理返回 {@code true}；已有任务执行返回 {@code false}
     */
    @PostMapping("/rebuild")
    @RequirePermission("sys:agent:knowledge:manage")
    @AuditLog(operation = "全量重建文件知识库", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<Boolean> rebuild() {
        return Result.ok(syncService.rebuildAsync());
    }

    /**
     * 查询文件知识库统计。
     *
     * @return 统计结果
     */
    @GetMapping("/stats")
    @RequirePermission("sys:agent:knowledge:list")
    public Result<AgentKnowledgeStatsVO> stats() {
        AgentKnowledgeStatsVO stats = new AgentKnowledgeStatsVO();
        long pending = fileService.countByStatus(KnowledgeConstants.STATUS_PENDING);
        long indexing = fileService.countByStatus(KnowledgeConstants.STATUS_INDEXING);
        long indexed = fileService.countByStatus(KnowledgeConstants.STATUS_INDEXED);
        long skipped = fileService.countByStatus(KnowledgeConstants.STATUS_SKIPPED);
        long failed = fileService.countByStatus(KnowledgeConstants.STATUS_FAILED);
        stats.setPending(pending);
        stats.setIndexing(indexing);
        stats.setIndexed(indexed);
        stats.setSkipped(skipped);
        stats.setFailed(failed);
        stats.setTotal(pending + indexing + indexed + skipped + failed);
        stats.setChunkCount(fileService.sumChunks());
        stats.setCursor(fileService.cursor());
        stats.setLastRunAt(syncService.lastRunAt());
        stats.setLastResult(syncService.lastResult());
        fillDocStats(stats);
        return Result.ok(stats);
    }

    /**
     * 分页查询文档采集台账。
     *
     * @param keyword 标题关键词
     * @param status  状态过滤
     * @param current 页码
     * @param size    每页条数
     * @return 分页结果
     */
    @GetMapping("/doc/page")
    @RequirePermission("sys:agent:knowledge:list")
    public Result<PageResult<AgentKnowledgeDocVO>> docPage(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "1") long current,
                                                           @RequestParam(defaultValue = "20") long size) {
        return Result.ok(docService.pageVo(keyword, status, current, size));
    }

    /**
     * 手动触发一轮文档知识同步。
     *
     * @return 同步结果
     */
    @PostMapping("/doc/sync")
    @RequirePermission("sys:agent:knowledge:manage")
    @AuditLog(operation = "同步文档知识库", type = OperateType.UPDATE, targetType = TARGET_TYPE_DOC)
    public Result<KnowledgeSyncResultVO> docSync() {
        return Result.ok(docSyncService.runOnce());
    }

    /**
     * 重建单个文档页面的索引。
     *
     * @param pageId 页面ID
     * @return 重建成功返回 {@code true}
     */
    @PostMapping("/doc/{pageId}/reindex")
    @RequirePermission("sys:agent:knowledge:manage")
    @AuditLog(operation = "重建文档知识索引", type = OperateType.UPDATE,
            targetType = TARGET_TYPE_DOC, targetId = "#pageId")
    public Result<Boolean> docReindex(@PathVariable Long pageId) {
        return Result.ok(docSyncService.reindex(pageId));
    }

    /**
     * 启用或停用文档知识。
     *
     * @param pageId  页面ID
     * @param enabled 是否启用
     * @return 空结果
     */
    @PutMapping("/doc/{pageId}/enabled")
    @RequirePermission("sys:agent:knowledge:manage")
    @AuditLog(operation = "启停文档知识", type = OperateType.UPDATE,
            targetType = TARGET_TYPE_DOC, targetId = "#pageId")
    public Result<Void> docSetEnabled(@PathVariable Long pageId,
                                      @RequestParam(defaultValue = "true") boolean enabled) {
        docSyncService.setEnabled(pageId, enabled);
        return Result.ok();
    }

    /**
     * 触发文档知识全量重建（异步）。
     *
     * @return 受理返回 {@code true}；已有任务执行返回 {@code false}
     */
    @PostMapping("/doc/rebuild")
    @RequirePermission("sys:agent:knowledge:manage")
    @AuditLog(operation = "全量重建文档知识库", type = OperateType.UPDATE, targetType = TARGET_TYPE_DOC)
    public Result<Boolean> docRebuild() {
        return Result.ok(docSyncService.rebuildAsync());
    }

    /**
     * 填充文档知识库统计。
     *
     * @param stats 统计对象
     */
    private void fillDocStats(AgentKnowledgeStatsVO stats) {
        long pending = docService.countByStatus(KnowledgeConstants.STATUS_PENDING);
        long indexing = docService.countByStatus(KnowledgeConstants.STATUS_INDEXING);
        long indexed = docService.countByStatus(KnowledgeConstants.STATUS_INDEXED);
        long skipped = docService.countByStatus(KnowledgeConstants.STATUS_SKIPPED);
        long failed = docService.countByStatus(KnowledgeConstants.STATUS_FAILED);
        stats.setDocPending(pending);
        stats.setDocIndexing(indexing);
        stats.setDocIndexed(indexed);
        stats.setDocSkipped(skipped);
        stats.setDocFailed(failed);
        stats.setDocTotal(pending + indexing + indexed + skipped + failed);
        stats.setDocChunkCount(docService.sumChunks());
        stats.setDocCursor(docService.cursor());
        stats.setDocLastRunAt(docSyncService.lastRunAt());
        stats.setDocLastResult(docSyncService.lastResult());
    }
}
