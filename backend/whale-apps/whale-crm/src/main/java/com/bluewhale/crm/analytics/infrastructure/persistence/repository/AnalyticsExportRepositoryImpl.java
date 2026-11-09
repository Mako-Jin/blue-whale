package com.bluewhale.crm.analytics.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.crm.analytics.domain.model.AnalyticsExport;
import com.bluewhale.crm.analytics.domain.model.ExportStatus;
import com.bluewhale.crm.analytics.domain.model.ExportType;
import com.bluewhale.crm.analytics.domain.repository.AnalyticsExportRepository;
import com.bluewhale.crm.analytics.infrastructure.persistence.entity.AnalyticsExportEntity;
import com.bluewhale.crm.analytics.infrastructure.persistence.mapper.AnalyticsExportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 分析导出任务仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AnalyticsExportRepositoryImpl implements AnalyticsExportRepository {

    private final AnalyticsExportMapper analyticsExportMapper;

    /**
     * 新增导出任务。
     *
     * @param export 导出任务
     */
    @Override
    public void insert(AnalyticsExport export) {
        AnalyticsExportEntity entity = toEntity(export);
        analyticsExportMapper.insert(entity);
        export.setId(entity.getId());
    }

    /**
     * 更新导出任务。
     *
     * @param export 导出任务
     */
    @Override
    public void update(AnalyticsExport export) {
        analyticsExportMapper.updateById(toEntity(export));
    }

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 导出任务
     */
    @Override
    public AnalyticsExport findById(Long id) {
        AnalyticsExportEntity entity = analyticsExportMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    /**
     * 按单号前缀统计数量。
     *
     * @param prefix 前缀
     * @return 数量
     */
    @Override
    public long countByPrefix(String prefix) {
        return analyticsExportMapper.selectCount(new LambdaQueryWrapper<AnalyticsExportEntity>()
                .likeRight(AnalyticsExportEntity::getExportNo, prefix));
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 导出任务
     */
    private AnalyticsExport toDomain(AnalyticsExportEntity entity) {
        AnalyticsExport export = new AnalyticsExport();
        export.setId(entity.getId());
        export.setTenantId(entity.getTenantId());
        export.setExportNo(entity.getExportNo());
        export.setExportType(ExportType.from(entity.getExportType()));
        export.setFiltersJson(entity.getFiltersJson());
        export.setStatus(ExportStatus.from(entity.getStatus()));
        export.setRowCount(entity.getRowCount());
        export.setFileId(entity.getFileId());
        export.setFileName(entity.getFileName());
        export.setError(entity.getError());
        export.setRequestedBy(entity.getRequestedBy());
        export.setCreateTime(entity.getCreateTime());
        export.setUpdateTime(entity.getUpdateTime());
        return export;
    }

    /**
     * 领域对象转实体。
     *
     * @param export 导出任务
     * @return 实体
     */
    private AnalyticsExportEntity toEntity(AnalyticsExport export) {
        AnalyticsExportEntity entity = new AnalyticsExportEntity();
        entity.setId(export.getId());
        entity.setTenantId(export.getTenantId());
        entity.setExportNo(export.getExportNo());
        entity.setExportType(export.getExportType() == null ? null : export.getExportType().name());
        entity.setFiltersJson(export.getFiltersJson());
        entity.setStatus(export.getStatus() == null ? null : export.getStatus().name());
        entity.setRowCount(export.getRowCount());
        entity.setFileId(export.getFileId());
        entity.setFileName(export.getFileName());
        entity.setError(export.getError());
        entity.setRequestedBy(export.getRequestedBy());
        return entity;
    }
}
