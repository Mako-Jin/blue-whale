package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.BoardVO;
import com.bluewhale.forum.dto.TagStatVO;
import com.bluewhale.forum.mapper.ForumThreadTagMapper;
import com.bluewhale.forum.service.BoardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标签聚合服务单测：可见版块下推、空版块短路、条数上限收敛。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

    @Mock
    private ForumThreadTagMapper threadTagMapper;

    @Mock
    private BoardService boardService;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private TagServiceImpl tagService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("聚合：把可见版块下推 SQL，缺省条数取配置")
    void listPushesVisibleBoards() {
        when(boardService.listVisible(any())).thenReturn(List.of(board(9L), board(10L)));
        TagStatVO stat = new TagStatVO();
        stat.setTag("后端");
        stat.setThreadCount(3L);
        when(threadTagMapper.selectTagStats(1L, List.of(9L, 10L), properties.getTagListLimit()))
                .thenReturn(List.of(stat));

        List<TagStatVO> result = tagService.list(null, actor());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTag()).isEqualTo("后端");
        verify(threadTagMapper).selectTagStats(1L, List.of(9L, 10L), properties.getTagListLimit());
    }

    @Test
    @DisplayName("聚合：无可见版块直接空结果，不打库")
    void listReturnsEmptyWhenNoBoardVisible() {
        when(boardService.listVisible(any())).thenReturn(List.of());

        assertThat(tagService.list(50, actor())).isEmpty();
        verify(threadTagMapper, never()).selectTagStats(any(), any(), anyInt());
    }

    @Test
    @DisplayName("聚合：条数超硬上限按 200 截断")
    void listClampsLimitToHardCap() {
        when(boardService.listVisible(any())).thenReturn(List.of(board(9L)));
        when(threadTagMapper.selectTagStats(1L, List.of(9L), 200)).thenReturn(List.of());

        tagService.list(1000, actor());

        verify(threadTagMapper).selectTagStats(1L, List.of(9L), 200);
    }

    /**
     * 构造可见版块。
     *
     * @param id 版块ID
     * @return 版块视图
     */
    private BoardVO board(Long id) {
        BoardVO vo = new BoardVO();
        vo.setId(id);
        return vo;
    }

    /**
     * 构造操作者。
     *
     * @return 操作者
     */
    private ForumActor actor() {
        return new ForumActor(100L, Set.of("sys:forum:browse"), null);
    }
}
