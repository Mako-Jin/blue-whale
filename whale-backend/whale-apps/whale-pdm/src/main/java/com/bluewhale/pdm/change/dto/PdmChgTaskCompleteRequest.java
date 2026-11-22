package com.bluewhale.pdm.change.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 变更任务完成请求
 *
 * @author BlueWhale
 */
@Data
public class PdmChgTaskCompleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行结果版本号 */
    @NotBlank(message = "{validation.pdm.pdm-chg-task-complete.result-version-no-required}")
    @Size(max = 32, message = "{validation.pdm.pdm-chg-task-complete.result-version-no-size}")
    private String resultVersionNo;
}
