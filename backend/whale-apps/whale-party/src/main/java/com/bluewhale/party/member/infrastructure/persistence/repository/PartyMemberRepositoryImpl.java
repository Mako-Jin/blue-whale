package com.bluewhale.party.member.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.member.domain.model.MemberStatus;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.model.PartyMemberQuery;
import com.bluewhale.party.member.domain.model.PoliticalStatus;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import com.bluewhale.party.member.infrastructure.persistence.entity.PartyMemberEntity;
import com.bluewhale.party.member.infrastructure.persistence.mapper.PartyMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 党员档案仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PartyMemberRepositoryImpl implements PartyMemberRepository {

    private final PartyMemberMapper partyMemberMapper;

    @Override
    public List<PartyMember> page(PartyMemberQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<PartyMemberEntity> page = partyMemberMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(PartyMemberQuery query) {
        return partyMemberMapper.selectCount(filter(query));
    }

    @Override
    public Optional<PartyMember> findById(Long id) {
        return Optional.ofNullable(partyMemberMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<PartyMember> findByEmployeeNo(String employeeNo) {
        return Optional.ofNullable(partyMemberMapper.selectOne(new LambdaQueryWrapper<PartyMemberEntity>()
                .eq(PartyMemberEntity::getEmployeeNo, employeeNo))).map(this::toDomain);
    }

    @Override
    public Optional<PartyMember> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return partyMemberMapper.selectList(new LambdaQueryWrapper<PartyMemberEntity>()
                        .eq(PartyMemberEntity::getUserId, userId)
                        .orderByDesc(PartyMemberEntity::getId))
                .stream().findFirst().map(this::toDomain);
    }

    @Override
    public List<PartyMember> listPayable(Long branchId) {
        LambdaQueryWrapper<PartyMemberEntity> wrapper = new LambdaQueryWrapper<PartyMemberEntity>()
                .ne(PartyMemberEntity::getStatus, MemberStatus.TRANSFERRED.name());
        if (branchId != null) {
            wrapper.eq(PartyMemberEntity::getBranchId, branchId);
        }
        return partyMemberMapper.selectList(wrapper.orderByAsc(PartyMemberEntity::getBranchId)
                        .orderByAsc(PartyMemberEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countActiveByBranch(Long branchId) {
        return partyMemberMapper.selectCount(new LambdaQueryWrapper<PartyMemberEntity>()
                .eq(PartyMemberEntity::getBranchId, branchId)
                .eq(PartyMemberEntity::getStatus, MemberStatus.ACTIVE.name()));
    }

    @Override
    public List<PartyMember> listActive(Long branchId) {
        LambdaQueryWrapper<PartyMemberEntity> wrapper = new LambdaQueryWrapper<PartyMemberEntity>()
                .eq(PartyMemberEntity::getStatus, MemberStatus.ACTIVE.name());
        if (branchId != null) {
            wrapper.eq(PartyMemberEntity::getBranchId, branchId);
        }
        return partyMemberMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public Long save(PartyMember member) {
        PartyMemberEntity entity = toEntity(member);
        if (entity.getId() == null) {
            partyMemberMapper.insert(entity);
        } else {
            partyMemberMapper.updateById(entity);
        }
        member.setId(entity.getId());
        member.setCreateTime(entity.getCreateTime());
        member.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public long countActive() {
        return partyMemberMapper.selectCount(new LambdaQueryWrapper<PartyMemberEntity>()
                .eq(PartyMemberEntity::getStatus, MemberStatus.ACTIVE.name()));
    }

    @Override
    public long countProbationaryPending() {
        return partyMemberMapper.selectCount(new LambdaQueryWrapper<PartyMemberEntity>()
                .eq(PartyMemberEntity::getPoliticalStatus, PoliticalStatus.PROBATIONARY.name())
                .isNull(PartyMemberEntity::getFullMemberDate));
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<PartyMemberEntity> filter(PartyMemberQuery query) {
        LambdaQueryWrapper<PartyMemberEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PartyMemberEntity::getId);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(PartyMemberEntity::getMemberNo, keyword)
                    .or().like(PartyMemberEntity::getEmployeeNo, keyword)
                    .or().like(PartyMemberEntity::getMemberName, keyword));
        }
        if (query.getBranchId() != null) {
            wrapper.eq(PartyMemberEntity::getBranchId, query.getBranchId());
        }
        if (StringUtils.hasText(query.getPoliticalStatus())) {
            wrapper.eq(PartyMemberEntity::getPoliticalStatus,
                    query.getPoliticalStatus().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PartyMemberEntity::getStatus, query.getStatus().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (Boolean.TRUE.equals(query.getProbationaryPending())) {
            wrapper.eq(PartyMemberEntity::getPoliticalStatus, PoliticalStatus.PROBATIONARY.name())
                    .isNull(PartyMemberEntity::getFullMemberDate);
        }
        return wrapper.orderByDesc(PartyMemberEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PartyMember toDomain(PartyMemberEntity entity) {
        PartyMember member = new PartyMember();
        member.setId(entity.getId());
        member.setMemberNo(entity.getMemberNo());
        member.setEmployeeNo(entity.getEmployeeNo());
        member.setUserId(entity.getUserId());
        member.setMemberName(entity.getMemberName());
        member.setBranchId(entity.getBranchId());
        member.setPoliticalStatus(PoliticalStatus.parse(entity.getPoliticalStatus()));
        member.setBirthDate(entity.getBirthDate());
        member.setEducation(entity.getEducation());
        member.setJoinPartyDate(entity.getJoinPartyDate());
        member.setFullMemberDate(entity.getFullMemberDate());
        member.setDuesBase(entity.getDuesBase());
        member.setPhone(entity.getPhone());
        member.setStatus(MemberStatus.parse(entity.getStatus()));
        member.setTransferOutDate(entity.getTransferOutDate());
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
    private PartyMemberEntity toEntity(PartyMember member) {
        PartyMemberEntity entity = new PartyMemberEntity();
        entity.setId(member.getId());
        entity.setMemberNo(member.getMemberNo());
        entity.setEmployeeNo(member.getEmployeeNo());
        entity.setUserId(member.getUserId());
        entity.setMemberName(member.getMemberName());
        entity.setBranchId(member.getBranchId());
        entity.setPoliticalStatus(member.getPoliticalStatus() == null
                ? PoliticalStatus.FULL_MEMBER.name() : member.getPoliticalStatus().name());
        entity.setBirthDate(member.getBirthDate());
        entity.setEducation(member.getEducation());
        entity.setJoinPartyDate(member.getJoinPartyDate());
        entity.setFullMemberDate(member.getFullMemberDate());
        entity.setDuesBase(member.getDuesBase());
        entity.setPhone(member.getPhone());
        entity.setStatus(member.getStatus() == null ? MemberStatus.ACTIVE.name() : member.getStatus().name());
        entity.setTransferOutDate(member.getTransferOutDate());
        entity.setRemark(member.getRemark());
        return entity;
    }
}
