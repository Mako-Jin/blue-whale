package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * BOM 视图（头 + 平铺行 + 树）
 *
 * @author BlueWhale
 */
@Data
public class PdmBomVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** BOM 编号 */
    private String bomNo;

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 视图 */
    private String view;

    /** 版本号 */
    private String versionNo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 平铺行 */
    private List<PdmBomLineVO> lines = new ArrayList<>();

    /** 树结构 */
    private List<PdmBomTreeNodeVO> tree = new ArrayList<>();
}
