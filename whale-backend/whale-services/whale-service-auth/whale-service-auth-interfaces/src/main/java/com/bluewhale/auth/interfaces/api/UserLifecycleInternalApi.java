package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.UserLifecycleRequest;
import com.bluewhale.auth.interfaces.dto.response.UserLifecycleResponse;
import com.bluewhale.common.core.result.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 人员生命周期内网 API 契约：供 OA 人事异动审批通过后入职/转正/调岗/离职/返聘。
 *
 * <p>路径以 {@code /user/v1/internal} 开头，网关收口外部访问；入职建号返回的一次性
 * 临时口令仅本次响应可见，不落库。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/user/v{version}/internal/lifecycle", version = "1")
public interface UserLifecycleInternalApi {

    /**
     * 执行人员生命周期动作
     *
     * @param request 生命周期请求
     * @return 执行结果
     */
    @PostMapping
    Result<UserLifecycleResponse> apply(@RequestBody UserLifecycleRequest request);
}
