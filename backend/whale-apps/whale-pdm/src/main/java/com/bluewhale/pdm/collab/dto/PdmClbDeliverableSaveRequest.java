package com.bluewhale.pdm.collab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 项目交付物归档请求
 *
 * @author BlueWhale
 */
@Data
public class PdmClbDeliverableSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** whale-pm 项目ID */
    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    /** 项目名称快照 */
    private String projectName;

    /** whale-pm 任务ID */
    private Long workItemId;

    /** 任务名称快照 */
    private String workItemName;

    /** 对象类型：DOCUMENT/BOM/ROUTE */
    @NotBlank(message = "对象类型不能为空")
    private String objectType;

    /** 对象ID */
    @NotNull(message = "对象ID不能为空")
    private Long objectId;

    /** 对象编号（可空，服务会回填） */
    private String objectNo;

    /** 对象标题（可空，服务会回填） */
    private String objectTitle;

    /** 已发布对象版本号 */
    @NotBlank(message = "对象版本号不能为空")
    private String objectVersionNo;

    /** 交付物类型：DESIGN/DRAWING/REPORT/OTHER */
    private String deliverableType;

    /** 备注 */
    private String remark;
}
