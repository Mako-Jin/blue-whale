package com.bluewhale.im.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.im.dto.InternalNotifyRequest;
import com.bluewhale.im.service.ImNotifyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知内网接口。
 *
 * <p>服务内路径前缀 {@code /im/internal}，供其他微服务（如 whale-pm）投递系统提醒；
 * 网关 {@code internal-deny} 对 {@code /im/internal/**} 统一返回 403，外部不可达，故不加权限码注解。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/im/internal")
@RequiredArgsConstructor
public class ImInternalController {

    private final ImNotifyService notifyService;

    /**
     * 向多个接收人的机器人会话投递通知。
     *
     * @param request 通知请求
     * @return 成功投递人数
     */
    @PostMapping("/notify")
    public Result<Integer> notify(@Valid @RequestBody InternalNotifyRequest request) {
        return Result.ok(notifyService.notify(request));
    }
}
