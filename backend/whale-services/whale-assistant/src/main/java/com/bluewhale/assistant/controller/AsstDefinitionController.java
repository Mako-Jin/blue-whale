package com.bluewhale.assistant.controller;

import com.bluewhale.assistant.dto.DefinitionSaveRequest;
import com.bluewhale.assistant.dto.DefinitionVO;
import com.bluewhale.assistant.dto.ToolGrantRequest;
import com.bluewhale.assistant.service.AsstDefinitionService;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
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
 * 助手定义接口：创建/更新/发布/停用/删除、分页查询与工具授权（管理视角）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/assistant/definitions")
@RequiredArgsConstructor
public class AsstDefinitionController {

    private final AsstDefinitionService definitionService;

    /**
     * 创建助手（草稿态）。
     *
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 视图对象
     */
    @PostMapping
    @RequirePermission("sys:assistant:define")
    @AuditLog(operation = "创建助手", type = OperateType.CREATE, targetType = "asst_definition")
    public Result<DefinitionVO> create(@Valid @RequestBody DefinitionSaveRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(definitionService.create(request, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 助手定义分页。
     *
     * @param keyword     名称/编码模糊过滤（可空）
     * @param status      状态过滤（可空：draft/published/disabled）
     * @param current     当前页
     * @param size        每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:assistant:define")
    public Result<PageResult<DefinitionVO>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return Result.ok(definitionService.page(keyword, status, current, size));
    }

    /**
     * 已发布助手清单（问答视角：平台工作台 / 业务内嵌入口选择助手用）。
     *
     * @return 已发布助手列表
     */
    @GetMapping("/published")
    @RequirePermission("sys:assistant:chat")
    public Result<java.util.List<DefinitionVO>> published() {
        return Result.ok(definitionService.listPublished());
    }

    /**
     * 助手详情（含工具白名单）。
     *
     * @param id 助手ID
     * @return 视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:assistant:define")
    public Result<DefinitionVO> get(@PathVariable("id") Long id) {
        return Result.ok(definitionService.get(id));
    }

    /**
     * 更新助手（编码不可改）。
     *
     * @param id          助手ID
     * @param request     保存请求
     * @return 视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:assistant:define")
    @AuditLog(operation = "更新助手", type = OperateType.UPDATE, targetType = "asst_definition")
    public Result<DefinitionVO> update(@PathVariable("id") Long id,
                                       @Valid @RequestBody DefinitionSaveRequest request) {
        return Result.ok(definitionService.update(id, request));
    }

    /**
     * 发布助手。
     *
     * @param id 助手ID
     * @return 视图对象
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:assistant:define")
    @AuditLog(operation = "发布助手", type = OperateType.UPDATE, targetType = "asst_definition")
    public Result<DefinitionVO> publish(@PathVariable("id") Long id) {
        return Result.ok(definitionService.publish(id));
    }

    /**
     * 停用助手。
     *
     * @param id 助手ID
     * @return 视图对象
     */
    @PostMapping("/{id}/disable")
    @RequirePermission("sys:assistant:define")
    @AuditLog(operation = "停用助手", type = OperateType.UPDATE, targetType = "asst_definition")
    public Result<DefinitionVO> disable(@PathVariable("id") Long id) {
        return Result.ok(definitionService.disable(id));
    }

    /**
     * 删除助手（逻辑删除）。
     *
     * @param id 助手ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:assistant:define")
    @AuditLog(operation = "删除助手", type = OperateType.DELETE, targetType = "asst_definition")
    public Result<Void> delete(@PathVariable("id") Long id) {
        definitionService.delete(id);
        return Result.ok();
    }

    /**
     * 整表覆盖式设置工具白名单。
     *
     * @param id          助手ID
     * @param request     授权请求
     * @param httpRequest 当前请求
     * @return 空结果
     */
    @PutMapping("/{id}/tools")
    @RequirePermission("sys:assistant:define")
    @AuditLog(operation = "设置助手工具授权", type = OperateType.UPDATE, targetType = "asst_tool_grant")
    public Result<Void> setTools(@PathVariable("id") Long id,
                                 @Valid @RequestBody ToolGrantRequest request,
                                 HttpServletRequest httpRequest) {
        definitionService.setTools(id, request.getToolCodes(), UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 查看助手工具白名单。
     *
     * @param id 助手ID
     * @return 工具编码列表
     */
    @GetMapping("/{id}/tools")
    @RequirePermission("sys:assistant:define")
    public Result<java.util.List<String>> tools(@PathVariable("id") Long id) {
        return Result.ok(definitionService.toolWhitelist(id));
    }
}
