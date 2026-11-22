package com.bluewhale.mes.quality.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.quality.dto.MesDispositionCreateRequest;
import com.bluewhale.mes.quality.dto.MesDispositionQueryRequest;
import com.bluewhale.mes.quality.service.MesNcrService;
import com.bluewhale.mes.quality.vo.MesDispositionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 不合格品处置接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/quality/disposition", version = "1")
@RequiredArgsConstructor
public class MesDispositionController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_quality";

    private final MesNcrService ncrService;

    /**
     * 创建处置（数量必须等于 NCR 不良数量）
     *
     * @param request 处置请求
     * @return 处置视图
     */
    @PostMapping
    @RequirePermission("sys:mes:quality:disposition")
    @AuditLog(operation = "MES 不合格品处置创建", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesDispositionVO> create(@Valid @RequestBody MesDispositionCreateRequest request) {
        return Result.ok(ncrService.createDisposition(request));
    }

    /**
     * 执行处置（返工处置自动生成返工单）
     *
     * @param id 处置 ID
     * @return 处置视图
     */
    @PostMapping("/{id}/execute")
    @RequirePermission("sys:mes:quality:disposition")
    @AuditLog(operation = "MES 不合格品处置执行", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesDispositionVO> execute(@PathVariable Long id) {
        return Result.ok(ncrService.executeDisposition(id));
    }

    /**
     * 分页查询处置
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:mes:quality:list")
    public Result<PageResult<MesDispositionVO>> page(MesDispositionQueryRequest request) {
        return Result.ok(ncrService.pageDispositions(request));
    }
}
