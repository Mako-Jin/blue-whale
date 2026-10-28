package com.bluewhale.message.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.message.dto.SendMessageRequest;
import com.bluewhale.message.service.MessageSendService;
import com.bluewhale.message.vo.SendMessageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息内网发送接口。
 *
 * <p>服务内路径前缀 {@code /message/internal}，供业务服务经 Nacos 内网直连调用；
 * 网关 {@code internal-deny} 对 {@code /message/internal/**} 统一返回 403，外部不可达，
 * 故不加权限码注解。生产者在请求中显式传 {@code tenantId}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/message/internal")
@RequiredArgsConstructor
public class MessageInternalController {

    private final MessageSendService messageSendService;

    /**
     * 发送一条消息。
     *
     * @param request 发送请求
     * @return 发送结果
     */
    @PostMapping("/send")
    public Result<SendMessageResult> send(@Valid @RequestBody SendMessageRequest request) {
        return Result.ok(messageSendService.send(request));
    }
}
