package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.twin.dto.SnapshotVO;
import com.bluewhale.twin.service.TwinSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 孪生发布快照接口：列表/详情/回滚（发布动作在场景接口 {@code POST /twin/scenes/{id}/publish}）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/twin/scenes/{sceneId}/snapshots")
@RequiredArgsConstructor
public class TwinSnapshotController {

    private final TwinSnapshotService snapshotService;

    /**
     * 场景快照列表（版本倒序）。
     *
     * @param sceneId 场景ID
     * @return 快照视图列表
     */
    @GetMapping
    @RequirePermission("sys:twin:scene")
    public Result<List<SnapshotVO>> list(@PathVariable("sceneId") Long sceneId) {
        return Result.ok(snapshotService.list(sceneId));
    }

    /**
     * 快照详情（含整包 JSON，回滚前预览用）。
     *
     * @param sceneId 场景ID
     * @param id      快照ID
     * @return 视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    public Result<SnapshotVO> get(@PathVariable("sceneId") Long sceneId,
                                  @PathVariable("id") Long id) {
        return Result.ok(snapshotService.get(sceneId, id));
    }

    /**
     * 回滚到指定快照（重建工作副本；场景须为草稿/停用态）。
     *
     * @param sceneId 场景ID
     * @param id      快照ID
     * @return 被回滚到的快照视图对象
     */
    @PostMapping("/{id}/rollback")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "孪生场景回滚快照", type = OperateType.UPDATE, targetType = "twin_snapshot")
    public Result<SnapshotVO> rollback(@PathVariable("sceneId") Long sceneId,
                                       @PathVariable("id") Long id) {
        return Result.ok(snapshotService.rollback(sceneId, id));
    }
}
