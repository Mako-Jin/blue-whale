package com.bluewhale.alert.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 告警通知发送日志实体，对应表 {@code alert_notify_log}。
 *
 * <p>该表是只追加的发送证据，没有更新与逻辑删除语义，因此不继承
 * {@link com.bluewhale.common.entity.BaseEntity}，只保留主键与创建时间。</p>
 *
 * @author BlueWhale
 */
@Data
@TableName("alert_notify_log")
public class AlertNotifyLogPo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 告警记录ID */
    private Long recordId;

    /** 规则编码 */
    private String ruleCode;

    /** 通知通道：EMAIL / SMS / WEBHOOK */
    private String channel;

    /** 发送结果：SUCCESS / FAILED */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 失败原因（异常类名 + 消息） */
    private String error;

    /** 创建时间（插入自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
