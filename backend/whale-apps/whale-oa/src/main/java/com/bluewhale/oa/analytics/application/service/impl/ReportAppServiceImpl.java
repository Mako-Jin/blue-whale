package com.bluewhale.oa.analytics.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.analytics.application.query.ReportQuery;
import com.bluewhale.oa.analytics.application.service.ReportAppService;
import com.bluewhale.oa.analytics.application.support.ReportCatalog;
import com.bluewhale.oa.analytics.application.support.ReportCsvWriter;
import com.bluewhale.oa.analytics.application.view.ReportViews;
import com.bluewhale.oa.analytics.domain.model.ReportExportLog;
import com.bluewhale.oa.analytics.domain.model.ReportRows;
import com.bluewhale.oa.analytics.domain.model.ReportSnapshot;
import com.bluewhale.oa.analytics.domain.model.ReportType;
import com.bluewhale.oa.analytics.domain.repository.ReportRepository;
import com.bluewhale.oa.common.OaErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 数据报表应用服务实现。
 *
 * <p>报表一律按请求实时只读聚合，不复制业务表；快照仅保存聚合结果。
 * 九类报表共用统一的行列结构，前端按列定义渲染通用表格。</p>
 *
 * @author BlueWhale
 */
@Service
public class ReportAppServiceImpl implements ReportAppService {

    /** 单次报表最大的自然日跨度 */
    private static final int MAX_RANGE_DAYS = 366;

    /** 快照期间格式 */
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 每 MB 字节数 */
    private static final long BYTES_PER_MB = 1024L * 1024L;

    /** 审批积压判定阈值（小时） */
    private static final long OVERDUE_HOURS = 48L;

    /** 网盘单用户配额（MB），与网盘模块共用配置键 */
    private final long quotaMb;

    private final ReportRepository reportRepository;

    private final ObjectMapper objectMapper;

    /**
     * 构造器。
     *
     * @param reportRepository 报表仓储
     * @param objectMapper JSON 序列化器
     * @param quotaMb 网盘单用户配额（MB）
     */
    public ReportAppServiceImpl(ReportRepository reportRepository, ObjectMapper objectMapper,
            @Value("${whale.oa.drive.quota-mb:2048}") long quotaMb) {
        this.reportRepository = reportRepository;
        this.objectMapper = objectMapper;
        this.quotaMb = quotaMb;
    }

    @Override
    public List<ReportViews.CatalogItem> catalog() {
        return ReportCatalog.catalog();
    }

    @Override
    public ReportViews.ReportResult run(String code, ReportQuery query) {
        return execute(ReportType.of(code), query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportViews.SnapshotView createSnapshot(String code, String period, Long operatorId, String operator) {
        ReportType type = ReportType.of(code);
        YearMonth month = parsePeriod(period);
        ReportQuery query = new ReportQuery();
        query.setFrom(month.atDay(1));
        query.setTo(month.atEndOfMonth());
        query.setYear(month.getYear());
        ReportViews.ReportResult result = execute(type, query);
        ReportSnapshot snapshot = new ReportSnapshot();
        snapshot.setReportCode(type.name());
        snapshot.setReportName(type.getTitle());
        snapshot.setPeriod(month.format(PERIOD_FORMAT));
        snapshot.setFromDate(result.getFrom());
        snapshot.setToDate(result.getTo());
        snapshot.setRowCount(result.getRowCount());
        snapshot.setPayload(serialize(result));
        snapshot.setGeneratedBy(operatorId);
        snapshot.setGeneratedByName(operator);
        snapshot.setGeneratedAt(LocalDateTime.now());
        reportRepository.saveSnapshot(snapshot);
        return reportRepository.findSnapshot(type.name(), snapshot.getPeriod())
                .map(ReportAppServiceImpl::toSnapshotView)
                .orElseGet(() -> toSnapshotView(snapshot));
    }

    @Override
    public PageResult<ReportViews.SnapshotView> pageSnapshots(String code, long current, long size) {
        long page = Math.max(1L, current);
        long pageSize = Math.min(Math.max(1L, size), 100L);
        String reportCode = code == null || code.isBlank() ? null : ReportType.of(code).name();
        List<ReportViews.SnapshotView> records = reportRepository.pageSnapshots(reportCode, page, pageSize)
                .stream().map(ReportAppServiceImpl::toSnapshotView).toList();
        return PageResult.of(records, reportRepository.countSnapshots(reportCode), page, pageSize);
    }

    @Override
    public ReportViews.ReportResult snapshotDetail(Long id) {
        ReportSnapshot snapshot = reportRepository.findSnapshotById(id)
                .orElseThrow(() -> OaErrorCode.exception(OaErrorCode.REPORT_SNAPSHOT_NOT_FOUND));
        try {
            return objectMapper.readValue(snapshot.getPayload(), ReportViews.ReportResult.class);
        } catch (JsonProcessingException exception) {
            throw OaErrorCode.exception(OaErrorCode.REPORT_INVALID, "报表快照内容已损坏，请重新生成");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportViews.ExportFile export(String code, ReportQuery query, Long operatorId, String operator) {
        ReportType type = ReportType.of(code);
        ReportViews.ReportResult result = execute(type, query);
        byte[] content = ReportCsvWriter.write(result);
        ReportExportLog log = new ReportExportLog();
        log.setReportCode(type.name());
        log.setReportName(type.getTitle());
        log.setFromDate(result.getFrom());
        log.setToDate(result.getTo());
        log.setExportFormat("CSV");
        log.setRowCount(result.getRowCount());
        log.setExportedBy(operatorId);
        log.setExportedByName(operator);
        log.setExportedAt(LocalDateTime.now());
        reportRepository.saveExportLog(log);
        ReportViews.ExportFile file = new ReportViews.ExportFile();
        file.setFilename(type.name() + "_" + result.getFrom() + "_" + result.getTo() + ".csv");
        file.setContent(content);
        file.setRowCount(result.getRowCount());
        return file;
    }

    /**
     * 执行报表并组装统一结果。
     *
     * @param type 报表类型
     * @param query 查询条件
     * @return 报表结果
     */
    private ReportViews.ReportResult execute(ReportType type, ReportQuery query) {
        ReportQuery normalized = normalize(query);
        List<Map<String, Object>> rows = loadRows(type, normalized);
        ReportViews.ReportResult result = new ReportViews.ReportResult();
        result.setCode(type.name());
        result.setTitle(type.getTitle());
        result.setFrom(normalized.getFrom());
        result.setTo(normalized.getTo());
        result.setYear(normalized.getYear());
        result.setColumns(ReportCatalog.columns(type));
        result.setRows(rows);
        result.setRowCount(rows.size());
        result.setGeneratedAt(LocalDateTime.now());
        return result;
    }

    /**
     * 规范化并校验查询条件。
     *
     * @param query 原始条件
     * @return 补齐缺省值后的条件
     */
    private ReportQuery normalize(ReportQuery query) {
        LocalDate today = LocalDate.now();
        LocalDate from = query == null || query.getFrom() == null ? today.withDayOfMonth(1) : query.getFrom();
        LocalDate to = query == null || query.getTo() == null ? today : query.getTo();
        if (to.isBefore(from)) {
            throw OaErrorCode.exception(OaErrorCode.REPORT_INVALID, "截止日期不能早于起始日期");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw OaErrorCode.exception(OaErrorCode.REPORT_INVALID,
                    "统计区间不能超过 " + MAX_RANGE_DAYS + " 天");
        }
        int year = query == null || query.getYear() == null ? from.getYear() : query.getYear();
        if (year < 2000 || year > 2100) {
            throw OaErrorCode.exception(OaErrorCode.REPORT_INVALID, "额度年份应在 2000-2100 之间");
        }
        ReportQuery normalized = new ReportQuery();
        normalized.setFrom(from);
        normalized.setTo(to);
        normalized.setYear(year);
        return normalized;
    }

    /**
     * 按报表类型加载聚合数据。
     *
     * @param type 报表类型
     * @param query 规范化后的条件
     * @return 数据行
     */
    private List<Map<String, Object>> loadRows(ReportType type, ReportQuery query) {
        return switch (type) {
            case ATTENDANCE_MONTHLY -> attendanceRows(
                    reportRepository.attendanceMonthly(query.getFrom(), query.getTo()));
            case APPROVAL_EFFICIENCY -> approvalRows(
                    reportRepository.approvalInstances(query.getFrom(), query.getTo()));
            case EXPENSE_DEPT -> expenseRows(
                    reportRepository.expenseByDept(query.getFrom(), query.getTo()));
            case LEAVE_BALANCE -> leaveRows(reportRepository.leaveBalance(query.getYear()));
            case VEHICLE_UTILIZATION -> vehicleRows(
                    reportRepository.vehicleUtilization(query.getFrom(), query.getTo()));
            case SEAL_UTILIZATION -> sealRows(
                    reportRepository.sealUtilization(query.getFrom(), query.getTo()));
            case MEETING_ROOM_UTILIZATION -> meetingRows(
                    reportRepository.meetingRoomUtilization(query.getFrom(), query.getTo()));
            case NOTICE_READ_RATE -> noticeRows(
                    reportRepository.noticeReadRate(query.getFrom(), query.getTo()));
            case DRIVE_QUOTA -> driveRows(reportRepository.driveQuota(quotaMb * BYTES_PER_MB));
        };
    }

    /**
     * 考勤行转换。
     *
     * @param source 聚合行
     * @return 统一行
     */
    private List<Map<String, Object>> attendanceRows(List<ReportRows.AttendanceRow> source) {
        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (ReportRows.AttendanceRow item : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", item.getUserId());
            row.put("userName", item.getUserName());
            row.put("workDays", item.getWorkDays());
            row.put("lateDays", item.getLateDays());
            row.put("earlyDays", item.getEarlyDays());
            row.put("absentDays", item.getAbsentDays());
            row.put("lateMinutes", item.getLateMinutes());
            row.put("earlyMinutes", item.getEarlyMinutes());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 部门报销行转换。
     *
     * @param source 聚合行
     * @return 统一行
     */
    private List<Map<String, Object>> expenseRows(List<ReportRows.ExpenseDeptRow> source) {
        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (ReportRows.ExpenseDeptRow item : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deptId", item.getDeptId());
            row.put("headcount", item.getHeadcount());
            row.put("expenseCount", item.getExpenseCount());
            row.put("applicantCount", item.getApplicantCount());
            row.put("totalAmount", item.getTotalAmount());
            row.put("avgAmount", item.getAvgAmount());
            row.put("perCapitaAmount", item.getPerCapitaAmount());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 假期余额行转换。
     *
     * @param source 聚合行
     * @return 统一行
     */
    private List<Map<String, Object>> leaveRows(List<ReportRows.LeaveBalanceRow> source) {
        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (ReportRows.LeaveBalanceRow item : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("leaveTypeCode", item.getLeaveTypeCode());
            row.put("leaveTypeName", item.getLeaveTypeName());
            row.put("userCount", item.getUserCount());
            row.put("totalDays", item.getTotalDays());
            row.put("usedDays", item.getUsedDays());
            row.put("frozenDays", item.getFrozenDays());
            row.put("availableDays", item.getAvailableDays());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 用车行转换。
     *
     * @param source 聚合行
     * @return 统一行
     */
    private List<Map<String, Object>> vehicleRows(List<ReportRows.VehicleRow> source) {
        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (ReportRows.VehicleRow item : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("vehicleId", item.getVehicleId());
            row.put("plateNo", item.getPlateNo());
            row.put("bookingCount", item.getBookingCount());
            row.put("totalHours", item.getTotalHours());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 用章行转换。
     *
     * @param source 聚合行
     * @return 统一行
     */
    private List<Map<String, Object>> sealRows(List<ReportRows.SealRow> source) {
        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (ReportRows.SealRow item : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sealId", item.getSealId());
            row.put("sealName", item.getSealName());
            row.put("useCount", item.getUseCount());
            row.put("fileCount", item.getFileCount());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 会议室行转换。
     *
     * @param source 聚合行
     * @return 统一行
     */
    private List<Map<String, Object>> meetingRows(List<ReportRows.MeetingRoomRow> source) {
        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (ReportRows.MeetingRoomRow item : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roomId", item.getRoomId());
            row.put("roomName", item.getRoomName());
            row.put("meetingCount", item.getMeetingCount());
            row.put("totalHours", item.getTotalHours());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 公告阅读行转换。
     *
     * @param source 聚合行
     * @return 统一行
     */
    private List<Map<String, Object>> noticeRows(List<ReportRows.NoticeRow> source) {
        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (ReportRows.NoticeRow item : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("noticeId", item.getNoticeId());
            row.put("title", item.getTitle());
            row.put("publishTime", item.getPublishTime());
            row.put("recipientCount", item.getRecipientCount());
            row.put("readCount", item.getReadCount());
            row.put("readRate", item.getReadRate());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 网盘配额行转换。
     *
     * @param source 聚合行
     * @return 统一行
     */
    private List<Map<String, Object>> driveRows(List<ReportRows.DriveQuotaRow> source) {
        List<Map<String, Object>> rows = new ArrayList<>(source.size());
        for (ReportRows.DriveQuotaRow item : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uploaderId", item.getUploaderId());
            row.put("uploaderName", item.getUploaderName());
            row.put("fileCount", item.getFileCount());
            row.put("usedMb", item.getUsedMb());
            row.put("usagePercent", item.getUsagePercent());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 审批效率聚合：按业务类型统计状态分布与时长分位数。
     *
     * @param source 审批实例行
     * @return 统一行
     */
    private List<Map<String, Object>> approvalRows(List<ReportRows.ApprovalRow> source) {
        Map<String, ApprovalAggregate> groups = new TreeMap<>();
        LocalDateTime overdueThreshold = LocalDateTime.now().minusHours(OVERDUE_HOURS);
        for (ReportRows.ApprovalRow item : source) {
            String businessType = item.getBusinessType() == null ? "UNKNOWN" : item.getBusinessType();
            groups.computeIfAbsent(businessType, ApprovalAggregate::new).accept(item, overdueThreshold);
        }
        List<ApprovalAggregate> sorted = new ArrayList<>(groups.values());
        sorted.sort(Comparator.comparingInt((ApprovalAggregate agg) -> agg.total).reversed());
        List<Map<String, Object>> rows = new ArrayList<>(sorted.size());
        for (ApprovalAggregate aggregate : sorted) {
            rows.add(aggregate.toRow());
        }
        return rows;
    }

    /**
     * 序列化报表结果为 JSON。
     *
     * @param result 报表结果
     * @return JSON 文本
     */
    private String serialize(ReportViews.ReportResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw OaErrorCode.exception(OaErrorCode.REPORT_INVALID, "报表快照序列化失败");
        }
    }

    /**
     * 解析统计期间。
     *
     * @param period 期间文本
     * @return 年月
     */
    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(period.trim(), PERIOD_FORMAT);
        } catch (DateTimeParseException exception) {
            throw OaErrorCode.exception(OaErrorCode.REPORT_INVALID, "统计期间格式应为 YYYY-MM：" + period);
        }
    }

    /**
     * 快照模型转视图。
     *
     * @param snapshot 快照
     * @return 快照视图
     */
    private static ReportViews.SnapshotView toSnapshotView(ReportSnapshot snapshot) {
        ReportViews.SnapshotView view = new ReportViews.SnapshotView();
        view.setId(snapshot.getId());
        view.setReportCode(snapshot.getReportCode());
        view.setReportName(snapshot.getReportName());
        view.setPeriod(snapshot.getPeriod());
        view.setFromDate(snapshot.getFromDate());
        view.setToDate(snapshot.getToDate());
        view.setRowCount(snapshot.getRowCount());
        view.setGeneratedBy(snapshot.getGeneratedBy());
        view.setGeneratedByName(snapshot.getGeneratedByName());
        view.setGeneratedAt(snapshot.getGeneratedAt());
        return view;
    }

    /**
     * 审批聚合累加器。
     */
    private static final class ApprovalAggregate {

        /** 业务类型 */
        private final String businessType;

        /** 申请总数 */
        private int total;

        /** 已通过 */
        private int approved;

        /** 已驳回 */
        private int rejected;

        /** 审批中 */
        private int running;

        /** 超 48 小时未办 */
        private int overdue;

        /** 已完成时长（分钟） */
        private final List<Long> durations = new ArrayList<>();

        /**
         * 构造器。
         *
         * @param businessType 业务类型
         */
        private ApprovalAggregate(String businessType) {
            this.businessType = businessType;
        }

        /**
         * 累加一条审批实例。
         *
         * @param row 审批实例行
         * @param overdueThreshold 积压判定阈值
         */
        private void accept(ReportRows.ApprovalRow row, LocalDateTime overdueThreshold) {
            total++;
            String status = row.getStatus();
            if ("APPROVED".equals(status)) {
                approved++;
            } else if ("REJECTED".equals(status)) {
                rejected++;
            } else if ("RUNNING".equals(status)) {
                running++;
                if (row.getSubmittedAt() != null && row.getSubmittedAt().isBefore(overdueThreshold)) {
                    overdue++;
                }
            }
            if (row.getSubmittedAt() != null && row.getFinishedAt() != null) {
                long minutes = ChronoUnit.MINUTES.between(row.getSubmittedAt(), row.getFinishedAt());
                durations.add(Math.max(0L, minutes));
            }
        }

        /**
         * 转换为统一行。
         *
         * @return 行数据
         */
        private Map<String, Object> toRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("businessType", businessType);
            row.put("totalCount", total);
            row.put("approvedCount", approved);
            row.put("rejectedCount", rejected);
            row.put("runningCount", running);
            row.put("avgMinutes", average());
            row.put("p50Minutes", percentile(50));
            row.put("p90Minutes", percentile(90));
            row.put("overdue48Count", overdue);
            return row;
        }

        /**
         * 平均办理时长（分钟）。
         *
         * @return 平均分钟数
         */
        private long average() {
            if (durations.isEmpty()) {
                return 0L;
            }
            long sum = 0L;
            for (long duration : durations) {
                sum += duration;
            }
            return Math.round((double) sum / durations.size());
        }

        /**
         * 最近秩分位数。
         *
         * @param percent 分位（50/90）
         * @return 分位时长（分钟）
         */
        private long percentile(int percent) {
            if (durations.isEmpty()) {
                return 0L;
            }
            List<Long> sorted = new ArrayList<>(durations);
            Collections.sort(sorted);
            int index = (int) Math.ceil(percent / 100.0 * sorted.size()) - 1;
            index = Math.max(0, Math.min(index, sorted.size() - 1));
            return sorted.get(index);
        }
    }
}
