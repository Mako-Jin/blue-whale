package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.twin.dto.TwinAlertVO;
import com.bluewhale.twin.service.TwinAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 孪生阈值告警接口（Wave 6.06 M1）：场景告警历史与未恢复列表。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/twin/scenes")
@RequiredArgsConstructor
public class TwinAlertController {

    private final TwinAlertService alertService;

    /**
     * 场景告警历史（按触发时间倒序）。
     *
     * @param sceneId  场景ID
     * @param level    级别过滤（normal/warn/critical，可空 = 全部）
     * @param resolved 恢复状态过滤（可空 = 全部）
     * @param limit    返回条数上限（默认 50，最大 200）
     * @return 告警列表
     */
    @GetMapping("/{sceneId}/alerts")
    @RequirePermission("sys:twin:scene")
    public Result<List<TwinAlertVO>> list(@PathVariable("sceneId") Long sceneId,
                                          @RequestParam(value = "level", required = false) String level,
                                          @RequestParam(value = "resolved", required = false) Boolean resolved,
                                          @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(alertService.list(sceneId, level, resolved, limit));
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
}
