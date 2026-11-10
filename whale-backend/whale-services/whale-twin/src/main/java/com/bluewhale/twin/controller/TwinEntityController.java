package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.twin.dto.EntitySaveRequest;
import com.bluewhale.twin.dto.EntityVO;
import com.bluewhale.twin.service.TwinEntityService;
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
 * 孪生实体接口：场景树节点的增删改查（挂 /twin/scenes/{sceneId}/entities 下）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/twin/scenes/{sceneId}/entities")
@RequiredArgsConstructor
public class TwinEntityController {

    private final TwinEntityService entityService;

    /**
     * 创建实体（挂在指定父节点下）。
     *
     * @param sceneId 场景ID
     * @param request 保存请求
     * @return 视图对象
     */
    @PostMapping
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "创建孪生实体", type = OperateType.CREATE, targetType = "twin_entity")
    public Result<EntityVO> create(@PathVariable("sceneId") Long sceneId,
                                   @Valid @RequestBody EntitySaveRequest request) {
        return Result.ok(entityService.create(sceneId, request));
    }

    /**
     * 场景实体树。
     *
     * @param sceneId 场景ID
     * @return 根节点列表
     */
    @GetMapping("/tree")
    @RequirePermission("sys:twin:scene")
    public Result<List<EntityVO>> tree(@PathVariable("sceneId") Long sceneId) {
        return Result.ok(entityService.tree(sceneId));
    }

    /**
     * 场景实体平铺列表。
     *
     * @param sceneId 场景ID
     * @return 实体列表
     */
    @GetMapping
    @RequirePermission("sys:twin:scene")
    public Result<List<EntityVO>> list(@PathVariable("sceneId") Long sceneId) {
        return Result.ok(entityService.list(sceneId));
    }

    /**
     * 实体详情。
     *
     * @param sceneId 场景ID
     * @param id      实体ID
     * @return 视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    public Result<EntityVO> get(@PathVariable("sceneId") Long sceneId,
                                @PathVariable("id") Long id) {
        return Result.ok(entityService.get(sceneId, id));
    }

    /**
     * 更新实体（编码不可改；移动父节点做环检测）。
     *
     * @param sceneId 场景ID
     * @param id      实体ID
     * @param request 保存请求
     * @return 视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "更新孪生实体", type = OperateType.UPDATE, targetType = "twin_entity")
    public Result<EntityVO> update(@PathVariable("sceneId") Long sceneId,
                                   @PathVariable("id") Long id,
                                   @Valid @RequestBody EntitySaveRequest request) {
        return Result.ok(entityService.update(sceneId, id, request));
    }

    /**
     * 删除实体（逻辑删除；需无子节点）。
     *
     * @param sceneId 场景ID
     * @param id      实体ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "删除孪生实体", type = OperateType.DELETE, targetType = "twin_entity")
    public Result<Void> delete(@PathVariable("sceneId") Long sceneId,
                               @PathVariable("id") Long id) {
        entityService.delete(sceneId, id);
        return Result.ok();
    }
}
