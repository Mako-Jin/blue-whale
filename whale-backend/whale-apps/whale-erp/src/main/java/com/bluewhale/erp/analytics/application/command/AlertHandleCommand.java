package com.bluewhale.erp.analytics.application.command;

import lombok.Data;

/**
 * 预警事件处理命令。
 *
 * @author BlueWhale
 */
@Data
public class AlertHandleCommand {

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;
}
