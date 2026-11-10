package com.bluewhale.erp.mfg.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.mfg.domain.model.Forecast;
import com.bluewhale.erp.mfg.domain.model.ForecastLine;
import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.repository.ForecastRepository;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.ForecastEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.ForecastLineEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.ForecastLineMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.ForecastMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 需求预测仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ForecastRepositoryImpl implements ForecastRepository {

    /** 批次号前缀 */
    private static final String DOC_PREFIX = "FC-";

    private final ForecastMapper forecastMapper;

    private final ForecastLineMapper forecastLineMapper;

    @Override
    public List<Forecast> page(MfgQuery query) {
        Page<ForecastEntity> page = forecastMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MfgQuery query) {
        return forecastMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Forecast> findById(Long id) {
        ForecastEntity entity = forecastMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        Forecast forecast = toDomain(entity);
        forecast.setLines(forecastLineMapper.selectList(new LambdaQueryWrapper<ForecastLineEntity>()
                        .eq(ForecastLineEntity::getForecastId, id)
                        .orderByAsc(ForecastLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(forecast);
    }

    @Override
    public String nextBatchNo(String period) {
        long sequence = forecastMapper.selectCount(new LambdaQueryWrapper<ForecastEntity>()
                .eq(ForecastEntity::getPeriod, period)) + 1;
        return String.format("%s%s-%04d", DOC_PREFIX, period.replace("-", ""), sequence);
    }

    @Override
    public void insert(Forecast forecast) {
        ForecastEntity entity = new ForecastEntity();
        fill(entity, forecast);
        forecastMapper.insert(entity);
        forecast.setId(entity.getId());
        for (ForecastLine line : forecast.getLines()) {
            ForecastLineEntity lineEntity = new ForecastLineEntity();
            lineEntity.setForecastId(entity.getId());
            fillLine(lineEntity, line);
            forecastLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void update(Forecast forecast) {
        ForecastEntity entity = new ForecastEntity();
        entity.setId(forecast.getId());
        entity.setStatus(forecast.getStatus());
        entity.setTotalQuantity(forecast.getTotalQuantity());
        forecastMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<ForecastEntity> filter(MfgQuery query) {
        LambdaQueryWrapper<ForecastEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(ForecastEntity::getBatchNo, query.getKeyword())
                    .or().like(ForecastEntity::getPeriod, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ForecastEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPeriod())) {
            wrapper.eq(ForecastEntity::getPeriod, query.getPeriod());
        }
        return wrapper.orderByDesc(ForecastEntity::getPeriod).orderByDesc(ForecastEntity::getBatchNo);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Forecast toDomain(ForecastEntity entity) {
        Forecast forecast = new Forecast();
        forecast.setId(entity.getId());
        forecast.setBatchNo(entity.getBatchNo());
        forecast.setPeriod(entity.getPeriod());
        forecast.setVersion(entity.getVersion());
        forecast.setStatus(entity.getStatus());
        forecast.setTotalQuantity(entity.getTotalQuantity());
        forecast.setRemark(entity.getRemark());
        return forecast;
    }

    /**
     * 分录实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ForecastLine toDomain(ForecastLineEntity entity) {
        ForecastLine line = new ForecastLine();
        line.setLineNo(entity.getLineNo());
        line.setItemId(entity.getItemId());
        line.setItemCode(entity.getItemCode());
        line.setItemName(entity.getItemName());
        line.setUom(entity.getUom());
        line.setQuantity(entity.getQuantity());
        return line;
    }

    /**
     * 领域模型写入单头实体。
     *
     * @param entity 实体
     * @param forecast 领域模型
     */
    private void fill(ForecastEntity entity, Forecast forecast) {
        entity.setBatchNo(forecast.getBatchNo());
        entity.setPeriod(forecast.getPeriod());
        entity.setVersion(forecast.getVersion());
        entity.setStatus(forecast.getStatus());
        entity.setTotalQuantity(forecast.getTotalQuantity());
        entity.setRemark(forecast.getRemark());
    }

    /**
     * 领域模型写入分录实体。
     *
     * @param entity 实体
     * @param line 领域模型
     */
    private void fillLine(ForecastLineEntity entity, ForecastLine line) {
        entity.setLineNo(line.getLineNo());
        entity.setItemId(line.getItemId());
        entity.setItemCode(line.getItemCode());
        entity.setItemName(line.getItemName());
        entity.setUom(line.getUom());
        entity.setQuantity(line.getQuantity());
    }
}
