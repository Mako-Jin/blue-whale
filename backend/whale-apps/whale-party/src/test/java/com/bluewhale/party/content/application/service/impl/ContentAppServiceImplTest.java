package com.bluewhale.party.content.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.party.branch.domain.repository.BranchRepository;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.content.application.command.ContentRejectCommand;
import com.bluewhale.party.content.application.command.ContentSaveCommand;
import com.bluewhale.party.content.application.view.ContentView;
import com.bluewhale.party.content.domain.model.AuditAction;
import com.bluewhale.party.content.domain.model.ContentAudit;
import com.bluewhale.party.content.domain.model.ContentQuery;
import com.bluewhale.party.content.domain.model.ContentRead;
import com.bluewhale.party.content.domain.model.ContentStatus;
import com.bluewhale.party.content.domain.model.ContentVisibility;
import com.bluewhale.party.content.domain.model.PartyContent;
import com.bluewhale.party.content.domain.repository.ContentRepository;
import com.bluewhale.party.member.domain.model.MemberStatus;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 党建内容应用服务：先审后发、未审可见性、退回意见、阅读去重。
 *
 * @author BlueWhale
 */
class ContentAppServiceImplTest {

    private ContentRepository contentRepository;

    private BranchRepository branchRepository;

    private PartyMemberRepository partyMemberRepository;

    private ContentAppServiceImpl service;

    private PartyActor reporter;

    private PartyActor reviewer;

    private PartyActor reader;

    @BeforeEach
    void setUp() {
        contentRepository = mock(ContentRepository.class);
        branchRepository = mock(BranchRepository.class);
        partyMemberRepository = mock(PartyMemberRepository.class);
        service = new ContentAppServiceImpl(contentRepository, branchRepository,
                partyMemberRepository, new PartyProperties());
        reporter = new PartyActor(11L, "投稿人", Set.of("branch_secretary"), Set.of());
        reviewer = new PartyActor(9L, "党务管理员", Set.of("party_admin"), Set.of());
        reader = new PartyActor(20L, "普通党员", Set.of(), Set.of());
    }

    @Test
    @DisplayName("新建内容为草稿且作者取当前用户")
    void createShouldSaveDraft() {
        stubSave();
        ContentView view = service.create(command(), reporter);
        assertEquals(ContentStatus.DRAFT.name(), view.getStatus());
        assertEquals(11L, view.getAuthorId());
        assertEquals(ContentVisibility.ALL_STAFF.name(), view.getVisibility());
        verify(contentRepository).save(any(PartyContent.class));
    }

    @Test
    @DisplayName("已发布内容被修改后自动退回待审并写EDIT轨迹")
    void updatePublishedShouldResubmit() {
        PartyContent content = content(101L, 11L, ContentStatus.PUBLISHED);
        when(contentRepository.findById(101L)).thenReturn(Optional.of(content));
        stubSave();

        ContentView view = service.update(101L, command(), reporter);
        assertEquals(ContentStatus.PENDING.name(), view.getStatus());

        ArgumentCaptor<ContentAudit> captor = ArgumentCaptor.forClass(ContentAudit.class);
        verify(contentRepository).saveAudit(captor.capture());
        assertEquals(AuditAction.EDIT, captor.getValue().getAction());
        assertEquals(ContentStatus.PUBLISHED.name(), captor.getValue().getFromStatus());
        assertEquals(ContentStatus.PENDING.name(), captor.getValue().getToStatus());
    }

    @Test
    @DisplayName("非作者非审核人看不到别人的草稿")
    void detailShouldHideOthersDraft() {
        PartyContent content = content(101L, 99L, ContentStatus.DRAFT);
        when(contentRepository.findById(101L)).thenReturn(Optional.of(content));

        BizException exception = assertThrows(BizException.class,
                () -> service.detail(101L, reporter));
        assertEquals(PartyErrorCode.CONTENT_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("审核人可以查看别人的待审稿")
    void detailShouldAllowReviewer() {
        PartyContent content = content(101L, 99L, ContentStatus.PENDING);
        when(contentRepository.findById(101L)).thenReturn(Optional.of(content));

        ContentView view = service.detail(101L, reviewer);
        assertEquals(ContentStatus.PENDING.name(), view.getStatus());
    }

    @Test
    @DisplayName("退回意见为空直接拒绝")
    void rejectShouldRequireOpinion() {
        PartyContent content = content(101L, 11L, ContentStatus.PENDING);
        when(contentRepository.findById(101L)).thenReturn(Optional.of(content));

        ContentRejectCommand command = new ContentRejectCommand();
        command.setOpinion("  ");
        BizException exception = assertThrows(BizException.class,
                () -> service.reject(101L, command, reviewer));
        assertEquals(PartyErrorCode.CONTENT_AUDIT_OPINION_REQUIRED, exception.getCode());
    }

    @Test
    @DisplayName("非审核人不能审核")
    void approveShouldRequireReviewer() {
        PartyContent content = content(101L, 11L, ContentStatus.PENDING);
        when(contentRepository.findById(101L)).thenReturn(Optional.of(content));

        BizException exception = assertThrows(BizException.class,
                () -> service.approve(101L, reporter));
        assertEquals(PartyErrorCode.CONTENT_FORBIDDEN, exception.getCode());
    }

    @Test
    @DisplayName("同一人重复阅读只累加一次阅读数")
    void readShouldCountOncePerUser() {
        PartyContent content = content(101L, 11L, ContentStatus.PUBLISHED);
        content.setVisibility(ContentVisibility.ALL_STAFF);
        when(contentRepository.findById(101L)).thenReturn(Optional.of(content));
        when(contentRepository.recordRead(any(ContentRead.class))).thenReturn(true, false);

        service.read(101L, reader);
        service.read(101L, reader);

        verify(contentRepository, times(1)).incrementReadCount(101L);
    }

    @Test
    @DisplayName("非审核人的管理列表限定为本人稿件与已发布内容")
    void pageShouldApplyAuthorScopeForNonReviewer() {
        when(contentRepository.page(any(ContentQuery.class), anyLong(), anyInt()))
                .thenReturn(List.of());
        when(contentRepository.count(any(ContentQuery.class))).thenReturn(0L);

        service.page(new ContentQuery(), reporter);

        ArgumentCaptor<ContentQuery> captor = ArgumentCaptor.forClass(ContentQuery.class);
        verify(contentRepository).page(captor.capture(), anyLong(), anyInt());
        assertEquals(11L, captor.getValue().getScopeAuthorId());
    }

    @Test
    @DisplayName("园地详情对不可见内容按不存在处理")
    void portalDetailShouldHideInvisibleContent() {
        PartyContent content = content(101L, 11L, ContentStatus.PUBLISHED);
        content.setVisibility(ContentVisibility.ALL_MEMBERS);
        when(contentRepository.findById(101L)).thenReturn(Optional.of(content));
        when(partyMemberRepository.findByUserId(20L)).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class,
                () -> service.portalDetail(101L, reader));
        assertEquals(PartyErrorCode.CONTENT_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("指定支部内容未命中本人支部时不可见")
    void portalDetailShouldRequireBranchMatch() {
        PartyContent content = content(101L, 11L, ContentStatus.PUBLISHED);
        content.setVisibility(ContentVisibility.BRANCHES);
        content.setBranchIds(List.of(9L));
        when(contentRepository.findById(101L)).thenReturn(Optional.of(content));
        when(partyMemberRepository.findByUserId(20L)).thenReturn(Optional.of(member()));

        BizException exception = assertThrows(BizException.class,
                () -> service.portalDetail(101L, reader));
        assertEquals(PartyErrorCode.CONTENT_NOT_FOUND, exception.getCode());
    }

    /**
     * 保存桩：首次保存分配主键。
     */
    private void stubSave() {
        when(contentRepository.save(any(PartyContent.class))).thenAnswer(invocation -> {
            PartyContent saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(101L);
            }
            return saved.getId();
        });
    }

    /**
     * 构造内容样本。
     *
     * @param id 主键
     * @param authorId 作者
     * @param status 状态
     * @return 内容
     */
    private PartyContent content(Long id, Long authorId, ContentStatus status) {
        PartyContent content = new PartyContent();
        content.setId(id);
        content.setCategory("理论学习");
        content.setTitle("学习材料");
        content.setBody("正文");
        content.setAuthorId(authorId);
        content.setAuthorName("作者");
        content.setVisibility(ContentVisibility.ALL_MEMBERS);
        content.setStatus(status);
        content.setReadCount(0);
        return content;
    }

    /**
     * 构造党员样本（支部 7）。
     *
     * @return 党员档案
     */
    private PartyMember member() {
        PartyMember member = new PartyMember();
        member.setBranchId(7L);
        member.setStatus(MemberStatus.ACTIVE);
        return member;
    }

    /**
     * 构造保存命令。
     *
     * @return 命令
     */
    private ContentSaveCommand command() {
        ContentSaveCommand command = new ContentSaveCommand();
        command.setCategory("理论学习");
        command.setTitle("学习材料");
        command.setBody("正文");
        command.setVisibility(ContentVisibility.ALL_STAFF.name());
        return command;
    }
}
