package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * BOM 对比结果
 *
 * @author BlueWhale
 */
@Data
public class PdmBomDiffVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 基线 BOM 编号 */
    private String baseBomNo;

    /** 基线版本号 */
    private String baseVersionNo;

    /** 基线视图 */
    private String baseView;

    /** 目标 BOM 编号 */
    private String targetBomNo;

    /** 目标版本号 */
    private String targetVersionNo;

    /** 目标视图 */
    private String targetView;

    /** 新增行数 */
    private int added;

    /** 删除行数 */
    private int removed;

    /** 变更行数 */
    private int changed;

    /** 明细 */
    private List<PdmBomDiffLineVO> lines = new ArrayList<>();
}
