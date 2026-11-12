package com.bluewhale.forum.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ReportCreateRequest;
import com.bluewhale.forum.dto.ReportHandleRequest;
import com.bluewhale.forum.entity.ForumReport;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumReportMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.service.ModerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 举报工单单测：不能举报自己、去重返回既有单、驳回必填理由、处置动作落到禁言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ForumReportMapper reportMapper;

    @Mock
    private ForumThreadMapper threadMapper;

    @Mock
    private ForumPostMapper postMapper;

    @Mock
    private ModerationService moderationService;

    @Mock
    private ForumNotifyService notifyService;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("举报自己的帖子：直接拒绝，不建单")
    void cannotReportOwnThread() {
        ForumThread thread = thread();
        when(threadMapper.selectById(11L)).thenReturn(thread);

        assertThatThrownBy(() -> service.submit(request("THREAD", 11L), reporter(100L)))
                .isInstanceOf(BizException.class);
        verify(reportMapper, never()).insert(any(ForumReport.class));
    }

    @Test
    @DisplayName("同人同目标重复举报：返回既有工单，不重复建单")
    void duplicateReportReturnsExisting() {
        when(threadMapper.selectById(11L)).thenReturn(thread());
        ForumReport existing = new ForumReport();
        existing.setId(77L);
        when(reportMapper.selectOne(any())).thenReturn(existing);

        Long id = service.submit(request("THREAD", 11L), reporter(200L));

        assertThat(id).isEqualTo(77L);
        verify(reportMapper, never()).insert(any(ForumReport.class));
    }

    @Test
    @DisplayName("驳结案不填理由：返回 30020")
    void rejectWithoutRemark() {
        ForumReport report = report();
        when(reportMapper.selectById(55L)).thenReturn(report);
        ReportHandleRequest request = new ReportHandleRequest();
        request.setStatus("REJECTED");

        assertThatThrownBy(() -> service.handle(55L, request, admin()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ForumErrorCodes.AUDIT_REMARK_REQUIRED));
        verify(reportMapper, never()).updateById(any(ForumReport.class));
    }

    @Test
    @DisplayName("结案并禁言 7 天：调用处置并通知举报人")
    void resolveWithMute() {
        ForumReport report = report();
        when(reportMapper.selectById(55L)).thenReturn(report);
        when(threadMapper.selectById(11L)).thenReturn(thread());
        ReportHandleRequest request = new ReportHandleRequest();
        request.setStatus("RESOLVED");
        request.setAction("MUTE");
        request.setMuteDays(7);
        request.setRemark("广告刷屏");

        service.handle(55L, request, admin());

        assertThat(report.getStatus()).isEqualTo("RESOLVED");
        verify(moderationService).ban(eq(100L), eq("MUTE"), eq(7), eq("广告刷屏"), any(), eq(55L));
        verify(notifyService).enqueue(eq(300L), eq("MODERATION"), eq(55L), anyString(), anyString(), anyString());
    }

    /**
     * 构造举报请求。
     *
     * @param type 对象类型
     * @param targetId 对象ID
     * @return 请求
     */
    private ReportCreateRequest request(String type, Long targetId) {
        ReportCreateRequest request = new ReportCreateRequest();
        request.setTargetType(type);
        request.setTargetId(targetId);
        request.setReportType("SPAM");
        return request;
    }

    /**
     * 被举报的帖子（作者 100）。
     *
     * @return 帖子
     */
    private ForumThread thread() {
        ForumThread thread = new ForumThread();
        thread.setId(11L);
        thread.setTenantId(1L);
        thread.setAuthorId(100L);
        thread.setTitle("广告帖");
        thread.setStatus("PUBLISHED");
        return thread;
    }

    /**
     * 待处理工单。
     *
     * @return 工单
     */
    private ForumReport report() {
        ForumReport report = new ForumReport();
        report.setId(55L);
        report.setTenantId(1L);
        report.setTargetType("THREAD");
        report.setTargetId(11L);
        report.setReporterId(300L);
        report.setReportType("SPAM");
        report.setStatus("PENDING");
        return report;
    }

    /**
     * 构造举报人。
     *
     * @param userId 用户ID
     * @return 操作者
     */
    private ForumActor reporter(Long userId) {
        return new ForumActor(userId, Set.of(ForumPermissions.BROWSE), null);
    }

    /**
     * 构造处置管理员。
     *
     * @return 操作者
     */
    private ForumActor admin() {
        return new ForumActor(900L, Set.of(ForumPermissions.MANAGE_DISPOSE), null);
    }
}
