package com.bluewhale.workflow.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.workflow.dto.DefinitionEnsureRequest;
import com.bluewhale.workflow.service.ProcessDefinitionService;
import com.bluewhale.workflow.vo.ProcessDefinitionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程定义内网接口：按编码幂等 ensure。
 *
 * <p>服务内路径前缀 {@code /workflow/internal/definition}；网关 {@code internal-deny} 对
 * {@code /workflow/internal/**} 统一返回 403，外部不可达，故不加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/workflow/internal/definition")
@RequiredArgsConstructor
public class WorkflowInternalDefinitionController {

    private final ProcessDefinitionService processDefinitionService;

    /**
     * 按流程编码幂等 ensure（重复调用不重复建定义）。
     *
     * @param request ensure 请求
     * @return 已存在或新建的流程定义
     */
    @PostMapping("/ensure")
    public Result<ProcessDefinitionVO> ensure(@Valid @RequestBody DefinitionEnsureRequest request) {
        return Result.ok(processDefinitionService.ensure(request));
    }
}
