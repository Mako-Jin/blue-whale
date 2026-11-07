package com.bluewhale.oa.workbench.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.workbench.application.service.WorkbenchAppService;
import com.bluewhale.oa.workbench.application.view.WorkbenchViews;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * OA 工作台接口。
 *
 * <p>服务内路径 {@code /oa/workbench/**}，受 {@code sys:oa:workbench:view} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchAppService workbenchAppService;

    /**
     * 汇总工作台数据。
     *
     * @param request 请求上下文
     * @return 汇总视图
     */
    @GetMapping("/summary")
    @RequirePermission("sys:oa:workbench:view")
    public Result<WorkbenchViews.SummaryView> summary(HttpServletRequest request) {
        return Result.ok(workbenchAppService.summary(
                UserContext.requireUserId(request), WebHeaders.username(request), LocalDate.now()));
    }
}
