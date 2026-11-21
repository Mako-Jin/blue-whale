package com.bluewhale.alert.controller;

import com.bluewhale.alert.dto.AlertAckRequest;
import com.bluewhale.alert.dto.AlertRecordQueryRequest;
import com.bluewhale.alert.service.AlertRecordService;
import com.bluewhale.alert.vo.AlertRecordStatsVO;
import com.bluewhale.alert.vo.AlertRecordVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 告警记录查询与操作接口
 *
 * <p>服务内路径 {@code /alert/v1/record/**}；经网关按 {@code Path=/alert/**} 规则转发，
 * 外部路径与本文档一致。接口返回统一响应体 {@link Result}，受
 * {@code @RequirePermission("sys:alert:record:list")} 保护（由网关权限码头
 * {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/alert/v1/record")
@RequiredArgsConstructor
public class AlertRecordController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "alert_record";

    private final AlertRecordService alertRecordService;

    /**
     * 分页查询告警记录
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:alert:record:list")
    public Result<PageResult<AlertRecordVO>> page(AlertRecordQueryRequest request) {
        return Result.ok(alertRecordService.page(request));
    }

    /**
     * 告警统计聚合（看板）
     *
     * @param start 触发时间起始
     * @param end   触发时间截止
     * @return 统计数据
     */
    @GetMapping("/stats")
    @RequirePermission("sys:alert:record:list")
    public Result<AlertRecordStatsVO> stats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return Result.ok(alertRecordService.stats(start, end));
    }

    /**
     * 认领告警
     *
     * @param id      告警记录ID
     * @param request 认领请求
     * @return 更新后的记录
     */
    @PutMapping("/{id}/ack")
    @RequirePermission("sys:alert:record:edit")
    @AuditLog(operation = "认领告警", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<AlertRecordVO> ack(@PathVariable Long id, @Valid @RequestBody AlertAckRequest request) {
        return Result.ok(alertRecordService.ack(id, request));
    }
}
