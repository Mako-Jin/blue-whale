package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.TripItineraryCommand;
import com.bluewhale.oa.hr.application.command.TripReportCommand;
import com.bluewhale.oa.hr.application.command.TripSaveCommand;
import com.bluewhale.oa.hr.application.command.TripStatusCommand;
import com.bluewhale.oa.hr.domain.model.EmployeeProfile;
import com.bluewhale.oa.hr.domain.model.Trip;
import com.bluewhale.oa.hr.domain.model.TripItinerary;
import com.bluewhale.oa.hr.domain.model.TripReportStatus;
import com.bluewhale.oa.hr.domain.model.TripStatus;
import com.bluewhale.oa.hr.domain.model.TripTransportType;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.oa.hr.domain.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TripAppServiceImpl} 出差闭环单元测试：天数计算、金额与行程校验、预支幂等、报告提交。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TripAppServiceImplTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    private TripAppServiceImpl tripAppService;

    @BeforeEach
    void setUp() {
        tripAppService = new TripAppServiceImpl(tripRepository, employeeProfileRepository);
    }

    @Test
    @DisplayName("create：按自然日计算天数并落库为待审批，行程随单保存")
    void shouldCreatePendingTripWithNaturalDays() {
        when(tripRepository.existsByCode(anyString(), isNull())).thenReturn(false);
        when(employeeProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile()));

        tripAppService.create(command(3));

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).insert(captor.capture());
        Trip saved = captor.getValue();
        assertEquals("OA-TRIP-202611-100", saved.getCode());
        assertEquals(3, saved.getDays());
        assertEquals(TripStatus.PENDING, saved.getStatus());
        assertEquals(TripReportStatus.PENDING, saved.getReportStatus());
        assertEquals(TripTransportType.TRAIN, saved.getTransportType());
        verify(tripRepository).replaceItineraries(isNull(), anyList());
    }

    @Test
    @DisplayName("create：预支金额超过预算时拒绝")
    void shouldRejectAdvanceGreaterThanBudget() {
        TripSaveCommand command = command(3);
        command.setBudgetAmount(new BigDecimal("1000"));
        command.setAdvanceAmount(new BigDecimal("1200"));

        BizException exception = assertThrows(BizException.class, () -> tripAppService.create(command));

        assertEquals(OaErrorCode.TRIP_INVALID, exception.getCode());
        verify(tripRepository, never()).insert(any());
    }

    @Test
    @DisplayName("create：结束日期早于开始日期时拒绝")
    void shouldRejectInvalidPeriod() {
        TripSaveCommand command = command(3);
        command.setStartDate(LocalDate.of(2026, 11, 22));
        command.setEndDate(LocalDate.of(2026, 11, 20));

        BizException exception = assertThrows(BizException.class, () -> tripAppService.create(command));

        assertEquals(OaErrorCode.TRIP_INVALID_PERIOD, exception.getCode());
        verify(tripRepository, never()).insert(any());
    }

    @Test
    @DisplayName("updateStatus：审批通过生成预支，重复同步只生成一次")
    void shouldCreateAdvanceOnceOnApproval() {
        when(tripRepository.findById(1L)).thenReturn(Optional.of(pending()));
        when(tripRepository.findAdvanceByTripId(1L)).thenReturn(Optional.empty());

        tripAppService.updateStatus(1L, status("APPROVED"));
        tripAppService.updateStatus(1L, status("APPROVED"));

        verify(tripRepository, times(1)).update(any());
        verify(tripRepository, times(1)).insertAdvance(any());
    }

    @Test
    @DisplayName("updateStatus：驳回不生成预支")
    void shouldNotCreateAdvanceOnRejection() {
        when(tripRepository.findById(1L)).thenReturn(Optional.of(pending()));

        tripAppService.updateStatus(1L, status("REJECTED"));

        verify(tripRepository, never()).insertAdvance(any());
    }

    @Test
    @DisplayName("submitReport：仅审批通过可提交，且只能提交一次")
    void shouldSubmitReportOnlyOnceWhenApproved() {
        Trip trip = pending();
        trip.setStatus(TripStatus.APPROVED);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        tripAppService.submitReport(1L, report("客户拜访完成，签约待跟进。"));
        BizException exception = assertThrows(BizException.class,
                () -> tripAppService.submitReport(1L, report("重复提交")));

        assertEquals(OaErrorCode.TRIP_REPORT_NOT_ALLOWED, exception.getCode());
        assertEquals(TripReportStatus.SUBMITTED, trip.getReportStatus());
        assertTrue(trip.getReportContent().contains("客户拜访"));
    }

    /**
     * 构造出差保存命令。
     *
     * @param itineraryCount 行程条数
     * @return 保存命令
     */
    private TripSaveCommand command(int itineraryCount) {
        TripSaveCommand command = new TripSaveCommand();
        command.setCode("OA-TRIP-202611-100");
        command.setApplicant("zhangsan");
        command.setApplicantId(100L);
        command.setDestination("上海");
        command.setStartDate(LocalDate.of(2026, 11, 20));
        command.setEndDate(LocalDate.of(2026, 11, 22));
        command.setTransportType("TRAIN");
        command.setBudgetAmount(new BigDecimal("1000"));
        command.setAdvanceAmount(new BigDecimal("500"));
        command.setRemark("客户拜访");
        command.setItineraries(itineraries(itineraryCount));
        return command;
    }

    /**
     * 构造行程命令。
     *
     * @param count 条数
     * @return 行程命令
     */
    private List<TripItineraryCommand> itineraries(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count).mapToObj(dayNo -> {
            TripItineraryCommand itinerary = new TripItineraryCommand();
            itinerary.setDayNo(dayNo);
            itinerary.setTripDate(LocalDate.of(2026, 11, 20).plusDays(dayNo - 1));
            itinerary.setFromPlace("杭州");
            itinerary.setToPlace("上海");
            itinerary.setTransport("TRAIN");
            itinerary.setRemark("行程 " + dayNo);
            return itinerary;
        }).toList();
    }

    /**
     * 构造待审批出差申请。
     *
     * @return 出差申请
     */
    private Trip pending() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setCode("OA-TRIP-202611-100");
        trip.setApplicant("zhangsan");
        trip.setApplicantId(100L);
        trip.setDestination("上海");
        trip.setStartDate(LocalDate.of(2026, 11, 20));
        trip.setEndDate(LocalDate.of(2026, 11, 22));
        trip.setDays(3);
        trip.setTransportType(TripTransportType.TRAIN);
        trip.setBudgetAmount(new BigDecimal("1000"));
        trip.setAdvanceAmount(new BigDecimal("500"));
        trip.setReportStatus(TripReportStatus.PENDING);
        trip.setStatus(TripStatus.PENDING);
        trip.setItineraries(List.<TripItinerary>of());
        return trip;
    }

    /**
     * 构造状态命令。
     *
     * @param status 目标状态
     * @return 状态命令
     */
    private TripStatusCommand status(String status) {
        TripStatusCommand command = new TripStatusCommand();
        command.setStatus(status);
        return command;
    }

    /**
     * 构造报告命令。
     *
     * @param content 报告内容
     * @return 报告命令
     */
    private TripReportCommand report(String content) {
        TripReportCommand command = new TripReportCommand();
        command.setContent(content);
        return command;
    }

    /**
     * 构造员工档案。
     *
     * @return 员工档案
     */
    private EmployeeProfile profile() {
        EmployeeProfile profile = new EmployeeProfile();
        profile.setUserId(100L);
        return profile;
    }
}
