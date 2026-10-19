package com.bluewhale.workflow.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.workflow.dto.ProcessDefinitionSaveRequest;
import com.bluewhale.workflow.dto.ProcessQueryRequest;
import com.bluewhale.workflow.service.ProcessDefinitionService;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
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

/**
 * 流程定义管理接口。
 *
 * <p>服务内路径前缀 {@code /workflow}；经网关按 {@code Path=/workflow/**} 规则转发，外部路径与本文档一致。
 * 全部接口受 {@code @RequirePermission("sys:workflow:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final ProcessDefinitionService processDefinitionService;

    /**
     * 新增流程定义。
     *
     * @param request 流程定义请求参数：code / name 必填
     * @return 流程定义视图对象
     */
    @PostMapping("/definition")
    @RequirePermission("sys:workflow:add")
    public Result<ProcessDefinitionVO> create(@Valid @RequestBody ProcessDefinitionSaveRequest request) {
        return Result.ok(processDefinitionService.create(request));
    }

    /**
     * 更新流程定义。
     *
     * @param id 流程定义ID
     * @param request 流程定义请求参数：code / name 必填
     * @return 流程定义视图对象
     */
    @PutMapping("/definition/{id}")
    @RequirePermission("sys:workflow:edit")
    public Result<ProcessDefinitionVO> update(@PathVariable Long id,
                                              @Valid @RequestBody ProcessDefinitionSaveRequest request) {
        return Result.ok(processDefinitionService.update(id, request));
    }

    /**
     * 删除流程定义（逻辑删除）。
     *
     * @param id 流程定义ID
     * @return 空结果
     */
    @DeleteMapping("/definition/{id}")
    @RequirePermission("sys:workflow:delete")
    public Result<Void> delete(@PathVariable Long id) {
        processDefinitionService.delete(id);
        return Result.ok();
    }

    /**
     * 流程定义详情。
     *
     * @param id 流程定义ID
     * @return 流程定义视图对象
     */
    @GetMapping("/definition/{id}")
    @RequirePermission("sys:workflow:list")
    public Result<ProcessDefinitionVO> detail(@PathVariable Long id) {
        return Result.ok(processDefinitionService.getDetail(id));
    }

    /**
     * 分页查询流程定义。
     *
     * @param request 查询条件：name 模糊、code 等值、status 等值
     * @return 流程定义分页结果
     */
    @GetMapping("/definition/page")
    @RequirePermission("sys:workflow:list")
    public Result<PageResult<ProcessDefinitionVO>> page(ProcessQueryRequest request) {
        return Result.ok(processDefinitionService.page(request));
    }
}
