package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.dto.ExpSummaryVO;
import com.bluewhale.forum.service.ExpService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 论坛经验接口（F-07-04）。
 *
 * <p>经验只读：入账由发帖/投花/题金榜/金榜事件自动触发，没有手工加减入口——
 * 手工改账本等于绕过全部防刷规则。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/forum/exp")
@RequiredArgsConstructor
public class ExpController {

    private final ExpService expService;

    /**
     * 我的经验（总额 + 最近流水）。
     *
     * @param request 当前请求
     * @return 汇总
     */
    @GetMapping("/me")
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<ExpSummaryVO> me(HttpServletRequest request) {
        return Result.ok(expService.myExp(ForumActor.from(request)));
    }
}
