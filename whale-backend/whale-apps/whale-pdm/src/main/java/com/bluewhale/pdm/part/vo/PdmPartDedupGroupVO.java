package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 零件去重候选组
 *
 * @author BlueWhale
 */
@Data
public class PdmPartDedupGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 归一化分组键：名称|材质|规格 */
    private String dedupKey;

    /** 展示名称（取组内首件原值） */
    private String name;

    /** 展示材质 */
    private String material;

    /** 展示规格 */
    private String spec;

    /** 候选零件数量 */
    private Integer count;

    /** 候选零件明细 */
    private List<PdmPartDedupItemVO> parts;
}
