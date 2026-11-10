package com.bluewhale.pdm.change.dto;

import com.bluewhale.pdm.change.vo.PdmChgImpactObjectVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 变更申请新建/修改请求
 *
 * @author BlueWhale
 */
@Data
public class PdmChgRequestSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变更原因 */
    @NotBlank(message = "变更原因不能为空")
    @Size(max = 1000, message = "变更原因长度不能超过 1000")
    private String changeReason;

    /** 变更范围 */
    @Size(max = 500, message = "变更范围长度不能超过 500")
    private String changeScope;

    /** 影响对象（提交前至少一条） */
    @Valid
    @Size(max = 100, message = "影响对象不能超过 100 条")
    private List<PdmChgImpactObjectVO> impactObjects;
}
