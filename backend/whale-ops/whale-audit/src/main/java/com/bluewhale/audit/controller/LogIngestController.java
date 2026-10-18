package com.bluewhale.audit.controller;

import com.bluewhale.audit.dto.ApiLogQueryRequest;
import com.bluewhale.audit.dto.BehaviorLogQueryRequest;
import com.bluewhale.audit.dto.FrontendLogQueryRequest;
import com.bluewhale.audit.dto.LogBatchRequest;
import com.bluewhale.audit.dto.LogItem;
import com.bluewhale.audit.dto.OutboundLogQueryRequest;
import com.bluewhale.audit.service.ApiLogService;
import com.bluewhale.audit.service.BehaviorLogService;
import com.bluewhale.audit.service.FrontendLogService;
import com.bluewhale.audit.service.OutboundLogService;
import com.bluewhale.audit.vo.ApiLogVO;
import com.bluewhale.audit.vo.BehaviorLogVO;
import com.bluewhale.audit.vo.FrontendLogVO;
import com.bluewhale.audit.vo.OutboundLogVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 多通道日志查询与前端上报接口（REQ-SEC-006）。
 *
 * <p>接收接口 {@code POST /audit/log/frontend/batch} 与 {@code POST /audit/log/behavior/batch}
 * 为 {@code permitAll}（未登录期的报错也需上报），投递落库后立即返回，不等落库完成。</p>
 * <p>查询接口四通道并列，均受 {@code @RequirePermission("sys:audit:log:list")} 保护（AUTH-06 权限码头）：
 * 通道① {@code GET /audit/log/api/page}、通道② {@code GET /audit/log/outbound/page}、
 * 通道③ {@code GET /audit/log/frontend/page}、通道④ {@code GET /audit/log/behavior/page}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/audit/log")
@RequiredArgsConstructor
public class LogIngestController {

    /** 单次上报条目上限（防刷量，超出即拒绝） */
    private static final int MAX_BATCH_SIZE = 50;

    private final FrontendLogService frontendLogService;

    private final BehaviorLogService behaviorLogService;

    private final ApiLogService apiLogService;

    private final OutboundLogService outboundLogService;

    /**
     * 接收前端 console 日志批量上报（permitAll，投递落库后立即返回）
     *
     * @param request 批量上报请求体
     * @param httpRequest 当前请求（用于读取网关权限码头 X-User-Id）
     * @return 统一成功响应（不代表落库已完成）
     */
    @PostMapping("/frontend/batch")
    public Result<Void> frontendBatch(@RequestBody LogBatchRequest request, HttpServletRequest httpRequest) {
        validateItems(request);
        frontendLogService.saveBatch(request, resolveUserId(httpRequest, request.getUserId()));
        return Result.ok();
    }

    /**
     * 接收前端行为埋点批量上报（permitAll，投递落库后立即返回）
     *
     * @param request 批量上报请求体
     * @param httpRequest 当前请求（用于读取网关权限码头 X-User-Id）
     * @return 统一成功响应（不代表落库已完成）
     */
    @PostMapping("/behavior/batch")
    public Result<Void> behaviorBatch(@RequestBody LogBatchRequest request, HttpServletRequest httpRequest) {
        validateItems(request);
        behaviorLogService.saveBatch(request, resolveUserId(httpRequest, request.getUserId()));
        return Result.ok();
    }

    /** 前端开发者日志分页查询（需审计日志查询权限） */
    @GetMapping("/frontend/page")
    @RequirePermission("sys:audit:log:list")
    public Result<PageResult<FrontendLogVO>> frontendPage(FrontendLogQueryRequest request) {
        return Result.ok(frontendLogService.page(request));
    }

    /** 前端用户行为埋点分页查询（需审计日志查询权限） */
    @GetMapping("/behavior/page")
    @RequirePermission("sys:audit:log:list")
    public Result<PageResult<BehaviorLogVO>> behaviorPage(BehaviorLogQueryRequest request) {
        return Result.ok(behaviorLogService.page(request));
    }

    /** 后端接口日志分页查询（需审计日志查询权限） */
    @GetMapping("/api/page")
    @RequirePermission("sys:audit:log:list")
    public Result<PageResult<ApiLogVO>> apiPage(ApiLogQueryRequest request) {
        return Result.ok(apiLogService.page(request));
    }

    /** 后端出站三方调用日志分页查询（需审计日志查询权限） */
    @GetMapping("/outbound/page")
    @RequirePermission("sys:audit:log:list")
    public Result<PageResult<OutboundLogVO>> outboundPage(OutboundLogQueryRequest request) {
        return Result.ok(outboundLogService.page(request));
    }

    /**
     * 校验上报条目非空且不超过上限
     *
     * @param request 批量上报请求体
     * @throws BizException 条目为空或超过 {@link #MAX_BATCH_SIZE} 时抛出 {@code PARAM_ERROR}
     */
    private static void validateItems(LogBatchRequest request) {
        List<LogItem> items = request.getItems();
        if (items == null || items.isEmpty() || items.size() > MAX_BATCH_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 解析生效用户ID：以网关 {@code X-User-Id} 为准，头缺失或不可解析时才回落请求体 {@code userId}
     *
     * @param request 当前请求
     * @param fallbackUserId 请求体上报的用户ID（不可信，仅兜底）
     * @return 生效用户ID；两处均无有效值时返回 {@code null}
     */
    private static Long resolveUserId(HttpServletRequest request, Long fallbackUserId) {
        String headerUserId = WebHeaders.userId(request);
        if (headerUserId == null) {
            return fallbackUserId;
        }
        try {
            return Long.valueOf(headerUserId);
        } catch (NumberFormatException e) {
            return fallbackUserId;
        }
    }
}
