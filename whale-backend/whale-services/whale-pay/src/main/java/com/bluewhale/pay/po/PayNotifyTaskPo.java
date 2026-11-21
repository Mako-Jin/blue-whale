package com.bluewhale.pay.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 业务通知任务实体，对应表 {@code pay_notify_task}。
 *
 * <p>支付/退款成功后投递，由重试器按指数退避投递到业务方内网回调地址；
 * 6 次失败后置 DEAD，等待人工重试。进程重启不丢通知。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_notify_task")
public class PayNotifyTaskPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 业务方类型（MALL_ORDER 等） */
    private String bizType;

    /** 业务单号（如商城订单号） */
    private String bizOrderNo;

    /** 事件：PAID / REFUNDED */
    private String event;

    /** 回调地址（格式：&lt;服务名&gt;/&lt;路径&gt;） */
    private String notifyUrl;

    /** 回调 JSON 载荷 */
    private String payload;

    /** 状态：PENDING / SUCCESS / DEAD */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 最近一次失败原因 */
    private String lastError;
}
