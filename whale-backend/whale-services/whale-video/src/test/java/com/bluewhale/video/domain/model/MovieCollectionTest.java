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
 * 电影片单领域模型单元测试（M4-4）：标题校验、状态流转与条目上限。
 *
 * @author BlueWhale
 */
class MovieCollectionTest {

    /**
     * 标题为空或超长时拒绝设置（40001）。
     */
    @Test
    void shouldRejectInvalidTitle() {
        MovieCollection collection = new MovieCollection();
        assertEquals(VideoErrorCode.CONTENT_TITLE_INVALID,
                assertThrows(BizException.class, () -> collection.retitle("  ")).getCode());
        StringBuilder longTitle = new StringBuilder();
        for (int index = 0; index < MovieCollection.TITLE_MAX_LENGTH + 1; index++) {
            longTitle.append('片');
        }
        assertEquals(VideoErrorCode.CONTENT_TITLE_INVALID,
                assertThrows(BizException.class, () -> collection.retitle(longTitle.toString())).getCode());
    }

    /**
     * 上架记录上架时间，重复上架幂等。
     */
    @Test
    void shouldPublishAndKeepIdempotent() {
        MovieCollection collection = new MovieCollection();
        collection.retitle("深海影院");
        collection.setPublishState(PublishState.DRAFT);
        collection.publish();
        assertEquals(PublishState.PUBLISHED, collection.getPublishState());
        assertNotNull(collection.getPublishedAt());
        collection.publish();
        assertEquals(PublishState.PUBLISHED, collection.getPublishState());
        assertNotNull(collection.getPublishedAt());
    }

    /**
     * 草稿不可下架（40901），已上架可下架且重复下架幂等。
     */
    @Test
    void shouldRejectOfflineDraftAndKeepOfflineIdempotent() {
        MovieCollection draft = new MovieCollection();
        draft.setPublishState(PublishState.DRAFT);
        assertEquals(VideoErrorCode.CONTENT_STATE_CONFLICT,
                assertThrows(BizException.class, draft::offline).getCode());

        MovieCollection published = new MovieCollection();
        published.setPublishState(PublishState.PUBLISHED);
        published.offline();
        assertEquals(PublishState.OFFLINE, published.getPublishState());
        published.offline();
        assertEquals(PublishState.OFFLINE, published.getPublishState());
    }

    /**
     * 仅已上架片单对用户侧可见。
     */
    @Test
    void shouldOnlyPublishVisible() {
        MovieCollection collection = new MovieCollection();
        collection.setPublishState(PublishState.DRAFT);
        assertFalse(collection.published());
        collection.setPublishState(PublishState.PUBLISHED);
        assertTrue(collection.published());
    }

    /**
     * 条目数刷新不允许负数。
     */
    @Test
    void shouldClampItemCount() {
        MovieCollection collection = new MovieCollection();
        collection.refreshItemCount(-3);
        assertEquals(0, collection.getItemCount());
        collection.refreshItemCount(5);
        assertEquals(5, collection.getItemCount());
    }

    /**
     * 达到上限后不允许继续加片（40903）。
     */
    @Test
    void shouldRejectAddItemBeyondLimit() {
        MovieCollection collection = new MovieCollection();
        collection.assertCanAddItem(MovieCollection.MAX_ITEMS - 1);
        assertEquals(VideoErrorCode.COLLECTION_ITEM_CONFLICT,
                assertThrows(BizException.class,
                        () -> collection.assertCanAddItem(MovieCollection.MAX_ITEMS)).getCode());
    }
}
