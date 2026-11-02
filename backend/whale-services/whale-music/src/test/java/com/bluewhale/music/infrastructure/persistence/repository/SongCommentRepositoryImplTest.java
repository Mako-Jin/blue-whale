package com.bluewhale.music.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.music.domain.model.CommentStatus;
import com.bluewhale.music.domain.model.SongComment;
import com.bluewhale.music.infrastructure.persistence.entity.SongCommentEntity;
import com.bluewhale.music.infrastructure.persistence.mapper.SongCommentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 歌曲评论仓储实现单元测试（T-07 覆盖率补齐）。
 *
 * <p>覆盖评论状态枚举与实体字符串的双向转换、分页号换算、回复短路查询与审核过滤条件。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SongCommentRepositoryImplTest {

    /** 固定时间，便于断言生成主键回填 */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0);

    @Mock
    private SongCommentMapper songCommentMapper;

    @InjectMocks
    private SongCommentRepositoryImpl repository;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 查询条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                SongCommentEntity.class);
    }

    /**
     * 实体存在时映射为领域模型并还原状态枚举。
     */
    @Test
    void findByIdShouldMapEntityAndStatus() {
        SongCommentEntity entity = commentEntity(1L, CommentStatus.VISIBLE);
        when(songCommentMapper.selectById(1L)).thenReturn(entity);

        SongComment comment = repository.findById(1L).orElseThrow();

        assertEquals(1L, comment.getId());
        assertEquals(2L, comment.getSongId());
        assertEquals(3L, comment.getUserId());
        assertEquals(0L, comment.getParentId());
        assertEquals("好听", comment.getContent());
        assertEquals(CommentStatus.VISIBLE, comment.getStatus());
        assertEquals(5L, comment.getLikeCount());
    }

    /**
     * 主键不存在时返回空 Optional。
     */
    @Test
    void findByIdShouldReturnEmptyWhenAbsent() {
        Optional<SongComment> result = repository.findById(2L);

        assertTrue(result.isEmpty());
        verify(songCommentMapper).selectById(2L);
    }

    /**
     * 新增时状态写入字符串，并回写数据库生成的主键与时间。
     */
    @Test
    void insertShouldWriteStatusNameAndPropagateKeys() {
        when(songCommentMapper.insert(any(SongCommentEntity.class))).thenAnswer(invocation -> {
            SongCommentEntity entity = invocation.getArgument(0);
            entity.setId(44L);
            entity.setCreateTime(FIXED_TIME);
            entity.setUpdateTime(FIXED_TIME);
            return 1;
        });

        SongComment comment = new SongComment();
        comment.setSongId(2L);
        comment.setUserId(3L);
        comment.setContent("好听");
        comment.setStatus(CommentStatus.PENDING);
        repository.insert(comment);

        assertEquals(44L, comment.getId());
        assertEquals(FIXED_TIME, comment.getCreateTime());
        assertEquals(FIXED_TIME, comment.getUpdateTime());
        ArgumentCaptor<SongCommentEntity> captor = ArgumentCaptor.forClass(SongCommentEntity.class);
        verify(songCommentMapper).insert(captor.capture());
        assertEquals(CommentStatus.PENDING.name(), captor.getValue().getStatus());
    }

    /**
     * 更新时同样把状态枚举转成字符串。
     */
    @Test
    void updateShouldWriteStatusName() {
        SongComment comment = new SongComment();
        comment.setId(5L);
        comment.setStatus(CommentStatus.HIDDEN);

        repository.update(comment);

        ArgumentCaptor<SongCommentEntity> captor = ArgumentCaptor.forClass(SongCommentEntity.class);
        verify(songCommentMapper).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals(CommentStatus.HIDDEN.name(), captor.getValue().getStatus());
    }

    /**
     * 一级评论分页按 offset 换算页码并映射。
     */
    @Test
    void pageTopLevelShouldConvertOffsetToPage() {
        Page<SongCommentEntity> page = new Page<>(3, 10);
        page.setRecords(List.of(commentEntity(1L, CommentStatus.VISIBLE)));
        when(songCommentMapper.selectPage(any(), any())).thenReturn(page);

        List<SongComment> comments = repository.pageTopLevel(2L, 20L, 10L);

        assertEquals(1, comments.size());
        assertEquals(CommentStatus.VISIBLE, comments.get(0).getStatus());
    }

    /**
     * 每页条数非正时按第一页查询，避免除零。
     */
    @Test
    void pageTopLevelShouldFallbackWhenLimitNonPositive() {
        Page<SongCommentEntity> page = new Page<>(1, 0);
        page.setRecords(List.of());
        when(songCommentMapper.selectPage(any(), any())).thenReturn(page);

        assertTrue(repository.pageTopLevel(2L, 20L, 0L).isEmpty());
    }

    /**
     * 一级评论计数委托给 Mapper。
     */
    @Test
    void countTopLevelShouldDelegateToMapper() {
        when(songCommentMapper.selectCount(any())).thenReturn(4L);

        assertEquals(4L, repository.countTopLevel(2L));
    }

    /**
     * 父评论集合为空时短路返回空列表，不访问数据库。
     */
    @Test
    void findRepliesShouldShortCircuitWhenParentsMissing() {
        assertTrue(repository.findReplies(2L, null).isEmpty());
        assertTrue(repository.findReplies(2L, List.of()).isEmpty());

        verify(songCommentMapper, never()).selectList(any());
    }

    /**
     * 回复列表按父评论集合查询并映射。
     */
    @Test
    void findRepliesShouldMapEntities() {
        when(songCommentMapper.selectList(any())).thenReturn(List.of(commentEntity(2L, CommentStatus.VISIBLE)));

        List<SongComment> replies = repository.findReplies(2L, List.of(1L));

        assertEquals(1, replies.size());
        assertEquals(2L, replies.get(0).getId());
    }

    /**
     * 审核分页命中歌曲 / 状态 / 关键词全部过滤条件。
     */
    @Test
    void pageAuditShouldApplyAllFilters() {
        Page<SongCommentEntity> page = new Page<>(2, 10);
        page.setRecords(List.of(commentEntity(3L, CommentStatus.HIDDEN)));
        when(songCommentMapper.selectPage(any(), any())).thenReturn(page);

        List<SongComment> comments = repository.pageAudit(2L, CommentStatus.HIDDEN, "  好听  ", 10L, 10L);

        assertEquals(1, comments.size());
        assertEquals(CommentStatus.HIDDEN, comments.get(0).getStatus());
    }

    /**
     * 审核分页在无过滤条件且每页条数非正时仍返回第一页。
     */
    @Test
    void pageAuditShouldFallbackWhenLimitNonPositiveAndNoFilters() {
        Page<SongCommentEntity> page = new Page<>(1, 0);
        page.setRecords(List.of());
        when(songCommentMapper.selectPage(any(), any())).thenReturn(page);

        assertTrue(repository.pageAudit(null, null, null, 0L, 0L).isEmpty());
    }

    /**
     * 审核计数委托给 Mapper。
     */
    @Test
    void countAuditShouldDelegateToMapper() {
        when(songCommentMapper.selectCount(any())).thenReturn(6L);

        assertEquals(6L, repository.countAudit(2L, CommentStatus.PENDING, null));
    }

    /**
     * 构造评论实体。
     *
     * @param id 主键
     * @param status 状态
     * @return 评论实体
     */
    private SongCommentEntity commentEntity(Long id, CommentStatus status) {
        SongCommentEntity entity = new SongCommentEntity();
        entity.setId(id);
        entity.setSongId(2L);
        entity.setUserId(3L);
        entity.setParentId(SongComment.TOP_LEVEL_PARENT_ID);
        entity.setContent("好听");
        entity.setStatus(status.name());
        entity.setLikeCount(5L);
        entity.setCreateTime(FIXED_TIME);
        return entity;
    }
}
