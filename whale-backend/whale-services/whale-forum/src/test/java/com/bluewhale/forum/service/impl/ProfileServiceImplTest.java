package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.AuthorStatVO;
import com.bluewhale.forum.dto.ProfileVO;
import com.bluewhale.forum.entity.ForumUserBan;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.BanService;
import com.bluewhale.forum.service.ExpService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 个人主页单测：统计映射、鲸阶、禁言状态。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private ForumThreadMapper threadMapper;

    @Mock
    private ExpService expService;

    @Mock
    private BanService banService;

    private ProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
        profileService = new ProfileServiceImpl(threadMapper, expService,
                new LevelServiceImpl(new ForumProperties()), banService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("主页：聚合统计 + 经验换算鲸阶")
    void profileMapsStatsAndLevel() {
        AuthorStatVO stat = new AuthorStatVO();
        stat.setThreadCount(12L);
        stat.setLikeCount(34L);
        stat.setEssenceCount(2L);
        when(threadMapper.selectAuthorStats(1L, 100L)).thenReturn(stat);
        when(expService.totalOf(100L)).thenReturn(600L);

        ProfileVO vo = profileService.profile(100L);

        assertThat(vo.getUserId()).isEqualTo(100L);
        assertThat(vo.getThreadCount()).isEqualTo(12L);
        assertThat(vo.getLikeCount()).isEqualTo(34L);
        assertThat(vo.getEssenceCount()).isEqualTo(2L);
        assertThat(vo.getExpTotal()).isEqualTo(600L);
        assertThat(vo.getLevel()).isEqualTo(3);
        assertThat(vo.getLevelName()).isEqualTo("听潮");
        assertThat(vo.getNextLevelExp()).isEqualTo(2000L);
        assertThat(vo.getBanned()).isFalse();
    }

    @Test
    @DisplayName("主页：无统计行时三项归零，不返回 null")
    void profileDefaultsMissingStats() {
        when(threadMapper.selectAuthorStats(1L, 100L)).thenReturn(null);
        when(expService.totalOf(100L)).thenReturn(0L);

        ProfileVO vo = profileService.profile(100L);

        assertThat(vo.getThreadCount()).isZero();
        assertThat(vo.getLikeCount()).isZero();
        assertThat(vo.getEssenceCount()).isZero();
        assertThat(vo.getLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("主页：生效禁言只回类型与到期时间")
    void profileMapsActiveBan() {
        when(threadMapper.selectAuthorStats(1L, 100L)).thenReturn(new AuthorStatVO());
        when(expService.totalOf(100L)).thenReturn(0L);
        ForumUserBan ban = new ForumUserBan();
        ban.setBanType("MUTE");
        ban.setReason("刷屏");
        ban.setExpireTime(LocalDateTime.of(2026, 12, 1, 0, 0));
        when(banService.activeBan(100L)).thenReturn(ban);

        ProfileVO vo = profileService.profile(100L);

        assertThat(vo.getBanned()).isTrue();
        assertThat(vo.getBanType()).isEqualTo("MUTE");
        assertThat(vo.getBanUntil()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
    }
}
