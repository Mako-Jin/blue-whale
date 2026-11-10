package com.bluewhale.message.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.message.dto.DeliveryReceiptRequest;
import com.bluewhale.message.dto.SendMessageRequest;
import com.bluewhale.message.service.MessageReceiptService;
import com.bluewhale.message.service.MessageSendService;
import com.bluewhale.message.vo.ReceiptApplyResult;
import com.bluewhale.message.vo.SendMessageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息内网接口（发送 + 回执受理）。
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

    private final MessageReceiptService messageReceiptService;

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

    /**
     * 上报渠道送达回执。
     *
     * <p>由渠道供应商或部署侧回执网关调用；按 {@code providerMessageId} 匹配投递记录，
     * 幂等受理并异步回调业务方登记的回调地址。</p>
     *
     * @param request 回执上报请求
     * @return 受理结果
     */
    @PostMapping("/receipt")
    public Result<ReceiptApplyResult> receipt(@Valid @RequestBody DeliveryReceiptRequest request) {
        return Result.ok(messageReceiptService.apply(request));
    }
}
