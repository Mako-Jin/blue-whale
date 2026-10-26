package com.bluewhale.workflow.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.workflow.dto.TaskCompleteRequest;
import com.bluewhale.workflow.service.ProcessTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程任务内网完成接口（写）。
 *
 * <p>服务内路径前缀 {@code /workflow/internal/task}，供合同服务在第三方电子签章回调到达后
 * 推进签署节点（Wave 5.61 §5.3 方案 B）；网关 {@code internal-deny} 对 {@code /workflow/internal/**}
 * 统一返回 403，外部不可达，故不加权限码注解。任务已处理时按动作幂等返回。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/workflow/internal/task")
@RequiredArgsConstructor
public class WorkflowInternalTaskController {

    private final ProcessTaskService processTaskService;

    /**
     * 内网完成任务（幂等）。
     *
     * @param id      任务ID
     * @param request 完成请求（操作人/动作/意见/变量/来源）
     * @return 空结果
     */
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @RequestBody TaskCompleteRequest request) {
        processTaskService.completeInternal(id, request);
        return Result.ok();
    }
}
