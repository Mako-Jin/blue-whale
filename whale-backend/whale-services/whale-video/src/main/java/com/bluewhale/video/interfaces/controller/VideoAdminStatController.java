package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.video.application.service.AdminStatAppService;
import com.bluewhale.video.application.view.StatOverviewView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 视频运营统计接口：内容 / 播放 / 审核 / 授权到期的总览数据。
 *
 * <p>授权到期默认按 7 天提醒窗口统计；统计口径与运营页面展示一致。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/video/v{version}/admin/stat", version = "1")
@RequiredArgsConstructor
public class VideoAdminStatController {

    /** 授权到期默认提醒窗口（天） */
    private static final int DEFAULT_EXPIRING_DAYS = 7;

    private final AdminStatAppService adminStatAppService;

    /**
     * 统计概览。
     *
     * @param expiringDays 授权到期提醒窗口（天），可选，默认 7 天
     * @return 统计概览
     */
    @GetMapping("/overview")
    @RequirePermission("sys:video:stat:view")
    public Result<StatOverviewView> overview(
            @RequestParam(value = "expiringDays", required = false) Integer expiringDays) {
        int days = expiringDays == null ? DEFAULT_EXPIRING_DAYS : Math.max(expiringDays, 1);
        return Result.ok(adminStatAppService.overview(days));
    }
}
