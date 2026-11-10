package com.bluewhale.video.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.video.domain.exception.VideoErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 系列领域模型单元测试（M3-1）：标题校验、状态流转与集数刷新。
 *
 * @author BlueWhale
 */
class SeriesTest {

    /**
     * 标题为空或超长时拒绝设置（40001）。
     */
    @Test
    void shouldRejectInvalidTitle() {
        Series series = new Series();
        assertEquals(VideoErrorCode.CONTENT_TITLE_INVALID,
                assertThrows(BizException.class, () -> series.retitle("  ")).getCode());
        StringBuilder longTitle = new StringBuilder();
        for (int index = 0; index < Series.TITLE_MAX_LENGTH + 1; index++) {
            longTitle.append('长');
        }
        assertEquals(VideoErrorCode.CONTENT_TITLE_INVALID,
                assertThrows(BizException.class, () -> series.retitle(longTitle.toString())).getCode());
    }

    /**
     * 上架：切到 PUBLISHED 并记录上架时间，重复上架幂等。
     */
    @Test
    void shouldPublishAndKeepIdempotent() {
        Series series = new Series();
        series.retitle("深海探秘");
        series.setPublishState(PublishState.DRAFT);
        series.publish();
        assertEquals(PublishState.PUBLISHED, series.getPublishState());
        assertNotNull(series.getPublishedAt());
        series.publish();
        assertEquals(PublishState.PUBLISHED, series.getPublishState());
        assertNotNull(series.getPublishedAt());
    }

    /**
     * 草稿不可下架（40901），已上架可下架且重复下架幂等。
     */
    @Test
    void shouldRejectOfflineDraftAndKeepOfflineIdempotent() {
        Series draft = new Series();
        draft.setPublishState(PublishState.DRAFT);
        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class, draft::offline).getCode());

        Series published = new Series();
        published.setPublishState(PublishState.PUBLISHED);
        published.offline();
        assertEquals(PublishState.OFFLINE, published.getPublishState());
        published.offline();
        assertEquals(PublishState.OFFLINE, published.getPublishState());
    }

    /**
     * 仅已上架系列对用户侧可见。
     */
    @Test
    void shouldOnlyPublishVisible() {
        Series series = new Series();
        series.setPublishState(PublishState.DRAFT);
        assertFalse(series.published());
        series.setPublishState(PublishState.PUBLISHED);
        assertTrue(series.published());
    }

    /**
     * 集数刷新不允许负数。
     */
    @Test
    void shouldClampEpisodeCount() {
        Series series = new Series();
        series.refreshEpisodeCount(-3);
        assertEquals(0, series.getEpisodeCount());
        series.refreshEpisodeCount(5);
        assertEquals(5, series.getEpisodeCount());
    }
}
