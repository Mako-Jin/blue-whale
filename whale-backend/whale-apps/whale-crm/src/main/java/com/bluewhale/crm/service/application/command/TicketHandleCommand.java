package com.bluewhale.crm.service.application.command;

import lombok.Data;

/**
 * 工单处理动作命令（内容 / 解决方案 / 原因按动作取用）。
 *
 * @author BlueWhale
 */
@Data
public class TicketHandleCommand {

    /** 处理内容、备注或原因 */
    private String content;

    /** 解决方案 */
    private String solution;

    /** 原因（暂停 / 重开 / 取消） */
    private String reason;
}
