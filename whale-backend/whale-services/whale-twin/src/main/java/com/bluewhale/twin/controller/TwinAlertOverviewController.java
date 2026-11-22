package com.bluewhale.twin.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.twin.dto.TwinAlertSummaryVO;
import com.bluewhale.twin.dto.TwinAlertVO;
import com.bluewhale.twin.service.TwinAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 跨场景告警总览接口（Wave 6.08 M1）：平台级未恢复列表与统计。
 *
 * <p>场景维度的历史/确认接口仍在 {@link TwinAlertController}（{@code /twin/scenes/{id}/alerts}）；
 * 本控制器只提供不依赖「先选场景」的总览视图，供场景列表页角标与全局告警面板使用。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/twin/v{version}/alerts", version = "1")
@RequiredArgsConstructor
public class TwinAlertOverviewController {

    private final TwinAlertService alertService;

    /**
     * 跨场景未恢复告警列表（按触发时间倒序，含场景编码/名称）。
     *
     * @param limit 返回条数上限（默认 100，最大 200）
     * @return 告警列表
     */
    @GetMapping("/unresolved")
    @RequirePermission("sys:twin:scene")
    public Result<List<TwinAlertVO>> unresolved(@RequestParam(value = "limit", required = false) Integer limit) {
        return Result.ok(alertService.listUnresolvedAcrossScenes(limit));
    }

    /**
     * 平台级告警统计：未恢复总数 / critical / warn / 未确认数。
     *
     * @return 统计视图
     */
    @GetMapping("/summary")
    @RequirePermission("sys:twin:scene")
    public Result<TwinAlertSummaryVO> summary() {
        return Result.ok(alertService.summary());
    }
}
