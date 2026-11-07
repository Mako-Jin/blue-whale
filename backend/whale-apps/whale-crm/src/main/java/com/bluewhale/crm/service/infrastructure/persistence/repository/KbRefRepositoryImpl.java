package com.bluewhale.crm.service.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.service.domain.model.KbRef;
import com.bluewhale.crm.service.domain.model.KbRefQuery;
import com.bluewhale.crm.service.domain.repository.KbRefRepository;
import com.bluewhale.crm.service.infrastructure.persistence.entity.KbRefEntity;
import com.bluewhale.crm.service.infrastructure.persistence.mapper.KbRefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 知识引用仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class KbRefRepositoryImpl implements KbRefRepository {

    private final KbRefMapper kbRefMapper;

    @Override
    public List<KbRef> page(KbRefQuery query) {
        LambdaQueryWrapper<KbRefEntity> wrapper = wrapper(query)
                .orderByDesc(KbRefEntity::getUpdateTime)
                .orderByDesc(KbRefEntity::getId)
                .last("limit " + offset(query) + ", " + size(query));
        return kbRefMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public long count(KbRefQuery query) {
        return kbRefMapper.selectCount(wrapper(query));
    }

    @Override
    public KbRef findById(Long id) {
        KbRefEntity entity = kbRefMapper.selectById(id);
        return entity == null ? null : toDomain(entity);
    }

    @Override
    public void insert(KbRef ref) {
        KbRefEntity entity = toEntity(ref);
        kbRefMapper.insert(entity);
        ref.setId(entity.getId());
    }

    @Override
    public void update(KbRef ref) {
        kbRefMapper.updateById(toEntity(ref));
    }

    @Override
    public void deleteById(Long id) {
        kbRefMapper.deleteById(id);
    }

    @Override
    public void incrementHit(Long id) {
        kbRefMapper.update(null, new LambdaUpdateWrapper<KbRefEntity>()
                .eq(KbRefEntity::getId, id)
                .setSql("hit_count = hit_count + 1"));
    }

    /**
     * 构造查询条件。
     *
     * @param query 查询条件
     * @return 条件包装器
     */
    private LambdaQueryWrapper<KbRefEntity> wrapper(KbRefQuery query) {
        KbRefQuery safe = query == null ? new KbRefQuery() : query;
        return new LambdaQueryWrapper<KbRefEntity>()
                .eq(safe.getCategory() != null && !safe.getCategory().isBlank(),
                        KbRefEntity::getCategory, safe.getCategory())
                .eq(safe.getVisibleScope() != null && !safe.getVisibleScope().isBlank(),
                        KbRefEntity::getVisibleScope, safe.getVisibleScope())
                .eq(safe.getStatus() != null && !safe.getStatus().isBlank(),
                        KbRefEntity::getStatus, safe.getStatus())
                .and(safe.getKeyword() != null && !safe.getKeyword().isBlank(), wrapper -> wrapper
                        .like(KbRefEntity::getTitle, safe.getKeyword())
                        .or().like(KbRefEntity::getProductTags, safe.getKeyword())
                        .or().like(KbRefEntity::getFaultTags, safe.getKeyword()));
    }

    /**
     * 分页偏移。
     *
     * @param query 查询条件
     * @return 偏移
     */
    private long offset(KbRefQuery query) {
        int current = query.getCurrent() == null || query.getCurrent() < 1 ? 1 : query.getCurrent();
        return (long) (current - 1) * size(query);
    }

    /**
     * 每页条数。
     *
     * @param query 查询条件
     * @return 条数
     */
    private int size(KbRefQuery query) {
        int size = query.getSize() == null || query.getSize() < 1 ? 10 : query.getSize();
        return Math.min(size, 100);
    }

    /**
     * 实体转领域对象。
     *
     * @param entity 实体
     * @return 知识引用
     */
    private KbRef toDomain(KbRefEntity entity) {
        KbRef ref = new KbRef();
        ref.setId(entity.getId());
        ref.setTenantId(entity.getTenantId());
        ref.setTitle(entity.getTitle());
        ref.setCategory(entity.getCategory());
        ref.setProductTags(entity.getProductTags());
        ref.setFaultTags(entity.getFaultTags());
        ref.setVisibleScope(entity.getVisibleScope());
        ref.setDocId(entity.getDocId());
        ref.setDocVersion(entity.getDocVersion());
        ref.setDocAvailable(entity.getDocAvailable());
        ref.setHitCount(entity.getHitCount());
        ref.setStatus(entity.getStatus());
        ref.setOwnerId(entity.getOwnerId());
        ref.setDeptId(entity.getDeptId());
        return ref;
    }

    /**
     * 领域对象转实体。
     *
     * @param ref 知识引用
     * @return 实体
     */
    private KbRefEntity toEntity(KbRef ref) {
        KbRefEntity entity = new KbRefEntity();
        entity.setId(ref.getId());
        entity.setTenantId(ref.getTenantId());
        entity.setTitle(ref.getTitle());
        entity.setCategory(ref.getCategory());
        entity.setProductTags(ref.getProductTags());
        entity.setFaultTags(ref.getFaultTags());
        entity.setVisibleScope(ref.getVisibleScope());
        entity.setDocId(ref.getDocId());
        entity.setDocVersion(ref.getDocVersion());
        entity.setDocAvailable(ref.getDocAvailable());
        entity.setHitCount(ref.getHitCount());
        entity.setStatus(ref.getStatus());
        entity.setOwnerId(ref.getOwnerId());
        entity.setDeptId(ref.getDeptId());
        return entity;
    }
}
