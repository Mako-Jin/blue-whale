package com.bluewhale.erp.qc.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.qc.domain.model.Nonconformity;
import com.bluewhale.erp.qc.domain.model.QcQuery;
import com.bluewhale.erp.qc.domain.repository.NcrRepository;
import com.bluewhale.erp.qc.infrastructure.persistence.entity.NcrEntity;
import com.bluewhale.erp.qc.infrastructure.persistence.mapper.NcrMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 不合格品单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class NcrRepositoryImpl implements NcrRepository {

    /** 单号前缀 */
    private static final String DOC_PREFIX = "NCR-";

    /** 单号期间格式 */
    private static final DateTimeFormatter DOC_PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final NcrMapper ncrMapper;

    @Override
    public List<Nonconformity> page(QcQuery query) {
        Page<NcrEntity> page = ncrMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(QcQuery query) {
        return ncrMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Nonconformity> findById(Long id) {
        NcrEntity entity = ncrMapper.selectById(id);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public Optional<Nonconformity> findBySource(String sourceType, String sourceNo) {
        NcrEntity entity = ncrMapper.selectOne(new LambdaQueryWrapper<NcrEntity>()
                .eq(NcrEntity::getSourceType, sourceType)
                .eq(NcrEntity::getSourceNo, sourceNo)
                .last("limit 1"));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public String nextNcrNo(LocalDate date) {
        String period = date.format(DOC_PERIOD_FORMATTER);
        long sequence = ncrMapper.selectCount(new LambdaQueryWrapper<NcrEntity>()
                .likeRight(NcrEntity::getNcrNo, DOC_PREFIX + period)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, period, sequence);
    }

    @Override
    public void insert(Nonconformity nonconformity) {
        NcrEntity entity = new NcrEntity();
        fill(entity, nonconformity);
        ncrMapper.insert(entity);
        nonconformity.setId(entity.getId());
    }

    @Override
    public void update(Nonconformity nonconformity) {
        NcrEntity entity = new NcrEntity();
        entity.setId(nonconformity.getId());
        entity.setDisposition(nonconformity.getDisposition());
        entity.setStatus(nonconformity.getStatus());
        entity.setCostAmount(nonconformity.getCostAmount());
        entity.setDisposeDate(nonconformity.getDisposeDate());
        entity.setCloseDate(nonconformity.getCloseDate());
        entity.setRemark(nonconformity.getRemark());
        ncrMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<NcrEntity> filter(QcQuery query) {
        LambdaQueryWrapper<NcrEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(NcrEntity::getNcrNo, query.getKeyword())
                    .or().like(NcrEntity::getItemName, query.getKeyword())
                    .or().like(NcrEntity::getDefectDesc, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(NcrEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(NcrEntity::getSourceType, query.getSourceType());
        }
        if (StringUtils.hasText(query.getLotNo())) {
            wrapper.eq(NcrEntity::getLotNo, query.getLotNo());
        }
        if (query.getFromDate() != null) {
            wrapper.ge(NcrEntity::getDisposeDate, query.getFromDate());
        }
        if (query.getToDate() != null) {
            wrapper.le(NcrEntity::getDisposeDate, query.getToDate());
        }
        return wrapper.orderByDesc(NcrEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Nonconformity toDomain(NcrEntity entity) {
        Nonconformity nonconformity = new Nonconformity();
        nonconformity.setId(entity.getId());
        nonconformity.setNcrNo(entity.getNcrNo());
        nonconformity.setSourceType(entity.getSourceType());
        nonconformity.setSourceNo(entity.getSourceNo());
        nonconformity.setItemId(entity.getItemId());
        nonconformity.setItemName(entity.getItemName());
        nonconformity.setLotNo(entity.getLotNo());
        nonconformity.setQuantity(entity.getQuantity());
        nonconformity.setDefectDesc(entity.getDefectDesc());
        nonconformity.setDisposition(entity.getDisposition());
        nonconformity.setStatus(entity.getStatus());
        nonconformity.setCostAmount(entity.getCostAmount());
        nonconformity.setDisposeDate(entity.getDisposeDate());
        nonconformity.setCloseDate(entity.getCloseDate());
        nonconformity.setRemark(entity.getRemark());
        return nonconformity;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param nonconformity 不合格品单
     */
    private void fill(NcrEntity entity, Nonconformity nonconformity) {
        entity.setNcrNo(nonconformity.getNcrNo());
        entity.setSourceType(nonconformity.getSourceType());
        entity.setSourceNo(nonconformity.getSourceNo());
        entity.setItemId(nonconformity.getItemId());
        entity.setItemName(nonconformity.getItemName());
        entity.setLotNo(nonconformity.getLotNo());
        entity.setQuantity(nonconformity.getQuantity());
        entity.setDefectDesc(nonconformity.getDefectDesc());
        entity.setDisposition(nonconformity.getDisposition());
        entity.setStatus(nonconformity.getStatus());
        entity.setCostAmount(nonconformity.getCostAmount());
        entity.setDisposeDate(nonconformity.getDisposeDate());
        entity.setCloseDate(nonconformity.getCloseDate());
        entity.setRemark(nonconformity.getRemark());
    }
}
