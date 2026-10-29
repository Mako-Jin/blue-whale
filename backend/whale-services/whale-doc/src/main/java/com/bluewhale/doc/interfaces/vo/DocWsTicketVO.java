package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 协作 WS ticket 响应（M2）。
 *
 * @author BlueWhale
 */
@Data
public class DocWsTicketVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 一次性票据，用于 {@code /doc/ws?ticket=...} */
    private String ticket;

    /** 票据有效期（秒） */
    private long expiresIn;
}
