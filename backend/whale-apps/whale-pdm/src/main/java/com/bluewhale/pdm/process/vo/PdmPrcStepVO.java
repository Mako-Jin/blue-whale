package com.bluewhale.pdm.process.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 工艺工序视图。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcStepVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工序ID */
    private Long id;

    /** 工序序号 */
    private Integer stepNo;

    /** 工序名称 */
    private String name;

    /** 工作中心 */
    private String workCenter;

    /** 标准工时（小时） */
    private BigDecimal standardHours;

    /** 准备工时（小时） */
    private BigDecimal setupHours;

    /** 工序说明 */
    private String description;

    /** 工装列表 */
    private List<PdmPrcToolingVO> toolings = new ArrayList<>();

    /** SOP 引用 */
    private PdmPrcSopVO sop;
}
