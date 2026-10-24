package com.bluewhale.im.ws;

import com.bluewhale.im.vo.MessageVO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 帧构造工具。
 *
 * <p>帧格式统一为 {@code type} + 载荷字段的 JSON 对象：</p>
 *
 * <ul>
 *   <li>{@code ready}：连接建立后的首帧，携带当前用户、服务器时间与心跳间隔</li>
 *   <li>{@code ping} / {@code pong}：客户端心跳与服务端应答</li>
 *   <li>{@code message.new}：新消息推送（载荷为 REST 同构的 {@link MessageVO}）</li>
 *   <li>{@code error}：协议错误提示</li>
 * </ul>
 *
 * @author BlueWhale
 */
public final class WsFrames {

    /** 连接就绪帧 */
    public static final String TYPE_READY = "ready";

    /** 客户端心跳帧 */
    public static final String TYPE_PING = "ping";

    /** 服务端心跳应答帧 */
    public static final String TYPE_PONG = "pong";

    /** 新消息推送帧 */
    public static final String TYPE_MESSAGE_NEW = "message.new";

    /** 协议错误帧 */
    public static final String TYPE_ERROR = "error";

    /** 时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 工具类禁止实例化
     */
    private WsFrames() {
    }

    /**
     * 构造连接就绪帧。
     *
     * @param userId          当前用户ID
     * @param heartbeatSeconds 建议心跳间隔（秒）
     * @return 帧对象
     */
    public static Map<String, Object> ready(long userId, long heartbeatSeconds) {
        Map<String, Object> frame = frame(TYPE_READY);
        frame.put("userId", String.valueOf(userId));
        frame.put("serverTime", now());
        frame.put("heartbeatSeconds", heartbeatSeconds);
        return frame;
    }

    /**
     * 构造心跳应答帧。
     *
     * @return 帧对象
     */
    public static Map<String, Object> pong() {
        Map<String, Object> frame = frame(TYPE_PONG);
        frame.put("serverTime", now());
        return frame;
    }

    /**
     * 构造新消息推送帧。
     *
     * @param message 消息视图对象
     * @return 帧对象
     */
    public static Map<String, Object> messageNew(MessageVO message) {
        Map<String, Object> frame = frame(TYPE_MESSAGE_NEW);
        frame.put("message", message);
        return frame;
    }

    /**
     * 构造协议错误帧。
     *
     * @param message 错误提示
     * @return 帧对象
     */
    public static Map<String, Object> error(String message) {
        Map<String, Object> frame = frame(TYPE_ERROR);
        frame.put("message", message);
        return frame;
    }

    /**
     * 创建带类型字段的有序帧容器。
     *
     * @param type 帧类型
     * @return 帧对象
     */
    private static Map<String, Object> frame(String type) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", type);
        return frame;
    }

    /**
     * 当前服务器时间（ISO-8601）。
     *
     * @return 时间字符串
     */
    private static String now() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }
}
