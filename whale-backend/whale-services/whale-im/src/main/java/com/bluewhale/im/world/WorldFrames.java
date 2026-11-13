package com.bluewhale.im.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界频道帧定义（{@code /im/world}）。
 *
 * <p>与聊天帧（{@code /im/ws}）完全隔离：帧名统一 {@code world.} 前缀，互不串线。
 * 协议口径（与前端 {@code whale-world/src/world/net.ts} 约定一致）：</p>
 *
 * <pre>
 * 上行 world.join   { sceneKey, name, robe, x, z, yaw }   进场景一次
 * 上行 world.frame  { x, z, yaw, speed }                 10Hz，仅位置与朝向
 * 上行 world.say    { sceneKey, text }                   同城喊话（限 1.5 秒 1 条 / 120 字）
 * 下行 world.snapshot { sceneKey, selfId, peers[] }      兴趣区内全量
 * 下行 world.delta   { peers[] }                         增量（服务端 5Hz 聚合）
 * 下行 world.leave   { ids[] }                           离开通知
 * 下行 world.say     { from, name, text, x, z, at }      附近有人说话了
 * 下行 world.error   { code, message }                   协议/配额错误
 * </pre>
 *
 * <p><b>位置不落库</b>：世界帧是瞬时状态，服务端只在内存里做「按场景 + 距离裁剪」的转发，
 * 不写数据库、不进审计，避免把高频帧变成写入压力。</p>
 *
 * @author BlueWhale
 */
public final class WorldFrames {

    /** 客户端进场景 */
    public static final String TYPE_JOIN = "world.join";

    /** 客户端位置帧 */
    public static final String TYPE_FRAME = "world.frame";

    /** 服务端全量快照 */
    public static final String TYPE_SNAPSHOT = "world.snapshot";

    /** 服务端增量 */
    public static final String TYPE_DELTA = "world.delta";

    /** 服务端离开通知 */
    public static final String TYPE_LEAVE = "world.leave";

    /** 服务端协议错误 */
    public static final String TYPE_ERROR = "world.error";

    /** 心跳应答 */
    public static final String TYPE_PONG = "world.pong";

    /** 同城喊话（上行发一句，下行广播给附近的人） */
    public static final String TYPE_SAY = "world.say";

    /** 错误码：场景标识非法 */
    public static final String CODE_BAD_SCENE = "BAD_SCENE";

    /** 错误码：场景人数已满 */
    public static final String CODE_SCENE_FULL = "SCENE_FULL";

    /** 错误码：位置帧坐标非法 */
    public static final String CODE_BAD_FRAME = "BAD_FRAME";

    /** 错误码：未进场景就发位置帧 */
    public static final String CODE_NOT_JOINED = "NOT_JOINED";

    /** 错误码：帧过大或帧率超限 */
    public static final String CODE_RATE_LIMITED = "RATE_LIMITED";

    /** 错误码：喊话内容不合法（空、超长） */
    public static final String CODE_BAD_SAY = "BAD_SAY";

    /** 错误码：喊话太频 */
    public static final String CODE_SAY_LIMITED = "SAY_LIMITED";

    private WorldFrames() {
    }

    /**
     * 构造喊话帧（上下行同构：客户端发上来时只用 {@code text}，下行时带上发言人与位置）。
     *
     * <p>{@code x/z} 是广播时刻服务端记录的坐标：气泡要挂在那个人当时站的地方，
     * 用接收方收到时的位置会让别人走动时气泡跟着飘。</p>
     *
     * @param fromId 发言人用户ID
     * @param name   发言人显示名
     * @param text   内容
     * @param x      当时坐标 X
     * @param z      当时坐标 Z
     * @param at     服务端时间戳（毫秒）
     * @return 帧对象
     */
    public static Map<String, Object> say(long fromId, String name, String text, double x, double z, long at) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", TYPE_SAY);
        frame.put("from", String.valueOf(fromId));
        frame.put("name", name);
        frame.put("text", text);
        frame.put("x", x);
        frame.put("z", z);
        frame.put("at", at);
        return frame;
    }

    /**
     * 构造快照帧。
     *
     * @param sceneKey 场景标识
     * @param selfId   本人用户ID
     * @param peers    兴趣区内其他玩家
     * @return 帧对象
     */
    public static Map<String, Object> snapshot(String sceneKey, long selfId, List<Map<String, Object>> peers) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", TYPE_SNAPSHOT);
        frame.put("sceneKey", sceneKey);
        frame.put("selfId", String.valueOf(selfId));
        frame.put("peers", peers);
        return frame;
    }

    /**
     * 构造增量帧。
     *
     * @param peers 发生变化的玩家
     * @return 帧对象
     */
    public static Map<String, Object> delta(List<Map<String, Object>> peers) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", TYPE_DELTA);
        frame.put("peers", peers);
        return frame;
    }

    /**
     * 构造离开帧。
     *
     * @param ids 离开的玩家ID
     * @return 帧对象
     */
    public static Map<String, Object> leave(List<String> ids) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", TYPE_LEAVE);
        frame.put("ids", ids);
        return frame;
    }

    /**
     * 构造心跳应答帧。
     *
     * @return 帧对象
     */
    public static Map<String, Object> pong() {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", TYPE_PONG);
        return frame;
    }

    /**
     * 构造错误帧。
     *
     * @param code    错误码
     * @param message 说明
     * @return 帧对象
     */
    public static Map<String, Object> error(String code, String message) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", TYPE_ERROR);
        frame.put("code", code);
        frame.put("message", message);
        return frame;
    }
}
