package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.twin.dto.MarkerSaveRequest;
import com.bluewhale.twin.dto.MarkerVO;
import com.bluewhale.twin.service.TwinMarkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 孪生标注/热点接口：场景内位置锚点的增删改查（挂 /twin/scenes/{sceneId}/markers 下）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/twin/v{version}/scenes/{sceneId}/markers", version = "1")
@RequiredArgsConstructor
public class TwinMarkerController {

    private final TwinMarkerService markerService;

    /**
     * 创建标注。
     *
     * @param sceneId 场景ID
     * @param request 保存请求
     * @return 视图对象
     */
    @PostMapping
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "创建孪生标注", type = OperateType.CREATE, targetType = "twin_marker")
    public Result<MarkerVO> create(@PathVariable("sceneId") Long sceneId,
                                   @Valid @RequestBody MarkerSaveRequest request) {
        return Result.ok(markerService.create(sceneId, request));
    }

    /**
     * 场景标注列表。
     *
     * @param sceneId 场景ID
     * @return 标注视图列表
     */
    @GetMapping
    @RequirePermission("sys:twin:scene")
    public Result<List<MarkerVO>> list(@PathVariable("sceneId") Long sceneId) {
        return Result.ok(markerService.list(sceneId));
    }

    /**
     * 标注详情。
     *
     * @param sceneId 场景ID
     * @param id      标注ID
     * @return 视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    public Result<MarkerVO> get(@PathVariable("sceneId") Long sceneId,
                                @PathVariable("id") Long id) {
        return Result.ok(markerService.get(sceneId, id));
    }

    /**
     * 更新标注（挂靠实体可换）。
     *
     * @param sceneId 场景ID
     * @param id      标注ID
     * @param request 保存请求
     * @return 视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "更新孪生标注", type = OperateType.UPDATE, targetType = "twin_marker")
    public Result<MarkerVO> update(@PathVariable("sceneId") Long sceneId,
                                   @PathVariable("id") Long id,
                                   @Valid @RequestBody MarkerSaveRequest request) {
        return Result.ok(markerService.update(sceneId, id, request));
    }

    /**
     * 删除标注（逻辑删除）。
     *
     * @param sceneId 场景ID
     * @param id      标注ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "删除孪生标注", type = OperateType.DELETE, targetType = "twin_marker")
    public Result<Void> delete(@PathVariable("sceneId") Long sceneId,
                               @PathVariable("id") Long id) {
        markerService.delete(sceneId, id);
        return Result.ok();
    }
}
