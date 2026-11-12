package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.HotItemVO;
import com.bluewhale.forum.service.HotService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 热榜接口：只读快照 + 手工刷新。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/forum/hot")
@RequiredArgsConstructor
public class HotController {

    private final HotService hotService;

    /**
     * 热榜（读快照；快照为空时实时头部兜底）。
     *
     * @param limit 条数（为空取配置上限）
     * @param request 当前请求
     * @return 热榜列表
     */
    @GetMapping
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<List<HotItemVO>> list(@RequestParam(value = "limit", required = false) Integer limit,
                                       HttpServletRequest request) {
        return Result.ok(hotService.list(limit, ForumActor.from(request)));
    }

    /**
     * 手工刷新热榜快照（运维补跑 / 定时任务未到时）。
     *
     * @return 写入条数
     */
    @PostMapping("/refresh")
    @RequirePermission(ForumPermissions.HOT_REFRESH)
    @AuditLog(operation = "手工刷新论坛热榜", type = OperateType.UPDATE, targetType = "fm_hot_snapshot")
    public Result<Integer> refresh() {
        return Result.ok(hotService.refresh());
    }
}
