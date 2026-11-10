package com.bluewhale.oa.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.oa.analytics.domain.model.ReportExportLog;
import com.bluewhale.oa.analytics.domain.model.ReportRows;
import com.bluewhale.oa.analytics.domain.model.ReportSnapshot;
import com.bluewhale.oa.analytics.domain.repository.ReportRepository;
import com.bluewhale.oa.analytics.infrastructure.persistence.entity.ReportExportLogEntity;
import com.bluewhale.oa.analytics.infrastructure.persistence.entity.ReportSnapshotEntity;
import com.bluewhale.oa.analytics.infrastructure.persistence.mapper.ReportExportLogMapper;
import com.bluewhale.oa.analytics.infrastructure.persistence.mapper.ReportQueryMapper;
import com.bluewhale.oa.analytics.infrastructure.persistence.mapper.ReportSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 报表仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ReportRepositoryImpl implements ReportRepository {

    private final ReportQueryMapper reportQueryMapper;

    private final ReportSnapshotMapper reportSnapshotMapper;

    private final ReportExportLogMapper reportExportLogMapper;

    @Override
    public List<ReportRows.AttendanceRow> attendanceMonthly(LocalDate from, LocalDate to) {
        return reportQueryMapper.attendanceMonthly(from, to);
    }

    @Override
    public List<ReportRows.ApprovalRow> approvalInstances(LocalDate from, LocalDate to) {
        return reportQueryMapper.approvalInstances(from, to);
    }

    @Override
    public List<ReportRows.ExpenseDeptRow> expenseByDept(LocalDate from, LocalDate to) {
        return reportQueryMapper.expenseByDept(from, to);
    }

    @Override
    public List<ReportRows.LeaveBalanceRow> leaveBalance(int year) {
        return reportQueryMapper.leaveBalance(year);
    }

    @Override
    public List<ReportRows.VehicleRow> vehicleUtilization(LocalDate from, LocalDate to) {
        return reportQueryMapper.vehicleUtilization(from, to);
    }

    @Override
    public List<ReportRows.SealRow> sealUtilization(LocalDate from, LocalDate to) {
        return reportQueryMapper.sealUtilization(from, to);
    }

    @Override
    public List<ReportRows.MeetingRoomRow> meetingRoomUtilization(LocalDate from, LocalDate to) {
        return reportQueryMapper.meetingRoomUtilization(from, to);
    }

    @Override
    public List<ReportRows.NoticeRow> noticeReadRate(LocalDate from, LocalDate to) {
        return reportQueryMapper.noticeReadRate(from, to);
    }

    @Override
    public List<ReportRows.DriveQuotaRow> driveQuota(long quotaBytes) {
        return reportQueryMapper.driveQuota(quotaBytes);
    }

    @Override
    public Optional<ReportSnapshot> findSnapshot(String reportCode, String period) {
        ReportSnapshotEntity entity = reportSnapshotMapper.selectOne(
                new LambdaQueryWrapper<ReportSnapshotEntity>()
                        .eq(ReportSnapshotEntity::getReportCode, reportCode)
                        .eq(ReportSnapshotEntity::getPeriod, period));
        return Optional.ofNullable(entity).map(ReportRepositoryImpl::toSnapshotModel);
    }

    @Override
    public void saveSnapshot(ReportSnapshot snapshot) {
        ReportSnapshotEntity entity = reportSnapshotMapper.selectOne(
                new LambdaQueryWrapper<ReportSnapshotEntity>()
                        .eq(ReportSnapshotEntity::getReportCode, snapshot.getReportCode())
                        .eq(ReportSnapshotEntity::getPeriod, snapshot.getPeriod()));
        if (entity == null) {
            reportSnapshotMapper.insert(toSnapshotEntity(snapshot));
            return;
        }
        entity.setReportName(snapshot.getReportName());
        entity.setFromDate(snapshot.getFromDate());
        entity.setToDate(snapshot.getToDate());
        entity.setRowCount(snapshot.getRowCount());
        entity.setPayload(snapshot.getPayload());
        entity.setGeneratedBy(snapshot.getGeneratedBy());
        entity.setGeneratedByName(snapshot.getGeneratedByName());
        entity.setGeneratedAt(snapshot.getGeneratedAt());
        reportSnapshotMapper.updateById(entity);
    }

    @Override
    public List<ReportSnapshot> pageSnapshots(String reportCode, long current, long size) {
        IPage<ReportSnapshotEntity> page = reportSnapshotMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<ReportSnapshotEntity>()
                        .eq(reportCode != null, ReportSnapshotEntity::getReportCode, reportCode)
                        .orderByDesc(ReportSnapshotEntity::getGeneratedAt)
                        .orderByDesc(ReportSnapshotEntity::getId));
        return page.getRecords().stream().map(ReportRepositoryImpl::toSnapshotModel).toList();
    }

    @Override
    public long countSnapshots(String reportCode) {
        return reportSnapshotMapper.selectCount(new LambdaQueryWrapper<ReportSnapshotEntity>()
                .eq(reportCode != null, ReportSnapshotEntity::getReportCode, reportCode));
    }

    @Override
    public Optional<ReportSnapshot> findSnapshotById(Long id) {
        return Optional.ofNullable(reportSnapshotMapper.selectById(id))
                .map(ReportRepositoryImpl::toSnapshotModel);
    }

    @Override
    public void saveExportLog(ReportExportLog log) {
        ReportExportLogEntity entity = new ReportExportLogEntity();
        entity.setReportCode(log.getReportCode());
        entity.setReportName(log.getReportName());
        entity.setPeriod(log.getPeriod());
        entity.setFromDate(log.getFromDate());
        entity.setToDate(log.getToDate());
        entity.setExportFormat(log.getExportFormat());
        entity.setRowCount(log.getRowCount());
        entity.setExportedBy(log.getExportedBy());
        entity.setExportedByName(log.getExportedByName());
        entity.setExportedAt(log.getExportedAt());
        reportExportLogMapper.insert(entity);
    }

    /**
     * 实体转快照模型。
     *
     * @param entity 实体
     * @return 快照模型
     */
    private static ReportSnapshot toSnapshotModel(ReportSnapshotEntity entity) {
        ReportSnapshot snapshot = new ReportSnapshot();
        snapshot.setId(entity.getId());
        snapshot.setReportCode(entity.getReportCode());
        snapshot.setReportName(entity.getReportName());
        snapshot.setPeriod(entity.getPeriod());
        snapshot.setFromDate(entity.getFromDate());
        snapshot.setToDate(entity.getToDate());
        snapshot.setRowCount(entity.getRowCount());
        snapshot.setPayload(entity.getPayload());
        snapshot.setGeneratedBy(entity.getGeneratedBy());
        snapshot.setGeneratedByName(entity.getGeneratedByName());
        snapshot.setGeneratedAt(entity.getGeneratedAt());
        return snapshot;
    }

    /**
     * 快照模型转实体。
     *
     * @param snapshot 快照模型
     * @return 实体
     */
    private static ReportSnapshotEntity toSnapshotEntity(ReportSnapshot snapshot) {
        ReportSnapshotEntity entity = new ReportSnapshotEntity();
        entity.setReportCode(snapshot.getReportCode());
        entity.setReportName(snapshot.getReportName());
        entity.setPeriod(snapshot.getPeriod());
        entity.setFromDate(snapshot.getFromDate());
        entity.setToDate(snapshot.getToDate());
        entity.setRowCount(snapshot.getRowCount());
        entity.setPayload(snapshot.getPayload());
        entity.setGeneratedBy(snapshot.getGeneratedBy());
        entity.setGeneratedByName(snapshot.getGeneratedByName());
        entity.setGeneratedAt(snapshot.getGeneratedAt());
        return entity;
    }
}
