package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.user.UserLifecycleAppService;
import com.bluewhale.auth.interfaces.api.UserLifecycleInternalApi;
import com.bluewhale.auth.interfaces.dto.UserLifecycleRequest;
import com.bluewhale.auth.interfaces.dto.response.UserLifecycleResponse;
import com.bluewhale.auth.starter.assemble.UserAssembler;
import com.bluewhale.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人员生命周期内网接口实现（Web 适配层）：仅供服务间内网直连调用。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class UserLifecycleInternalController implements UserLifecycleInternalApi {

    private final UserLifecycleAppService userLifecycleAppService;

    private final UserAssembler userAssembler;

    /**
     * 执行人员生命周期动作
     *
     * @param request 生命周期请求
     * @return 执行结果
     */
    @Override
    public Result<UserLifecycleResponse> apply(@RequestBody UserLifecycleRequest request) {
        return Result.ok(userAssembler.toLifecycleResponse(
                userLifecycleAppService.apply(userAssembler.toLifecycleCommand(request))));
    }
}
