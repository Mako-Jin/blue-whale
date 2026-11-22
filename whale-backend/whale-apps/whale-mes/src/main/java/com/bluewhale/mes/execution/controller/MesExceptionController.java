package com.bluewhale.mes.execution.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.execution.dto.MesExceptionActionRequest;
import com.bluewhale.mes.execution.dto.MesExceptionCreateRequest;
import com.bluewhale.mes.execution.service.MesExecutionService;
import com.bluewhale.mes.execution.vo.MesExceptionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 现场异常接口
 *
 * <p>异常上报与处理动作（确认/解决/关闭），所有操作留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/execution/exception", version = "1")
@RequiredArgsConstructor
public class MesExceptionController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_exception";

    private final MesExecutionService executionService;

    /**
     * 上报异常
     *
     * @param request 异常请求
     * @return 异常视图
     */
    @PostMapping
    @RequirePermission("sys:mes:execution:exception")
    @AuditLog(operation = "MES 异常上报", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesExceptionVO> raise(@Valid @RequestBody MesExceptionCreateRequest request) {
        return Result.ok(executionService.raiseException(request));
    }

    /**
     * 处理异常
     *
     * @param id      异常 ID
     * @param request 处理请求
     * @return 异常视图
     */
    @PostMapping("/{id}/action")
    @RequirePermission("sys:mes:execution:exception")
    @AuditLog(operation = "MES 异常处理", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesExceptionVO> handle(@PathVariable Long id,
                                         @Valid @RequestBody MesExceptionActionRequest request) {
        return Result.ok(executionService.handleException(id, request));
    }
}
