package com.bluewhale.erp.mfg.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MES 回传请求包装（事件键 + JSON 报文）。
 *
 * @author BlueWhale
 */
@Data
public class MesCallbackRequest {

    /** MES 事件幂等键 */
    @NotBlank(message = "{validation.erp.mes-callback.event-id-required}")
    private String eventId;

    /** JSON 报文 */
    @NotBlank(message = "{validation.erp.mes-callback.payload-required}")
    private String payload;
}
