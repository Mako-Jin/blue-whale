package com.bluewhale.workflow.controller;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.workflow.request.ProcessCcQueryRequest;
import com.bluewhale.workflow.service.ProcessCcService;
import com.bluewhale.workflow.vo.ProcessCcVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流程抄送内网接口。
 *
 * <p>服务内路径前缀 {@code /workflow/v1/internal/cc}；网关 {@code internal-deny} 对
 * {@code /workflow/v1/internal/**} 统一返回 403，外部不可达，故不加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/workflow/v{version}/internal/cc", version = "1")
@RequiredArgsConstructor
public class WorkflowInternalCcController {

    private final ProcessCcService processCcService;

    /**
     * 分页查询抄送记录。
     *
     * @param query 查询条件（实例/收件人/已读标记）
     * @return 抄送分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<ProcessCcVO>> page(ProcessCcQueryRequest query) {
        return Result.ok(processCcService.page(query));
    }

    /**
     * 按主键查询抄送记录。
     *
     * @param id 抄送记录ID
     * @return 抄送记录
     */
    @GetMapping("/{id}")
    public Result<ProcessCcVO> get(@PathVariable Long id) {
        return Result.ok(processCcService.get(id));
    }

    /**
     * 标记抄送已读（幂等）。
     *
     * @param id 抄送记录ID
     * @return 空结果
     */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        processCcService.markRead(id);
        return Result.ok();
    }
}
