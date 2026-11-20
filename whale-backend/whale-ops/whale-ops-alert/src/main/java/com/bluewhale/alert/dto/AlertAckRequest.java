package com.bluewhale.alert.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 告警认领请求。
 *
 * @author BlueWhale
 */
@Data
public class AlertAckRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 认领人ID（由调用方传入，便于与用户中心对接） */
    @Size(max = 64, message = "认领人长度不能超过 64")
    private String ackUser;

    /** 认领备注 */
    @Size(max = 255, message = "认领备注长度不能超过 255")
    private String ackNote;
}
