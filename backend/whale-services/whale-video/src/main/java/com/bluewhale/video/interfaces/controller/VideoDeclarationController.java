package com.bluewhale.video.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.video.application.command.DeclarationAcceptCommand;
import com.bluewhale.video.application.service.DeclarationAppService;
import com.bluewhale.video.application.view.DeclarationView;
import com.bluewhale.video.interfaces.support.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 上传声明接口：查询当前版本与签署留痕。
 *
 * <p>上传能力统一要求 {@code sys:video:upload}；未签署当前版本时创建内容被拒绝。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/video/declaration")
@RequiredArgsConstructor
public class VideoDeclarationController {

    /** 客户端标识请求头 */
    private static final String USER_AGENT_HEADER = "User-Agent";

    private final DeclarationAppService declarationAppService;

    /**
     * 查询当前声明版本与本人签署状态。
     *
     * @param request 当前请求（读取网关注入的用户头）
     * @return 声明视图
     */
    @GetMapping("/current")
    @RequirePermission("sys:video:upload")
    public Result<DeclarationView> current(HttpServletRequest request) {
        return Result.ok(declarationAppService.current(UserContext.requireUserId(request)));
    }

    /**
     * 签署当前声明版本（同一版本重复签署幂等）。
     *
     * @param command 签署命令
     * @param request 当前请求（读取用户头、客户端 IP 与 UA）
     * @return 声明视图
     */
    @PostMapping("/accept")
    @RequirePermission("sys:video:upload")
    @AuditLog(operation = "签署内容合法与版权无争议声明", type = OperateType.OTHER,
            targetType = "vd_upload_declaration")
    public Result<DeclarationView> accept(@Valid @RequestBody DeclarationAcceptCommand command,
                                          HttpServletRequest request) {
        return Result.ok(declarationAppService.accept(command, UserContext.requireUserId(request),
                request.getRemoteAddr(), WebHeaders.header(request, USER_AGENT_HEADER)));
    }
}
