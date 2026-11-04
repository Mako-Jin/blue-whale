package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批配置视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalConfigView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

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

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
