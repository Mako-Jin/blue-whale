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
 *   <li>{@code message.recalled}：消息撤回通知</li>
 *   <li>{@code unread.changed}：当前用户某会话未读数变更（仅推送给本人，供基座角标使用）</li>
 *   <li>{@code agent.meta} / {@code agent.delta} / {@code agent.done} / {@code agent.error} / {@code agent.confirm}：机器人流式回答与写操作确认</li>
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

    /** 消息撤回通知帧 */
    public static final String TYPE_MESSAGE_RECALLED = "message.recalled";

    /** 群成员变更通知帧 */
    public static final String TYPE_MEMBER_CHANGED = "member.changed";

    /** 未读数变更帧（仅推送本人） */
    public static final String TYPE_UNREAD_CHANGED = "unread.changed";

    /** 机器人工具调用帧 */
    public static final String TYPE_AGENT_TOOL = "agent.tool";

    /** 协议错误帧 */
    public static final String TYPE_ERROR = "error";

    /** 机器人回答元信息帧 */
    public static final String TYPE_AGENT_META = "agent.meta";

    /** 机器人回答增量帧 */
    public static final String TYPE_AGENT_DELTA = "agent.delta";

    /** 机器人回答完成帧 */
    public static final String TYPE_AGENT_DONE = "agent.done";

    /** 机器人回答失败帧 */
    public static final String TYPE_AGENT_ERROR = "agent.error";

    /** 写操作确认卡片帧 */
    public static final String TYPE_AGENT_CONFIRM = "agent.confirm";

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
     * 构造消息撤回通知帧。
     *
     * @param conversationId 会话ID
     * @param messageId      被撤回消息ID
     * @param seq            被撤回消息序号
     * @param operatorId     操作人ID
     * @return 帧对象
     */
    public static Map<String, Object> messageRecalled(Long conversationId, Long messageId, Long seq, Long operatorId) {
        Map<String, Object> frame = frame(TYPE_MESSAGE_RECALLED);
        frame.put("conversationId", String.valueOf(conversationId));
        frame.put("messageId", String.valueOf(messageId));
        frame.put("seq", seq);
        frame.put("operatorId", operatorId == null ? null : String.valueOf(operatorId));
        return frame;
    }

    /**
     * 构造群成员变更通知帧。
     *
     * @param conversationId 会话ID
     * @param action         动作：ADD / REMOVE / QUIT / TRANSFER / DISSOLVED
     * @param userId         变更用户ID（解散时为操作人ID）
     * @return 帧对象
     */
    public static Map<String, Object> memberChanged(Long conversationId, String action, Long userId) {
        Map<String, Object> frame = frame(TYPE_MEMBER_CHANGED);
        frame.put("conversationId", String.valueOf(conversationId));
        frame.put("action", action);
        frame.put("userId", userId == null ? null : String.valueOf(userId));
        return frame;
    }

    /**
     * 构造未读数变更帧。
     *
     * <p>只推送给该用户自己的在线会话（多端同步），不推给会话其他成员，
     * 避免泄露他人的阅读进度。</p>
     *
     * @param conversationId 会话ID
     * @param unread         该会话的最新未读数
     * @return 帧对象
     */
    public static Map<String, Object> unreadChanged(Long conversationId, long unread) {
        Map<String, Object> frame = frame(TYPE_UNREAD_CHANGED);
        frame.put("conversationId", String.valueOf(conversationId));
        frame.put("unread", unread);
        return frame;
    }

    /**
     * 构造机器人工具调用帧。
     *
     * @param conversationId IM 会话ID
     * @param runId          运行记录ID
     * @param tool           脱敏后的工具事件
     * @return 帧对象
     */
    public static Map<String, Object> agentTool(Long conversationId, Long runId, Object tool) {
        Map<String, Object> frame = frame(TYPE_AGENT_TOOL);
        frame.put("conversationId", String.valueOf(conversationId));
        frame.put("runId", String.valueOf(runId));
        frame.put("tool", tool);
        return frame;
    }

    /**
     * 构造机器人回答元信息帧。
     *
     * @param conversationId       IM 会话ID
     * @param runId                运行记录ID
     * @param agentConversationId  智能体会话ID（可空）
     * @param intent               意图（可空）
     * @param degraded             是否降级回答
     * @return 帧对象
     */
    public static Map<String, Object> agentMeta(Long conversationId, Long runId, String agentConversationId,
                                                String intent, boolean degraded) {
        Map<String, Object> frame = frame(TYPE_AGENT_META);
        frame.put("conversationId", String.valueOf(conversationId));
        frame.put("runId", String.valueOf(runId));
        frame.put("agentConversationId", agentConversationId);
        frame.put("intent", intent);
        frame.put("degraded", degraded);
        return frame;
    }

    /**
     * 构造机器人回答增量帧。
     *
     * @param conversationId IM 会话ID
     * @param runId          运行记录ID
     * @param text           本次增量文本
     * @return 帧对象
     */
    public static Map<String, Object> agentDelta(Long conversationId, Long runId, String text) {
        Map<String, Object> frame = frame(TYPE_AGENT_DELTA);
        frame.put("conversationId", String.valueOf(conversationId));
        frame.put("runId", String.valueOf(runId));
        frame.put("text", text);
        return frame;
    }

    /**
     * 构造机器人回答完成帧。
     *
     * @param conversationId IM 会话ID
     * @param runId          运行记录ID
     * @param messageId      机器人回复消息ID
     * @param tokens         估算 token 数
     * @param elapsedMillis  耗时（毫秒）
     * @param degraded       是否降级回答
     * @return 帧对象
     */
    public static Map<String, Object> agentDone(Long conversationId, Long runId, Long messageId,
                                                int tokens, long elapsedMillis, boolean degraded) {
        Map<String, Object> frame = frame(TYPE_AGENT_DONE);
        frame.put("conversationId", String.valueOf(conversationId));
        frame.put("runId", String.valueOf(runId));
        frame.put("messageId", String.valueOf(messageId));
        frame.put("tokens", tokens);
        frame.put("elapsedMillis", elapsedMillis);
        frame.put("degraded", degraded);
        return frame;
    }

    /**
     * 构造机器人回答失败帧。
     *
     * @param conversationId IM 会话ID
     * @param runId          运行记录ID
     * @param message        错误提示
     * @return 帧对象
     */
    public static Map<String, Object> agentError(Long conversationId, Long runId, String message) {
        Map<String, Object> frame = frame(TYPE_AGENT_ERROR);
        frame.put("conversationId", String.valueOf(conversationId));
        frame.put("runId", String.valueOf(runId));
        frame.put("message", message);
        return frame;
    }

    /**
     * 构造写操作确认卡片帧。
     *
     * @param conversationId   IM 会话ID
     * @param runId            运行记录ID
     * @param messageId        卡片消息ID
     * @param confirmationId   确认令牌ID
     * @param operation        操作名
     * @param summary          参数摘要
     * @param permission       所需权限码
     * @param expiresAt        过期时间
     * @return 帧对象
     */
    public static Map<String, Object> agentConfirm(Long conversationId, Long runId, Long messageId,
                                                   String confirmationId, String operation, String summary,
                                                   String permission, String expiresAt) {
        Map<String, Object> frame = frame(TYPE_AGENT_CONFIRM);
        frame.put("conversationId", String.valueOf(conversationId));
        frame.put("runId", String.valueOf(runId));
        frame.put("messageId", messageId == null ? null : String.valueOf(messageId));
        frame.put("confirmationId", confirmationId);
        frame.put("operation", operation);
        frame.put("summary", summary);
        frame.put("permission", permission);
        frame.put("expiresAt", expiresAt);
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
