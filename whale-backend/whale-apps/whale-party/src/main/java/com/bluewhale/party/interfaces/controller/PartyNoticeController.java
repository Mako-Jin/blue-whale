package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.integration.application.service.NotifyAppService;
import com.bluewhale.party.integration.application.view.NoticeScanResultView;
import com.bluewhale.party.integration.application.view.NotifyLogView;
import com.bluewhale.party.integration.domain.model.NotifyLogQuery;
import com.bluewhale.party.interfaces.support.PageParams;
import com.bluewhale.party.interfaces.support.PartyActorContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知与提醒接口（M-P4）。
 *
 * <p>扫描接口按场景返回「本次实际发出条数」：同一批数据重复扫描应当返回 0，
 * 这是幂等的可观测证据，而不是静默吞掉。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/party/v{version}/notice", version = "1")
@RequiredArgsConstructor
public class PartyNoticeController {

    private final NotifyAppService notifyAppService;

    /**
     * 通知留痕分页。
     *
     * @param query 查询条件
     * @param pageNum 前端风格页码
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.NOTICE_LIST)
    public Result<PageResult<NotifyLogView>> page(NotifyLogQuery query,
                                                  @RequestParam(value = "pageNum", required = false) Long pageNum,
                                                  @RequestParam(value = "pageSize", required = false) Long pageSize,
                                                  HttpServletRequest request) {
        NotifyLogQuery safe = query == null ? new NotifyLogQuery() : query;
        safe.setCurrent(PageParams.current(safe.getCurrent(), pageNum));
        safe.setSize(PageParams.size(safe.getSize(), pageSize));
        return Result.ok(notifyAppService.page(safe, PartyActorContext.from(request)));
    }

    /**
     * 党务提醒扫描（转正 / 党费 / 组织生活 / 换届）。
     *
     * @return 扫描结果
     */
    @PostMapping("/scan")
    @RequirePermission(PartyPermissions.METRIC_MANAGE)
    @AuditLog(operation = "扫描并发送党务提醒", type = OperateType.CREATE, targetType = "pty_notify_log")
    public Result<NoticeScanResultView> scan() {
        return Result.ok(notifyAppService.scanParty());
    }
}
