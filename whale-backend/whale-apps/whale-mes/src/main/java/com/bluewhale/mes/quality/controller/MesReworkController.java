package com.bluewhale.mes.quality.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.quality.dto.MesReworkActionRequest;
import com.bluewhale.mes.quality.dto.MesReworkQueryRequest;
import com.bluewhale.mes.quality.service.MesReworkService;
import com.bluewhale.mes.quality.vo.MesReworkOrderVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 返工单接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/quality/rework", version = "1")
@RequiredArgsConstructor
public class MesReworkController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_quality";

    private final MesReworkService reworkService;

    /**
     * 分页查询返工单
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:mes:quality:list")
    public Result<PageResult<MesReworkOrderVO>> page(MesReworkQueryRequest request) {
        return Result.ok(reworkService.page(request));
    }

    /**
     * 返工开工
     *
     * @param id      返工单 ID
     * @param request 动作请求
     * @return 返工单视图
     */
    @PostMapping("/{id}/start")
    @RequirePermission("sys:mes:quality:rework")
    @AuditLog(operation = "MES 返工开工", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesReworkOrderVO> start(@PathVariable Long id,
                                          @Valid @RequestBody(required = false) MesReworkActionRequest request) {
        return Result.ok(reworkService.start(id, request));
    }

    /**
     * 返工完工
     *
     * @param id      返工单 ID
     * @param request 动作请求
     * @return 返工单视图
     */
    @PostMapping("/{id}/complete")
    @RequirePermission("sys:mes:quality:rework")
    @AuditLog(operation = "MES 返工完工", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesReworkOrderVO> complete(@PathVariable Long id,
                                             @Valid @RequestBody(required = false) MesReworkActionRequest request) {
        return Result.ok(reworkService.complete(id, request));
    }
}
