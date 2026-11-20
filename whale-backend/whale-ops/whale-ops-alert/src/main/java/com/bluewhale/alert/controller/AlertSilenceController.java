package com.bluewhale.alert.controller;

import com.bluewhale.alert.dto.AlertSilenceCreateRequest;
import com.bluewhale.alert.dto.AlertSilenceQueryRequest;
import com.bluewhale.alert.service.AlertSilenceService;
import com.bluewhale.alert.vo.AlertSilenceVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警静默管理接口。
 *
 * <p>服务内路径 {@code /alert/silence/**}；命中静默的告警以 {@code SILENCED} 状态落库且不通知。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/alert/silence")
@RequiredArgsConstructor
public class AlertSilenceController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "alert_silence";

    private final AlertSilenceService alertSilenceService;

    /**
     * 分页查询静默规则
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:alert:silence:list")
    public Result<PageResult<AlertSilenceVO>> page(AlertSilenceQueryRequest request) {
        return Result.ok(alertSilenceService.page(request));
    }

    /**
     * 创建静默规则
     *
     * @param request 创建请求
     * @return 静默视图对象
     */
    @PostMapping
    @RequirePermission("sys:alert:silence:add")
    @AuditLog(operation = "创建告警静默", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<AlertSilenceVO> create(@Valid @RequestBody AlertSilenceCreateRequest request) {
        return Result.ok(alertSilenceService.create(request));
    }

    /**
     * 删除静默规则（逻辑删除）
     *
     * @param id 静默ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:alert:silence:delete")
    @AuditLog(operation = "删除告警静默", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        alertSilenceService.delete(id);
        return Result.ok();
    }
}
