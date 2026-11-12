package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.entity.ForumThreadView;
import com.bluewhale.forum.mapper.ForumThreadViewMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 浏览去重单测：窗口过期才计、首次未计、窗口内不再计、去重基建异常时降级放行。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ViewCountServiceImplTest {

    @Mock
    private ForumThreadViewMapper viewMapper;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private ViewCountServiceImpl viewCountService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("去重行已过期：条件更新命中，本次计入浏览")
    void expiredRowCounts() {
        when(viewMapper.touchIfExpired(eq(11L), eq(100L), any(LocalDateTime.class))).thenReturn(1);

        assertThat(viewCountService.touch(11L, 100L)).isTrue();
        verify(viewMapper, never()).insert(any(ForumThreadView.class));
    }

    @Test
    @DisplayName("首次浏览：插入去重行成功，本次计入")
    void firstViewInsertsRow() {
        when(viewMapper.touchIfExpired(anyLong(), anyLong(), any(LocalDateTime.class))).thenReturn(0);
        when(viewMapper.insert(any(ForumThreadView.class))).thenReturn(1);

        assertThat(viewCountService.touch(11L, 100L)).isTrue();
        verify(viewMapper).insert(any(ForumThreadView.class));
    }

    @Test
    @DisplayName("窗口内重复浏览：插入撞唯一键即不计")
    void repeatedViewWithinWindowIgnored() {
        when(viewMapper.touchIfExpired(anyLong(), anyLong(), any(LocalDateTime.class))).thenReturn(0);
        when(viewMapper.insert(any(ForumThreadView.class))).thenThrow(new DuplicateKeyException("uk_fm_thread_view"));

        assertThat(viewCountService.touch(11L, 100L)).isFalse();
    }

    @Test
    @DisplayName("去重基建异常时降级为计入，不阻断详情页")
    void infrastructureFailureDegradesToCounting() {
        when(viewMapper.touchIfExpired(anyLong(), anyLong(), any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("db down"));

        assertThat(viewCountService.touch(11L, 100L)).isTrue();
    }
}
