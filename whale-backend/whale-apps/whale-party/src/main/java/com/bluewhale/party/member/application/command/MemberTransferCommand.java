package com.bluewhale.party.member.application.command;

import lombok.Data;

import java.time.LocalDate;

/**
 * 组织关系转出命令。
 *
 * @author BlueWhale
 */
@Data
public class MemberTransferCommand {

    /** 转出日期（空表示当天） */
    private LocalDate transferDate;

    /** 转出说明（去哪一级党组织，仅作记录） */
    private String remark;
}
