package com.bluewhale.doc.ws;

/**
 * 文档协作 WS 握手属性名（M2）。
 *
 * @author BlueWhale
 */
public final class DocCollabAttributes {

    /** 握手阶段消费的 ticket 载荷 */
    public static final String TICKET = "doc.ws.ticket";

    /** 浏览器生成的客户端ID */
    public static final String CLIENT_ID = "doc.ws.clientId";

    /** 协作会话对象（注册表与帧处理复用） */
    public static final String SESSION = "doc.ws.session";

    /**
     * 私有构造器：常量类，禁止实例化。
     */
    private DocCollabAttributes() {
    }
}
