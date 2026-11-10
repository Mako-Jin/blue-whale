package com.bluewhale.oa.attendance.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.service.AttendanceCorrectionAppService;
import com.bluewhale.oa.attendance.application.service.AttendanceDailyAppService;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceCorrection;
import com.bluewhale.oa.attendance.domain.model.AttendanceCorrectionQuery;
import com.bluewhale.oa.attendance.domain.model.AttendanceGroup;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecord;
import com.bluewhale.oa.attendance.domain.model.AttendanceRule;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import com.bluewhale.oa.attendance.domain.repository.AttendanceConfigRepository;
import com.bluewhale.oa.attendance.domain.repository.AttendanceCorrectionRepository;
import com.bluewhale.oa.attendance.domain.repository.AttendanceRecordRepository;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.common.application.service.SequenceAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 补卡申请应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AttendanceCorrectionAppServiceImpl implements AttendanceCorrectionAppService {

    private static final String CODE_BIZ_TYPE = "OA_ATT_CORRECTION";

    private static final String CODE_PREFIX = "ATT";

    private final AttendanceCorrectionRepository correctionRepository;

    private final AttendanceRecordRepository recordRepository;

    private final AttendanceConfigRepository configRepository;

    private final AttendanceDailyAppService dailyAppService;

    private final SequenceAppService sequenceAppService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceViews.CorrectionView create(Long userId, AttendanceCommands.CorrectionSaveCommand command) {
        if (!AttendanceStatus.isValidPunchType(command.getPunchType())) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "补卡类型仅支持 IN / OUT");
        }
        LocalDate workDate = command.getWorkDate();
        if (!command.getPunchTime().toLocalDate().equals(workDate)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "补卡时间必须落在补卡日期当天");
        }
        if (workDate.isAfter(LocalDate.now())) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "不能为未来日期补卡");
        }
        AttendanceRule rule = resolveRule(userId);
        int windowDays = rule == null || rule.getCorrectionWindowDays() == null
                ? 7 : rule.getCorrectionWindowDays();
        if (workDate.isBefore(LocalDate.now().minusDays(windowDays))) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "仅支持补 " + windowDays + " 天内的考勤");
        }
        if (recordRepository.exists(userId, command.getPunchTime(), command.getPunchType())) {
            throw OaErrorCode.exception(OaErrorCode.ATT_RECORD_DUPLICATE, "该时间已有打卡记录，无需补卡");
        }
        if (correctionRepository.findPending(userId, workDate, command.getPunchType()).isPresent()) {
            throw OaErrorCode.exception(OaErrorCode.ATT_CORRECTION_LIMIT, "该日同类型补卡正在审批中");
        }
        LocalDate monthStart = workDate.withDayOfMonth(1);
        LocalDate monthEnd = workDate.withDayOfMonth(workDate.lengthOfMonth());
        long used = correctionRepository.countByMonth(userId, monthStart, monthEnd);
        int max = rule == null || rule.getMaxCorrectionsPerMonth() == null
                ? 3 : rule.getMaxCorrectionsPerMonth();
        if (used >= max) {
            throw OaErrorCode.exception(OaErrorCode.ATT_CORRECTION_LIMIT);
        }
        AttendanceCorrection correction = new AttendanceCorrection();
        correction.setCode(sequenceAppService.nextCode(CODE_BIZ_TYPE, CODE_PREFIX));
        correction.setUserId(userId);
        correction.setUserName(command.getUserName());
        correction.setWorkDate(workDate);
        correction.setPunchTime(command.getPunchTime());
        correction.setPunchType(command.getPunchType());
        correction.setReason(command.getReason());
        correction.setStatus(AttendanceStatus.CORRECTION_PENDING);
        correctionRepository.save(correction);
        return AttendanceViews.CorrectionView.from(correction);
    }

    @Override
    public PageResult<AttendanceViews.CorrectionView> page(AttendanceCorrectionQuery query) {
        PageResult<AttendanceCorrection> page = correctionRepository.page(query);
        List<AttendanceViews.CorrectionView> records = page.getRecords().stream()
                .map(AttendanceViews.CorrectionView::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncApprovalResult(Long id, String result) {
        AttendanceCorrection correction = correctionRepository.findById(id).orElse(null);
        if (correction == null || !AttendanceStatus.CORRECTION_PENDING.equals(correction.getStatus())) {
            return;
        }
        String normalized = result == null ? "" : result.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case AttendanceStatus.CORRECTION_APPROVED -> applyApproved(correction);
            case AttendanceStatus.CORRECTION_REJECTED -> {
                correction.setStatus(AttendanceStatus.CORRECTION_REJECTED);
                correction.setHandler("APPROVAL");
                correction.setHandledTime(LocalDateTime.now());
                correctionRepository.save(correction);
            }
            case AttendanceStatus.CORRECTION_CANCELLED -> {
                correction.setStatus(AttendanceStatus.CORRECTION_CANCELLED);
                correction.setHandler("APPROVAL");
                correction.setHandledTime(LocalDateTime.now());
                correctionRepository.save(correction);
            }
            default -> {
                // 其它结果（如撤回/流程终止）不改变业务状态
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, AttendanceCommands.CorrectionStatusCommand command, String handler) {
        AttendanceCorrection correction = correctionRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_CORRECTION_NOT_FOUND));
        String target = StringUtils.hasText(command.getStatus())
                ? command.getStatus().trim().toUpperCase(Locale.ROOT) : "";
        if (!AttendanceStatus.CORRECTION_APPROVED.equals(target)
                && !AttendanceStatus.CORRECTION_REJECTED.equals(target)
                && !AttendanceStatus.CORRECTION_CANCELLED.equals(target)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "状态仅支持 APPROVED / REJECTED / CANCELLED");
        }
        if (!correction.cancellable()) {
            if (target.equals(correction.getStatus())) {
                return;
            }
            throw OaErrorCode.exception(OaErrorCode.ATT_CORRECTION_STATUS_CONFLICT);
        }
        if (AttendanceStatus.CORRECTION_APPROVED.equals(target)) {
            applyApproved(correction);
            correction.setHandler(handler);
            correction.setRemark(command.getRemark());
            correctionRepository.save(correction);
            return;
        }
        correction.setStatus(target);
        correction.setHandler(handler);
        correction.setHandledTime(LocalDateTime.now());
        correction.setRemark(command.getRemark());
        correctionRepository.save(correction);
    }

    /**
     * 审批通过：写入补卡记录并重算当日汇总。
     */
    private void applyApproved(AttendanceCorrection correction) {
        if (!recordRepository.exists(correction.getUserId(), correction.getPunchTime(), correction.getPunchType())) {
            AttendanceRecord record = new AttendanceRecord();
            record.setUserId(correction.getUserId());
            record.setUserName(correction.getUserName());
            record.setPunchTime(correction.getPunchTime());
            record.setPunchType(correction.getPunchType());
            record.setSource(AttendanceStatus.SOURCE_WEB);
            record.setCorrection(1);
            record.setRemark("补卡：" + (correction.getCode() == null ? "" : correction.getCode()));
            recordRepository.save(record);
        }
        correction.setStatus(AttendanceStatus.CORRECTION_APPROVED);
        correction.setHandledTime(LocalDateTime.now());
        correctionRepository.save(correction);

        AttendanceCommands.RecalculateCommand recalculate = new AttendanceCommands.RecalculateCommand();
        recalculate.setUserId(correction.getUserId());
        recalculate.setDateFrom(correction.getWorkDate());
        recalculate.setDateTo(correction.getWorkDate());
        dailyAppService.recalculate(recalculate);
    }

    /**
     * 解析用户适用的考勤规则：优先考勤组规则，回退默认规则。
     *
     * @param userId 用户ID
     * @return 考勤规则，未配置时返回 null
     */
    private AttendanceRule resolveRule(Long userId) {
        AttendanceGroup group = configRepository.findGroupByUserId(userId).orElse(null);
        if (group != null && group.getRuleId() != null) {
            AttendanceRule rule = configRepository.findRule(group.getRuleId()).orElse(null);
            if (rule != null) {
                return rule;
            }
        }
        return configRepository.findDefaultRule().orElse(null);
    }
}
