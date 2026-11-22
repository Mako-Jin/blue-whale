package com.bluewhale.contract.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.dto.SignExecuteRequest;
import com.bluewhale.contract.dto.SignRefuseRequest;
import com.bluewhale.contract.dto.SignStartRequest;
import com.bluewhale.contract.service.SignService;
import com.bluewhale.contract.vo.EvidenceVO;
import com.bluewhale.contract.vo.SignResultVO;
import com.bluewhale.contract.vo.SignTaskVO;
import com.bluewhale.contract.vo.VerifyResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合同签署、证据链与验签接口。
 *
 * <p>服务内路径 {@code /contract/**}；签署、拒签、验签与作废均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/contract/v{version}", version = "1")
@RequiredArgsConstructor
public class ContractSignController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "ct_contract";

    private final SignService signService;

    /**
     * 发起签署。
     *
     * @param id          合同ID
     * @param request     发起请求（可空，按相对方生成）
     * @param httpRequest 当前请求（解析操作人）
     * @return 签署任务列表
     */
    @PostMapping("/{id}/sign/start")
    @RequirePermission("sys:contract:sign:start")
    @AuditLog(operation = "发起合同签署", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<List<SignTaskVO>> start(@PathVariable Long id,
                                          @RequestBody(required = false) SignStartRequest request,
                                          HttpServletRequest httpRequest) {
        return Result.ok(signService.start(id, request, operator(httpRequest)));
    }

    /**
     * 查询合同签署任务。
     *
     * @param id 合同ID
     * @return 任务列表
     */
    @GetMapping("/{id}/sign/tasks")
    @RequirePermission("sys:contract:list")
    public Result<List<SignTaskVO>> tasks(@PathVariable Long id) {
        return Result.ok(signService.tasks(id));
    }

    /**
     * 当前用户待签署任务。
     *
     * @param httpRequest 当前请求（解析操作人）
     * @return 待签任务列表
     */
    @GetMapping("/sign/pending")
    @RequirePermission("sys:contract:sign:execute")
    public Result<List<SignTaskVO>> pending(HttpServletRequest httpRequest) {
        return Result.ok(signService.pending(operator(httpRequest)));
    }

    /**
     * 本地签署。
     *
     * @param id          签署任务ID
     * @param request     签署参数
     * @param httpRequest 当前请求（解析操作人与客户端IP）
     * @return 签署结果
     */
    @PostMapping("/sign-task/{id}/sign")
    @RequirePermission("sys:contract:sign:execute")
    @AuditLog(operation = "合同本地签署", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<SignResultVO> sign(@PathVariable Long id, @Valid @RequestBody SignExecuteRequest request,
                                     HttpServletRequest httpRequest) {
        return Result.ok(signService.sign(id, request, operator(httpRequest), clientIp(httpRequest)));
    }

    /**
     * 拒签。
     *
     * @param id          签署任务ID
     * @param request     拒签请求
     * @param httpRequest 当前请求（解析操作人）
     * @return 空结果
     */
    @PostMapping("/sign-task/{id}/refuse")
    @RequirePermission("sys:contract:sign:execute")
    @AuditLog(operation = "合同签署拒签", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> refuse(@PathVariable Long id, @Valid @RequestBody SignRefuseRequest request,
                               HttpServletRequest httpRequest) {
        signService.refuse(id, request, operator(httpRequest));
        return Result.ok();
    }

    /**
     * 查询证据链。
     *
     * @param id 合同ID
     * @return 证据链视图
     */
    @GetMapping("/{id}/evidence")
    @RequirePermission("sys:contract:evidence:read")
    public Result<EvidenceVO> evidence(@PathVariable Long id) {
        return Result.ok(signService.evidence(id));
    }

    /**
     * 验签。
     *
     * @param id 合同ID
     * @return 验签结果
     */
    @PostMapping("/{id}/verify")
    @RequirePermission("sys:contract:evidence:read")
    @AuditLog(operation = "合同验签", type = OperateType.QUERY, targetType = TARGET_TYPE, targetId = "#id")
    public Result<VerifyResultVO> verify(@PathVariable Long id) {
        return Result.ok(signService.verify(id));
    }

    /**
     * 作废合同（保留签署证据）。
     *
     * @param id 合同ID
     * @return 空结果
     */
    @PostMapping("/{id}/void")
    @RequirePermission("sys:contract:sign:void")
    @AuditLog(operation = "作废合同", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> voidContract(@PathVariable Long id) {
        signService.voidContract(id);
        return Result.ok();
    }

    /**
     * 解析当前操作人。
     *
     * @param request 当前请求
     * @return 操作人上下文
     */
    private OperatorContext operator(HttpServletRequest request) {
        return new OperatorContext(WebHeaders.userId(request), WebHeaders.username(request),
                request.getHeader(WebHeaders.PERMISSION_CODES), request.getHeader(WebHeaders.USER_ROLES));
    }

    /**
     * 解析客户端IP（优先 X-Forwarded-For 首段）。
     *
     * @param request 当前请求
     * @return 客户端IP
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
