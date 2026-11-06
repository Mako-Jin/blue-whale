package com.bluewhale.oa.hr.application.service.impl;

import com.bluewhale.oa.approval.domain.gateway.ApprovalNotifyGateway;
import com.bluewhale.oa.approval.domain.gateway.UserDirectoryGateway;
import com.bluewhale.oa.approval.domain.model.ApprovalNotifyMessage;
import com.bluewhale.oa.hr.application.service.TripReportReminderAppService;
import com.bluewhale.oa.hr.domain.model.Trip;
import com.bluewhale.oa.hr.domain.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 出差报告提醒应用服务实现。
 *
 * <p>只扫描审批通过且报告待提交的出差单；接收人优先使用申请人用户ID，缺失时按账号精确解析。
 * 投递异常逐条兜底为 warn，不中断批处理。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripReportReminderAppServiceImpl implements TripReportReminderAppService {

    /** 通知业务类型 */
    private static final String BIZ_TYPE = "OA_TRIP_REPORT";

    /** 提醒模板编码 */
    private static final String TEMPLATE_REMINDER = "OA_TRIP_REPORT_REMINDER";

    /** 出差申请前端路由 */
    private static final String BUSINESS_URL = "/oa/hr/trip";

    private final TripRepository tripRepository;

    private final UserDirectoryGateway userDirectoryGateway;

    private final ApprovalNotifyGateway approvalNotifyGateway;

    @Override
    public int remindOverdueReports(LocalDate deadline, int batchSize) {
        if (deadline == null || batchSize <= 0) {
            return 0;
        }
        List<Trip> trips = tripRepository.listReportPendingEndedBefore(deadline, batchSize);
        int notified = 0;
        for (Trip trip : trips) {
            try {
                if (remind(trip, deadline)) {
                    notified++;
                }
            } catch (RuntimeException exception) {
                log.warn("出差报告提醒失败: tripId={}, error={}", trip.getId(), exception.getMessage());
            }
        }
        if (notified > 0) {
            log.info("出差报告提醒完成: scanned={}, notified={}", trips.size(), notified);
        }
        return notified;
    }

    /**
     * 向单个出差申请发送提醒。
     *
     * @param trip 出差申请
     * @param deadline 结束日期上限，同时作为按天幂等键
     * @return 已投递返回 {@code true}；无接收人返回 {@code false}
     */
    private boolean remind(Trip trip, LocalDate deadline) {
        Long userId = resolveUserId(trip).orElse(null);
        if (userId == null) {
            log.warn("出差申请无可用接收人，跳过提醒: tripId={}, applicant={}",
                    trip.getId(), trip.getApplicant());
            return false;
        }
        ApprovalNotifyMessage message = new ApprovalNotifyMessage();
        message.setBizType(BIZ_TYPE);
        message.setBizId(String.valueOf(trip.getId()));
        message.setIdempotencyKey("OA_TRIP_REPORT:" + trip.getId() + ":" + deadline);
        message.setTemplateCode(TEMPLATE_REMINDER);
        message.setTitle(nullToEmpty(trip.getDestination()) + "出差报告提交提醒");
        message.setContent("");
        message.setPayload(payload(trip));
        message.setUserIds(List.of(userId));
        approvalNotifyGateway.send(message);
        return true;
    }

    /**
     * 解析接收人：申请人用户ID优先，缺失时按账号精确匹配。
     *
     * @param trip 出差申请
     * @return 用户ID
     */
    private Optional<Long> resolveUserId(Trip trip) {
        if (trip.getApplicantId() != null) {
            return Optional.of(trip.getApplicantId());
        }
        return userDirectoryGateway.findUserIdByUsername(trip.getApplicant());
    }

    /**
     * 组装模板变量。
     *
     * @param trip 出差申请
     * @return 模板变量
     */
    private Map<String, Object> payload(Trip trip) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicant", nullToEmpty(trip.getApplicant()));
        payload.put("destination", nullToEmpty(trip.getDestination()));
        payload.put("endDate", trip.getEndDate() == null ? "" : trip.getEndDate().toString());
        payload.put("businessUrl", BUSINESS_URL);
        return payload;
    }

    /**
     * 空值转空串。
     *
     * @param value 原始值
     * @return 非空字符串
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
