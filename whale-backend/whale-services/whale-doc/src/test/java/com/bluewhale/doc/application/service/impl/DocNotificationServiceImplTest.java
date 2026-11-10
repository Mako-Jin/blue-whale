package com.bluewhale.doc.application.service.impl;

import com.bluewhale.doc.application.service.DocNotifyEvent;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link DocNotificationServiceImpl} 通知事件发布行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocNotificationServiceImplTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private DocProperties properties;

    private DocNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DocProperties();
        service = new DocNotificationServiceImpl(eventPublisher, properties);
    }

    @Test
    void commentMentionShouldSkipWhenNotifyDisabled() {
        service.commentMention(1L, page(7L, "页面", 3L), 5L, 3L, List.of(9L));

        verify(eventPublisher, never()).publishEvent(any(DocNotifyEvent.class));
    }

    @Test
    void commentMentionShouldSkipWhenNoMentionedUsers() {
        properties.setNotifyEnabled(true);

        service.commentMention(1L, page(7L, "页面", 3L), 5L, 3L, null);
        service.commentMention(1L, page(7L, "页面", 3L), 5L, 3L, List.of());

        verify(eventPublisher, never()).publishEvent(any(DocNotifyEvent.class));
    }

    @Test
    void commentMentionShouldDeduplicateAndExcludeAuthor() {
        properties.setNotifyEnabled(true);

        service.commentMention(1L, page(7L, "设计稿", 3L), 5L, 3L, Arrays.asList(9L, 9L, 3L, null));

        ArgumentCaptor<DocNotifyEvent> captor = ArgumentCaptor.forClass(DocNotifyEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DocNotifyEvent event = captor.getValue();
        assertThat(event.tenantId()).isEqualTo(1L);
        assertThat(event.bizType()).isEqualTo("DOC_COMMENT");
        assertThat(event.bizId()).isEqualTo("7");
        assertThat(event.idempotencyKey()).isEqualTo("doc:comment:5:9");
        assertThat(event.content()).contains("设计稿");
        assertThat(event.recipientIds()).containsExactly(9L);
    }

    @Test
    void commentMentionShouldSkipWhenAllRecipientsExcluded() {
        properties.setNotifyEnabled(true);

        service.commentMention(1L, page(7L, "页面", 3L), 5L, 3L, List.of(3L));

        verify(eventPublisher, never()).publishEvent(any(DocNotifyEvent.class));
    }

    @Test
    void approvalSubmittedShouldSkipWhenAuthorMissingAndOperatorExcluded() {
        properties.setNotifyEnabled(true);

        service.approvalSubmitted(1L, page(7L, "页面", null), 11L, 3L);

        // 作者缺失时回退候选即操作人本人，而操作人始终被剔除，因此不发布通知
        verify(eventPublisher, never()).publishEvent(any(DocNotifyEvent.class));
    }

    @Test
    void approvalSubmittedShouldSkipAuthorWhoIsOperator() {
        properties.setNotifyEnabled(true);

        service.approvalSubmitted(1L, page(7L, "页面", 3L), 11L, 3L);

        verify(eventPublisher, never()).publishEvent(any(DocNotifyEvent.class));
    }

    @Test
    void approvalResultShouldNotifyAuthorAndLastEditorOnce() {
        properties.setNotifyEnabled(true);
        DocPage page = page(7L, "页面", 3L);
        page.setLastEditorId(3L);

        service.approvalResult(1L, page, 11L, true);

        ArgumentCaptor<DocNotifyEvent> captor = ArgumentCaptor.forClass(DocNotifyEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        DocNotifyEvent event = captor.getValue();
        assertThat(event.title()).isEqualTo("文档发布审批已通过");
        assertThat(event.idempotencyKey()).isEqualTo("doc:approval-result:11:PASS:3");
        assertThat(event.content()).contains("已发布");
    }

    @Test
    void approvalResultShouldUseRejectKeyAndMessage() {
        properties.setNotifyEnabled(true);
        DocPage page = page(7L, "页面", 3L);
        page.setLastEditorId(4L);

        service.approvalResult(1L, page, 11L, false);

        ArgumentCaptor<DocNotifyEvent> captor = ArgumentCaptor.forClass(DocNotifyEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(DocNotifyEvent::idempotencyKey)
                .containsExactly("doc:approval-result:11:REJECT:3", "doc:approval-result:11:REJECT:4");
        assertThat(captor.getAllValues()).allSatisfy(event ->
                assertThat(event.title()).isEqualTo("文档发布审批已驳回"));
    }

    @Test
    void approvalResultShouldSkipNullRecipients() {
        properties.setNotifyEnabled(true);

        service.approvalResult(1L, page(7L, "页面", null), 11L, true);

        verify(eventPublisher, never()).publishEvent(any(DocNotifyEvent.class));
    }

    @Test
    void memberChangedShouldNotifyMembersExceptOperator() {
        properties.setNotifyEnabled(true);

        service.memberChanged(1L, 8L, "研发空间", 3L, Arrays.asList(9L, 10L, 3L, null), "ADD");

        ArgumentCaptor<DocNotifyEvent> captor = ArgumentCaptor.forClass(DocNotifyEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(DocNotifyEvent::bizType)
                .containsOnly("DOC_MEMBER");
        assertThat(captor.getAllValues()).extracting(DocNotifyEvent::idempotencyKey)
                .containsExactly("doc:member:8:ADD:9", "doc:member:8:ADD:10");
        assertThat(captor.getAllValues()).allSatisfy(event -> {
            assertThat(event.bizId()).isEqualTo("8");
            assertThat(event.content()).contains("研发空间");
        });
    }

    @Test
    void memberChangedShouldSkipWhenDisabled() {
        service.memberChanged(1L, 8L, "研发空间", 3L, List.of(9L), "REMOVE");

        verify(eventPublisher, never()).publishEvent(any(DocNotifyEvent.class));
    }

    @Test
    void publishShouldSkipNullTenant() {
        properties.setNotifyEnabled(true);

        service.memberChanged(null, 8L, "研发空间", 3L, List.of(9L), "ADD");

        verify(eventPublisher, never()).publishEvent(any(DocNotifyEvent.class));
    }

    @Test
    void memberChangedShouldCapRecipientsAtHundred() {
        properties.setNotifyEnabled(true);
        List<Long> members = java.util.stream.LongStream.rangeClosed(1, 150).boxed().toList();

        service.memberChanged(1L, 8L, "研发空间", 999L, members, "ADD");

        // DocNotifyEvent 是普通 record，生产走 publishEvent(Object) 重载，
        // verify 必须用 any(DocNotifyEvent.class) 才能匹配到实际调用
        verify(eventPublisher, times(100)).publishEvent(any(DocNotifyEvent.class));
    }

    /**
     * 构造页面。
     *
     * @param id      页面ID
     * @param title   标题
     * @param authorId 作者ID
     * @return 页面
     */
    private DocPage page(Long id, String title, Long authorId) {
        DocPage page = new DocPage();
        page.setId(id);
        page.setTitle(title);
        page.setAuthorId(authorId);
        return page;
    }
}
