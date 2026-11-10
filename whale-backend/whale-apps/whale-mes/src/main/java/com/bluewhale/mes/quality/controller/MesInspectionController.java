package com.bluewhale.mes.quality.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.quality.dto.MesInspectionJudgeRequest;
import com.bluewhale.mes.quality.dto.MesInspectionResultSubmitRequest;
import com.bluewhale.mes.quality.dto.MesInspectionTaskCreateRequest;
import com.bluewhale.mes.quality.dto.MesInspectionTaskQueryRequest;
import com.bluewhale.mes.quality.service.MesInspectionService;
import com.bluewhale.mes.quality.vo.MesFirstArticleGateVO;
import com.bluewhale.mes.quality.vo.MesInspectionTaskDetailVO;
import com.bluewhale.mes.quality.vo.MesInspectionTaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 首件与 IPQC 检验接口
 *
 * <p>首件门禁查询 {@code /first-article/gate} 供派工/执行侧在批量释放前校验最近一次首件结果。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/quality/inspection")
@RequiredArgsConstructor
public class MesInspectionController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_quality";

    private final MesInspectionService inspectionService;

    /**
     * 创建首件 / IPQC 检验任务
     *
     * @param request 任务请求
     * @return 检验任务视图
     */
    @PostMapping("/task")
    @RequirePermission("sys:mes:quality:inspection")
    @AuditLog(operation = "MES 检验任务创建", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesInspectionTaskVO> createTask(@Valid @RequestBody MesInspectionTaskCreateRequest request) {
        return Result.ok(inspectionService.createTask(request));
    }

    /**
     * 分页查询检验任务
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/task/page")
    @RequirePermission("sys:mes:quality:list")
    public Result<PageResult<MesInspectionTaskVO>> pageTasks(MesInspectionTaskQueryRequest request) {
        return Result.ok(inspectionService.pageTasks(request));
    }

    /**
     * 查询检验任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @GetMapping("/task/{id}")
    @RequirePermission("sys:mes:quality:list")
    public Result<MesInspectionTaskDetailVO> detail(@PathVariable Long id) {
        return Result.ok(inspectionService.getTask(id));
    }

    /**
     * 录入检验结果
     *
     * @param id      任务 ID
     * @param request 结果请求
     * @return 任务详情
     */
    @PostMapping("/task/{id}/results")
    @RequirePermission("sys:mes:quality:inspection")
    @AuditLog(operation = "MES 检验结果录入", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesInspectionTaskDetailVO> submitResults(@PathVariable Long id,
                                                          @Valid @RequestBody
                                                          MesInspectionResultSubmitRequest request) {
        return Result.ok(inspectionService.submitResults(id, request));
    }

    /**
     * 判定检验任务（不合格自动生成 NCR）
     *
     * @param id      任务 ID
     * @param request 判定请求
     * @return 检验任务视图
     */
    @PostMapping("/task/{id}/judge")
    @RequirePermission("sys:mes:quality:inspection")
    @AuditLog(operation = "MES 检验判定", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesInspectionTaskVO> judge(@PathVariable Long id,
                                             @Valid @RequestBody MesInspectionJudgeRequest request) {
        return Result.ok(inspectionService.judge(id, request));
    }

    /**
     * 首件门禁查询
     *
     * @param workOrderId   工单 ID
     * @param operationCode 工序编码（空则不限制）
     * @return 门禁结果
     */
    @GetMapping("/first-article/gate")
    @RequirePermission("sys:mes:quality:list")
    public Result<MesFirstArticleGateVO> firstArticleGate(
            @RequestParam(required = false) Long workOrderId,
            @RequestParam(required = false) String operationCode) {
        return Result.ok(inspectionService.firstArticleGate(workOrderId, operationCode));
    }
}
