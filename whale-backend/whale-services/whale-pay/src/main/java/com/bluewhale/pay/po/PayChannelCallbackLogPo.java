package com.bluewhale.pay.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 渠道回调日志实体，对应表 {@code pay_channel_callback_log}。
 *
 * <p>回调验签通过即先落一行（初始结果 {@code FAILED}，表示尚未处理完成），
 * 处理结果回写；验签失败也落库（{@code REJECTED/SIGN_INVALID}），用于攻击面观测。
 * 该表不参与租户插件自动过滤，{@code tenant_id} 由支付单反查后回填，未匹配时为空。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("pay_channel_callback_log")
public class PayChannelCallbackLogPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户 ID（由支付单反查，未匹配为 {@code null}） */
    private Long tenantId;

    /** 渠道编码 */
    private String channel;

    /** 渠道流水号 */
    private String channelTradeNo;

    /** 商户支付单号 */
    private String outTradeNo;

    /** 事件：PAID / REFUNDED / CLOSED / UNKNOWN */
    private String event;

    /** 幂等键：channel + ':' + channelTradeNo + ':' + event */
    private String idempotencyKey;

    /** 结果：PROCESSED / DUPLICATE / REJECTED / FAILED */
    private String result;

    /** 拒绝原因（仅 REJECTED 有值） */
    private String rejectReason;

    /** 渠道金额 */
    private BigDecimal amount;

    /** 渠道币种 */
    private String currency;

    /** 原始报文（截断后入库） */
    private String rawBody;

    /** 创建时间（库默认值） */
    private LocalDateTime createTime;
}
