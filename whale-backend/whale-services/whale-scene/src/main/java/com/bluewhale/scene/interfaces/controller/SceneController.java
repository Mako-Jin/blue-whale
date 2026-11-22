package com.bluewhale.scene.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.scene.application.command.NodeBindingSaveCommand;
import com.bluewhale.scene.application.command.SceneNodeSaveCommand;
import com.bluewhale.scene.application.command.SceneQueryCommand;
import com.bluewhale.scene.application.command.SceneSaveCommand;
import com.bluewhale.scene.application.command.SceneVisibilitySaveCommand;
import com.bluewhale.scene.application.service.SceneAppService;
import com.bluewhale.scene.application.view.NodeBindingView;
import com.bluewhale.scene.application.view.SceneDetailView;
import com.bluewhale.scene.application.view.SceneNodeView;
import com.bluewhale.scene.application.view.SceneView;
import com.bluewhale.scene.application.view.SceneVisibilityView;
import com.bluewhale.scene.interfaces.support.PageParams;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景管理接口：场景 CRUD、发布 / 下线、节点、入口绑定与可见性。
 *
 * <p>全部写接口要求 {@code sys:scene:*} 权限码并记录审计；导航只读接口在
 * {@link SceneNavigationController}。入口地址只保存跳转信息，不直连业务库。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/scene/v{version}", version = "1")
@RequiredArgsConstructor
public class SceneController {

    private final SceneAppService sceneAppService;

    /**
     * 场景分页。
     *
     * @param command 查询命令
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @return 场景分页
     */
    @GetMapping("/page")
    @RequirePermission("sys:scene:list")
    public Result<PageResult<SceneView>> page(
            @ModelAttribute SceneQueryCommand command,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize) {
        command.setCurrent(PageParams.current(current, pageNum));
        command.setSize(PageParams.size(size, pageSize));
        return Result.ok(sceneAppService.page(command));
    }

    /**
     * 场景详情。
     *
     * @param id 场景 ID
     * @return 场景详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:scene:list")
    public Result<SceneDetailView> detail(@PathVariable Long id) {
        return Result.ok(sceneAppService.detail(id));
    }

    /**
     * 新建场景（草稿）。
     *
     * @param command 保存命令
     * @return 场景视图
     */
    @PostMapping
    @RequirePermission("sys:scene:add")
    @AuditLog(operation = "新建场景", type = OperateType.CREATE, targetType = "sc_scene")
    public Result<SceneView> create(@Valid @RequestBody SceneSaveCommand command) {
        return Result.ok(sceneAppService.create(command));
    }

    /**
     * 编辑场景。
     *
     * @param id 场景 ID
     * @param command 保存命令
     * @return 场景视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:scene:edit")
    @AuditLog(operation = "编辑场景", type = OperateType.UPDATE, targetType = "sc_scene", targetId = "#id")
    public Result<SceneView> update(@PathVariable Long id, @Valid @RequestBody SceneSaveCommand command) {
        return Result.ok(sceneAppService.update(id, command));
    }

    /**
     * 发布场景（校验节点树与入口后生成布局快照）。
     *
     * @param id 场景 ID
     * @return 场景视图
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:scene:publish")
    @AuditLog(operation = "发布场景", type = OperateType.UPDATE, targetType = "sc_scene", targetId = "#id")
    public Result<SceneView> publish(@PathVariable Long id) {
        return Result.ok(sceneAppService.publish(id));
    }

    /**
     * 下线场景。
     *
     * @param id 场景 ID
     * @return 场景视图
     */
    @PostMapping("/{id}/offline")
    @RequirePermission("sys:scene:offline")
    @AuditLog(operation = "下线场景", type = OperateType.UPDATE, targetType = "sc_scene", targetId = "#id")
    public Result<SceneView> offline(@PathVariable Long id) {
        return Result.ok(sceneAppService.offline(id));
    }

    /**
     * 新增节点。
     *
     * @param id 场景 ID
     * @param command 节点命令
     * @return 节点视图
     */
    @PostMapping("/{id}/node")
    @RequirePermission("sys:scene:node:manage")
    @AuditLog(operation = "新增场景节点", type = OperateType.CREATE, targetType = "sc_scene_node", targetId = "#id")
    public Result<SceneNodeView> createNode(@PathVariable Long id,
                                            @Valid @RequestBody SceneNodeSaveCommand command) {
        return Result.ok(sceneAppService.saveNode(id, null, command));
    }

    /**
     * 编辑节点。
     *
     * @param id 场景 ID
     * @param nodeId 节点 ID
     * @param command 节点命令
     * @return 节点视图
     */
    @PutMapping("/{id}/node/{nodeId}")
    @RequirePermission("sys:scene:node:manage")
    @AuditLog(operation = "编辑场景节点", type = OperateType.UPDATE, targetType = "sc_scene_node",
            targetId = "#nodeId")
    public Result<SceneNodeView> updateNode(@PathVariable Long id, @PathVariable Long nodeId,
                                            @Valid @RequestBody SceneNodeSaveCommand command) {
        return Result.ok(sceneAppService.saveNode(id, nodeId, command));
    }

    /**
     * 删除节点（存在子节点时拒绝）。
     *
     * @param nodeId 节点 ID
     * @return 成功响应
     */
    @DeleteMapping("/node/{nodeId}")
    @RequirePermission("sys:scene:node:manage")
    @AuditLog(operation = "删除场景节点", type = OperateType.DELETE, targetType = "sc_scene_node",
            targetId = "#nodeId")
    public Result<Void> deleteNode(@PathVariable Long nodeId) {
        sceneAppService.deleteNode(nodeId);
        return Result.ok();
    }

    /**
     * 设置节点入口绑定（一个节点一个入口，重复调用即覆盖）。
     *
     * @param nodeId 节点 ID
     * @param command 绑定命令
     * @return 绑定视图
     */
    @PutMapping("/node/{nodeId}/binding")
    @RequirePermission("sys:scene:binding:manage")
    @AuditLog(operation = "设置节点入口", type = OperateType.UPDATE, targetType = "sc_node_binding",
            targetId = "#nodeId")
    public Result<NodeBindingView> saveBinding(@PathVariable Long nodeId,
                                               @Valid @RequestBody NodeBindingSaveCommand command) {
        return Result.ok(sceneAppService.saveBinding(nodeId, command));
    }

    /**
     * 替换场景可见性规则（DENY 优先）。
     *
     * @param id 场景 ID
     * @param command 规则列表命令
     * @return 最新规则
     */
    @PutMapping("/{id}/visibility")
    @RequirePermission("sys:scene:visibility:manage")
    @AuditLog(operation = "设置场景可见性", type = OperateType.GRANT, targetType = "sc_visibility",
            targetId = "#id")
    public Result<List<SceneVisibilityView>> replaceVisibility(
            @PathVariable Long id, @Valid @RequestBody SceneVisibilitySaveCommand command) {
        return Result.ok(sceneAppService.replaceVisibility(id, command.getRules()));
    }
}
