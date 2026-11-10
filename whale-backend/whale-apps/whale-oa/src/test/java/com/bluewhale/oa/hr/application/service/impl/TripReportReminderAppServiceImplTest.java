package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.oa.approval.domain.gateway.ApprovalNotifyGateway;
import com.bluewhale.oa.approval.domain.gateway.UserDirectoryGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalNotifyMessage;
import com.bluewhale.oa.hr.domain.model.Trip;
import com.bluewhale.oa.hr.domain.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TripReportReminderAppServiceImpl} 接收人解析、幂等键与失败兜底测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TripReportReminderAppServiceImplTest {

    /** 测试用结束日期上限 */
    private static final LocalDate DEADLINE = LocalDate.of(2026, 11, 3);

    @Mock
    private TripRepository tripRepository;

    @Mock
    private UserDirectoryGateway userDirectoryGateway;

    @Mock
    private ApprovalNotifyGateway approvalNotifyGateway;

    private TripReportReminderAppServiceImpl service;

    /**
     * 构造待测服务。
     */
    @BeforeEach
    void setUp() {
        service = new TripReportReminderAppServiceImpl(
                tripRepository, userDirectoryGateway, approvalNotifyGateway);
    }

    @Test
    @DisplayName("无待提醒出差单时不投递")
    void shouldDoNothingWhenNoTrips() {
        when(tripRepository.listReportPendingEndedBefore(DEADLINE, 100)).thenReturn(List.of());

        int notified = service.remindOverdueReports(DEADLINE, 100);

        assertEquals(0, notified);
        verifyNoInteractions(approvalNotifyGateway);
    }

    @Test
    @DisplayName("申请人ID存在时按用户ID投递并生成按天幂等键")
    void shouldSendWithApplicantUserId() {
        when(tripRepository.listReportPendingEndedBefore(DEADLINE, 100))
                .thenReturn(List.of(trip(1L, "zhangsan", 88L)));

        int notified = service.remindOverdueReports(DEADLINE, 100);

        assertEquals(1, notified);
        ArgumentCaptor<ApprovalNotifyMessage> captor =
                ArgumentCaptor.forClass(ApprovalNotifyMessage.class);
        verify(approvalNotifyGateway).send(captor.capture());
        ApprovalNotifyMessage message = captor.getValue();
        assertEquals("OA_TRIP_REPORT", message.getBizType());
        assertEquals("1", message.getBizId());
        assertEquals("OA_TRIP_REPORT_REMINDER", message.getTemplateCode());
        assertEquals("OA_TRIP_REPORT:1:2026-11-03", message.getIdempotencyKey());
        assertEquals(List.of(88L), message.getUserIds());
        assertEquals("杭州", message.getPayload().get("destination"));
        assertEquals("2026-11-03", message.getPayload().get("endDate"));
        verifyNoInteractions(userDirectoryGateway);
    }

    @Test
    @DisplayName("申请人ID缺失时按账号解析后投递")
    void shouldResolveUserIdByAccount() {
        when(tripRepository.listReportPendingEndedBefore(DEADLINE, 100))
                .thenReturn(List.of(trip(2L, "lisi", null)));
        when(userDirectoryGateway.findUserIdByUsername("lisi")).thenReturn(Optional.of(99L));

        int notified = service.remindOverdueReports(DEADLINE, 100);

        assertEquals(1, notified);
        ArgumentCaptor<ApprovalNotifyMessage> captor =
                ArgumentCaptor.forClass(ApprovalNotifyMessage.class);
        verify(approvalNotifyGateway).send(captor.capture());
        assertEquals(List.of(99L), captor.getValue().getUserIds());
    }

    @Test
    @DisplayName("账号解析失败时跳过并继续处理后续记录")
    void shouldSkipUnresolvedApplicant() {
        when(tripRepository.listReportPendingEndedBefore(DEADLINE, 100))
                .thenReturn(List.of(trip(3L, "unknown", null), trip(4L, "wangwu", 77L)));
        when(userDirectoryGateway.findUserIdByUsername("unknown")).thenReturn(Optional.empty());

        int notified = service.remindOverdueReports(DEADLINE, 100);

        assertEquals(1, notified);
        verify(approvalNotifyGateway, times(1)).send(any());
    }

    @Test
    @DisplayName("单条投递异常不中断批处理")
    void shouldContinueWhenOneSendFails() {
        when(tripRepository.listReportPendingEndedBefore(DEADLINE, 100))
                .thenReturn(List.of(trip(5L, "a", 1L), trip(6L, "b", 2L)));
        doThrow(new IllegalStateException("消息服务不可用"))
                .when(approvalNotifyGateway).send(any());

        int notified = service.remindOverdueReports(DEADLINE, 100);

        assertEquals(0, notified);
        verify(approvalNotifyGateway, times(2)).send(any());
    }

    @Test
    @DisplayName("批量非正或截止日为空时直接返回")
    void shouldReturnZeroOnInvalidArguments() {
        assertEquals(0, service.remindOverdueReports(null, 100));
        assertEquals(0, service.remindOverdueReports(DEADLINE, 0));

        verify(tripRepository, never()).listReportPendingEndedBefore(any(), anyInt());
        verify(approvalNotifyGateway, never()).send(any());
        verify(userDirectoryGateway, never()).findUserIdByUsername(anyString());
    }

    /**
     * 构造出差申请。
     *
     * @param id 主键
     * @param applicant 申请人账号
     * @param applicantId 申请人用户ID
     * @return 出差申请
     */
    private static Trip trip(Long id, String applicant, Long applicantId) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setApplicant(applicant);
        trip.setApplicantId(applicantId);
        trip.setDestination("杭州");
        trip.setEndDate(DEADLINE);
        return trip;
    }
}
