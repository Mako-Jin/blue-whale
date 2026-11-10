package com.bluewhale.pdm.change.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.change.dto.PdmChgNoticeQueryRequest;
import com.bluewhale.pdm.change.dto.PdmChgNoticeSaveRequest;
import com.bluewhale.pdm.change.service.PdmChgNoticeService;
import com.bluewhale.pdm.change.vo.PdmChgNoticeVO;
import com.bluewhale.pdm.change.vo.PdmChgNotifyLogVO;
import com.bluewhale.pdm.part.support.PdmBomRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDM 变更通知接口（ECN）
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/change/notice")
@RequiredArgsConstructor
public class PdmChgNoticeController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "pdm_chg_notice";

    private final PdmChgNoticeService noticeService;

    /**
     * 分页查询变更通知。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:change:notice:list")
    public Result<PageResult<PdmChgNoticeVO>> page(PdmChgNoticeQueryRequest request) {
        return Result.ok(noticeService.page(request));
    }

    /**
     * 变更通知详情。
     *
     * @param id 通知ID
     * @return 通知视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:change:notice:list")
    public Result<PdmChgNoticeVO> detail(@PathVariable Long id) {
        return Result.ok(noticeService.detail(id));
    }

    /**
     * 创建变更通知。
     *
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 通知视图
     */
    @PostMapping
    @RequirePermission("sys:pdm:change:notice:add")
    @AuditLog(operation = "创建变更通知", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<PdmChgNoticeVO> create(@Valid @RequestBody PdmChgNoticeSaveRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.ok(noticeService.create(request, PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 修改草稿变更通知。
     *
     * @param id          通知ID
     * @param request     保存请求
     * @param httpRequest 当前请求
     * @return 通知视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:change:notice:edit")
    @AuditLog(operation = "修改变更通知", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgNoticeVO> update(@PathVariable Long id,
                                         @Valid @RequestBody PdmChgNoticeSaveRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.ok(noticeService.update(id, request,
                PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 删除草稿变更通知。
     *
     * @param id 通知ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:change:notice:edit")
    @AuditLog(operation = "删除变更通知", type = OperateType.DELETE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.ok();
    }

    /**
     * 发送通知。
     *
     * @param id          通知ID
     * @param httpRequest 当前请求
     * @return 通知视图
     */
    @PostMapping("/{id}/notify")
    @RequirePermission("sys:pdm:change:notice:notify")
    @AuditLog(operation = "发送变更通知", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgNoticeVO> notify(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(noticeService.notify(id, PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 生效（旧版本置作废）。
     *
     * @param id          通知ID
     * @param httpRequest 当前请求
     * @return 通知视图
     */
    @PostMapping("/{id}/effective")
    @RequirePermission("sys:pdm:change:notice:effective")
    @AuditLog(operation = "变更通知生效", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgNoticeVO> effective(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(noticeService.effective(id, PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 关闭已生效通知。
     *
     * @param id          通知ID
     * @param httpRequest 当前请求
     * @return 通知视图
     */
    @PostMapping("/{id}/close")
    @RequirePermission("sys:pdm:change:notice:edit")
    @AuditLog(operation = "关闭变更通知", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgNoticeVO> close(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(noticeService.close(id, PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 取消草稿通知。
     *
     * @param id          通知ID
     * @param httpRequest 当前请求
     * @return 通知视图
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission("sys:pdm:change:notice:edit")
    @AuditLog(operation = "取消变更通知", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgNoticeVO> cancel(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(noticeService.cancel(id, PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 重试失败的投递。
     *
     * @param id          通知ID
     * @param httpRequest 当前请求
     * @return 通知视图
     */
    @PostMapping("/{id}/retry-notify")
    @RequirePermission("sys:pdm:change:notice:notify")
    @AuditLog(operation = "重试变更通知", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmChgNoticeVO> retryNotify(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(noticeService.retryNotify(id, PdmBomRequestContext.requireUserId(httpRequest)));
    }

    /**
     * 通知投递日志。
     *
     * @param id      通知ID
     * @param current 当前页
     * @param size    每页条数
     * @return 分页结果
     */
    @GetMapping("/{id}/notify-logs")
    @RequirePermission("sys:pdm:change:notice:log")
    public Result<PageResult<PdmChgNotifyLogVO>> notifyLogs(@PathVariable Long id,
                                                            @RequestParam(defaultValue = "1") Long current,
                                                            @RequestParam(defaultValue = "20") Long size) {
        return Result.ok(noticeService.notifyLogPage(id, current, size));
    }
}
