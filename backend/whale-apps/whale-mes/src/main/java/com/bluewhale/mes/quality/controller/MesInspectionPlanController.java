package com.bluewhale.mes.quality.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.quality.dto.MesInspectionPlanQueryRequest;
import com.bluewhale.mes.quality.dto.MesInspectionPlanSaveRequest;
import com.bluewhale.mes.quality.service.MesInspectionService;
import com.bluewhale.mes.quality.vo.MesInspectionPlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 检验计划接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/quality/inspection-plan")
@RequiredArgsConstructor
public class MesInspectionPlanController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_quality";

    private final MesInspectionService inspectionService;

    /**
     * 保存检验计划（含检验项）
     *
     * @param request 计划请求
     * @return 计划视图
     */
    @PostMapping
    @RequirePermission("sys:mes:quality:plan")
    @AuditLog(operation = "MES 检验计划保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesInspectionPlanVO> save(@Valid @RequestBody MesInspectionPlanSaveRequest request) {
        return Result.ok(inspectionService.savePlan(request));
    }

    /**
     * 分页查询检验计划
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:mes:quality:list")
    public Result<PageResult<MesInspectionPlanVO>> page(MesInspectionPlanQueryRequest request) {
        return Result.ok(inspectionService.pagePlans(request));
    }

    /**
     * 查询检验计划详情
     *
     * @param id 计划 ID
     * @return 计划视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:mes:quality:list")
    public Result<MesInspectionPlanVO> detail(@PathVariable Long id) {
        return Result.ok(inspectionService.getPlan(id));
    }
}
