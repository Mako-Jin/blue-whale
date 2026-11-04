package com.bluewhale.erp.qc.application.command;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 不合格品关闭命令。
 *
 * @author BlueWhale
 */
@Data
public class NcrCloseCommand {

    /** 关闭日期 */
    @NotNull
    private LocalDate closeDate;

    /** 备注 */
    private String remark;
}
