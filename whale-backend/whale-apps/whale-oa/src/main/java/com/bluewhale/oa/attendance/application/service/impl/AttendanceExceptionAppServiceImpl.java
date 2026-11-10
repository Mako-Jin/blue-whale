package com.bluewhale.oa.attendance.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.attendance.application.command.AttendanceCommands;
import com.bluewhale.oa.attendance.application.service.AttendanceExceptionAppService;
import com.bluewhale.oa.attendance.application.view.AttendanceViews;
import com.bluewhale.oa.attendance.domain.model.AttendanceException;
import com.bluewhale.oa.attendance.domain.model.AttendanceExceptionQuery;
import com.bluewhale.oa.attendance.domain.model.AttendanceStatus;
import com.bluewhale.oa.attendance.domain.repository.AttendanceExceptionRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 考勤异常应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AttendanceExceptionAppServiceImpl implements AttendanceExceptionAppService {

    private final AttendanceExceptionRepository exceptionRepository;

    @Override
    public PageResult<AttendanceViews.ExceptionView> page(AttendanceExceptionQuery query) {
        PageResult<AttendanceException> page = exceptionRepository.page(query);
        List<AttendanceViews.ExceptionView> records = page.getRecords().stream()
                .map(AttendanceViews.ExceptionView::from).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, AttendanceCommands.ExceptionHandleCommand command, String handler) {
        AttendanceException exception = exceptionRepository.findById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.ATT_EXCEPTION_NOT_FOUND));
        String target = command.getStatus() == null ? "" : command.getStatus().trim().toUpperCase(Locale.ROOT);
        if (!AttendanceStatus.EXCEPTION_CONFIRMED.equals(target)
                && !AttendanceStatus.EXCEPTION_WAIVED.equals(target)) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "处理状态仅支持 CONFIRMED / WAIVED");
        }
        if (!AttendanceStatus.EXCEPTION_PENDING.equals(exception.getStatus())) {
            throw OaErrorCode.exception(OaErrorCode.ATT_INVALID, "该异常已处理");
        }
        exception.setStatus(target);
        exception.setHandler(handler);
        exception.setHandledTime(LocalDateTime.now());
        exception.setRemark(command.getRemark());
        exceptionRepository.update(exception);
    }
}
