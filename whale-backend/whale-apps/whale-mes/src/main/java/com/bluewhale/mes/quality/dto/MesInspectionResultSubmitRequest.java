package com.bluewhale.mes.quality.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 检验结果录入请求
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionResultSubmitRequest {

    /** 检验员 */
    @Size(max = 64, message = "{validation.mes.mes-inspection-result-submit.inspector-size}")
    private String inspector;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-inspection-result-submit.remark-size}")
    private String remark;

    /** 检验结果明细 */
    @Valid
    @NotEmpty(message = "{validation.mes.mes-inspection-result-submit.results-required}")
    private List<MesInspectionResultItemRequest> results;

    /** 不良明细 */
    @Valid
    private List<MesInspectionDefectRequest> defects;
}
