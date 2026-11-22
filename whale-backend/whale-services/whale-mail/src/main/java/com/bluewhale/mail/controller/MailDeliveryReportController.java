package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.service.MailDeliveryReportService;
import com.bluewhale.mail.vo.MailDeliveryReportVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退信回执接口。
 *
 * <p>只提供查询：回执由同步流程在识别到 DSN 时自动写入，没有手工新增的入口——
 * 手工造出来的退信记录没有任何诊断价值。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mail/v{version}/delivery", version = "1")
@RequiredArgsConstructor
public class MailDeliveryReportController {

    private final MailDeliveryReportService deliveryReportService;

    /**
     * 分页查询退信回执
     *
     * @param accountId 账号ID，为空取默认账号
     * @param current 当前页
     * @param size 每页条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/report/page")
    @RequirePermission("sys:mail:message:list")
    public Result<PageResult<MailDeliveryReportVO>> page(@RequestParam(required = false) Long accountId,
                                                         @RequestParam(defaultValue = "1") long current,
                                                         @RequestParam(defaultValue = "20") long size,
                                                         HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        return Result.ok(deliveryReportService.page(accountId, userId, current, size));
    }
}
