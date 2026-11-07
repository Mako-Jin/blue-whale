package com.bluewhale.oa.attendance.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.service.AttendanceDailyAppService;
import com.bluewhale.oa.attendance.application.service.AttendanceRecordAppService;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecord;
import com.bluewhale.oa.attendance.domain.model.AttendanceRecordQuery;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import com.bluewhale.oa.attendance.domain.repository.AttendanceRecordRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 考勤打卡应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AttendanceRecordAppServiceImpl implements AttendanceRecordAppService {

    private final AttendanceRecordRepository recordRepository;

    private final AttendanceDailyAppService dailyAppService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AttendanceViews.RecordView punch(Long userId, AttendanceCommands.PunchCommand command) {
        if (!AttendanceStatus.isValidPunchType(command.getPunchType())) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "打卡类型仅支持 IN / OUT");
        }
        String source = StringUtils.hasText(command.getSource()) ? command.getSource() : AttendanceStatus.SOURCE_WEB;
        if (!AttendanceStatus.isValidSource(source)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "打卡来源仅支持 APP / DEVICE / WEB");
        }
        LocalDateTime punchTime = command.getPunchTime() == null ? LocalDateTime.now() : command.getPunchTime();
        if (recordRepository.exists(userId, punchTime, command.getPunchType())) {
            throw OaErrorCode.exception(OaErrorCode.ATT_RECORD_DUPLICATE);
        }
        AttendanceRecord record = new AttendanceRecord();
        record.setUserId(userId);
        record.setUserName(command.getUserName());
        record.setPunchTime(punchTime);
        record.setPunchType(command.getPunchType());
        record.setSource(source);
        record.setLatitude(command.getLatitude());
        record.setLongitude(command.getLongitude());
        record.setAddress(command.getAddress());
        record.setDeviceCode(command.getDeviceCode());
        record.setCorrection(0);
        record.setRemark(command.getRemark());
        recordRepository.save(record);

        AttendanceCommands.RecalculateCommand recalculate = new AttendanceCommands.RecalculateCommand();
        recalculate.setUserId(userId);
        recalculate.setDateFrom(punchTime.toLocalDate());
        recalculate.setDateTo(punchTime.toLocalDate());
        dailyAppService.recalculate(recalculate);
        return AttendanceViews.RecordView.from(record);
    }

    @Override
    public PageResult<AttendanceViews.RecordView> page(AttendanceRecordQuery query) {
        PageResult<AttendanceRecord> page = recordRepository.page(query);
        List<AttendanceViews.RecordView> records = page.getRecords().stream()
                .map(AttendanceViews.RecordView::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
