package com.bluewhale.doc.interfaces.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 协作 WS ticket 申请请求（M2）。
 *
 * @author BlueWhale
 */
@Data
public class DocWsTicketRequest {

    /** 页面ID */
    @NotNull(message = "{validation.doc.collab.page-id-required}")
    private Long pageId;
}
