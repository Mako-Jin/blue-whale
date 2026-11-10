package com.bluewhale.workflow.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.workflow.dto.DelegateRuleSaveRequest;
import com.bluewhale.workflow.service.ProcessDelegateService;
import com.bluewhale.workflow.vo.DelegateRuleVO;
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
 * 流程代理规则内网接口。
 *
 * <p>服务内路径前缀 {@code /workflow/internal/delegate}；网关 {@code internal-deny} 对
 * {@code /workflow/internal/**} 统一返回 403，外部不可达，故不加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/workflow/internal/delegate")
@RequiredArgsConstructor
public class WorkflowInternalDelegateController {

    private final ProcessDelegateService processDelegateService;

    /**
     * 分页查询代理规则。
     *
     * @param fromAccount 委托人账号（可选）
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<DelegateRuleVO>> page(
            @RequestParam(value = "fromAccount", required = false) String fromAccount,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "10") long size) {
        return Result.ok(processDelegateService.page(fromAccount, current, size));
    }

    /**
     * 新建代理规则。
     *
     * @param request 保存请求
     * @return 新建后的规则
     */
    @PostMapping
    public Result<DelegateRuleVO> create(@Valid @RequestBody DelegateRuleSaveRequest request) {
        return Result.ok(processDelegateService.create(request));
    }

    /**
     * 更新代理规则。
     *
     * @param id 规则ID
     * @param request 保存请求
     * @return 更新后的规则
     */
    @PutMapping("/{id}")
    public Result<DelegateRuleVO> update(@PathVariable Long id,
                                         @Valid @RequestBody DelegateRuleSaveRequest request) {
        return Result.ok(processDelegateService.update(id, request));
    }

    /**
     * 删除代理规则。
     *
     * @param id 规则ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        processDelegateService.delete(id);
        return Result.ok();
    }
}
