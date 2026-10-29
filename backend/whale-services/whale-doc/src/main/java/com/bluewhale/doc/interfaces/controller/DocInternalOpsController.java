package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.scheduler.DocOrphanAttachmentCleanupJob;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 文档服务内网运维接口（M5）。
 *
 * <p>网关对 {@code /doc/internal/**} 统一返回 403，仅允许本机/内网运维脚本直接调用。
 * 提供孤儿附件引用清理的手工触发能力，便于发布后巡检与故障恢复。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc/internal/ops")
@RequiredArgsConstructor
public class DocInternalOpsController {

    private final ObjectProvider<DocOrphanAttachmentCleanupJob> orphanCleanupJob;

    /**
     * 手工触发孤儿附件引用清理。
     *
     * @return 清理行数；任务关闭时返回 0
     */
    @PostMapping("/orphan-attachment-cleanup")
    public Result<Map<String, Integer>> cleanupOrphanAttachments() {
        DocOrphanAttachmentCleanupJob job = orphanCleanupJob.getIfAvailable();
        int removed = job == null ? 0 : job.cleanup();
        return Result.ok(Map.of("removed", removed));
    }
}
