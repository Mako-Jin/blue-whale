package com.bluewhale.pdm.process.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 工艺路线新建/编辑请求。
 *
 * <p>新建时 {@code partId} 必填；编辑仅允许修改备注，零件与编号不可变。</p>
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcRouteSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 零件/产品ID（新建必填） */
    private Long partId;

    /** 路线编号（缺省按零件编码生成） */
    @Size(max = 32, message = "{validation.pdm.pdm-prc-route.route-no-size}")
    private String routeNo;

    /** 备注 */
    @Size(max = 255, message = "{validation.pdm.pdm-prc-route.remark-size}")
    private String remark;
}
