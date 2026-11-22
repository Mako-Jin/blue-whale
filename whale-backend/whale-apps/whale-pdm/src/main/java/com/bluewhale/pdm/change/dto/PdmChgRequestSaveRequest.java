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
    @NotBlank(message = "{validation.pdm.pdm-chg-request.change-reason-required}")
    @Size(max = 1000, message = "{validation.pdm.pdm-chg-request.change-reason-size}")
    private String changeReason;

    /** 变更范围 */
    @Size(max = 500, message = "{validation.pdm.pdm-chg-request.change-scope-size}")
    private String changeScope;

    /** 影响对象（提交前至少一条） */
    @Valid
    @Size(max = 100, message = "{validation.pdm.pdm-chg-request.impact-objects-size}")
    private List<PdmChgImpactObjectVO> impactObjects;
}
