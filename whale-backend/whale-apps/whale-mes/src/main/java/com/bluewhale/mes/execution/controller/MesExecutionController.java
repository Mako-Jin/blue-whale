package com.bluewhale.mes.execution.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.dispatch.vo.MesWorkOrderTaskVO;
import com.bluewhale.mes.execution.dto.MesMaterialIssueRequest;
import com.bluewhale.mes.execution.dto.MesMaterialReturnRequest;
import com.bluewhale.mes.execution.dto.MesReportQueryRequest;
import com.bluewhale.mes.execution.dto.MesReportRequest;
import com.bluewhale.mes.execution.dto.MesTaskActionRequest;
import com.bluewhale.mes.execution.service.MesExecutionService;
import com.bluewhale.mes.execution.vo.MesProductionReportVO;
import com.bluewhale.mes.execution.vo.MesWipVO;
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
 * MES 生产执行接口
 *
 * <p>覆盖任务开工/暂停/恢复/完工/报废、扫码报工、在制品查询与领退料登记；
 * 现场异常上报与处理见 {@code MesExceptionController}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/execution", version = "1")
@RequiredArgsConstructor
public class MesExecutionController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_execution";

    private final MesExecutionService executionService;

    /**
     * 开工
     *
     * @param id      任务 ID
     * @param request 动作请求
     * @return 任务视图
     */
    @PostMapping("/task/{id}/start")
    @RequirePermission("sys:mes:execution:action")
    @AuditLog(operation = "MES 任务开工", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesWorkOrderTaskVO> start(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) MesTaskActionRequest request) {
        return Result.ok(executionService.start(id, request == null ? new MesTaskActionRequest() : request));
    }

    /**
     * 暂停
     *
     * @param id      任务 ID
     * @param request 动作请求
     * @return 任务视图
     */
    @PostMapping("/task/{id}/pause")
    @RequirePermission("sys:mes:execution:action")
    @AuditLog(operation = "MES 任务暂停", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesWorkOrderTaskVO> pause(@PathVariable Long id,
                                            @Valid @RequestBody(required = false) MesTaskActionRequest request) {
        return Result.ok(executionService.pause(id, request == null ? new MesTaskActionRequest() : request));
    }

    /**
     * 恢复
     *
     * @param id      任务 ID
     * @param request 动作请求
     * @return 任务视图
     */
    @PostMapping("/task/{id}/resume")
    @RequirePermission("sys:mes:execution:action")
    @AuditLog(operation = "MES 任务恢复", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesWorkOrderTaskVO> resume(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) MesTaskActionRequest request) {
        return Result.ok(executionService.resume(id, request == null ? new MesTaskActionRequest() : request));
    }

    /**
     * 完工
     *
     * @param id      任务 ID
     * @param request 动作请求
     * @return 任务视图
     */
    @PostMapping("/task/{id}/complete")
    @RequirePermission("sys:mes:execution:action")
    @AuditLog(operation = "MES 任务完工", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesWorkOrderTaskVO> complete(@PathVariable Long id,
                                               @Valid @RequestBody(required = false) MesTaskActionRequest request) {
        return Result.ok(executionService.complete(id, request == null ? new MesTaskActionRequest() : request));
    }

    /**
     * 报废
     *
     * @param id      任务 ID
     * @param request 动作请求
     * @return 任务视图
     */
    @PostMapping("/task/{id}/scrap")
    @RequirePermission("sys:mes:execution:action")
    @AuditLog(operation = "MES 任务报废", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesWorkOrderTaskVO> scrap(@PathVariable Long id,
                                            @Valid @RequestBody MesTaskActionRequest request) {
        return Result.ok(executionService.scrap(id, request));
    }

    /**
     * 扫码报工
     *
     * @param request 报工请求
     * @return 报工记录
     */
    @PostMapping("/report")
    @RequirePermission("sys:mes:execution:report")
    @AuditLog(operation = "MES 生产报工", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesProductionReportVO> report(@Valid @RequestBody MesReportRequest request) {
        return Result.ok(executionService.report(request));
    }

    /**
     * 分页查询报工记录
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/report/page")
    @RequirePermission("sys:mes:execution:list")
    public Result<PageResult<MesProductionReportVO>> pageReports(MesReportQueryRequest request) {
        return Result.ok(executionService.pageReports(request));
    }

    /**
     * 查询在制品快照
     *
     * @param workOrderId 工单 ID
     * @return 在制品列表
     */
    @GetMapping("/wip")
    @RequirePermission("sys:mes:execution:list")
    public Result<List<MesWipVO>> wip(@RequestParam(required = false) Long workOrderId) {
        return Result.ok(executionService.wip(workOrderId));
    }

    /**
     * 登记领料 / 补料
     *
     * @param request 领料请求
     * @return 领料单号
     */
    @PostMapping("/material-issue")
    @RequirePermission("sys:mes:execution:material")
    @AuditLog(operation = "MES 领料登记", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<String> issueMaterial(@Valid @RequestBody MesMaterialIssueRequest request) {
        return Result.ok(executionService.issueMaterial(request));
    }

    /**
     * 登记退料
     *
     * @param request 退料请求
     * @return 退料单号
     */
    @PostMapping("/material-return")
    @RequirePermission("sys:mes:execution:material")
    @AuditLog(operation = "MES 退料登记", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<String> returnMaterial(@Valid @RequestBody MesMaterialReturnRequest request) {
        return Result.ok(executionService.returnMaterial(request));
    }
}
