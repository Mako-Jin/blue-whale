package com.bluewhale.mes.trace.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.trace.dto.MesLineSideMovementQueryRequest;
import com.bluewhale.mes.trace.dto.MesLineSideOperationRequest;
import com.bluewhale.mes.trace.dto.MesLineSideStockQueryRequest;
import com.bluewhale.mes.trace.dto.MesReplenishmentQueryRequest;
import com.bluewhale.mes.trace.dto.MesReplenishmentRequest;
import com.bluewhale.mes.trace.service.MesLineSideService;
import com.bluewhale.mes.trace.vo.MesLineSideMovementVO;
import com.bluewhale.mes.trace.vo.MesLineSideStockVO;
import com.bluewhale.mes.trace.vo.MesReplenishmentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 线边仓接口
 *
 * <p>线边库存查询、入库/退料、领用扣减、流水分页与叫料补货请求。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/trace/line-side", version = "1")
@RequiredArgsConstructor
public class MesLineSideController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_line_side";

    private final MesLineSideService lineSideService;

    /**
     * 分页查询线边库存
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/stock/page")
    @RequirePermission("sys:mes:trace:line-side")
    public Result<PageResult<MesLineSideStockVO>> pageStock(MesLineSideStockQueryRequest request) {
        return Result.ok(lineSideService.pageStock(request));
    }

    /**
     * 线边入库 / 退料退回
     *
     * @param request 入库请求
     * @return 流水号
     */
    @PostMapping("/stock/inbound")
    @RequirePermission("sys:mes:trace:line-side")
    @AuditLog(operation = "MES 线边入库", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<String> inbound(@Valid @RequestBody MesLineSideOperationRequest request) {
        return Result.ok(lineSideService.inbound(request));
    }

    /**
     * 线边领用扣减
     *
     * @param request 领用请求
     * @return 流水号
     */
    @PostMapping("/stock/issue")
    @RequirePermission("sys:mes:trace:line-side")
    @AuditLog(operation = "MES 线边领用", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<String> issue(@Valid @RequestBody MesLineSideOperationRequest request) {
        return Result.ok(lineSideService.issue(request));
    }

    /**
     * 分页查询线边流水
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/movement/page")
    @RequirePermission("sys:mes:trace:line-side")
    public Result<PageResult<MesLineSideMovementVO>> pageMovements(MesLineSideMovementQueryRequest request) {
        return Result.ok(lineSideService.pageMovements(request));
    }

    /**
     * 叫料补货请求（相同请求号幂等）
     *
     * @param request 补货请求
     * @return 补货请求视图
     */
    @PostMapping("/replenish")
    @RequirePermission("sys:mes:trace:replenish")
    @AuditLog(operation = "MES 线边叫料", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesReplenishmentVO> replenish(@Valid @RequestBody MesReplenishmentRequest request) {
        return Result.ok(lineSideService.replenish(request));
    }

    /**
     * 分页查询补货请求
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/replenish/page")
    @RequirePermission("sys:mes:trace:replenish")
    public Result<PageResult<MesReplenishmentVO>> pageReplenishments(MesReplenishmentQueryRequest request) {
        return Result.ok(lineSideService.pageReplenishments(request));
    }
}
