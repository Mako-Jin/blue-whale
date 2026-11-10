package com.bluewhale.pdm.part.vo;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * BOM 发布快照（ERP 内网契约）
 *
 * <p>字段顺序固定，行按深度优先路径排序；同一版本重复读取结果一致。</p>
 *
 * @author BlueWhale
 */
@Data
@JsonPropertyOrder({"bomNo", "versionNo", "view", "productCode", "productName", "lineCount", "lines"})
public class PdmBomSnapshotVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** BOM 编号 */
    private String bomNo;

    /** 版本号 */
    private String versionNo;

    /** 视图 */
    private String view;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 快照行数 */
    private Integer lineCount;

    /** 快照校验和（响应时由发布记录回填，不参与 checksum 计算） */
    private String checksum;

    /** 快照行（深度优先） */
    private List<PdmBomSnapshotLineVO> lines = new ArrayList<>();
}
