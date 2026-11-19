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
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
 * <p>四通道查询与 {@code AuditLogController} 一致均标注 {@code @AuditLog(type=QUERY)}（AUTH-07）：
 * 读日志亦须留痕，调用将产生 QUERY 元审计记录。</p>
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
        applyGatewayTraceId(request, httpRequest);
        frontendLogService.saveBatch(request, resolveUserId(httpRequest));
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
        applyGatewayTraceId(request, httpRequest);
        behaviorLogService.saveBatch(request, resolveUserId(httpRequest));
        return Result.ok();
    }

    /** 前端开发者日志分页查询（需审计日志查询权限；本方法调用将产生 QUERY 元审计） */
    @GetMapping("/frontend/page")
    @RequirePermission("sys:audit:log:list")
    @AuditLog(operation = "分页查询前端日志", type = OperateType.QUERY,
            targetType = "frontend_log", recordResult = false)
    public Result<PageResult<FrontendLogVO>> frontendPage(FrontendLogQueryRequest request) {
        return Result.ok(frontendLogService.page(request));
    }

    /** 前端用户行为埋点分页查询（需审计日志查询权限；本方法调用将产生 QUERY 元审计） */
    @GetMapping("/behavior/page")
    @RequirePermission("sys:audit:log:list")
    @AuditLog(operation = "分页查询行为埋点日志", type = OperateType.QUERY,
            targetType = "behavior_log", recordResult = false)
    public Result<PageResult<BehaviorLogVO>> behaviorPage(BehaviorLogQueryRequest request) {
        return Result.ok(behaviorLogService.page(request));
    }

    /** 后端接口日志分页查询（需审计日志查询权限；本方法调用将产生 QUERY 元审计） */
    @GetMapping("/api/page")
    @RequirePermission("sys:audit:log:list")
    @AuditLog(operation = "分页查询接口日志", type = OperateType.QUERY,
            targetType = "api_log", recordResult = false)
    public Result<PageResult<ApiLogVO>> apiPage(ApiLogQueryRequest request) {
        return Result.ok(apiLogService.page(request));
    }

    /** 后端出站三方调用日志分页查询（需审计日志查询权限；本方法调用将产生 QUERY 元审计） */
    @GetMapping("/outbound/page")
    @RequirePermission("sys:audit:log:list")
    @AuditLog(operation = "分页查询出站调用日志", type = OperateType.QUERY,
            targetType = "outbound_log", recordResult = false)
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
     * 以网关注入的 {@code X-Trace-Id} 覆盖上报体中的 traceId。
     *
     * <p>上报体来自浏览器、可被伪造，若直接采信可把恶意操作挂到他人链路下污染审计轨迹；
     * 仅当头缺失时才回退使用请求体值（兼容未经网关的本地直连调试）。</p>
     *
     * @param request 批量上报请求体（就地覆盖 traceId）
     * @param httpRequest 当前请求
     */
    private static void applyGatewayTraceId(LogBatchRequest request, HttpServletRequest httpRequest) {
        String gatewayTraceId = WebHeaders.traceId(httpRequest);
        if (StringUtils.hasText(gatewayTraceId)) {
            request.setTraceId(gatewayTraceId);
        }
    }

    /**
     * 解析生效用户ID：只信任网关 {@code X-User-Id} 头。
     *
     * <p>上报体中的 {@code userId} 来自浏览器、可被任意伪造，仅作兼容字段保留，服务端一律忽略：
     * 头缺失或不可解析时落库为 {@code null}（匿名上报），绝不把请求体声明当作归属。</p>
     *
     * @param request 当前请求
     * @return 网关注入的用户ID；头缺失或不可解析时返回 {@code null}
     */
    private static Long resolveUserId(HttpServletRequest request) {
        String headerUserId = WebHeaders.userId(request);
        if (headerUserId == null) {
            return null;
        }
        try {
            return Long.valueOf(headerUserId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
