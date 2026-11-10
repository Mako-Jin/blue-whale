package com.bluewhale.oa.culture.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureEvent;
import com.bluewhale.oa.culture.domain.model.Poll;
import com.bluewhale.oa.culture.domain.model.PollOption;
import com.bluewhale.oa.culture.domain.model.Suggestion;
import com.bluewhale.oa.culture.domain.repository.CultureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业文化应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class CultureAppServiceImplTest {

    @Mock
    private CultureRepository repository;

    private CultureEventAppServiceImpl eventAppService;

    private PollAppServiceImpl pollAppService;

    private SuggestionAppServiceImpl suggestionAppService;

    @BeforeEach
    void setUp() {
        eventAppService = new CultureEventAppServiceImpl(repository);
        pollAppService = new PollAppServiceImpl(repository);
        suggestionAppService = new SuggestionAppServiceImpl(repository);
    }

    @Test
    @DisplayName("事件：发布后不可再编辑")
    void shouldRejectEditAfterPublish() {
        when(repository.findEvent(1L)).thenReturn(draftEvent());

        CultureViews.Event published = eventAppService.publish(1L, 100L, "alice");
        assertEquals("PUBLISHED", published.getStatus());
        assertEquals(100L, published.getPublisherId());

        BizException exception = assertThrows(BizException.class,
                () -> eventAppService.update(1L, eventSaveCommand()));

        assertEquals(OaErrorCode.CULTURE_EVENT_STATUS_CONFLICT, exception.getCode());
        verify(repository).updateEvent(any(CultureEvent.class));
    }

    @Test
    @DisplayName("投票：重复投票拒绝")
    void shouldRejectDuplicateVote() {
        when(repository.findPoll(1L)).thenReturn(publishedPoll(0, 1));
        when(repository.existsAnyVote(1L, 300L)).thenReturn(true);

        CultureCommands.VoteCommand command = voteCommand(11L);

        BizException exception = assertThrows(BizException.class,
                () -> pollAppService.vote(1L, command, 300L, "bob"));

        assertEquals(OaErrorCode.POLL_ALREADY_VOTED, exception.getCode());
        verify(repository, never()).saveVote(any());
    }

    @Test
    @DisplayName("投票：单选提交 2 个选项拒绝")
    void shouldRejectMultipleOptionsForSingleChoice() {
        when(repository.findPoll(1L)).thenReturn(publishedPoll(0, 1));
        when(repository.existsAnyVote(1L, 300L)).thenReturn(false);
        when(repository.listOptions(1L)).thenReturn(List.of(option(11L), option(12L)));

        CultureCommands.VoteCommand command = voteCommand(11L, 12L);

        BizException exception = assertThrows(BizException.class,
                () -> pollAppService.vote(1L, command, 300L, "bob"));

        assertEquals(OaErrorCode.POLL_INVALID, exception.getCode());
        verify(repository, never()).saveVote(any());
    }

    @Test
    @DisplayName("建议：匿名详情隐藏提交人")
    void shouldHideSubmitterForAnonymousSuggestion() {
        when(repository.findSuggestion(1L)).thenReturn(suggestion(1));
        when(repository.listReplies(1L)).thenReturn(List.of());

        CultureViews.SuggestionDetail detail = suggestionAppService.detail(1L);

        assertNull(detail.getSubmitterId());
        assertNull(detail.getSubmitterName());
    }

    @Test
    @DisplayName("建议：回复后状态置 REPLIED 并记录处理人")
    void shouldMarkSuggestionRepliedAfterReply() {
        when(repository.findSuggestion(1L)).thenReturn(suggestion(0));

        CultureCommands.SuggestionReplySave command = new CultureCommands.SuggestionReplySave();
        command.setContent("已安排跟进");
        suggestionAppService.reply(1L, command, 200L, "hr");

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(repository).updateSuggestion(captor.capture());
        assertEquals("REPLIED", captor.getValue().getStatus());
        assertEquals(200L, captor.getValue().getHandlerId());
        assertEquals("hr", captor.getValue().getHandlerName());
        assertNotNull(captor.getValue().getHandledTime());
        verify(repository).saveReply(any());
    }

    private static CultureEvent draftEvent() {
        CultureEvent event = new CultureEvent();
        event.setId(1L);
        event.setEventType("ACTIVITY");
        event.setTitle("团建");
        event.setContent("内容");
        event.setStatus("DRAFT");
        return event;
    }

    private static CultureCommands.EventSave eventSaveCommand() {
        CultureCommands.EventSave command = new CultureCommands.EventSave();
        command.setTitle("团建");
        command.setEventType("ACTIVITY");
        command.setContent("内容");
        return command;
    }

    private static Poll publishedPoll(int multipleChoice, int maxChoices) {
        Poll poll = new Poll();
        poll.setId(1L);
        poll.setTitle("满意度调查");
        poll.setMultipleChoice(multipleChoice);
        poll.setMaxChoices(maxChoices);
        poll.setStatus("PUBLISHED");
        return poll;
    }

    private static PollOption option(Long id) {
        PollOption option = new PollOption();
        option.setId(id);
        option.setPollId(1L);
        option.setOptionText("选项" + id);
        return option;
    }

    private static CultureCommands.VoteCommand voteCommand(Long... optionIds) {
        CultureCommands.VoteCommand command = new CultureCommands.VoteCommand();
        command.setOptionIds(List.of(optionIds));
        return command;
    }

    private static Suggestion suggestion(int anonymous) {
        Suggestion suggestion = new Suggestion();
        suggestion.setId(1L);
        suggestion.setTitle("建议");
        suggestion.setCategory("SUGGESTION");
        suggestion.setContent("内容");
        suggestion.setAnonymous(anonymous);
        suggestion.setSubmitterId(400L);
        suggestion.setSubmitterName("张三");
        suggestion.setStatus("PENDING");
        return suggestion;
    }
}
