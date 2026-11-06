package com.bluewhale.mail.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.mail.dto.MailProvisionRequest;
import com.bluewhale.mail.service.MailProvisionService;
import com.bluewhale.mail.vo.MailProvisionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台代开邮箱内网接口。
 *
 * <p>只供 {@code whale-auth} 建用户后置动作与运维补开使用。网关对
 * {@code /mail/internal/**} 一律 403，外部无法直接调用；服务间走内网直连。</p>
 *
 * <p>刻意<b>不加</b> {@code @RequirePermission}：调用方是服务不是用户，没有用户权限码；
 * 访问控制由网络边界（内网 + 网关拒绝）承担。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mail/internal")
@RequiredArgsConstructor
public class MailInternalController {

    private final MailProvisionService provisionService;

    /**
     * 为用户开通邮箱（幂等：同一用户重复调用返回 EXISTS）
     *
     * @param request 开通请求
     * @return 开通结果；DISABLED / CONFLICT / FAILED 通过 {@code state} 表达，不抛异常
     */
    @PostMapping("/provision")
    public Result<MailProvisionVO> provision(@Valid @RequestBody MailProvisionRequest request) {
        return Result.ok(provisionService.provision(request));
    }
}
