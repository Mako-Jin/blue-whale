package com.bluewhale.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.pay.entity.PayTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付/退款流水 Mapper。
 *
 * <p>流水表只追加不修改（每次支付或退款成功追一行），所以"今天退了多少"只能从这里取：
 * {@code pay_order.refund_amount} 是累计值，回答不了按天的问题。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface PayTransactionMapper extends BaseMapper<PayTransaction> {

    /**
     * 时间窗内成功退款金额（度支殿概览 T5.3）
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 退款金额；无数据时为 0
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM pay_transaction "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND type = 'REFUND' AND status = 'SUCCESS' "
            + "AND create_time >= #{from} AND create_time < #{to}")
    BigDecimal sumRefundBetween(@Param("tenantId") Long tenantId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    /**
     * 时间窗内支付失败的流水笔数（度支殿概览 T5.3）
     *
     * <p>只看 {@code type = 'PAY'}：退款失败是另一件事且量级小得多，
     * 混在一起会把渠道故障的信号稀释掉。</p>
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 失败笔数
     */
    @Select("SELECT COUNT(*) FROM pay_transaction "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 "
            + "AND type = 'PAY' AND status = 'FAILED' "
            + "AND create_time >= #{from} AND create_time < #{to}")
    long countFailedPayBetween(@Param("tenantId") Long tenantId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to);
}
