package com.bluewhale.archive.controller;

import com.bluewhale.archive.service.ArchiveSyncService;
import com.bluewhale.archive.vo.ArchiveSyncResultVO;
import com.bluewhale.archive.vo.ArchiveSyncStatusVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档案同步接口。
 *
 * <p>路径前缀 {@code /archive/sync}；手动触发权限 {@code sys:archive:sync:run}，
 * 状态查询复用条目查询权限 {@code sys:archive:item:list}；触发动作落审计。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/archive/sync")
@RequiredArgsConstructor
public class ArchiveSyncController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "archive_sync";

    private final ArchiveSyncService archiveSyncService;

    /**
     * 手动触发一轮档案同步。
     *
     * @return 本轮同步结果
     */
    @PostMapping("/run")
    @RequirePermission("sys:archive:sync:run")
    @AuditLog(operation = "触发档案同步", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ArchiveSyncResultVO> run() {
        return Result.ok(archiveSyncService.runOnce());
    }

    /**
     * 查询同步运行状态与条目/台账统计。
     *
     * @return 同步状态
     */
    @GetMapping("/status")
    @RequirePermission("sys:archive:item:list")
    public Result<ArchiveSyncStatusVO> status() {
        return Result.ok(archiveSyncService.status());
    }
}
