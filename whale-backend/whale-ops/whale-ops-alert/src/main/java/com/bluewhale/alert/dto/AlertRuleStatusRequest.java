package com.bluewhale.alert.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 告警规则启停请求。
 *
 * @author BlueWhale
 */
@Data
public class AlertRuleStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：1 启用 / 0 停用 */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
