package com.bluewhale.pdm.process.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PDM 工序 SOP 引用
 *
 * <p>每道工序最多关联一个文档版本，且只能引用已发布（RELEASED）版本。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_prc_sop")
public class PdmPrcSop extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工艺路线ID */
    private Long routeId;

    /** 工序ID */
    private Long stepId;

    /** 文档ID */
    private Long documentId;

    /** 文档版本号 */
    private String documentVersionNo;

    /** 文档标题快照 */
    private String documentTitle;
}
