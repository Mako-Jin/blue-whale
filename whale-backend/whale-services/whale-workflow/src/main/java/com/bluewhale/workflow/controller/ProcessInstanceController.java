package com.bluewhale.workflow.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.workflow.dto.ProcessInstanceQueryRequest;
import com.bluewhale.workflow.dto.ProcessInstanceStartRequest;
import com.bluewhale.workflow.service.ProcessInstanceService;
import com.bluewhale.workflow.vo.ProcessInstanceVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程实例管理接口。
 *
 * <p>服务内路径前缀 {@code /workflow/instance}；经网关按 {@code Path=/workflow/**} 规则转发，
 * 外部路径与本文档一致。全部接口受 {@code @RequirePermission("sys:workflow:instance:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/workflow/instance")
@RequiredArgsConstructor
public class ProcessInstanceController {

    private final ProcessInstanceService processInstanceService;

    /**
     * 发起流程实例。
     *
     * @param request 发起请求：definitionId / title 必填，businessKey 可选
     * @param httpRequest 当前请求（用于读取网关 {@code X-Username} 回填发起人）
     * @return 流程实例视图对象
     */
    @PostMapping
    @RequirePermission("sys:workflow:instance:start")
    public Result<ProcessInstanceVO> start(@Valid @RequestBody ProcessInstanceStartRequest request,
                                           HttpServletRequest httpRequest) {
        return Result.ok(processInstanceService.start(request, WebHeaders.username(httpRequest)));
    }

    /**
     * 流程实例详情（含该实例任务列表）。
     *
     * @param id 流程实例ID
     * @return 流程实例视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:workflow:instance:list")
    public Result<ProcessInstanceVO> detail(@PathVariable Long id) {
        return Result.ok(processInstanceService.getDetail(id));
    }

    /**
     * 分页查询流程实例。
     *
     * @param request 查询条件：title 模糊、status 等值、definitionCode 等值
     * @return 流程实例分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:workflow:instance:list")
    public Result<PageResult<ProcessInstanceVO>> page(ProcessInstanceQueryRequest request) {
        return Result.ok(processInstanceService.page(request));
    }

    /**
     * 撤销流程实例（仅发起人本人可撤销）。
     *
     * @param id 流程实例ID
     * @param httpRequest 当前请求（用于读取网关 {@code X-Username} 校验发起人）
     * @return 空结果
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:workflow:instance:cancel")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest httpRequest) {
        processInstanceService.cancel(id, WebHeaders.username(httpRequest));
        return Result.ok();
    }
}
