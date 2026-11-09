package com.bluewhale.mall.catalog.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.mall.catalog.domain.model.PriceType;
import com.bluewhale.mall.catalog.domain.model.Sku;
import com.bluewhale.mall.catalog.domain.model.SkuPrice;
import com.bluewhale.mall.catalog.domain.model.Spu;
import com.bluewhale.mall.catalog.domain.model.SpuQuery;
import com.bluewhale.mall.catalog.domain.model.SpuStatus;
import com.bluewhale.mall.catalog.domain.repository.SkuStockRepository;
import com.bluewhale.mall.catalog.domain.repository.SpuRepository;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SkuEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SkuPriceEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.entity.SpuEntity;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.SkuMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.SkuPriceMapper;
import com.bluewhale.mall.catalog.infrastructure.persistence.mapper.SpuMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 商品仓储实现（MyBatis-Plus，SPU 聚合 = 商品 + 规格 + 价目）。
 *
 * <p>保存采用「全量覆盖」：提交的 SKU 逐个 upsert，未出现在提交列表里的旧 SKU
 * 逻辑删除并改写编码释放唯一键，价目每次物理删除后重插。
 * 覆盖式保存的代价是「少传一个 SKU 就等于删掉它」，因此 DTO 侧必须始终回传完整列表
 * ——这也是编辑页必须加载完整详情再提交的原因。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SpuRepositoryImpl implements SpuRepository {

    /** 空数组 JSON（轮播图为空时写入） */
    private static final String EMPTY_JSON_ARRAY = "[]";

    /** 逻辑删除后的编码后缀分隔符 */
    private static final String DELETED_CODE_MARK = "#D";

    private final SpuMapper spuMapper;

    private final SkuMapper skuMapper;

    private final SkuPriceMapper skuPriceMapper;

    private final SkuStockRepository skuStockRepository;

    private final ObjectMapper objectMapper;

    @Override
    public List<Spu> page(SpuQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<SpuEntity> page = spuMapper.selectPage(new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomainShallow).toList();
    }

    @Override
    public long count(SpuQuery query) {
        return spuMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Spu> findById(Long id) {
        SpuEntity entity = spuMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(assemble(List.of(entity)).get(0));
    }

    @Override
    public List<Spu> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<SpuEntity> entities = spuMapper.selectList(new LambdaQueryWrapper<SpuEntity>()
                .in(SpuEntity::getId, ids));
        return assemble(entities);
    }

    @Override
    public List<Spu> findBySkuIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        List<SkuEntity> skus = skuMapper.selectList(new LambdaQueryWrapper<SkuEntity>()
                .in(SkuEntity::getId, skuIds));
        Set<Long> spuIds = new LinkedHashSet<>();
        for (SkuEntity sku : skus) {
            spuIds.add(sku.getSpuId());
        }
        return findByIds(spuIds);
    }

    @Override
    public List<Long> findSkuIdsByCode(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        List<SkuEntity> skus = skuMapper.selectList(new LambdaQueryWrapper<SkuEntity>()
                .like(SkuEntity::getSkuCode, keyword.trim().toUpperCase(Locale.ROOT))
                .orderByAsc(SkuEntity::getId)
                .last("LIMIT " + Math.max(limit, 1)));
        return skus.stream().map(SkuEntity::getId).toList();
    }

    @Override
    public List<Sku> findSkusByIds(Collection<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return skuMapper.selectList(new LambdaQueryWrapper<SkuEntity>()
                        .in(SkuEntity::getId, skuIds))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsCode(String spuCode, Long excludeId) {
        if (!StringUtils.hasText(spuCode)) {
            return false;
        }
        return spuMapper.selectCount(new LambdaQueryWrapper<SpuEntity>()
                .eq(SpuEntity::getSpuCode, spuCode.trim().toUpperCase(Locale.ROOT))
                .ne(excludeId != null, SpuEntity::getId, excludeId)) > 0;
    }

    @Override
    public List<String> findConflictingSkuCodes(Collection<String> skuCodes, Long excludeSpuId) {
        if (skuCodes == null || skuCodes.isEmpty()) {
            return List.of();
        }
        List<SkuEntity> conflicts = skuMapper.selectList(new LambdaQueryWrapper<SkuEntity>()
                .in(SkuEntity::getSkuCode, skuCodes)
                .ne(excludeSpuId != null, SkuEntity::getSpuId, excludeSpuId));
        return conflicts.stream().map(SkuEntity::getSkuCode).toList();
    }

    @Override
    public Long save(Spu spu) {
        SpuEntity entity = toEntity(spu);
        if (entity.getId() == null) {
            spuMapper.insert(entity);
        } else {
            spuMapper.updateById(entity);
        }
        spu.setId(entity.getId());
        List<Long> keptSkuIds = saveSkus(spu);
        removeStaleSkus(entity.getId(), keptSkuIds);
        return entity.getId();
    }

    @Override
    public void updateStatus(Long id, String status) {
        SpuEntity entity = new SpuEntity();
        entity.setId(id);
        entity.setStatus(status);
        spuMapper.updateById(entity);
    }

    @Override
    public void remove(Long id) {
        SpuEntity entity = spuMapper.selectById(id);
        if (entity == null) {
            return;
        }
        List<SkuEntity> skus = skuMapper.selectList(new LambdaQueryWrapper<SkuEntity>()
                .eq(SkuEntity::getSpuId, id));
        for (SkuEntity sku : skus) {
            skuPriceMapper.physicalDeleteBySkuId(sku.getId());
            skuMapper.softDeleteWithCode(sku.getId(), deletedCode(sku.getSkuCode(), sku.getId()));
        }
        spuMapper.softDeleteWithCode(id, deletedCode(entity.getSpuCode(), id));
    }

    @Override
    public long countByCategoryIds(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return 0L;
        }
        return spuMapper.selectCount(new LambdaQueryWrapper<SpuEntity>()
                .in(SpuEntity::getCategoryId, categoryIds));
    }

    @Override
    public long countAll() {
        return spuMapper.selectCount(new LambdaQueryWrapper<>());
    }

    /**
     * 保存 SKU 与价目，返回本次保留的 SKU ID。
     *
     * @param spu 商品聚合
     * @return 保留的 SKU ID 列表
     */
    private List<Long> saveSkus(Spu spu) {
        List<Long> kept = new ArrayList<>(spu.getSkus().size());
        for (Sku sku : spu.getSkus()) {
            SkuEntity entity = toSkuEntity(sku, spu.getId());
            if (entity.getId() == null) {
                skuMapper.insert(entity);
                sku.setId(entity.getId());
            } else {
                skuMapper.updateById(entity);
            }
            kept.add(entity.getId());
            skuPriceMapper.physicalDeleteBySkuId(entity.getId());
            for (SkuPrice price : sku.getPrices()) {
                skuPriceMapper.insert(toPriceEntity(entity.getId(), price));
            }
            skuStockRepository.init(entity.getId());
        }
        return kept;
    }

    /**
     * 删除本次未提交的旧 SKU（逻辑删除 + 编码改写）。
     *
     * @param spuId 商品 ID
     * @param keptSkuIds 本次保留的 SKU ID
     */
    private void removeStaleSkus(Long spuId, List<Long> keptSkuIds) {
        List<SkuEntity> existing = skuMapper.selectList(new LambdaQueryWrapper<SkuEntity>()
                .eq(SkuEntity::getSpuId, spuId));
        for (SkuEntity sku : existing) {
            if (keptSkuIds.contains(sku.getId())) {
                continue;
            }
            skuPriceMapper.physicalDeleteBySkuId(sku.getId());
            skuMapper.softDeleteWithCode(sku.getId(), deletedCode(sku.getSkuCode(), sku.getId()));
        }
    }

    /**
     * 组装聚合：商品 → SKU → 价目。
     *
     * @param entities 商品实体列表
     * @return 商品聚合列表
     */
    private List<Spu> assemble(List<SpuEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        Set<Long> spuIds = new LinkedHashSet<>();
        List<Spu> spus = new ArrayList<>(entities.size());
        for (SpuEntity entity : entities) {
            spuIds.add(entity.getId());
            spus.add(toDomainShallow(entity));
        }
        Map<Long, Spu> byId = new HashMap<>();
        for (Spu spu : spus) {
            byId.put(spu.getId(), spu);
        }
        List<SkuEntity> skus = skuMapper.selectList(new LambdaQueryWrapper<SkuEntity>()
                .in(SkuEntity::getSpuId, spuIds)
                .orderByAsc(SkuEntity::getSortNo)
                .orderByAsc(SkuEntity::getId));
        Map<Long, List<SkuPrice>> pricesBySku = loadPrices(skus);
        for (SkuEntity skuEntity : skus) {
            Spu spu = byId.get(skuEntity.getSpuId());
            if (spu == null) {
                continue;
            }
            Sku sku = toDomain(skuEntity);
            sku.setPrices(pricesBySku.getOrDefault(skuEntity.getId(), new ArrayList<>()));
            spu.getSkus().add(sku);
        }
        return spus;
    }

    /**
     * 批量加载价目并按 SKU 分组。
     *
     * @param skus SKU 实体列表
     * @return SKU ID → 价目列表
     */
    private Map<Long, List<SkuPrice>> loadPrices(List<SkuEntity> skus) {
        Map<Long, List<SkuPrice>> result = new HashMap<>();
        if (skus.isEmpty()) {
            return result;
        }
        List<Long> skuIds = skus.stream().map(SkuEntity::getId).toList();
        List<SkuPriceEntity> prices = skuPriceMapper.selectList(new LambdaQueryWrapper<SkuPriceEntity>()
                .in(SkuPriceEntity::getSkuId, skuIds));
        for (SkuPriceEntity entity : prices) {
            SkuPrice price = new SkuPrice();
            price.setPriceType(PriceType.parse(entity.getPriceType()));
            price.setPrice(entity.getPrice());
            result.computeIfAbsent(entity.getSkuId(), key -> new ArrayList<>()).add(price);
        }
        return result;
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<SpuEntity> filter(SpuQuery query) {
        LambdaQueryWrapper<SpuEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(SpuEntity::getId);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(SpuEntity::getSpuCode, keyword)
                    .or().like(SpuEntity::getSpuName, keyword));
        }
        if (query.getCategoryId() != null) {
            wrapper.eq(SpuEntity::getCategoryId, query.getCategoryId());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(SpuEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByAsc(SpuEntity::getSortNo).orderByDesc(SpuEntity::getId);
    }

    /**
     * 构造逻辑删除后的编码（加 ID 后缀保证唯一）。
     *
     * @param code 原编码
     * @param id 主键
     * @return 改写后的编码
     */
    private String deletedCode(String code, Long id) {
        String base = code == null ? "DELETED" : code;
        return base + DELETED_CODE_MARK + id;
    }

    /**
     * 实体转商品聚合（不含 SKU）。
     *
     * @param entity 实体
     * @return 商品聚合
     */
    private Spu toDomainShallow(SpuEntity entity) {
        Spu spu = new Spu();
        spu.setId(entity.getId());
        spu.setSpuCode(entity.getSpuCode());
        spu.setSpuName(entity.getSpuName());
        spu.setCategoryId(entity.getCategoryId());
        spu.setBrand(entity.getBrand());
        spu.setMainImage(entity.getMainImage());
        spu.setImages(parseImages(entity.getImages()));
        spu.setDetail(entity.getDetail());
        spu.setStatus(SpuStatus.parse(entity.getStatus()));
        spu.setSortNo(entity.getSortNo());
        spu.setRemark(entity.getRemark());
        spu.setCreateTime(entity.getCreateTime());
        spu.setUpdateTime(entity.getUpdateTime());
        return spu;
    }

    /**
     * 实体转 SKU。
     *
     * @param entity 实体
     * @return SKU
     */
    private Sku toDomain(SkuEntity entity) {
        Sku sku = new Sku();
        sku.setId(entity.getId());
        sku.setSpuId(entity.getSpuId());
        sku.setSkuCode(entity.getSkuCode());
        sku.setSpecJson(entity.getSpecJson());
        sku.setImageUrl(entity.getImageUrl());
        sku.setWeightKg(entity.getWeightKg());
        sku.setStatus(entity.getStatus());
        sku.setSortNo(entity.getSortNo());
        sku.setCreateTime(entity.getCreateTime());
        return sku;
    }

    /**
     * 商品聚合转实体。
     *
     * @param spu 聚合
     * @return 实体
     */
    private SpuEntity toEntity(Spu spu) {
        SpuEntity entity = new SpuEntity();
        entity.setId(spu.getId());
        entity.setSpuCode(spu.getSpuCode());
        entity.setSpuName(spu.getSpuName());
        entity.setCategoryId(spu.getCategoryId());
        entity.setBrand(spu.getBrand());
        entity.setMainImage(spu.getMainImage());
        entity.setImages(writeImages(spu.getImages()));
        entity.setDetail(spu.getDetail());
        entity.setStatus(spu.getStatus() == null ? SpuStatus.DRAFT.name() : spu.getStatus().name());
        entity.setSortNo(spu.getSortNo() == null ? 0 : spu.getSortNo());
        entity.setRemark(spu.getRemark());
        return entity;
    }

    /**
     * SKU 转实体。
     *
     * @param sku SKU
     * @param spuId 商品 ID
     * @return 实体
     */
    private SkuEntity toSkuEntity(Sku sku, Long spuId) {
        SkuEntity entity = new SkuEntity();
        entity.setId(sku.getId());
        entity.setSpuId(spuId);
        entity.setSkuCode(sku.getSkuCode());
        entity.setSpecJson(sku.getSpecJson());
        entity.setImageUrl(sku.getImageUrl());
        entity.setWeightKg(sku.getWeightKg());
        entity.setStatus(sku.isEnabled() ? "ENABLED" : "DISABLED");
        entity.setSortNo(sku.getSortNo() == null ? 0 : sku.getSortNo());
        return entity;
    }

    /**
     * 价目转实体。
     *
     * @param skuId SKU ID
     * @param price 价目
     * @return 实体
     */
    private SkuPriceEntity toPriceEntity(Long skuId, SkuPrice price) {
        SkuPriceEntity entity = new SkuPriceEntity();
        entity.setSkuId(skuId);
        entity.setPriceType(price.getPriceType().name());
        entity.setPrice(price.getPrice());
        return entity;
    }

    /**
     * 解析轮播图 JSON（损坏时按空处理，不阻断商品读取）。
     *
     * @param json JSON 文本
     * @return 图片列表
     */
    private List<String> parseImages(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            return new ArrayList<>();
        }
    }

    /**
     * 序列化轮播图。
     *
     * @param images 图片列表
     * @return JSON 文本
     */
    private String writeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return EMPTY_JSON_ARRAY;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception exception) {
            return EMPTY_JSON_ARRAY;
        }
    }
}
