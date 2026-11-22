package com.bluewhale.mes.dispatch.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.dispatch.dto.MesScheduleActionRequest;
import com.bluewhale.mes.dispatch.dto.MesScheduleGenerateRequest;
import com.bluewhale.mes.dispatch.dto.MesScheduleQueryRequest;
import com.bluewhale.mes.dispatch.service.MesSchedulingService;
import com.bluewhale.mes.dispatch.vo.MesScheduleGenerateVO;
import com.bluewhale.mes.dispatch.vo.MesSchedulePlanVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 有限产能排程接口。
 *
 * <p>排程生成、锁定与取消需 {@code sys:mes:scheduling:manage}，查询需
 * {@code sys:mes:scheduling:list}；写操作留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/dispatch/schedule", version = "1")
@RequiredArgsConstructor
public class MesSchedulingController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_scheduling";

    private final MesSchedulingService schedulingService;

    /**
     * 生成排程草稿
     *
     * @param request 生成请求
     * @return 生成结果
     */
    @PostMapping("/generate")
    @RequirePermission("sys:mes:scheduling:manage")
    @AuditLog(operation = "MES 有限产能排程生成", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesScheduleGenerateVO> generate(@Valid @RequestBody MesScheduleGenerateRequest request) {
        return Result.ok(schedulingService.generate(request));
    }

    /**
     * 分页查询排程计划
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/plan/page")
    @RequirePermission("sys:mes:scheduling:list")
    public Result<PageResult<MesSchedulePlanVO>> page(MesScheduleQueryRequest request) {
        return Result.ok(schedulingService.page(request));
    }

    /**
     * 查询排程计划详情（含时段明细）
     *
     * @param id 计划 ID
     * @return 计划视图
     */
    @GetMapping("/plan/{id}")
    @RequirePermission("sys:mes:scheduling:list")
    public Result<MesSchedulePlanVO> detail(@PathVariable Long id) {
        return Result.ok(schedulingService.detail(id));
    }

    /**
     * 锁定排程计划
     *
     * @param id      计划 ID
     * @param request 动作请求
     * @return 计划视图
     */
    @PostMapping("/plan/{id}/lock")
    @RequirePermission("sys:mes:scheduling:manage")
    @AuditLog(operation = "MES 排程锁定", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesSchedulePlanVO> lock(@PathVariable Long id,
                                          @RequestBody(required = false) MesScheduleActionRequest request) {
        return Result.ok(schedulingService.lock(id, request));
    }

    /**
     * 取消排程计划
     *
     * @param id      计划 ID
     * @param request 动作请求
     * @return 计划视图
     */
    @PostMapping("/plan/{id}/cancel")
    @RequirePermission("sys:mes:scheduling:manage")
    @AuditLog(operation = "MES 排程取消", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesSchedulePlanVO> cancel(@PathVariable Long id,
                                            @RequestBody(required = false) MesScheduleActionRequest request) {
        return Result.ok(schedulingService.cancel(id, request));
    }
}
