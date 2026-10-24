package com.bluewhale.im.dto;

import lombok.Data;

/**
 * 智能体写操作确认请求。
 *
 * @author BlueWhale
 */
@Data
public class AgentConfirmRequest {

    /** 是否执行：true 执行 / false 拒绝；缺省为执行 */
    private Boolean approved = Boolean.TRUE;
}
