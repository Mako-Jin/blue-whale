package com.bluewhale.mes.dispatch.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.dispatch.dto.MesDispatchRequest;
import com.bluewhale.mes.dispatch.dto.MesTaskQueryRequest;
import com.bluewhale.mes.dispatch.dto.MesTaskSplitRequest;
import com.bluewhale.mes.dispatch.service.MesDispatchService;
import com.bluewhale.mes.dispatch.vo.MesBoardVO;
import com.bluewhale.mes.dispatch.vo.MesWorkOrderTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MES 调度派工接口
 *
 * <p>覆盖工单拆分工序任务、批量派工/撤回、任务分页与生产看板；所有接口受
 * {@code sys:mes:dispatch:*} / {@code sys:mes:board:view} 权限保护，写操作留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/dispatch", version = "1")
@RequiredArgsConstructor
public class MesDispatchController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_dispatch";

    private final MesDispatchService dispatchService;

    /**
     * 分页查询工序任务
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/task/page")
    @RequirePermission("sys:mes:dispatch:list")
    public Result<PageResult<MesWorkOrderTaskVO>> pageTasks(MesTaskQueryRequest request) {
        return Result.ok(dispatchService.pageTasks(request));
    }

    /**
     * 拆分工单为工序任务
     *
     * @param id      工单 ID
     * @param request 拆分请求
     * @return 拆分后的任务列表
     */
    @PostMapping("/work-order/{id}/split")
    @RequirePermission("sys:mes:dispatch:split")
    @AuditLog(operation = "MES 工单拆分", type = OperateType.CREATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<List<MesWorkOrderTaskVO>> split(@PathVariable Long id,
                                                  @Valid @RequestBody MesTaskSplitRequest request) {
        return Result.ok(dispatchService.split(id, request));
    }

    /**
     * 批量派工
     *
     * @param request 派工请求
     * @return 派工单号
     */
    @PostMapping("/dispatch")
    @RequirePermission("sys:mes:dispatch:add")
    @AuditLog(operation = "MES 批量派工", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<String> dispatch(@Valid @RequestBody MesDispatchRequest request) {
        return Result.ok(dispatchService.dispatch(request));
    }

    /**
     * 撤回派工单
     *
     * @param id 派工单 ID
     * @return 空响应体
     */
    @PostMapping("/dispatch/{id}/withdraw")
    @RequirePermission("sys:mes:dispatch:withdraw")
    @AuditLog(operation = "MES 撤回派工", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> withdraw(@PathVariable Long id) {
        dispatchService.withdraw(id);
        return Result.ok();
    }

    /**
     * 生产看板
     *
     * @param lineId 产线 ID
     * @param teamId 班组 ID
     * @return 看板数据
     */
    @GetMapping("/board")
    @RequirePermission("sys:mes:board:view")
    public Result<MesBoardVO> board(@RequestParam(required = false) Long lineId,
                                    @RequestParam(required = false) Long teamId) {
        return Result.ok(dispatchService.board(lineId, teamId));
    }
}
