package com.bluewhale.party.union.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionWelfare;
import com.bluewhale.party.union.domain.model.UnionWelfareItem;
import com.bluewhale.party.union.domain.model.UnionWelfareQuery;
import com.bluewhale.party.union.domain.model.WelfareSignStatus;
import com.bluewhale.party.union.domain.model.WelfareStatus;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import com.bluewhale.party.union.domain.repository.UnionWelfareRepository;
import com.bluewhale.party.union.infrastructure.persistence.entity.UnionWelfareEntity;
import com.bluewhale.party.union.infrastructure.persistence.entity.UnionWelfareItemEntity;
import com.bluewhale.party.union.infrastructure.persistence.mapper.UnionWelfareItemMapper;
import com.bluewhale.party.union.infrastructure.persistence.mapper.UnionWelfareMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 节日福利仓储实现（MyBatis-Plus）。
 *
 * <p>「我的福利」先按 user_id 找到在册会员，再按 union_member_id 查明细：
 * 用户重新入会后会员 ID 会变，直接用 user_id 存明细会看到旧会籍的发放记录。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UnionWelfareRepositoryImpl implements UnionWelfareRepository {

    private final UnionWelfareMapper unionWelfareMapper;

    private final UnionWelfareItemMapper unionWelfareItemMapper;

    private final UnionMemberRepository unionMemberRepository;

    @Override
    public List<UnionWelfare> page(UnionWelfareQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionWelfareEntity> page = unionWelfareMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(UnionWelfareQuery query) {
        return unionWelfareMapper.selectCount(filter(query));
    }

    @Override
    public Optional<UnionWelfare> findById(Long id) {
        return Optional.ofNullable(unionWelfareMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<UnionWelfare> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return unionWelfareMapper.selectList(new LambdaQueryWrapper<UnionWelfareEntity>()
                        .in(UnionWelfareEntity::getId, ids))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Long save(UnionWelfare welfare) {
        UnionWelfareEntity entity = toEntity(welfare);
        if (entity.getId() == null) {
            unionWelfareMapper.insert(entity);
        } else {
            unionWelfareMapper.updateById(entity);
        }
        welfare.setId(entity.getId());
        welfare.setCreateTime(entity.getCreateTime());
        welfare.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public List<UnionWelfareItem> pageItems(Long welfareId, String signStatus, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionWelfareItemEntity> page = unionWelfareItemMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), itemFilter(welfareId, signStatus));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countItems(Long welfareId, String signStatus) {
        return unionWelfareItemMapper.selectCount(itemFilter(welfareId, signStatus));
    }

    @Override
    public List<UnionWelfareItem> listItems(Long welfareId) {
        if (welfareId == null) {
            return List.of();
        }
        return unionWelfareItemMapper.selectList(new LambdaQueryWrapper<UnionWelfareItemEntity>()
                        .eq(UnionWelfareItemEntity::getWelfareId, welfareId)
                        .orderByAsc(UnionWelfareItemEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<UnionWelfareItem> findItemById(Long itemId) {
        return Optional.ofNullable(unionWelfareItemMapper.selectById(itemId)).map(this::toDomain);
    }

    @Override
    public Long saveItem(UnionWelfareItem item) {
        UnionWelfareItemEntity entity = toEntity(item);
        if (entity.getId() == null) {
            unionWelfareItemMapper.insert(entity);
        } else {
            unionWelfareItemMapper.updateById(entity);
        }
        item.setId(entity.getId());
        item.setCreateTime(entity.getCreateTime());
        item.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public long countSigned(Long welfareId) {
        if (welfareId == null) {
            return 0L;
        }
        return unionWelfareItemMapper.selectCount(new LambdaQueryWrapper<UnionWelfareItemEntity>()
                .eq(UnionWelfareItemEntity::getWelfareId, welfareId)
                .eq(UnionWelfareItemEntity::getSignStatus, WelfareSignStatus.SIGNED.name()));
    }

    @Override
    public List<UnionWelfareItem> pageMyItems(Long userId, long offset, int size) {
        Long memberId = activeMemberId(userId);
        if (memberId == null) {
            return List.of();
        }
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionWelfareItemEntity> page = unionWelfareItemMapper.selectPage(
                new Page<>(current, Math.max(size, 1)),
                new LambdaQueryWrapper<UnionWelfareItemEntity>()
                        .eq(UnionWelfareItemEntity::getUnionMemberId, memberId)
                        .orderByDesc(UnionWelfareItemEntity::getId));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countMyItems(Long userId) {
        Long memberId = activeMemberId(userId);
        if (memberId == null) {
            return 0L;
        }
        return unionWelfareItemMapper.selectCount(new LambdaQueryWrapper<UnionWelfareItemEntity>()
                .eq(UnionWelfareItemEntity::getUnionMemberId, memberId));
    }

    /**
     * 用户 ID → 在册会员 ID。
     *
     * @param userId 用户 ID
     * @return 会员 ID（无在册会籍为 null）
     */
    private Long activeMemberId(Long userId) {
        if (userId == null) {
            return null;
        }
        return unionMemberRepository.findActiveByUserId(userId).map(UnionMember::getId).orElse(null);
    }

    /**
     * 组装批次查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<UnionWelfareEntity> filter(UnionWelfareQuery query) {
        LambdaQueryWrapper<UnionWelfareEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(UnionWelfareEntity::getId);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(UnionWelfareEntity::getWelfareNo, keyword)
                    .or().like(UnionWelfareEntity::getTitle, keyword));
        }
        if (query.getYear() != null) {
            wrapper.eq(UnionWelfareEntity::getWelfareYear, query.getYear());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(UnionWelfareEntity::getStatus,
                    query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByDesc(UnionWelfareEntity::getId);
    }

    /**
     * 组装明细查询条件。
     *
     * @param welfareId 批次 ID
     * @param signStatus 签收状态文本（可空）
     * @return 查询包装器
     */
    private LambdaQueryWrapper<UnionWelfareItemEntity> itemFilter(Long welfareId, String signStatus) {
        LambdaQueryWrapper<UnionWelfareItemEntity> wrapper = new LambdaQueryWrapper<>();
        if (welfareId != null) {
            wrapper.eq(UnionWelfareItemEntity::getWelfareId, welfareId);
        }
        if (StringUtils.hasText(signStatus)) {
            wrapper.eq(UnionWelfareItemEntity::getSignStatus,
                    signStatus.trim().toUpperCase(Locale.ROOT));
        }
        return wrapper.orderByAsc(UnionWelfareItemEntity::getId);
    }

    /**
     * 批次实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private UnionWelfare toDomain(UnionWelfareEntity entity) {
        UnionWelfare welfare = new UnionWelfare();
        welfare.setId(entity.getId());
        welfare.setWelfareNo(entity.getWelfareNo());
        welfare.setTitle(entity.getTitle());
        welfare.setWelfareYear(entity.getWelfareYear());
        welfare.setStandardAmount(entity.getStandardAmount());
        welfare.setStatus(WelfareStatus.parse(entity.getStatus()));
        welfare.setIssuedAt(entity.getIssuedAt());
        welfare.setClosedAt(entity.getClosedAt());
        welfare.setRemark(entity.getRemark());
        welfare.setCreateTime(entity.getCreateTime());
        welfare.setUpdateTime(entity.getUpdateTime());
        return welfare;
    }

    /**
     * 批次领域模型转实体。
     *
     * @param welfare 领域模型
     * @return 实体
     */
    private UnionWelfareEntity toEntity(UnionWelfare welfare) {
        UnionWelfareEntity entity = new UnionWelfareEntity();
        entity.setId(welfare.getId());
        entity.setWelfareNo(welfare.getWelfareNo());
        entity.setTitle(welfare.getTitle());
        entity.setWelfareYear(welfare.getWelfareYear());
        entity.setStandardAmount(welfare.getStandardAmount());
        entity.setStatus(welfare.getStatus() == null
                ? WelfareStatus.DRAFT.name() : welfare.getStatus().name());
        entity.setIssuedAt(welfare.getIssuedAt());
        entity.setClosedAt(welfare.getClosedAt());
        entity.setRemark(welfare.getRemark());
        return entity;
    }

    /**
     * 明细实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private UnionWelfareItem toDomain(UnionWelfareItemEntity entity) {
        UnionWelfareItem item = new UnionWelfareItem();
        item.setId(entity.getId());
        item.setWelfareId(entity.getWelfareId());
        item.setUnionMemberId(entity.getUnionMemberId());
        item.setEmployeeNo(entity.getEmployeeNo());
        item.setMemberName(entity.getMemberName());
        item.setAmount(entity.getAmount());
        item.setSignStatus(WelfareSignStatus.parse(entity.getSignStatus()));
        item.setSignedAt(entity.getSignedAt());
        item.setSignedBy(entity.getSignedBy());
        item.setCreateTime(entity.getCreateTime());
        item.setUpdateTime(entity.getUpdateTime());
        return item;
    }

    /**
     * 明细领域模型转实体。
     *
     * @param item 领域模型
     * @return 实体
     */
    private UnionWelfareItemEntity toEntity(UnionWelfareItem item) {
        UnionWelfareItemEntity entity = new UnionWelfareItemEntity();
        entity.setId(item.getId());
        entity.setWelfareId(item.getWelfareId());
        entity.setUnionMemberId(item.getUnionMemberId());
        entity.setEmployeeNo(item.getEmployeeNo());
        entity.setMemberName(item.getMemberName());
        entity.setAmount(item.getAmount());
        entity.setSignStatus(item.getSignStatus() == null
                ? WelfareSignStatus.UNSIGNED.name() : item.getSignStatus().name());
        entity.setSignedAt(item.getSignedAt());
        entity.setSignedBy(item.getSignedBy());
        return entity;
    }
}
