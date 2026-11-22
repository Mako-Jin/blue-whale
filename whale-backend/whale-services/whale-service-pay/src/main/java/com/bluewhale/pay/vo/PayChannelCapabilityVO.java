package com.bluewhale.pay.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 渠道能力视图对象（管理端渲染渠道能力矩阵）。
 *
 * @author BlueWhale
 */
@Data
public class PayChannelCapabilityVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 渠道编码 */
    private String channel;

    /** 能力编码列表（PARTIAL_REFUND / REFUND_QUERY / ASYNC_CALLBACK / CLOSE） */
    private List<String> features;

    /**
     * 构造渠道能力。
     *
     * @param channel  渠道编码
     * @param features 能力编码
     * @return 视图对象
     */
    public static PayChannelCapabilityVO of(String channel, List<String> features) {
        PayChannelCapabilityVO vo = new PayChannelCapabilityVO();
        vo.setChannel(channel);
        vo.setFeatures(features);
        return vo;
    }
}
