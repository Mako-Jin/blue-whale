package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.twin.dto.SceneSaveRequest;
import com.bluewhale.twin.dto.SceneVO;
import com.bluewhale.twin.service.TwinSceneService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 孪生场景接口：创建/更新/发布/停用/删除与分页查询（管理视角）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/twin/scenes")
@RequiredArgsConstructor
public class TwinSceneController {

    private final TwinSceneService sceneService;

    /**
     * 创建场景（草稿态）。
     *
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 视图对象
     */
    @PostMapping
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "创建孪生场景", type = OperateType.CREATE, targetType = "twin_scene")
    public Result<SceneVO> create(@Valid @RequestBody SceneSaveRequest request,
                                  HttpServletRequest httpRequest) {
        return Result.ok(sceneService.create(request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 场景分页。
     *
     * @param keyword 名称/编码模糊过滤（可空）
     * @param status  状态过滤（可空：draft/published/disabled）
     * @param current 当前页
     * @param size    每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:twin:scene")
    public Result<PageResult<SceneVO>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(sceneService.page(keyword, status, current, size));
    }

    /**
     * 场景详情。
     *
     * @param id 场景ID
     * @return 视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    public Result<SceneVO> get(@PathVariable("id") Long id) {
        return Result.ok(sceneService.get(id));
    }

    /**
     * 更新场景（编码不可改；已发布场景需先停用）。
     *
     * @param id      场景ID
     * @param request 保存请求
     * @return 视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "更新孪生场景", type = OperateType.UPDATE, targetType = "twin_scene")
    public Result<SceneVO> update(@PathVariable("id") Long id,
                                  @Valid @RequestBody SceneSaveRequest request) {
        return Result.ok(sceneService.update(id, request));
    }

    /**
     * 发布场景。
     *
     * @param id 场景ID
     * @return 视图对象
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "发布孪生场景", type = OperateType.UPDATE, targetType = "twin_scene")
    public Result<SceneVO> publish(@PathVariable("id") Long id) {
        return Result.ok(sceneService.publish(id));
    }

    /**
     * 停用场景（恢复可编辑）。
     *
     * @param id 场景ID
     * @return 视图对象
     */
    @PostMapping("/{id}/disable")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "停用孪生场景", type = OperateType.UPDATE, targetType = "twin_scene")
    public Result<SceneVO> disable(@PathVariable("id") Long id) {
        return Result.ok(sceneService.disable(id));
    }

    /**
     * 删除场景（逻辑删除；场景下需无实体）。
     *
     * @param id 场景ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "删除孪生场景", type = OperateType.DELETE, targetType = "twin_scene")
    public Result<Void> delete(@PathVariable("id") Long id) {
        sceneService.delete(id);
        return Result.ok();
    }
}
