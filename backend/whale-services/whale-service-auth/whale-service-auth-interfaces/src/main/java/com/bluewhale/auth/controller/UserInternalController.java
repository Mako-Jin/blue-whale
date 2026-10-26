package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.user.UserDirectoryAppService;
import com.bluewhale.auth.dto.UserExistsRequest;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户目录内网接口：供平台内其他服务校验目标用户是否存在且启用。
 *
 * <p>路径固定以 {@code /user/internal} 开头，已列入网关
 * {@code whale.gateway.internal-deny.patterns}，经网关的外部访问一律 403；
 * 调用方须经 Nacos 服务发现直连本服务。本接口不校验业务权限码，
 * 因为它不返回任何用户敏感信息，仅返回 ID 交集。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/user/internal")
@RequiredArgsConstructor
public class UserInternalController {

    /** 用户目录应用服务 */
    private final UserDirectoryAppService userDirectoryAppService;

    /**
     * 批量校验用户是否存在且启用
     *
     * @param request 待校验用户 ID 列表
     * @return 存在且启用的用户 ID 列表
     */
    @PostMapping("/exists")
    public Result<List<Long>> exists(@Valid @RequestBody UserExistsRequest request) {
        return Result.ok(userDirectoryAppService.findEnabledIds(request.getUserIds()));
    }
}
