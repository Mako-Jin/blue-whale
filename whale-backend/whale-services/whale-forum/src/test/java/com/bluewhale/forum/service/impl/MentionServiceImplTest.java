package com.bluewhale.forum.service.impl;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.entity.ForumMention;
import com.bluewhale.forum.mapper.ForumMentionMapper;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.service.MentionService;
import com.bluewhale.forum.support.ForumUserDirectoryClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @提及服务单测：解析、跳过规则与事务提交后派发。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MentionServiceImplTest {

    @Mock
    private ForumMentionMapper mentionMapper;

    @Mock
    private ForumUserDirectoryClient directoryClient;

    @Mock
    private ForumNotifyService notifyService;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private MentionServiceImpl mentionService;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("正常提及：落一条记录并发一条通知")
    void dispatchesMention() {
        when(directoryClient.resolve(any(), any())).thenReturn(Map.of("张三", 200L));

        int notified = mentionService.dispatch(11L, 3L, MentionService.SOURCE_POST,
                "<p>@张三 看下这个</p>", "今日讨论", 100L);

        assertThat(notified).isEqualTo(1);
        ArgumentCaptor<ForumMention> captor = ArgumentCaptor.forClass(ForumMention.class);
        verify(mentionMapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(1L);
        assertThat(captor.getValue().getThreadId()).isEqualTo(11L);
        assertThat(captor.getValue().getPostId()).isEqualTo(3L);
        assertThat(captor.getValue().getUserId()).isEqualTo(200L);
        assertThat(captor.getValue().getSource()).isEqualTo(MentionService.SOURCE_POST);
        verify(notifyService).enqueue(eq(200L), eq("MENTION"), eq(11L),
                eq("forum:mention:11:3:200"), anyString(), anyString());
    }

    @Test
    @DisplayName("自己 @ 自己不通知，也不落记录")
    void skipsSelfMention() {
        when(directoryClient.resolve(any(), any())).thenReturn(Map.of("我自己", 100L));

        int notified = mentionService.dispatch(11L, 3L, MentionService.SOURCE_POST,
                "@我自己 备忘", "标题", 100L);

        assertThat(notified).isZero();
        verify(mentionMapper, never()).insert(any(ForumMention.class));
        verify(notifyService, never()).enqueue(anyLong(), anyString(), anyLong(), anyString(),
                anyString(), anyString());
    }

    @Test
    @DisplayName("唯一键冲突视为已提及：不再打扰第二次")
    void skipsDuplicateMention() {
        when(directoryClient.resolve(any(), any())).thenReturn(Map.of("张三", 200L));
        when(mentionMapper.insert(any(ForumMention.class))).thenThrow(new DuplicateKeyException("uk_fm_mention_target"));

        int notified = mentionService.dispatch(11L, 3L, MentionService.SOURCE_POST,
                "@张三 重复", "标题", 100L);

        assertThat(notified).isZero();
        verify(notifyService, never()).enqueue(anyLong(), anyString(), anyLong(), anyString(),
                anyString(), anyString());
    }

    @Test
    @DisplayName("通讯录解析不到任何一个：静默跳过，不影响主流程")
    void skipsWhenDirectoryUnresolved() {
        when(directoryClient.resolve(any(), any())).thenReturn(new LinkedHashMap<>());

        int notified = mentionService.dispatch(11L, 3L, MentionService.SOURCE_POST,
                "@查无此人 你好", "标题", 100L);

        assertThat(notified).isZero();
        verify(mentionMapper, never()).insert(any(ForumMention.class));
    }

    @Test
    @DisplayName("事务内调用推迟到提交后：避免长事务里打 HTTP，也避免通知未提交的帖子")
    void defersDispatchUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();
        when(directoryClient.resolve(any(), any())).thenReturn(Map.of("张三", 200L));

        int immediate = mentionService.dispatch(11L, MentionService.MAIN_POST_ID,
                MentionService.SOURCE_THREAD, "@张三 见正文", "标题", 100L);

        assertThat(immediate).isZero();
        verify(mentionMapper, never()).insert(any(ForumMention.class));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(mentionMapper, times(1)).insert(any(ForumMention.class));
        verify(notifyService, times(1)).enqueue(eq(200L), eq("MENTION"), eq(11L),
                eq("forum:mention:11:0:200"), anyString(), anyString());
    }
}
