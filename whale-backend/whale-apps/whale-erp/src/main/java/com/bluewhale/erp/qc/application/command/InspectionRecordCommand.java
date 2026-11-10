package com.bluewhale.erp.qc.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 录入检验项命令。
 *
 * @author BlueWhale
 */
@Data
public class InspectionRecordCommand {

    /** 检验项录入行 */
    @Valid
    @NotEmpty
    private List<Line> lines;

    /**
     * 检验项录入行入参。
     */
    @Data
    public static class Line {

        /** 行号 */
        @NotNull
        private Integer lineNo;

        /** 实测值 */
        private String actualValue;

        /** 结论：PASS / FAIL */
        @NotBlank
        private String result;

        /** 备注 */
        private String remark;
    }
}
