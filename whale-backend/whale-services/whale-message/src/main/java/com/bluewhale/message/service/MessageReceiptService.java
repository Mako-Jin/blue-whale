package com.bluewhale.message.service;

import com.bluewhale.message.dto.DeliveryReceiptRequest;
import com.bluewhale.message.vo.ReceiptApplyResult;

/**
 * 渠道回执受理服务。
 *
 * @author BlueWhale
 */
public interface MessageReceiptService {

    /**
     * 受理一批渠道回执：幂等落库、推进投递状态、投递回调任务。
     *
     * @param request 回执上报请求
     * @return 受理结果
     */
    ReceiptApplyResult apply(DeliveryReceiptRequest request);
}
