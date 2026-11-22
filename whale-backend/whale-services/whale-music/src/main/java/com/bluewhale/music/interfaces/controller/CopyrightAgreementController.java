package com.bluewhale.music.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.music.application.command.AgreementAcceptCommand;
import com.bluewhale.music.application.service.CopyrightAgreementAppService;
import com.bluewhale.music.application.view.AgreementView;
import com.bluewhale.music.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版权无争议协议接口：查询当前版本与签署。
 *
 * <p>上传能力统一要求 {@code sys:music:upload}；未签署当前版本时上传被拒绝。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/music/v{version}/copyright/agreement", version = "1")
@RequiredArgsConstructor
public class CopyrightAgreementController {

    /** 客户端标识请求头 */
    private static final String USER_AGENT_HEADER = "User-Agent";

    private final CopyrightAgreementAppService agreementAppService;

    /**
     * 查询当前协议版本与本人签署状态。
     *
     * @param request 当前请求（读取网关注入的用户头）
     * @return 协议状态视图
     */
    @GetMapping("/current")
    @RequirePermission("sys:music:upload")
    public Result<AgreementView> current(HttpServletRequest request) {
        return Result.ok(agreementAppService.current(UserContext.requireUserId(request)));
    }

    /**
     * 签署当前协议版本（同一版本重复签署幂等）。
     *
     * @param command 签署命令
     * @param request 当前请求（读取用户头、客户端 IP 与 UA）
     * @return 协议状态视图
     */
    @PostMapping("/accept")
    @RequirePermission("sys:music:upload")
    @AuditLog(operation = "签署版权无争议协议", type = OperateType.OTHER, targetType = "mu_copyright_agreement")
    public Result<AgreementView> accept(@Valid @RequestBody AgreementAcceptCommand command,
                                        HttpServletRequest request) {
        return Result.ok(agreementAppService.accept(command, UserContext.requireUserId(request),
                request.getRemoteAddr(), WebHeaders.header(request, USER_AGENT_HEADER)));
    }
}
