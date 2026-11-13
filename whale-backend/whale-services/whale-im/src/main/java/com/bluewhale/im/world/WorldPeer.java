package com.bluewhale.im.world;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 世界频道里的一个在线玩家（场景内瞬时状态）。
 *
 * <p>只保存渲染别人所需的最小字段：id、名字、衣袍主色、位置与朝向。
 * 不保存形象细节（脸型/发式等参数）——客户端只按衣色即时生成同构形象，
 * 避免每帧携带体积较大的外观参数。</p>
 *
 * @author BlueWhale
 */
public final class WorldPeer {

    /** 用户ID（作为世界频道内的玩家标识） */
    private final long userId;

    /** 所在场景标识 */
    private final String sceneKey;

    /** 显示名 */
    private final String name;

    /** 衣袍主色（#rrggbb），客户端据此即时上色 */
    private final String robe;

    /** 世界坐标 X */
    private double x;

    /** 世界坐标 Z */
    private double z;

    /** 朝向（弧度） */
    private double yaw;

    /** 当前速度（米/秒，0 表示静止，供客户端决定是否播行走动作） */
    private double speed;

    /** 最近一次收到帧的时间戳（毫秒） */
    private long lastSeen;

    /** 最近一次对外广播的时间戳（毫秒，用于 5Hz 聚合） */
    private long lastBroadcast;

    /** 待广播标记：本周期被限流丢弃的帧会让它置位，下一次放行时一并带走 */
    private boolean dirty;

    /** 最近一次发言的时间戳（毫秒，用于发言限流；与位置帧限流各算各的） */
    private long lastSayAt;

    /**
     * 构造玩家状态。
     *
     * @param userId   用户ID
     * @param sceneKey 场景标识
     * @param name     显示名
     * @param robe     衣袍主色
     * @param x        世界坐标 X
     * @param z        世界坐标 Z
     * @param yaw      朝向
     * @param now      当前时间戳（毫秒）
     */
    public WorldPeer(long userId, String sceneKey, String name, String robe,
                     double x, double z, double yaw, long now) {
        this.userId = userId;
        this.sceneKey = sceneKey;
        this.name = name;
        this.robe = robe;
        this.x = x;
        this.z = z;
        this.yaw = yaw;
        this.speed = 0D;
        this.lastSeen = now;
        this.lastBroadcast = 0L;
        this.dirty = true;
    }

    /**
     * 更新位置并标记待广播。
     *
     * @param newX     世界坐标 X
     * @param newZ     世界坐标 Z
     * @param newYaw   朝向
     * @param newSpeed 速度
     * @param now      当前时间戳（毫秒）
     */
    public void move(double newX, double newZ, double newYaw, double newSpeed, long now) {
        this.x = newX;
        this.z = newZ;
        this.yaw = newYaw;
        this.speed = newSpeed;
        this.lastSeen = now;
        this.dirty = true;
    }

    /**
     * 判断与另一点的水平距离是否在兴趣区半径内。
     *
     * @param otherX 另一点 X
     * @param otherZ 另一点 Z
     * @param radius 半径
     * @return 在半径内返回 {@code true}
     */
    public boolean within(double otherX, double otherZ, double radius) {
        double dx = this.x - otherX;
        double dz = this.z - otherZ;
        return dx * dx + dz * dz <= radius * radius;
    }

    /**
     * 判断是否长时间无帧（半开连接）。
     *
     * @param now       当前时间戳（毫秒）
     * @param staleMillis 判定阈值
     * @return 超时返回 {@code true}
     */
    public boolean isStale(long now, long staleMillis) {
        return now - lastSeen > staleMillis;
    }

    /**
     * 序列化为下行帧里的玩家对象。
     *
     * <p>坐标保留三位小数：世界单位是米，毫米级精度足够，也能压缩 JSON 体积。</p>
     *
     * @return 帧字段映射
     */
    public Map<String, Object> toFrame() {
        Map<String, Object> peer = new LinkedHashMap<>();
        peer.put("id", String.valueOf(userId));
        peer.put("name", name);
        peer.put("robe", robe);
        peer.put("x", round3(x));
        peer.put("z", round3(z));
        peer.put("yaw", round3(yaw));
        peer.put("speed", round3(speed));
        return peer;
    }

    /**
     * 保留三位小数。
     *
     * @param value 原值
     * @return 四舍五入后的值
     */
    private static double round3(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    /** @return 用户ID */
    public long getUserId() {
        return userId;
    }

    /** @return 场景标识 */
    public String getSceneKey() {
        return sceneKey;
    }

    /** @return 世界坐标 X */
    public double getX() {
        return x;
    }

    /** @return 世界坐标 Z */
    public double getZ() {
        return z;
    }

    /** @return 最近心跳时间 */
    public long getLastSeen() {
        return lastSeen;
    }

    /** @return 最近广播时间 */
    public long getLastBroadcast() {
        return lastBroadcast;
    }

    /** @param value 最近广播时间 */
    public void setLastBroadcast(long value) {
        this.lastBroadcast = value;
    }

    /** @return 是否有待广播的变化 */
    public boolean isDirty() {
        return dirty;
    }

    /** @param value 是否待广播 */
    public void setDirty(boolean value) {
        this.dirty = value;
    }

    /** @return 最近发言时间 */
    public long getLastSayAt() {
        return lastSayAt;
    }

    /** @param value 最近发言时间 */
    public void setLastSayAt(long value) {
        this.lastSayAt = value;
    }

    /** @return 显示名（喊话气泡上要挂它） */
    public String getName() {
        return name;
    }
}
