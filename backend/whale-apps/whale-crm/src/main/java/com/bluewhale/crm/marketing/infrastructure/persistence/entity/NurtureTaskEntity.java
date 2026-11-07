package com.bluewhale.crm.marketing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 客户培育任务实体，对应表 {@code crm_nurture_task}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_nurture_task")
public class NurtureTaskEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 任务编号 */
    private String taskNo;

    /** CRM 客户 ID */
    private Long customerId;

    /** 客户名称快照 */
    private String customerName;

    /** 关联线索 ID */
    private Long leadId;

    /** 内容文档 ID */
    private Long contentDocId;

    /** 内容文档版本 */
    private Integer contentDocVersion;

    /** 触达渠道 */
    private String channel;

    /** 计划触达日期 */
    private LocalDate planDate;

    /** 任务状态 */
    private String status;

    /** 执行人 ID */
    private Long assigneeId;

    /** 执行人姓名 */
    private String assigneeName;

    /** 备注 */
    private String note;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;
}
