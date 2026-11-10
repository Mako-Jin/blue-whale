package com.bluewhale.party.union.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionMemberQuery;
import com.bluewhale.party.union.domain.model.UnionMemberStatus;
import com.bluewhale.party.union.domain.repository.UnionMemberRepository;
import com.bluewhale.party.union.infrastructure.persistence.entity.UnionMemberEntity;
import com.bluewhale.party.union.infrastructure.persistence.mapper.UnionMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 工会会员仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class UnionMemberRepositoryImpl implements UnionMemberRepository {

    private final UnionMemberMapper unionMemberMapper;

    @Override
    public List<UnionMember> page(UnionMemberQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<UnionMemberEntity> page = unionMemberMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(UnionMemberQuery query) {
        return unionMemberMapper.selectCount(filter(query));
    }

    @Override
    public Optional<UnionMember> findById(Long id) {
        return Optional.ofNullable(unionMemberMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<UnionMember> findActiveByEmployeeNo(String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            return Optional.empty();
        }
        return unionMemberMapper.selectList(new LambdaQueryWrapper<UnionMemberEntity>()
                        .eq(UnionMemberEntity::getEmployeeNo, employeeNo.trim())
                        .eq(UnionMemberEntity::getStatus, UnionMemberStatus.ACTIVE.name())
                        .orderByDesc(UnionMemberEntity::getId))
                .stream().findFirst().map(this::toDomain);
    }

    @Override
    public Optional<UnionMember> findActiveByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return unionMemberMapper.selectList(new LambdaQueryWrapper<UnionMemberEntity>()
                        .eq(UnionMemberEntity::getUserId, userId)
                        .eq(UnionMemberEntity::getStatus, UnionMemberStatus.ACTIVE.name())
                        .orderByDesc(UnionMemberEntity::getId))
                .stream().findFirst().map(this::toDomain);
    }

    @Override
    public List<UnionMember> listActive() {
        return unionMemberMapper.selectList(new LambdaQueryWrapper<UnionMemberEntity>()
                        .eq(UnionMemberEntity::getStatus, UnionMemberStatus.ACTIVE.name())
                        .orderByAsc(UnionMemberEntity::getMemberNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Long save(UnionMember member) {
        UnionMemberEntity entity = toEntity(member);
        if (entity.getId() == null) {
            unionMemberMapper.insert(entity);
        } else {
            unionMemberMapper.updateById(entity);
        }
        member.setId(entity.getId());
        member.setCreateTime(entity.getCreateTime());
        member.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<UnionMemberEntity> filter(UnionMemberQuery query) {
        LambdaQueryWrapper<UnionMemberEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(UnionMemberEntity::getId);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(UnionMemberEntity::getMemberNo, keyword)
                    .or().like(UnionMemberEntity::getEmployeeNo, keyword)
                    .or().like(UnionMemberEntity::getMemberName, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(UnionMemberEntity::getStatus,
                    query.getStatus().trim().toUpperCase(java.util.Locale.ROOT));
        }
        return wrapper.orderByDesc(UnionMemberEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private UnionMember toDomain(UnionMemberEntity entity) {
        UnionMember member = new UnionMember();
        member.setId(entity.getId());
        member.setMemberNo(entity.getMemberNo());
        member.setEmployeeNo(entity.getEmployeeNo());
        member.setUserId(entity.getUserId());
        member.setMemberName(entity.getMemberName());
        member.setDeptName(entity.getDeptName());
        member.setJoinDate(entity.getJoinDate());
        member.setStatus(UnionMemberStatus.parse(entity.getStatus()));
        member.setWithdrawDate(entity.getWithdrawDate());
        member.setDuesBase(entity.getDuesBase());
        member.setDuesPaidTo(entity.getDuesPaidTo());
        member.setRemark(entity.getRemark());
        member.setCreateTime(entity.getCreateTime());
        member.setUpdateTime(entity.getUpdateTime());
        return member;
    }

    /**
     * 领域模型转实体。
     *
     * @param member 领域模型
     * @return 实体
     */
    private UnionMemberEntity toEntity(UnionMember member) {
        UnionMemberEntity entity = new UnionMemberEntity();
        entity.setId(member.getId());
        entity.setMemberNo(member.getMemberNo());
        entity.setEmployeeNo(member.getEmployeeNo());
        entity.setUserId(member.getUserId());
        entity.setMemberName(member.getMemberName());
        entity.setDeptName(member.getDeptName());
        entity.setJoinDate(member.getJoinDate());
        entity.setStatus(member.getStatus() == null
                ? UnionMemberStatus.ACTIVE.name() : member.getStatus().name());
        entity.setWithdrawDate(member.getWithdrawDate());
        entity.setDuesBase(member.getDuesBase());
        entity.setDuesPaidTo(member.getDuesPaidTo());
        entity.setRemark(member.getRemark());
        return entity;
    }
}
