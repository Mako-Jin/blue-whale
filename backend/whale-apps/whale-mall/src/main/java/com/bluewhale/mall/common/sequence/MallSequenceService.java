package com.bluewhale.mall.common.sequence;

/**
 * 商城业务编号取号服务。
 *
 * @author BlueWhale
 */
public interface MallSequenceService {

    /**
     * 取下一个流水号。
     *
     * @param seqKey 编号键
     * @param seqDate 周期标识（空串表示不重置）
     * @return 流水号
     */
    long next(String seqKey, String seqDate);

    /**
     * 生成订单号（{@code MO} + 日期 + 6 位流水，按日重置）。
     *
     * @return 订单号
     */
    String nextOrderNo();

    /**
     * 生成发货单号（{@code MS} + 日期 + 6 位流水，按日重置）。
     *
     * @return 发货单号
     */
    String nextShipmentNo();
}
