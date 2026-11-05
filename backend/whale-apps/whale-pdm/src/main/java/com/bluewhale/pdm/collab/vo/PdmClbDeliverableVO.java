package com.bluewhale.pdm.collab.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目交付物视图
 *
 * @author BlueWhale
 */
@Data
public class PdmClbDeliverableVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 交付物ID */
    private Long id;

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 任务ID */
    private Long workItemId;

    /** 任务名称 */
    private String workItemName;

    /** 对象类型 */
    private String objectType;

    /** 对象ID */
    private Long objectId;

    /** 对象编号 */
    private String objectNo;

    /** 对象标题 */
    private String objectTitle;

    /** 对象版本号 */
    private String objectVersionNo;

    /** 交付物类型 */
    private String deliverableType;

    /** 状态 */
    private String status;

    /** 归档人 */
    private Long archivedBy;

    /** 归档时间 */
    private LocalDateTime archivedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
