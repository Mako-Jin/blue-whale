package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.hr.application.command.TripItineraryCommand;
import com.bluewhale.oa.hr.application.command.TripReportCommand;
import com.bluewhale.oa.hr.application.command.TripSaveCommand;
import com.bluewhale.oa.hr.application.command.TripStatusCommand;
import com.bluewhale.oa.hr.application.service.TripAppService;
import com.bluewhale.oa.hr.application.view.TripItineraryView;
import com.bluewhale.oa.hr.application.view.TripView;
import com.bluewhale.oa.hr.domain.model.Trip;
import com.bluewhale.oa.hr.domain.model.TripAdvance;
import com.bluewhale.oa.hr.domain.model.TripItinerary;
import com.bluewhale.oa.hr.domain.model.TripQuery;
import com.bluewhale.oa.hr.domain.model.TripStatus;
import com.bluewhale.oa.hr.domain.model.TripTransportType;
import com.bluewhale.oa.hr.domain.repository.EmployeeProfileRepository;
import com.bluewhale.oa.hr.domain.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 出差申请应用服务实现。
 *
 * <p>天数由服务端按自然日（含首尾）计算；预支不得超过预算；仅待审批可编辑/删除；
 * 审批通过且预支金额大于 0 时幂等生成一条预支记录；出差报告仅审批通过后提交一次。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripAppServiceImpl implements TripAppService {

    private final TripRepository tripRepository;

    private final EmployeeProfileRepository employeeProfileRepository;

    @Override
    public PageResult<TripView> page(TripQuery query) {
        List<TripView> records = tripRepository.page(query).stream().map(this::toView).toList();
        return PageResult.of(records, tripRepository.count(query), query.getCurrent(), query.getSize());
    }

    @Override
    public TripView detail(Long id) {
        Trip trip = requireTrip(id);
        trip.setItineraries(tripRepository.listItineraries(id));
        TripView view = toView(trip);
        tripRepository.findAdvanceByTripId(id).ifPresent(advance -> {
            view.setAdvanceId(advance.getId());
            view.setAdvanceStatus(advance.getStatus());
        });
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TripView create(TripSaveCommand command) {
        Trip trip = new Trip();
        List<TripItineraryCommand> itineraryCommands = command.getItineraries();
        apply(trip, command);
        trip.ensureValid();
        assertCodeUnique(command.getCode(), null);
        assertProfileExists(command.getApplicantId());
        validateItineraries(trip, itineraryCommands);
        trip.submit();
        tripRepository.insert(trip);
        tripRepository.replaceItineraries(trip.getId(), toItineraries(itineraryCommands));
        log.info("出差申请已创建: id={}, code={}, days={}", trip.getId(), trip.getCode(), trip.getDays());
        return toView(trip);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TripView update(Long id, TripSaveCommand command) {
        Trip trip = requireEditable(id);
        List<TripItineraryCommand> itineraryCommands = command.getItineraries();
        apply(trip, command);
        trip.ensureValid();
        assertCodeUnique(command.getCode(), id);
        assertProfileExists(command.getApplicantId());
        validateItineraries(trip, itineraryCommands);
        tripRepository.update(trip);
        tripRepository.replaceItineraries(id, toItineraries(itineraryCommands));
        trip.setItineraries(toItineraries(itineraryCommands));
        log.info("出差申请已更新: id={}, code={}, days={}", id, trip.getCode(), trip.getDays());
        return toView(trip);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, TripStatusCommand command) {
        Trip trip = requireTrip(id);
        TripStatus target = TripStatus.of(command.getStatus());
        if (!trip.changeStatus(target)) {
            log.info("出差申请状态未变化，幂等返回: id={}, status={}", id, target);
            return;
        }
        tripRepository.update(trip);
        if (target == TripStatus.APPROVED) {
            createAdvance(trip);
        }
        log.info("出差申请状态已变更: id={}, status={}", id, trip.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long tripId, String result) {
        TripStatus target = TripStatus.of(result);
        if (target == TripStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_STATUS_CONFLICT, "审批结果不能为待审批");
        }
        TripStatusCommand command = new TripStatusCommand();
        command.setStatus(target.name());
        updateStatus(tripId, command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReport(Long id, TripReportCommand command) {
        Trip trip = requireTrip(id);
        trip.submitReport(command.getContent().trim(), LocalDateTime.now());
        tripRepository.update(trip);
        log.info("出差报告已提交: id={}, reportTime={}", id, trip.getReportTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Trip trip = requireEditable(id);
        tripRepository.deleteById(trip.getId());
        log.info("出差申请已删除: id={}", id);
    }

    /**
     * 审批通过后按预支金额幂等生成预支记录（异常仅告警不阻断审批）。
     *
     * @param trip 出差申请
     */
    private void createAdvance(Trip trip) {
        if (trip.getAdvanceAmount() == null || trip.getAdvanceAmount().signum() <= 0) {
            return;
        }
        try {
            if (tripRepository.findAdvanceByTripId(trip.getId()).isPresent()) {
                log.info("出差预支记录已存在，跳过生成: tripId={}", trip.getId());
                return;
            }
            TripAdvance advance = new TripAdvance();
            advance.setTripId(trip.getId());
            advance.setApplicantId(trip.getApplicantId());
            advance.setAmount(trip.getAdvanceAmount());
            advance.setStatus(TripAdvance.STATUS_PENDING);
            tripRepository.insertAdvance(advance);
            log.info("出差预支已生成: tripId={}, amount={}", trip.getId(), trip.getAdvanceAmount());
        } catch (RuntimeException exception) {
            log.warn("出差预支生成失败: tripId={}, error={}", trip.getId(), exception.getMessage());
        }
    }

    /**
     * 校验行程序号、日期范围与重复序号。
     *
     * @param trip 出差申请
     * @param commands 行程命令
     */
    private void validateItineraries(Trip trip, List<TripItineraryCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        Set<Integer> dayNos = new HashSet<>();
        for (TripItineraryCommand command : commands) {
            if (command.getDayNo() == null || command.getDayNo() < 1
                    || (trip.getDays() != null && command.getDayNo() > trip.getDays())) {
                throw OaErrorCode.exception(OaErrorCode.TRIP_INVALID,
                        "行程序号必须在 1 至 " + trip.getDays() + " 之间");
            }
            if (!dayNos.add(command.getDayNo())) {
                throw OaErrorCode.exception(OaErrorCode.TRIP_INVALID,
                        "行程序号重复：" + command.getDayNo());
            }
            if (command.getTripDate().isBefore(trip.getStartDate())
                    || command.getTripDate().isAfter(trip.getEndDate())) {
                throw OaErrorCode.exception(OaErrorCode.TRIP_INVALID_PERIOD,
                        "行程日期必须落在出差起止日期内");
            }
        }
    }

    /**
     * 行程命令转领域模型。
     *
     * @param commands 行程命令
     * @return 行程列表
     */
    private List<TripItinerary> toItineraries(List<TripItineraryCommand> commands) {
        List<TripItinerary> itineraries = new ArrayList<>();
        if (commands == null) {
            return itineraries;
        }
        for (TripItineraryCommand command : commands) {
            TripItinerary itinerary = new TripItinerary();
            itinerary.setDayNo(command.getDayNo());
            itinerary.setTripDate(command.getTripDate());
            itinerary.setFromPlace(command.getFromPlace());
            itinerary.setToPlace(command.getToPlace());
            itinerary.setTransport(StringUtils.hasText(command.getTransport())
                    ? TripTransportType.of(command.getTransport()) : TripTransportType.TRAIN);
            itinerary.setRemark(command.getRemark());
            itineraries.add(itinerary);
        }
        return itineraries;
    }

    /**
     * 查询申请，缺失时抛业务异常。
     *
     * @param id 主键
     * @return 申请
     */
    private Trip requireTrip(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.TRIP_NOT_FOUND,
                        "出差申请不存在，id=" + id));
    }

    /**
     * 查询并校验申请处于可编辑状态。
     *
     * @param id 主键
     * @return 申请
     */
    private Trip requireEditable(Long id) {
        Trip trip = requireTrip(id);
        trip.ensureEditable();
        return trip;
    }

    /**
     * 校验申请单号在租户内唯一。
     *
     * @param code 申请单号
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        if (tripRepository.existsByCode(code, excludeId)) {
            throw OaErrorCode.exception(OaErrorCode.TRIP_INVALID, "申请单号已存在");
        }
    }

    /**
     * 校验申请人扩展档案存在。
     *
     * @param applicantId 申请人用户ID，可为空（历史调用方）
     */
    private void assertProfileExists(Long applicantId) {
        if (applicantId == null) {
            return;
        }
        employeeProfileRepository.findByUserId(applicantId)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.EMPLOYEE_PROFILE_NOT_FOUND,
                        "申请人档案不存在，userId=" + applicantId));
    }

    /**
     * 命令字段写入申请模型。
     *
     * @param trip 申请
     * @param command 保存命令
     */
    private void apply(Trip trip, TripSaveCommand command) {
        trip.setCode(command.getCode());
        trip.setApplicant(command.getApplicant());
        trip.setApplicantId(command.getApplicantId());
        trip.setDestination(command.getDestination());
        trip.setStartDate(command.getStartDate());
        trip.setEndDate(command.getEndDate());
        trip.setTransportType(StringUtils.hasText(command.getTransportType())
                ? TripTransportType.of(command.getTransportType()) : TripTransportType.TRAIN);
        trip.setBudgetAmount(command.getBudgetAmount() == null ? BigDecimal.ZERO : command.getBudgetAmount());
        trip.setAdvanceAmount(command.getAdvanceAmount() == null ? BigDecimal.ZERO : command.getAdvanceAmount());
        trip.setRemark(command.getRemark());
    }

    /**
     * 领域模型转视图。
     *
     * @param trip 申请
     * @return 申请视图
     */
    private TripView toView(Trip trip) {
        TripView view = new TripView();
        view.setId(trip.getId());
        view.setCode(trip.getCode());
        view.setApplicant(trip.getApplicant());
        view.setApplicantId(trip.getApplicantId());
        view.setDestination(trip.getDestination());
        view.setStartDate(trip.getStartDate());
        view.setEndDate(trip.getEndDate());
        view.setDays(trip.getDays());
        view.setTransportType(trip.getTransportType() == null ? null : trip.getTransportType().name());
        view.setBudgetAmount(trip.getBudgetAmount());
        view.setAdvanceAmount(trip.getAdvanceAmount());
        view.setReportStatus(trip.getReportStatus() == null ? null : trip.getReportStatus().name());
        view.setReportContent(trip.getReportContent());
        view.setReportTime(trip.getReportTime());
        view.setStatus(trip.getStatus() == null ? null : trip.getStatus().name());
        view.setApprovalInstanceId(trip.getApprovalInstanceId());
        view.setRemark(trip.getRemark());
        view.setCreateTime(trip.getCreateTime());
        view.setUpdateTime(trip.getUpdateTime());
        if (trip.getItineraries() != null) {
            view.setItineraries(trip.getItineraries().stream().map(this::toItineraryView).toList());
        }
        return view;
    }

    /**
     * 行程转视图。
     *
     * @param itinerary 行程
     * @return 行程视图
     */
    private TripItineraryView toItineraryView(TripItinerary itinerary) {
        TripItineraryView view = new TripItineraryView();
        view.setDayNo(itinerary.getDayNo());
        view.setTripDate(itinerary.getTripDate());
        view.setFromPlace(itinerary.getFromPlace());
        view.setToPlace(itinerary.getToPlace());
        view.setTransport(itinerary.getTransport() == null ? null : itinerary.getTransport().name());
        view.setRemark(itinerary.getRemark());
        return view;
    }
}
