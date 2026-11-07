package com.bluewhale.crm.marketing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批量触达任务实体，对应表 {@code crm_marketing_send}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_marketing_send")
public class MarketingSendEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 触达任务编号 */
    private String sendNo;

    /** 营销活动 ID */
    private Long campaignId;

    /** 人群快照 ID */
    private Long audienceId;

    /** 触达渠道 */
    private String channel;

    /** 消息模板编码 */
    private String templateCode;

    /** 消息主题 */
    private String subject;

    /** 内容文档 ID */
    private Long contentRefDocId;

    /** 任务状态 */
    private String status;

    /** 收件人总数 */
    private Integer totalCount;

    /** 成功数量 */
    private Integer sentCount;

    /** 跳过数量 */
    private Integer skippedCount;

    /** 失败数量 */
    private Integer failedCount;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 最近一次失败原因 */
    private String lastError;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;
}
