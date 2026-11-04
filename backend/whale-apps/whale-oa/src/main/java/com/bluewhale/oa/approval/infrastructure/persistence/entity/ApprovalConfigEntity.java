package com.bluewhale.oa.approval.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批业务配置实体，对应表 {@code oa_approval_config}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_approval_config")
public class ApprovalConfigEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 业务类型 */
    private String businessType;

    /** 业务名称 */
    private String businessName;

    /** Workflow 流程定义编码 */
    private String definitionCode;

    /** 标题模板 */
    private String titleTemplate;

    /** 业务详情前端路由 */
    private String businessUrl;

    /** 默认抄送人ID */
    private String ccUserIds;

    /** 是否启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;
}
