package com.bluewhale.doc.support.collab;

import lombok.Data;

import java.io.Serializable;

/**
 * 协作在线用户（M2）。
 *
 * <p>保存在 Redis presence Hash 中，字段为 WS 会话 clientId，值为该对象 JSON。</p>
 *
 * @author BlueWhale
 */
@Data
public class DocPresenceUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** WS 会话客户端ID（浏览器生成的 UUID） */
    private String clientId;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String name;

    /** 光标/头像颜色（十六进制） */
    private String color;

    /** 最近心跳时间（毫秒时间戳） */
    private long lastSeen;
}
