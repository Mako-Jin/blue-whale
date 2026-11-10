package com.bluewhale.party.union.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.union.domain.model.ReliefStatus;
import com.bluewhale.party.union.domain.model.ReliefType;
import com.bluewhale.party.union.domain.model.UnionRelief;
import com.bluewhale.party.union.domain.model.UnionReliefQuery;
import com.bluewhale.party.union.domain.repository.UnionReliefRepository;
import com.bluewhale.party.union.infrastructure.persistence.entity.UnionReliefEntity;
import com.bluewhale.party.union.infrastructure.persistence.mapper.UnionReliefMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 慰问帮扶仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UnionReliefRepositoryImpl implements UnionReliefRepository {

    /** 金额汇总别名（与 SQL 中的别名保持一致，便于取列值） */
    private static final String SUM_ALIAS = "total_amount";

    private final UnionReliefMapper unionReliefMapper;

    @Override
    public List<UnionRelief> page(UnionReliefQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionReliefEntity> page = unionReliefMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(UnionReliefQuery query) {
        return unionReliefMapper.selectCount(filter(query));
    }

    @Override
    public Optional<UnionRelief> findById(Long id) {
        return Optional.ofNullable(unionReliefMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Long save(UnionRelief relief) {
        UnionReliefEntity entity = toEntity(relief);
        if (entity.getId() == null) {
            unionReliefMapper.insert(entity);
        } else {
            unionReliefMapper.updateById(entity);
        }
        relief.setId(entity.getId());
        relief.setCreateTime(entity.getCreateTime());
        relief.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public long countApproved(Long unionMemberId, ReliefType type, Integer year) {
        if (unionMemberId == null || type == null || year == null) {
            return 0L;
        }
        return unionReliefMapper.selectCount(approvedWrapper(unionMemberId, type, year));
    }

    @Override
    public BigDecimal sumApprovedAmount(Long unionMemberId, ReliefType type, Integer year) {
        if (unionMemberId == null || type == null || year == null) {
            return BigDecimal.ZERO;
        }
        // 用 SQL 聚合而不是把明细全部捞回内存：上限校验每次提交都会跑，明细多时内存版会拖慢提交
        List<Map<String, Object>> rows = unionReliefMapper.selectMaps(
                new QueryWrapper<UnionReliefEntity>()
                        .select("COALESCE(SUM(amount), 0) AS " + SUM_ALIAS)
                        .eq("union_member_id", unionMemberId)
                        .eq("relief_type", type.name())
                        .eq("apply_year", year)
                        .in("status", ReliefStatus.APPROVED.name(), ReliefStatus.PAID.name()));
        if (rows.isEmpty() || rows.get(0) == null) {
            return BigDecimal.ZERO;
        }
        return amountOf(rows.get(0));
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<UnionReliefEntity> filter(UnionReliefQuery query) {
        LambdaQueryWrapper<UnionReliefEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(UnionReliefEntity::getId);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(UnionReliefEntity::getReliefNo, keyword)
                    .or().like(UnionReliefEntity::getReason, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(UnionReliefEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getReliefType())) {
            wrapper.eq(UnionReliefEntity::getReliefType,
                    query.getReliefType().trim().toUpperCase(Locale.ROOT));
        }
        if (query.getYear() != null) {
            wrapper.eq(UnionReliefEntity::getApplyYear, query.getYear());
        }
        return wrapper.orderByDesc(UnionReliefEntity::getId);
    }

    /**
     * 组装「已批」统计条件。
     *
     * @param unionMemberId 工会会员 ID
     * @param type 慰问类型
     * @param year 申请年度
     * @return 查询包装器
     */
    private LambdaQueryWrapper<UnionReliefEntity> approvedWrapper(Long unionMemberId, ReliefType type,
                                                                  Integer year) {
        return new LambdaQueryWrapper<UnionReliefEntity>()
                .eq(UnionReliefEntity::getUnionMemberId, unionMemberId)
                .eq(UnionReliefEntity::getReliefType, type.name())
                .eq(UnionReliefEntity::getApplyYear, year)
                .in(UnionReliefEntity::getStatus, ReliefStatus.APPROVED.name(), ReliefStatus.PAID.name());
    }

    /**
     * 从聚合结果中取金额。
     *
     * @param row 聚合结果行
     * @return 金额（缺失按 0）
     */
    private BigDecimal amountOf(Map<String, Object> row) {
        Object value = row.get(SUM_ALIAS);
        if (value == null && !row.isEmpty()) {
            value = row.values().iterator().next();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private UnionRelief toDomain(UnionReliefEntity entity) {
        UnionRelief relief = new UnionRelief();
        relief.setId(entity.getId());
        relief.setReliefNo(entity.getReliefNo());
        relief.setUnionMemberId(entity.getUnionMemberId());
        relief.setApplicantUserId(entity.getApplicantUserId());
        relief.setReliefType(ReliefType.parse(entity.getReliefType()));
        relief.setApplyYear(entity.getApplyYear());
        relief.setAmount(entity.getAmount());
        relief.setReason(entity.getReason());
        relief.setStatus(ReliefStatus.parse(entity.getStatus()));
        relief.setApprovalInstanceId(entity.getApprovalInstanceId());
        relief.setApprovalStatus(entity.getApprovalStatus());
        relief.setApprovalHint(entity.getApprovalHint());
        relief.setSubmitTime(entity.getSubmitTime());
        relief.setApprovedAt(entity.getApprovedAt());
        relief.setPaidAt(entity.getPaidAt());
        relief.setRemark(entity.getRemark());
        relief.setCreateTime(entity.getCreateTime());
        relief.setUpdateTime(entity.getUpdateTime());
        return relief;
    }

    /**
     * 领域模型转实体。
     *
     * @param relief 领域模型
     * @return 实体
     */
    private UnionReliefEntity toEntity(UnionRelief relief) {
        UnionReliefEntity entity = new UnionReliefEntity();
        entity.setId(relief.getId());
        entity.setReliefNo(relief.getReliefNo());
        entity.setUnionMemberId(relief.getUnionMemberId());
        entity.setApplicantUserId(relief.getApplicantUserId());
        entity.setReliefType(relief.getReliefType() == null ? null : relief.getReliefType().name());
        entity.setApplyYear(relief.getApplyYear());
        entity.setAmount(relief.getAmount());
        entity.setReason(relief.getReason());
        entity.setStatus(relief.getStatus() == null ? ReliefStatus.DRAFT.name() : relief.getStatus().name());
        entity.setApprovalInstanceId(relief.getApprovalInstanceId());
        entity.setApprovalStatus(relief.getApprovalStatus());
        entity.setApprovalHint(relief.getApprovalHint());
        entity.setSubmitTime(relief.getSubmitTime());
        entity.setApprovedAt(relief.getApprovedAt());
        entity.setPaidAt(relief.getPaidAt());
        entity.setRemark(relief.getRemark());
        return entity;
    }
}
