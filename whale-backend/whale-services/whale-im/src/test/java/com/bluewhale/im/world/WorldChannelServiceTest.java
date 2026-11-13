package com.bluewhale.im.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 世界频道服务单测：兴趣区裁剪、下行聚合、场景隔离、配额与超时清理。
 *
 * <p>这里断言的是「谁该收到什么」，不碰真实连接——服务本身不做 IO，
 * 所以这些规则能被确定性验证。手工起两个浏览器窗口也能看出来，
 * 但很难验证「限流窗口内确实没发」这种否定性行为。</p>
 *
 * @author BlueWhale
 */
class WorldChannelServiceTest {

    /** 测试用时钟（毫秒，可手工推进） */
    private final AtomicLong now = new AtomicLong(1_000L);

    /**
     * 构造被测服务：兴趣区 120 米、单场景上限 3 人、聚合窗口 200ms。
     *
     * @return 服务实例
     */
    private WorldChannelService service() {
        return new WorldChannelService(120D, 3, 200L, now::get);
    }

    @Test
    @DisplayName("快照只包含兴趣区内的其他玩家")
    void snapshotShouldOnlyContainPeersWithinAoi() {
        WorldChannelService service = service();
        // 近处（10 米）
        service.join("city", 2L, "近邻", "#112233", 10D, 10D, 0D);
        // 远处（200 米，超出 120 米兴趣区；坐标上限 200 内合法）
        service.join("city", 3L, "远客", "#445566", 190D, 190D, 0D);

        WorldChannelService.JoinOutcome outcome = service.join("city", 1L, "自己", "#aabbcc", 0D, 0D, 0D);
        assertTrue(outcome.ok(), "进场景应成功");

        List<?> peers = (List<?>) outcome.snapshot().get("peers");
        assertEquals(1, peers.size(), "只应看到兴趣区内的人");
        assertTrue(peers.get(0).toString().contains("近邻"), "看到的人应是近邻");
    }

    @Test
    @DisplayName("下行按 200ms 聚合：窗口内的帧不广播，放行帧带最新位置")
    void deltaShouldBeThrottled() {
        WorldChannelService service = service();
        service.join("city", 2L, "旁观", "#112233", 0D, 10D, 0D);
        service.join("city", 1L, "自己", "#aabbcc", 0D, 0D, 0D);

        // 首帧必发（lastBroadcast=0）
        WorldChannelService.UpdateOutcome first = service.update("city", 1L, 0D, 1D, 0D, 2D);
        assertTrue(first.hasBroadcast(), "首帧应立即广播");

        now.addAndGet(50L);
        WorldChannelService.UpdateOutcome throttled = service.update("city", 1L, 0D, 2D, 0D, 2D);
        assertFalse(throttled.hasBroadcast(), "聚合窗口内不应广播");

        now.addAndGet(200L);
        WorldChannelService.UpdateOutcome released = service.update("city", 1L, 0D, 3D, 0D, 2D);
        assertTrue(released.hasBroadcast(), "超出窗口后应放行");
        assertEquals(List.of(2L), released.broadcast().recipients(), "接收者应是兴趣区内的旁观者");
        List<?> peers = (List<?>) released.broadcast().frame().get("peers");
        assertTrue(peers.get(0).toString().contains("z=3.0"), "放行帧应携带最新位置：" + peers);
    }

    @Test
    @DisplayName("场景隔离：不同场景互不可见")
    void scenesShouldBeIsolated() {
        WorldChannelService service = service();
        service.join("city-a", 2L, "甲", "#112233", 0D, 0D, 0D);
        WorldChannelService.JoinOutcome outcome = service.join("city-b", 1L, "乙", "#aabbcc", 0D, 0D, 0D);
        assertTrue(outcome.ok());
        assertEquals(0, ((List<?>) outcome.snapshot().get("peers")).size(), "另一个场景的人不该出现");
        assertFalse(outcome.broadcast().recipients().contains(2L), "不该广播到另一个场景");
    }

    @Test
    @DisplayName("单场景人数上限生效，超限返回 SCENE_FULL")
    void sceneShouldRejectBeyondCapacity() {
        WorldChannelService service = service();
        assertEquals(true, service.join("city", 1L, "甲", "#112233", 0D, 0D, 0D).ok());
        assertEquals(true, service.join("city", 2L, "乙", "#112233", 0D, 0D, 0D).ok());
        assertEquals(true, service.join("city", 3L, "丙", "#112233", 0D, 0D, 0D).ok());

        WorldChannelService.JoinOutcome rejected = service.join("city", 4L, "丁", "#112233", 0D, 0D, 0D);
        assertFalse(rejected.ok(), "第 4 人应被拒");
        assertEquals(WorldFrames.CODE_SCENE_FULL, rejected.error().get("code"));
        assertEquals(3, service.peerCount("city"));
    }

    @Test
    @DisplayName("非法场景标识与非法坐标被拒绝")
    void invalidInputShouldBeRejected() {
        WorldChannelService service = service();
        WorldChannelService.JoinOutcome badScene = service.join("Blue Whale!", 1L, "甲", "#112233", 0D, 0D, 0D);
        assertFalse(badScene.ok());
        assertEquals(WorldFrames.CODE_BAD_SCENE, badScene.error().get("code"));

        WorldChannelService.JoinOutcome badCoord = service.join("city", 1L, "甲", "#112233", Double.NaN, 0D, 0D);
        assertFalse(badCoord.ok());
        assertEquals(WorldFrames.CODE_BAD_FRAME, badCoord.error().get("code"));

        // 坐标越界（城池 ±150，上限 200）
        WorldChannelService.JoinOutcome outOfRange = service.join("city", 1L, "甲", "#112233", 500D, 0D, 0D);
        assertFalse(outOfRange.ok());

        // 未进场景就发位置帧
        WorldChannelService.UpdateOutcome notJoined = service.update("city", 9L, 0D, 0D, 0D, 0D);
        assertNotNull(notJoined.error());
        assertEquals(WorldFrames.CODE_NOT_JOINED, notJoined.error().get("code"));
    }

    @Test
    @DisplayName("脏衣袍色回落默认值，超长名字截断")
    void profileShouldBeSanitized() {
        WorldChannelService service = service();
        service.join("city", 1L, "一二三四五六七八九十一二三四五六七八九十一二三四五六", "javascript:x", 0D, 0D, 0D);
        WorldChannelService.JoinOutcome outcome = service.join("city", 2L, "看客", "#aabbcc", 0D, 0D, 0D);
        Map<?, ?> frame = (Map<?, ?>) ((List<?>) outcome.snapshot().get("peers")).get(0);
        assertEquals("#2b4a63", frame.get("robe"), "非法色值应回落默认衣色");
        assertTrue(String.valueOf(frame.get("name")).length() <= 24, "名字应截断到 24 字");
    }

    @Test
    @DisplayName("离开后通知兴趣区内其他人，场景清空即回收")
    void leaveShouldNotifyAudience() {
        WorldChannelService service = service();
        service.join("city", 1L, "甲", "#112233", 0D, 0D, 0D);
        service.join("city", 2L, "乙", "#112233", 0D, 10D, 0D);

        WorldChannelService.Broadcast leave = service.leave("city", 1L);
        assertNotNull(leave);
        assertEquals(List.of(2L), leave.recipients());
        assertEquals(WorldFrames.TYPE_LEAVE, leave.frame().get("type"));

        WorldChannelService.Broadcast last = service.leave("city", 2L);
        assertNull(last, "最后一人离开无需广播");
        assertEquals(0, service.peerCount("city"), "空场景应被回收");
    }

    @Test
    @DisplayName("30 秒无帧的玩家被摘除并广播离场")
    void stalePeerShouldBeSwept() {
        WorldChannelService service = service();
        service.join("city", 1L, "甲", "#112233", 0D, 0D, 0D);
        service.join("city", 2L, "乙", "#112233", 0D, 5D, 0D);

        now.addAndGet(31_000L);
        service.join("city", 3L, "丙", "#112233", 0D, -5D, 0D);

        WorldChannelService.Broadcast swept = service.sweepStale("city");
        assertNotNull(swept, "应摘除超时玩家");
        assertEquals(WorldFrames.TYPE_LEAVE, swept.frame().get("type"));
        assertFalse(service.sceneKeysOf(1L).contains("city"), "超时玩家应已退场");
        assertTrue(service.sceneKeysOf(3L).contains("city"), "新玩家应仍在场");
    }

    @Test
    @DisplayName("喊话广播给兴趣区内的人，也回显给自己")
    void sayShouldReachAoiAndSelf() {
        WorldChannelService service = service();
        service.join("city", 1L, "甲", "#112233", 0D, 0D, 0D);
        service.join("city", 2L, "近邻", "#112233", 10D, 0D, 0D);
        // 190,190 距原点约 269 米，超出 120 米兴趣区
        service.join("city", 3L, "远客", "#112233", 190D, 190D, 0D);

        WorldChannelService.UpdateOutcome outcome = service.say("city", 1L, "见过主公");
        assertNull(outcome.error(), "正常喊话不应报错");
        assertTrue(outcome.hasBroadcast(), "应该有广播");

        List<Long> recipients = outcome.broadcast().recipients();
        assertTrue(recipients.contains(1L), "要回显给自己（否则玩家以为没说出去）");
        assertTrue(recipients.contains(2L), "兴趣区内的人应收到");
        assertFalse(recipients.contains(3L), "兴趣区外的人不应收到");

        Map<String, Object> frame = outcome.broadcast().frame();
        assertEquals(WorldFrames.TYPE_SAY, frame.get("type"));
        assertEquals("见过主公", frame.get("text"));
        assertEquals("甲", frame.get("name"), "气泡要带发言人名字");
    }

    @Test
    @DisplayName("喊话空内容被拒、超长被截断")
    void sayShouldRejectEmptyAndTrimLong() {
        WorldChannelService service = service();
        service.join("city", 1L, "甲", "#112233", 0D, 0D, 0D);

        assertEquals(WorldFrames.CODE_BAD_SAY, service.say("city", 1L, "   ").error().get("code"),
                "纯空白应被拒");

        now.addAndGet(2_000L);
        String longText = "言".repeat(200);
        WorldChannelService.UpdateOutcome outcome = service.say("city", 1L, longText);
        assertNull(outcome.error(), "超长只截断不拒发");
        assertEquals(120, ((String) outcome.broadcast().frame().get("text")).length(), "应截断到 120 字");
    }

    @Test
    @DisplayName("1.5 秒内第二次喊话被限流，过了窗口即可再说")
    void sayShouldBeRateLimited() {
        WorldChannelService service = service();
        service.join("city", 1L, "甲", "#112233", 0D, 0D, 0D);

        assertNull(service.say("city", 1L, "第一句").error(), "首句应放行");

        now.addAndGet(500L);
        WorldChannelService.UpdateOutcome second = service.say("city", 1L, "第二句");
        assertEquals(WorldFrames.CODE_SAY_LIMITED, second.error().get("code"), "500ms 内应被限流");

        now.addAndGet(1_200L);
        assertNull(service.say("city", 1L, "第三句").error(), "累计 1.7 秒后应放行");
    }

    @Test
    @DisplayName("未进场景就喊话被拒")
    void sayShouldRequireJoin() {
        WorldChannelService service = service();
        WorldChannelService.UpdateOutcome outcome = service.say("city", 9L, "有人吗");
        assertNotNull(outcome.error(), "未进场景不应成功");
        assertEquals(WorldFrames.CODE_NOT_JOINED, outcome.error().get("code"));
    }
}
