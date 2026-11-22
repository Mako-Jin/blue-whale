package com.bluewhale.oa.culture.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.service.CultureWishAppService;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 生日 / 入职周年祝福接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/culture", version = "1")
@RequiredArgsConstructor
public class CultureWishController {

    private static final String TARGET_TYPE = "oa_culture_wish";

    private final CultureWishAppService wishAppService;

    /**
     * 今日生日 / 入职周年。
     *
     * @param date 日期，缺省为今天
     * @return 生日 / 周年列表
     */
    @GetMapping("/birthdays")
    @RequirePermission("sys:oa:culture:list")
    public Result<List<CultureViews.Birthday>> birthdays(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(wishAppService.listCelebrations(date == null ? LocalDate.now() : date));
    }

    /**
     * 祝福分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/wishes/page")
    @RequirePermission("sys:oa:culture:list")
    public Result<PageResult<CultureViews.Wish>> page(CultureQuery query) {
        return Result.ok(wishAppService.page(query));
    }

    /**
     * 送祝福（轻社交动作，复用列表权限）。
     *
     * @param command 祝福命令
     * @param request 请求上下文
     * @return 祝福
     */
    @PostMapping("/wishes")
    @RequirePermission("sys:oa:culture:list")
    @AuditLog(operation = "送生日/周年祝福", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CultureViews.Wish> send(@Valid @RequestBody CultureCommands.WishSave command,
                                          HttpServletRequest request) {
        return Result.ok(wishAppService.send(command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }
}
