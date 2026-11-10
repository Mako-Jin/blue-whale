package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.twin.dto.TwinAlertDispatchVO;
import com.bluewhale.twin.dto.TwinAlertNotifySettingsRequest;
import com.bluewhale.twin.dto.TwinAlertNotifySettingsVO;
import com.bluewhale.twin.dto.TwinAlertVO;
import com.bluewhale.twin.service.TwinAlertDispatchService;
import com.bluewhale.twin.service.TwinAlertNotifySettingsService;
import com.bluewhale.twin.service.TwinAlertService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 孪生阈值告警接口（Wave 6.06 M1 + 6.07 M1 + 6.10 M2）：场景告警历史、
 * 未恢复列表、人工确认、告警通知设置与投递记录。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/twin/scenes")
@RequiredArgsConstructor
public class TwinAlertController {

    private final TwinAlertService alertService;

    private final TwinAlertNotifySettingsService notifySettingsService;

    private final TwinAlertDispatchService dispatchService;

    /**
     * 场景告警历史（按触发时间倒序）。
     *
     * @param sceneId  场景ID
     * @param level    级别过滤（normal/warn/critical，可空 = 全部）
     * @param resolved 恢复状态过滤（可空 = 全部）
     * @param acked    确认状态过滤（可空 = 全部）
     * @param limit    返回条数上限（默认 50，最大 200）
     * @return 告警列表
     */
    @GetMapping("/{sceneId}/alerts")
    @RequirePermission("sys:twin:scene")
    public Result<List<TwinAlertVO>> list(@PathVariable("sceneId") Long sceneId,
                                          @RequestParam(value = "level", required = false) String level,
                                          @RequestParam(value = "resolved", required = false) Boolean resolved,
                                          @RequestParam(value = "acked", required = false) Boolean acked,
                                          @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(alertService.list(sceneId, level, resolved, acked, limit));
    }

    /**
     * 场景未恢复告警列表（前端角标数据源）。
     *
     * @param sceneId 场景ID
     * @return 告警列表
     */
    @GetMapping("/{sceneId}/alerts/unresolved")
    @RequirePermission("sys:twin:scene")
    public Result<List<TwinAlertVO>> unresolved(@PathVariable("sceneId") Long sceneId) {
        return Result.ok(alertService.listUnresolved(sceneId));
    }

    /**
     * 确认告警（人工知悉；与恢复语义独立）。
     *
     * @param sceneId     场景ID
     * @param alertId     告警ID
     * @param httpRequest 当前请求（取确认人）
     * @return 更新后的告警视图
     */
    @PostMapping("/{sceneId}/alerts/{alertId}/ack")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "确认孪生告警", type = OperateType.UPDATE, targetType = "twin_alert")
    public Result<TwinAlertVO> ack(@PathVariable("sceneId") Long sceneId,
                                   @PathVariable("alertId") Long alertId,
                                   HttpServletRequest httpRequest) {
        return Result.ok(alertService.ack(sceneId, alertId, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 场景告警通知设置（Wave 6.10 M2）：原始配置 + 叠加全局后的生效值。
     *
     * @param sceneId 场景ID
     * @return 设置视图
     */
    @GetMapping("/{sceneId}/alert-notify")
    @RequirePermission("sys:twin:scene")
    public Result<TwinAlertNotifySettingsVO> getNotifySettings(@PathVariable("sceneId") Long sceneId) {
        return Result.ok(notifySettingsService.get(sceneId));
    }

    /**
     * 保存场景告警通知设置（Wave 6.10 M2）。
     *
     * <p>整体覆盖语义：字段传 {@code null} 即清除该项、回到跟随全局。
     * 场景只能收紧（关通道 / 抬高门槛），不能突破全局配置。</p>
     *
     * @param sceneId 场景ID
     * @param request 设置请求
     * @return 保存后的设置视图
     */
    @PutMapping("/{sceneId}/alert-notify")
    @RequirePermission("sys:twin:scene")
    @AuditLog(operation = "修改孪生告警通知设置", type = OperateType.UPDATE,
            targetType = "twin_scene")
    public Result<TwinAlertNotifySettingsVO> saveNotifySettings(
            @PathVariable("sceneId") Long sceneId,
            @Valid @RequestBody TwinAlertNotifySettingsRequest request) {
        return Result.ok(notifySettingsService.save(sceneId, request));
    }

    /**
     * 场景告警投递记录（Wave 6.10 M2）：按时间倒序，可按结果/通道过滤。
     *
     * @param sceneId 场景ID
     * @param status  结果过滤（SUCCESS / FAILED / SKIPPED，可空）
     * @param channel 通道过滤（WEBHOOK / INAPP，可空）
     * @param limit   条数上限（默认 50，最大 200）
     * @return 投递记录列表
     */
    @GetMapping("/{sceneId}/alert-dispatches")
    @RequirePermission("sys:twin:scene")
    public Result<List<TwinAlertDispatchVO>> listDispatches(
            @PathVariable("sceneId") Long sceneId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(dispatchService.list(sceneId, status, channel, limit));
    }
}
