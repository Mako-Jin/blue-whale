package com.bluewhale.pdm.process.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工艺路线视图（含工序、工装、SOP 与下发状态）。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcRouteVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 路线ID */
    private Long id;

    /** 路线编号 */
    private String routeNo;

    /** 零件ID */
    private Long partId;

    /** 零件编码 */
    private String partCode;

    /** 零件名称 */
    private String partName;

    /** 版本号 */
    private String versionNo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private Long createdBy;

    /** 发布人 */
    private Long releasedBy;

    /** 发布时间 */
    private LocalDateTime releasedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 工序列表 */
    private List<PdmPrcStepVO> steps = new ArrayList<>();

    /** 下发状态 */
    private PdmPrcPublishVO publish;
}
