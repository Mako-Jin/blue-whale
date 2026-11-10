package com.bluewhale.video.application.scheduler;

import com.bluewhale.video.domain.model.Content;
import com.bluewhale.video.domain.model.ContentType;
import com.bluewhale.video.domain.model.ContentVisibility;
import com.bluewhale.video.domain.model.CopyrightState;
import com.bluewhale.video.domain.model.PublishState;
import com.bluewhale.video.domain.repository.ContentRepository;
import com.bluewhale.video.domain.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 电影授权到期自动下线任务单元测试（M4-3）。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MovieLicenseExpirySchedulerTest {

    /** 已上架电影内容 ID */
    private static final long PUBLISHED_ID = 101L;

    /** 已下线电影内容 ID */
    private static final long OFFLINE_ID = 102L;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private ContentRepository contentRepository;

    @InjectMocks
    private MovieLicenseExpiryScheduler scheduler;

    /**
     * 仅已上架内容切到下架，已下线内容直接跳过。
     */
    @Test
    void shouldOfflineExpiredPublishedContents() {
        when(movieRepository.listExpiredLicenseContentIds(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(PUBLISHED_ID, OFFLINE_ID));
        when(contentRepository.findById(PUBLISHED_ID))
                .thenReturn(Optional.of(content(PUBLISHED_ID, PublishState.PUBLISHED)));
        when(contentRepository.findById(OFFLINE_ID))
                .thenReturn(Optional.of(content(OFFLINE_ID, PublishState.OFFLINE)));

        scheduler.offlineExpired();

        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).update(captor.capture());
        assertEquals(PublishState.OFFLINE, captor.getValue().getPublishState());
        assertEquals(PUBLISHED_ID, captor.getValue().getId());
    }

    /**
     * 单条失败仅告警，不影响后续内容处理。
     */
    @Test
    void shouldContinueWhenSingleContentFails() {
        when(movieRepository.listExpiredLicenseContentIds(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(PUBLISHED_ID, OFFLINE_ID));
        when(contentRepository.findById(PUBLISHED_ID)).thenThrow(new IllegalStateException("数据库抖动"));
        when(contentRepository.findById(OFFLINE_ID))
                .thenReturn(Optional.of(content(OFFLINE_ID, PublishState.PUBLISHED)));

        scheduler.offlineExpired();

        verify(contentRepository).update(any(Content.class));
    }

    /**
     * 无到期内容时不写库。
     */
    @Test
    void shouldSkipWhenNoExpiredLicense() {
        when(movieRepository.listExpiredLicenseContentIds(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of());

        scheduler.offlineExpired();

        verify(contentRepository, never()).update(any(Content.class));
    }

    /**
     * 构造电影内容。
     *
     * @param id 内容 ID
     * @param state 发布状态
     * @return 内容
     */
    private Content content(long id, PublishState state) {
        Content content = new Content();
        content.setId(id);
        content.setContentType(ContentType.MOVIE);
        content.setTitle("电影" + id);
        content.setPublishState(state);
        content.setCopyrightState(CopyrightState.AUTHORIZED);
        content.setVisibility(ContentVisibility.PUBLIC);
        return content;
    }
}
