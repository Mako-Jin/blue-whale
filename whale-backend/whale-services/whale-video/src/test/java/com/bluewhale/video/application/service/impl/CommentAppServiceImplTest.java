package com.bluewhale.video.application.service.impl;

import com.bluewhale.video.config.VideoProperties;
import com.bluewhale.video.domain.repository.CommentLikeRepository;
import com.bluewhale.video.domain.repository.CommentRepository;
import com.bluewhale.video.domain.repository.ContentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论应用服务单测：聚焦「取消点赞」这条此前缺失的链路。
 *
 * <p>背景：前端 {@code likeComment(id, false)} 走的是 {@code DELETE /video/comment/{id}/like}，
 * 而服务端只实现了 POST，取消点赞一直是 405。补端点时必须同时确认计数口径——
 * 点赞加 1、取消减 1，且重复取消不能把计数扣成负数。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CommentAppServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private CommentLikeRepository commentLikeRepository;

    @Mock
    private VideoProperties videoProperties;

    @InjectMocks
    private CommentAppServiceImpl commentAppService;

    @Test
    @DisplayName("取消点赞：真的删掉点赞行才扣减计数")
    void unlikeDecrementsWhenRowRemoved() {
        when(commentLikeRepository.remove(7L, 42L)).thenReturn(true);

        commentAppService.unlike(42L, 7L);

        verify(commentRepository).addLikeCount(42L, -1L);
    }

    @Test
    @DisplayName("取消点赞：本来没点过赞，计数不动（幂等）")
    void unlikeIsIdempotentWhenNotLiked() {
        when(commentLikeRepository.remove(7L, 42L)).thenReturn(false);

        commentAppService.unlike(42L, 7L);

        verify(commentRepository, never()).addLikeCount(anyLong(), anyLong());
    }
}
