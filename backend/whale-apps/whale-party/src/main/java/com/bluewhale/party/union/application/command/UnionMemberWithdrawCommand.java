package com.bluewhale.party.union.application.command;

import lombok.Data;

import java.time.LocalDate;

/**
 * 工会会员退会命令。
 *
 * <p>退会日期可空，空值表示当天退会；保留该字段是为了补录历史的离职退会日期，
 * 而不是让页面在正常操作里随意改日期。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionMemberWithdrawCommand {

    /** 退会日期（空表示当天） */
    private LocalDate withdrawDate;
}
