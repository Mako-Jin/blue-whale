package com.bluewhale.pdm.collab.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 研发评审关闭请求
 *
 * @author BlueWhale
 */
@Data
public class PdmClbReviewCloseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 结论：PASSED/CONDITIONAL/REJECTED */
    @NotBlank(message = "评审结论不能为空")
    private String conclusion;

    /** 结论说明 */
    private String conclusionComment;
}
