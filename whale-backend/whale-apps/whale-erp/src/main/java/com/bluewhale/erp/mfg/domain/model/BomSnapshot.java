package com.bluewhale.erp.mfg.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * PDM BOM 发布快照（ERP 侧值对象）。
 *
 * <p>同一 {@code bomNo + versionNo} 重复读取结果稳定；快照本身已是多级展开结构。</p>
 *
 * @author BlueWhale
 */
@Data
public class BomSnapshot {

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

    /** 快照校验和 */
    private String checksum;

    /** 快照行（深度优先） */
    private List<BomLine> lines = new ArrayList<>();
}
