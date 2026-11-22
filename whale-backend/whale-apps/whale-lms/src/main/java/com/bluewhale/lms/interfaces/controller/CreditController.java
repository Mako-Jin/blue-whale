package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.credit.application.command.CreditAccrueCommand;
import com.bluewhale.lms.credit.application.service.CreditAppService;
import com.bluewhale.lms.credit.application.view.CreditLedgerView;
import com.bluewhale.lms.credit.application.view.CreditSummaryView;
import com.bluewhale.lms.credit.domain.model.CreditLedgerQuery;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.interfaces.support.PageParams;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学分台账接口（M4-2）。
 *
 * <p>{@code /summary} 与 {@code /mine} 面向学员，用「我的证书与学分」权限码（1948）开放；
 * 查他人汇总时在服务层再校验管理角色，避免一个接口两种口径。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/credit", version = "1")
@RequiredArgsConstructor
public class CreditController {

    private final CreditAppService creditAppService;

    /**
     * 记账（同一业务事件幂等）。
     *
     * @param command 记账命令
     * @param request 当前请求
     * @return 台账 ID
     */
    @PostMapping("/accrue")
    @RequirePermission(LmsPermissions.CREDIT_MANAGE)
    @AuditLog(operation = "学分记账", type = OperateType.CREATE, targetType = "lr_credit_ledger")
    public Result<Long> accrue(@Valid @RequestBody CreditAccrueCommand command,
                               HttpServletRequest request) {
        return Result.ok(creditAppService.accrue(command, actor(request)));
    }

    /**
     * 台账分页（管理端）。
     *
     * @param keyword 关键字
     * @param userId 学员用户 ID
     * @param sourceType 来源类型
     * @param periodYear 归属年度
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(LmsPermissions.CREDIT_MANAGE)
    public Result<PageResult<CreditLedgerView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "periodYear", required = false) Integer periodYear,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CreditLedgerQuery query = new CreditLedgerQuery();
        query.setKeyword(keyword);
        query.setUserId(userId);
        query.setSourceType(sourceType);
        query.setPeriodYear(periodYear);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(creditAppService.page(query, actor(request)));
    }

    /**
     * 年度汇总。
     *
     * @param userId 学员用户 ID（为空取当前登录人）
     * @param periodYear 归属年度（为空表示全部年度）
     * @param request 当前请求
     * @return 汇总
     */
    @GetMapping("/summary")
    @RequirePermission(LmsPermissions.CERT_MINE)
    public Result<CreditSummaryView> summary(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "periodYear", required = false) Integer periodYear,
            HttpServletRequest request) {
        return Result.ok(creditAppService.summary(userId, periodYear, actor(request)));
    }

    /**
     * 我的学分台账。
     *
     * @param sourceType 来源类型
     * @param periodYear 归属年度
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/mine")
    @RequirePermission(LmsPermissions.CERT_MINE)
    public Result<PageResult<CreditLedgerView>> mine(
            @RequestParam(value = "sourceType", required = false) String sourceType,
            @RequestParam(value = "periodYear", required = false) Integer periodYear,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        CreditLedgerQuery query = new CreditLedgerQuery();
        query.setSourceType(sourceType);
        query.setPeriodYear(periodYear);
        query.setCurrent(PageParams.current(current, pageNum));
        query.setSize(PageParams.size(size, pageSize));
        return Result.ok(creditAppService.mine(query, actor(request)));
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private LmsActor actor(HttpServletRequest request) {
        return LmsActorContext.from(request);
    }
}
