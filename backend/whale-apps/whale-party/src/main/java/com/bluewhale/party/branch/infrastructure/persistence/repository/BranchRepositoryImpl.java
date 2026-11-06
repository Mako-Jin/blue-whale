package com.bluewhale.party.branch.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.model.BranchQuery;
import com.bluewhale.party.branch.domain.model.BranchStatus;
import com.bluewhale.party.branch.domain.repository.BranchRepository;
import com.bluewhale.party.branch.infrastructure.persistence.entity.BranchEntity;
import com.bluewhale.party.branch.infrastructure.persistence.mapper.BranchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 支部仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class BranchRepositoryImpl implements BranchRepository {

    private final BranchMapper branchMapper;

    @Override
    public List<Branch> page(BranchQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<BranchEntity> page = branchMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(BranchQuery query) {
        return branchMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Branch> findById(Long id) {
        return Optional.ofNullable(branchMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Branch> findByCode(String branchCode) {
        return Optional.ofNullable(branchMapper.selectOne(new LambdaQueryWrapper<BranchEntity>()
                .eq(BranchEntity::getBranchCode, branchCode))).map(this::toDomain);
    }

    @Override
    public List<Branch> listAll() {
        return branchMapper.selectList(new LambdaQueryWrapper<BranchEntity>()
                        .orderByAsc(BranchEntity::getBranchLevel)
                        .orderByAsc(BranchEntity::getSortNo)
                        .orderByAsc(BranchEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<Branch> listByParent(Long parentId) {
        return branchMapper.selectList(new LambdaQueryWrapper<BranchEntity>()
                        .eq(BranchEntity::getParentId, parentId == null ? Branch.ROOT_PARENT_ID : parentId)
                        .orderByAsc(BranchEntity::getSortNo)
                        .orderByAsc(BranchEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public long countByParent(Long parentId) {
        return branchMapper.selectCount(new LambdaQueryWrapper<BranchEntity>()
                .eq(BranchEntity::getParentId, parentId == null ? Branch.ROOT_PARENT_ID : parentId));
    }

    @Override
    public Long save(Branch branch) {
        BranchEntity entity = toEntity(branch);
        if (entity.getId() == null) {
            branchMapper.insert(entity);
        } else {
            branchMapper.updateById(entity);
        }
        branch.setId(entity.getId());
        branch.setCreateTime(entity.getCreateTime());
        branch.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public long countActive() {
        return branchMapper.selectCount(new LambdaQueryWrapper<BranchEntity>()
                .eq(BranchEntity::getStatus, BranchStatus.ACTIVE.name()));
    }

    /**
     * 组装查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<BranchEntity> filter(BranchQuery query) {
        LambdaQueryWrapper<BranchEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByAsc(BranchEntity::getBranchLevel).orderByAsc(BranchEntity::getSortNo);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(BranchEntity::getBranchCode, keyword)
                    .or().like(BranchEntity::getBranchName, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(BranchEntity::getStatus, query.getStatus().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (query.getParentId() != null) {
            wrapper.eq(BranchEntity::getParentId, query.getParentId());
        }
        if (StringUtils.hasText(query.getOrgCode())) {
            wrapper.eq(BranchEntity::getOrgCode, query.getOrgCode().trim());
        }
        return wrapper.orderByAsc(BranchEntity::getBranchLevel)
                .orderByAsc(BranchEntity::getSortNo)
                .orderByAsc(BranchEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Branch toDomain(BranchEntity entity) {
        Branch branch = new Branch();
        branch.setId(entity.getId());
        branch.setBranchCode(entity.getBranchCode());
        branch.setBranchName(entity.getBranchName());
        branch.setParentId(entity.getParentId());
        branch.setBranchLevel(entity.getBranchLevel());
        branch.setOrgCode(entity.getOrgCode());
        branch.setSecretaryName(entity.getSecretaryName());
        branch.setSecretaryUserId(entity.getSecretaryUserId());
        branch.setEstablishedDate(entity.getEstablishedDate());
        branch.setStatus(BranchStatus.parse(entity.getStatus()));
        branch.setSortNo(entity.getSortNo());
        branch.setRemark(entity.getRemark());
        branch.setCreateTime(entity.getCreateTime());
        branch.setUpdateTime(entity.getUpdateTime());
        return branch;
    }

    /**
     * 领域模型转实体。
     *
     * @param branch 领域模型
     * @return 实体
     */
    private BranchEntity toEntity(Branch branch) {
        BranchEntity entity = new BranchEntity();
        entity.setId(branch.getId());
        entity.setBranchCode(branch.getBranchCode());
        entity.setBranchName(branch.getBranchName());
        entity.setParentId(branch.getParentId() == null ? Branch.ROOT_PARENT_ID : branch.getParentId());
        entity.setBranchLevel(branch.getBranchLevel() == null ? 1 : branch.getBranchLevel());
        entity.setOrgCode(branch.getOrgCode());
        entity.setSecretaryName(branch.getSecretaryName());
        entity.setSecretaryUserId(branch.getSecretaryUserId());
        entity.setEstablishedDate(branch.getEstablishedDate());
        entity.setStatus(branch.getStatus() == null ? BranchStatus.ACTIVE.name() : branch.getStatus().name());
        entity.setSortNo(branch.getSortNo() == null ? 0 : branch.getSortNo());
        entity.setRemark(branch.getRemark());
        return entity;
    }
}
