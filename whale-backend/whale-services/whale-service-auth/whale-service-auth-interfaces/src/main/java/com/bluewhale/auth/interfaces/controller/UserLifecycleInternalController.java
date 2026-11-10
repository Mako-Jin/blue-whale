package com.bluewhale.auth.interfaces.controller;

import com.bluewhale.auth.application.user.UserLifecycleAppService;
import com.bluewhale.auth.application.user.UserLifecycleVO;
import com.bluewhale.auth.application.user.command.UserLifecycleCommand;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人员生命周期内网接口：供 OA 人事异动审批通过后入职/转正/调岗/离职/返聘。
 *
 * <p>路径以 {@code /user/internal} 开头，已列入网关
 * {@code whale.gateway.internal-deny.patterns}，经网关的外部访问一律 403；
 * 调用方须经 Nacos 服务发现直连本服务。入职建号返回的一次性临时口令仅本次响应可见，不落库。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/user/internal/lifecycle")
@RequiredArgsConstructor
public class UserLifecycleInternalController {

    /** 人员生命周期应用服务 */
    private final UserLifecycleAppService userLifecycleAppService;

    /**
     * 执行人员生命周期动作。
     *
     * @param command 生命周期命令
     * @return 执行结果
     */
    @PostMapping
    public Result<UserLifecycleVO> apply(@RequestBody UserLifecycleCommand command) {
        return Result.ok(userLifecycleAppService.apply(command));
    }
}
