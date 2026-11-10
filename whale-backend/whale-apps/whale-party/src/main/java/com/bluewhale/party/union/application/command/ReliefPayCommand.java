package com.bluewhale.party.union.application.command;

import lombok.Data;

import java.time.LocalDate;

/**
 * 慰问帮扶发放登记命令。
 *
 * @author BlueWhale
 */
@Data
public class ReliefPayCommand {

    /** 发放日期（空表示当天） */
    private LocalDate paidAt;
}
