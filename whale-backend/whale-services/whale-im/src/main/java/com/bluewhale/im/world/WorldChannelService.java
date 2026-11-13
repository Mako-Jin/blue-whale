package com.bluewhale.im.world;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * 世界频道服务：按「场景 + 距离」裁剪转发，内存态，不落库。
 *
 * <p>三个关键约束（都是为了让高频帧不把服务拖垮）：</p>
 * <ol>
 *   <li><b>兴趣区裁剪</b>：只把玩家附近（默认 120 米）的人发给它，远处的人不进它的包；</li>
 *   <li><b>5Hz 聚合</b>：客户端 10Hz 上报，下行每个玩家最多 5Hz，被限流丢弃的帧只标记
 *       {@code dirty}，下次放行时自动带走最新位置——丢位置不丢状态；</li>
 *   <li><b>上限与清理</b>：单场景人数上限、半开连接 30 秒无帧自动摘除。</li>
 * </ol>
 *
 * <p>本类不做任何网络 IO：每次调用返回「给谁发什么」（{@link Broadcast}），
 * 由 WebSocket 处理器交给会话注册表发送。这样单测可以完全脱离连接，
 * 直接把「谁该收到什么」断言出来。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class WorldChannelService {

    /** 兴趣区默认半径（米）：城内可视距离约 120 米，超出则不转发 */
    private static final double DEFAULT_AOI_RADIUS = 120D;

    /** 单场景默认人数上限 */
    private static final int DEFAULT_MAX_PEERS = 120;

    /** 下行默认聚合间隔（毫秒）：200ms ≈ 5Hz */
    private static final long DEFAULT_DELTA_INTERVAL_MS = 200L;

    /** 半开连接判定阈值（毫秒） */
    private static final long PEER_STALE_MS = 30_000L;

    /** 客户端最小帧间隔（毫秒）：约 60Hz 上限，防刷 */
    private static final long MIN_FRAME_INTERVAL_MS = 16L;

    /** 单条喊话最大字符数（超出截断而不是拒发：多打一个字就发不出去太挫） */
    private static final int MAX_SAY_CHARS = 120;

    /** 喊话最小间隔（毫秒）：1.5 秒一条，够打招呼但刷不成屏 */
    private static final long SAY_INTERVAL_MS = 1_500L;

    /** 坐标合法上限（米）：城池 ±150，留余量；越界视为脏帧 */
    private static final double COORD_LIMIT = 200D;

    /** 场景标识格式 */
    private static final Pattern SCENE_KEY = Pattern.compile("[a-z0-9][a-z0-9-]{1,63}");

    /** 衣袍色格式 */
    private static final Pattern ROBE_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    /** 默认衣袍色（脏数据兜底） */
    private static final String DEFAULT_ROBE = "#2b4a63";

    /** 场景 → 玩家表 */
    private final Map<String, SceneState> scenes = new ConcurrentHashMap<>();

    /** 时钟（可注入，便于测试聚合与超时） */
    private final LongSupplier clock;

    /** 兴趣区半径（米） */
    private final double aoiRadius;

    /** 单场景人数上限 */
    private final int maxPeers;

    /** 下行聚合间隔（毫秒） */
    private final long deltaIntervalMs;

    /**
     * 构造世界频道服务。
     *
     * @param aoiRadius       兴趣区半径（米）
     * @param maxPeers        单场景人数上限
     * @param deltaIntervalMs 下行聚合间隔（毫秒）
     */
    @Autowired
    public WorldChannelService(
            @Value("${whale.im.world.aoi-radius:120}") double aoiRadius,
            @Value("${whale.im.world.max-peers:120}") int maxPeers,
            @Value("${whale.im.world.delta-interval-ms:200}") long deltaIntervalMs) {
        this(aoiRadius, maxPeers, deltaIntervalMs, System::currentTimeMillis);
    }

    /**
     * 测试用构造：可注入时钟。
     *
     * @param aoiRadius       兴趣区半径（米）
     * @param maxPeers        单场景人数上限
     * @param deltaIntervalMs 下行聚合间隔（毫秒）
     * @param clock           时间源
     */
    WorldChannelService(double aoiRadius, int maxPeers, long deltaIntervalMs, LongSupplier clock) {
        this.aoiRadius = aoiRadius > 0 ? aoiRadius : DEFAULT_AOI_RADIUS;
        this.maxPeers = maxPeers > 0 ? maxPeers : DEFAULT_MAX_PEERS;
        this.deltaIntervalMs = deltaIntervalMs > 0 ? deltaIntervalMs : DEFAULT_DELTA_INTERVAL_MS;
        this.clock = clock;
    }

    /**
     * 一次下行广播：给哪些人、发什么帧。
     *
     * @param recipients 接收者用户ID
     * @param frame      帧对象
     */
    public record Broadcast(List<Long> recipients, Map<String, Object> frame) {
    }

    /**
     * 进场景结果。
     *
     * @param error   错误帧（成功时为 {@code null}）
     * @param snapshot 全量快照帧（含兴趣区内其他玩家）
     * @param broadcast 需要广播给其他人的「新人进场」增量
     */
    public record JoinOutcome(Map<String, Object> error, Map<String, Object> snapshot, Broadcast broadcast) {

        /** @return 是否进场景成功 */
        public boolean ok() {
            return error == null;
        }
    }

    /**
     * 位置帧结果。
     *
     * @param error     错误帧（正常时为 {@code null}）
     * @param broadcast 需要广播的增量；被限流或无需广播时为 {@code null}
     */
    public record UpdateOutcome(Map<String, Object> error, Broadcast broadcast) {

        /** @return 是否需要广播 */
        public boolean hasBroadcast() {
            return broadcast != null;
        }
    }

    /**
     * 进入场景：登记玩家并回全量快照。
     *
     * <p>重复 join 视为「换场景/重连」：先按旧场景退场再登记，避免残留幽灵。</p>
     *
     * @param sceneKey 场景标识
     * @param userId   用户ID
     * @param name     显示名
     * @param robe     衣袍主色
     * @param x        世界坐标 X
     * @param z        世界坐标 Z
     * @param yaw      朝向
     * @return 进场景结果
     */
    public JoinOutcome join(String sceneKey, long userId, String name, String robe,
                            double x, double z, double yaw) {
        if (!SCENE_KEY.matcher(sceneKey == null ? "" : sceneKey).matches()) {
            return new JoinOutcome(WorldFrames.error(WorldFrames.CODE_BAD_SCENE, "场景标识非法"), null, null);
        }
        if (!validPosition(x, z)) {
            return new JoinOutcome(WorldFrames.error(WorldFrames.CODE_BAD_FRAME, "坐标非法"), null, null);
        }
        long now = clock.getAsLong();
        leaveAll(userId, sceneKey);

        SceneState state = scenes.computeIfAbsent(sceneKey, key -> new SceneState());
        if (state.peers.size() >= maxPeers && !state.peers.containsKey(userId)) {
            return new JoinOutcome(WorldFrames.error(WorldFrames.CODE_SCENE_FULL, "场景人数已满"), null, null);
        }

        String safeName = safeName(name, userId);
        String safeRobe = ROBE_COLOR.matcher(robe == null ? "" : robe).matches() ? robe : DEFAULT_ROBE;
        WorldPeer peer = new WorldPeer(userId, sceneKey, safeName, safeRobe, x, z, normalizeYaw(yaw), now);
        state.peers.put(userId, peer);

        // 快照只含兴趣区内其他人
        List<Map<String, Object>> peers = new ArrayList<>();
        for (WorldPeer other : state.peers.values()) {
            if (other.getUserId() != userId && other.within(x, z, aoiRadius)) {
                peers.add(other.toFrame());
            }
        }
        Map<String, Object> snapshot = WorldFrames.snapshot(sceneKey, userId, peers);

        Broadcast enter = new Broadcast(audience(sceneKey, userId, x, z), WorldFrames.delta(List.of(peer.toFrame())));
        log.debug("世界频道进场: scene={}, userId={}, 同屏={}", sceneKey, userId, peers.size());
        return new JoinOutcome(null, snapshot, enter);
    }

    /**
     * 更新位置：写入最新状态，按聚合间隔决定是否广播。
     *
     * @param sceneKey 场景标识
     * @param userId   用户ID
     * @param x        世界坐标 X
     * @param z        世界坐标 Z
     * @param yaw      朝向
     * @param speed    速度
     * @return 更新结果
     */
    public UpdateOutcome update(String sceneKey, long userId, double x, double z, double yaw, double speed) {
        SceneState state = scenes.get(sceneKey);
        WorldPeer peer = state == null ? null : state.peers.get(userId);
        if (peer == null) {
            return new UpdateOutcome(WorldFrames.error(WorldFrames.CODE_NOT_JOINED, "尚未进入场景"), null);
        }
        if (!validPosition(x, z)) {
            return new UpdateOutcome(WorldFrames.error(WorldFrames.CODE_BAD_FRAME, "坐标非法"), null);
        }
        long now = clock.getAsLong();
        // 入帧限流：过密直接丢弃（位置是幂等的，丢一帧不影响观感）。
        // 注意要放过「进场后的第一帧」——它同时承担"我到位了"的语义，
        // 若被限流吞掉，别人要等下一次放行才能看到这个人。
        if (peer.getLastBroadcast() != 0L && now - peer.getLastSeen() < MIN_FRAME_INTERVAL_MS) {
            return new UpdateOutcome(null, null);
        }
        peer.move(x, z, normalizeYaw(yaw), clampSpeed(speed), now);

        boolean firstFrame = peer.getLastBroadcast() == 0L;
        if (!firstFrame && now - peer.getLastBroadcast() < deltaIntervalMs) {
            // 聚合窗口内：保持 dirty，等下一个放行帧带走最新位置
            return new UpdateOutcome(null, null);
        }
        peer.setLastBroadcast(now);
        peer.setDirty(false);
        return new UpdateOutcome(null,
                new Broadcast(audience(sceneKey, userId, x, z), WorldFrames.delta(List.of(peer.toFrame()))));
    }

    /**
     * 同城喊话：广播给兴趣区内的人（含发言人自己）。
     *
     * <p>为什么把发言人也算进接收者：客户端收到服务端回显才算"说出去了"，
     * 本地先显示、再等服务端确认的写法，一旦被限流会出现"我说了但别人没听见"的错觉。</p>
     *
     * <p>三种拒绝各用各的码：未进场景（{@link WorldFrames#CODE_NOT_JOINED}）、
     * 空内容（{@link WorldFrames#CODE_BAD_SAY}）、太频（{@link WorldFrames#CODE_SAY_LIMITED}）。
     * 太频不静默丢弃——玩家会以为卡了，明确告诉他"太快了"更省事。</p>
     *
     * @param sceneKey 场景标识
     * @param userId   用户ID
     * @param text     喊话内容
     * @return 喊话结果（成功时 broadcast 非空）
     */
    public UpdateOutcome say(String sceneKey, long userId, String text) {
        SceneState state = scenes.get(sceneKey);
        WorldPeer peer = state == null ? null : state.peers.get(userId);
        if (peer == null) {
            return new UpdateOutcome(WorldFrames.error(WorldFrames.CODE_NOT_JOINED, "尚未进入场景"), null);
        }
        String content = text == null ? "" : text.trim().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        if (content.isEmpty()) {
            return new UpdateOutcome(WorldFrames.error(WorldFrames.CODE_BAD_SAY, "不能说空话"), null);
        }
        if (content.length() > MAX_SAY_CHARS) {
            content = content.substring(0, MAX_SAY_CHARS);
        }
        long now = clock.getAsLong();
        if (peer.getLastSayAt() != 0L && now - peer.getLastSayAt() < SAY_INTERVAL_MS) {
            return new UpdateOutcome(WorldFrames.error(WorldFrames.CODE_SAY_LIMITED, "说得太快了"), null);
        }
        peer.setLastSayAt(now);

        // 接收者 = AOI 内其他人 + 自己
        List<Long> recipients = new ArrayList<>(audience(sceneKey, userId, peer.getX(), peer.getZ()));
        recipients.add(userId);
        return new UpdateOutcome(null, new Broadcast(recipients,
                WorldFrames.say(userId, peer.getName(), content, peer.getX(), peer.getZ(), now)));
    }

    /**
     * 离开场景。
     *
     * @param sceneKey 场景标识
     * @param userId   用户ID
     * @return 需要广播的退场帧；该玩家不在场景内时为 {@code null}
     */
    public Broadcast leave(String sceneKey, long userId) {
        SceneState state = scenes.get(sceneKey);
        if (state == null) {
            return null;
        }
        WorldPeer removed = state.peers.remove(userId);
        if (removed == null) {
            return null;
        }
        if (state.peers.isEmpty()) {
            scenes.remove(sceneKey);
            return null;
        }
        return new Broadcast(
                audience(sceneKey, userId, removed.getX(), removed.getZ()),
                WorldFrames.leave(List.of(String.valueOf(userId))));
    }

    /**
     * 摘除场景内的超时玩家（半开连接）。
     *
     * @param sceneKey 场景标识
     * @return 退场广播；无超时玩家时为 {@code null}
     */
    public Broadcast sweepStale(String sceneKey) {
        SceneState state = scenes.get(sceneKey);
        if (state == null) {
            return null;
        }
        long now = clock.getAsLong();
        if (now - state.lastSweep.get() < 5_000L) {
            return null;
        }
        state.lastSweep.set(now);
        List<String> stale = new ArrayList<>();
        for (WorldPeer peer : state.peers.values()) {
            if (peer.isStale(now, PEER_STALE_MS) && state.peers.remove(peer.getUserId()) != null) {
                stale.add(String.valueOf(peer.getUserId()));
            }
        }
        if (stale.isEmpty()) {
            return null;
        }
        log.info("世界频道摘除超时玩家: scene={}, count={}", sceneKey, stale.size());
        return new Broadcast(new ArrayList<>(state.peers.keySet()), WorldFrames.leave(stale));
    }

    /**
     * 查询某用户当前所在的场景（正常至多一个，重连瞬间可能残留，故返回集合）。
     *
     * @param userId 用户ID
     * @return 场景标识集合
     */
    public List<String> sceneKeysOf(long userId) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, SceneState> entry : scenes.entrySet()) {
            if (entry.getValue().peers.containsKey(userId)) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    /**
     * 查询场景在线人数。
     *
     * @param sceneKey 场景标识
     * @return 在线人数
     */
    public int peerCount(String sceneKey) {
        SceneState state = scenes.get(sceneKey);
        return state == null ? 0 : state.peers.size();
    }

    /**
     * 计算兴趣区内除本人外的接收者。
     *
     * @param sceneKey 场景标识
     * @param selfId   本人用户ID
     * @param x        广播源 X
     * @param z        广播源 Z
     * @return 接收者用户ID
     */
    private List<Long> audience(String sceneKey, long selfId, double x, double z) {
        SceneState state = scenes.get(sceneKey);
        if (state == null) {
            return List.of();
        }
        List<Long> recipients = new ArrayList<>();
        for (WorldPeer other : state.peers.values()) {
            if (other.getUserId() != selfId && other.within(x, z, aoiRadius)) {
                recipients.add(other.getUserId());
            }
        }
        return recipients;
    }

    /**
     * 把同一用户从其他场景摘掉（防止换场景后残留幽灵）。
     *
     * @param userId       用户ID
     * @param keepSceneKey 保留的场景
     */
    private void leaveAll(long userId, String keepSceneKey) {
        for (Map.Entry<String, SceneState> entry : scenes.entrySet()) {
            if (entry.getKey().equals(keepSceneKey)) {
                continue;
            }
            entry.getValue().peers.remove(userId);
        }
    }

    /**
     * 坐标合法性：必须是有限数且在城池范围内。
     *
     * @param x 世界坐标 X
     * @param z 世界坐标 Z
     * @return 合法返回 {@code true}
     */
    private boolean validPosition(double x, double z) {
        return Double.isFinite(x) && Double.isFinite(z)
                && Math.abs(x) <= COORD_LIMIT && Math.abs(z) <= COORD_LIMIT;
    }

    /**
     * 朝向归一化到 (-π, π]。
     *
     * @param yaw 原始朝向
     * @return 归一化朝向
     */
    private double normalizeYaw(double yaw) {
        if (!Double.isFinite(yaw)) {
            return 0D;
        }
        double value = yaw % (Math.PI * 2);
        if (value > Math.PI) {
            value -= Math.PI * 2;
        } else if (value <= -Math.PI) {
            value += Math.PI * 2;
        }
        return value;
    }

    /**
     * 速度裁剪（城池内不可能超过 30 米/秒）。
     *
     * @param speed 原始速度
     * @return 裁剪后速度
     */
    private double clampSpeed(double speed) {
        if (!Double.isFinite(speed)) {
            return 0D;
        }
        return Math.max(0D, Math.min(30D, speed));
    }

    /**
     * 名字兜底：去空白、限长，空名用「过客{id尾号}」。
     *
     * @param name   原始名字
     * @param userId 用户ID
     * @return 安全名字
     */
    private String safeName(String name, long userId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "过客" + Math.abs(userId % 1000L);
        }
        return trimmed.length() > 24 ? trimmed.substring(0, 24) : trimmed;
    }

    /**
     * 单场景状态。
     */
    private static final class SceneState {

        /** 玩家表 */
        private final Map<Long, WorldPeer> peers = new ConcurrentHashMap<>();

        /** 上次超时扫描时间 */
        private final AtomicLong lastSweep = new AtomicLong(0L);
    }

    /**
     * 帧字段校验辅助：把 JSON 数字安全转成 double。
     *
     * @param value 原始值
     * @param fallback 兜底值
     * @return 数值
     */
    public static double numberOr(Object value, double fallback) {
        if (value instanceof Number number) {
            double result = number.doubleValue();
            return Double.isFinite(result) ? result : fallback;
        }
        return fallback;
    }

    /**
     * 构造一个空的下行帧容器（供处理器聚合多个增量时使用）。
     *
     * @return 空帧
     */
    public static Map<String, Object> emptyFrame() {
        return new LinkedHashMap<>();
    }
}
