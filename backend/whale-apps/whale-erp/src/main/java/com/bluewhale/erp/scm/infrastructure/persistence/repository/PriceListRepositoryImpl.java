package com.bluewhale.erp.scm.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.scm.domain.model.PriceList;
import com.bluewhale.erp.scm.domain.model.PriceListQuery;
import com.bluewhale.erp.scm.domain.model.PriceType;
import com.bluewhale.erp.scm.domain.repository.PriceListRepository;
import com.bluewhale.erp.scm.infrastructure.persistence.entity.PriceListEntity;
import com.bluewhale.erp.scm.infrastructure.persistence.mapper.PriceListMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 价目表仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PriceListRepositoryImpl implements PriceListRepository {

    private final PriceListMapper priceListMapper;

    @Override
    public List<PriceList> page(PriceListQuery query) {
        Page<PriceListEntity> page = priceListMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(PriceListQuery query) {
        return priceListMapper.selectCount(filter(query));
    }

    @Override
    public Optional<PriceList> findById(Long id) {
        return Optional.ofNullable(priceListMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<PriceList> list(PriceListQuery query) {
        return priceListMapper.selectList(filter(query)).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<PriceList> find(PriceType priceType, Long partnerId, Long itemId) {
        PriceListEntity entity = priceListMapper.selectOne(new LambdaQueryWrapper<PriceListEntity>()
                .eq(PriceListEntity::getPriceType, priceType.name())
                .eq(PriceListEntity::getPartnerId, partnerId)
                .eq(PriceListEntity::getItemId, itemId)
                .last("limit 1"));
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public void insert(PriceList priceList) {
        PriceListEntity entity = new PriceListEntity();
        fill(entity, priceList);
        priceListMapper.insert(entity);
        priceList.setId(entity.getId());
    }

    @Override
    public void update(PriceList priceList) {
        PriceListEntity entity = new PriceListEntity();
        entity.setId(priceList.getId());
        entity.setPrice(priceList.getPrice());
        entity.setCurrency(priceList.getCurrency());
        entity.setValidFrom(priceList.getValidFrom());
        entity.setValidTo(priceList.getValidTo());
        entity.setStatus(priceList.getStatus());
        entity.setRemark(priceList.getRemark());
        priceListMapper.updateById(entity);
    }

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<PriceListEntity> filter(PriceListQuery query) {
        LambdaQueryWrapper<PriceListEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getPriceType() != null) {
            wrapper.eq(PriceListEntity::getPriceType, query.getPriceType().name());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(PriceListEntity::getItemCode, query.getKeyword())
                    .or().like(PriceListEntity::getItemName, query.getKeyword())
                    .or().like(PriceListEntity::getPartnerName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PriceListEntity::getStatus, query.getStatus());
        }
        if (query.getPartnerId() != null) {
            wrapper.eq(PriceListEntity::getPartnerId, query.getPartnerId());
        }
        if (query.getItemId() != null) {
            wrapper.eq(PriceListEntity::getItemId, query.getItemId());
        }
        return wrapper.orderByDesc(PriceListEntity::getCreateTime)
                .orderByDesc(PriceListEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PriceList toDomain(PriceListEntity entity) {
        PriceList priceList = new PriceList();
        priceList.setId(entity.getId());
        priceList.setPriceType(entity.getPriceType() == null ? null : PriceType.valueOf(entity.getPriceType()));
        priceList.setPartnerId(entity.getPartnerId());
        priceList.setPartnerName(entity.getPartnerName());
        priceList.setItemId(entity.getItemId());
        priceList.setItemCode(entity.getItemCode());
        priceList.setItemName(entity.getItemName());
        priceList.setUom(entity.getUom());
        priceList.setPrice(entity.getPrice());
        priceList.setCurrency(entity.getCurrency());
        priceList.setValidFrom(entity.getValidFrom());
        priceList.setValidTo(entity.getValidTo());
        priceList.setStatus(entity.getStatus());
        priceList.setRemark(entity.getRemark());
        return priceList;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param priceList 领域模型
     */
    private void fill(PriceListEntity entity, PriceList priceList) {
        entity.setPriceType(priceList.getPriceType().name());
        entity.setPartnerId(priceList.getPartnerId());
        entity.setPartnerName(priceList.getPartnerName());
        entity.setItemId(priceList.getItemId());
        entity.setItemCode(priceList.getItemCode());
        entity.setItemName(priceList.getItemName());
        entity.setUom(priceList.getUom());
        entity.setPrice(priceList.getPrice());
        entity.setCurrency(priceList.getCurrency());
        entity.setValidFrom(priceList.getValidFrom());
        entity.setValidTo(priceList.getValidTo());
        entity.setStatus(priceList.getStatus());
        entity.setRemark(priceList.getRemark());
    }
}
