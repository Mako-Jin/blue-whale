package com.bluewhale.crm.customer.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerLevel;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.customer.domain.model.PoolStatus;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import com.bluewhale.crm.customer.infrastructure.persistence.entity.CustomerEntity;
import com.bluewhale.crm.customer.infrastructure.persistence.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 客户仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CustomerRepositoryImpl implements CustomerRepository {

    /** 单次回收上限 */
    private static final int RECYCLE_LIMIT_MAX = 200;

    private final CustomerMapper customerMapper;

    @Override
    public List<Customer> page(CustomerQuery query) {
        Page<CustomerEntity> page = customerMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(CustomerQuery query) {
        return customerMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Customer> findById(Long id) {
        return Optional.ofNullable(customerMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Customer> findByCode(String code) {
        return Optional.ofNullable(customerMapper.selectOne(new LambdaQueryWrapper<CustomerEntity>()
                .eq(CustomerEntity::getCustomerCode, code))).map(this::toDomain);
    }

    @Override
    public List<Customer> findHardDuplicates(String normalizedName, String normalizedTaxNo,
                                             String normalizedDomain, Long excludeId) {
        if (!StringUtils.hasText(normalizedName) && !StringUtils.hasText(normalizedTaxNo)
                && !StringUtils.hasText(normalizedDomain)) {
            return List.of();
        }
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(CustomerEntity::getStatus, "MERGED")
                .and(w -> {
                    boolean first = true;
                    if (StringUtils.hasText(normalizedName)) {
                        w.eq(CustomerEntity::getNormalizedName, normalizedName);
                        first = false;
                    }
                    if (StringUtils.hasText(normalizedTaxNo)) {
                        if (!first) {
                            w.or();
                        }
                        w.eq(CustomerEntity::getNormalizedTaxNo, normalizedTaxNo);
                        first = false;
                    }
                    if (StringUtils.hasText(normalizedDomain)) {
                        if (!first) {
                            w.or();
                        }
                        w.eq(CustomerEntity::getNormalizedDomain, normalizedDomain);
                    }
                });
        if (excludeId != null) {
            wrapper.ne(CustomerEntity::getId, excludeId);
        }
        return customerMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<String> findMaxCode(String prefix) {
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(CustomerEntity::getCustomerCode, prefix)
                .orderByDesc(CustomerEntity::getCustomerCode)
                .last("LIMIT 1");
        CustomerEntity entity = customerMapper.selectOne(wrapper);
        return entity == null ? Optional.empty() : Optional.of(entity.getCustomerCode());
    }

    @Override
    public void insert(Customer customer) {
        CustomerEntity entity = new CustomerEntity();
        fill(entity, customer);
        customerMapper.insert(entity);
        customer.setId(entity.getId());
    }

    @Override
    public void update(Customer customer) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(customer.getId());
        fill(entity, customer);
        customerMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        customerMapper.deleteById(id);
    }

    @Override
    public boolean claim(Long id, Long ownerId, LocalDateTime at) {
        LambdaUpdateWrapper<CustomerEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CustomerEntity::getId, id)
                .ne(CustomerEntity::getStatus, "MERGED")
                .and(w -> w.eq(CustomerEntity::getPoolStatus, PoolStatus.IN_POOL.name())
                        .or().isNull(CustomerEntity::getOwnerId))
                .set(CustomerEntity::getOwnerId, ownerId)
                .set(CustomerEntity::getPoolStatus, PoolStatus.OWNED.name())
                .set(CustomerEntity::getPoolAt, at);
        return customerMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean release(Long id, LocalDateTime at) {
        LambdaUpdateWrapper<CustomerEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CustomerEntity::getId, id)
                .ne(CustomerEntity::getStatus, "MERGED")
                .set(CustomerEntity::getOwnerId, null)
                .set(CustomerEntity::getPoolStatus, PoolStatus.IN_POOL.name())
                .set(CustomerEntity::getPoolAt, at);
        return customerMapper.update(null, wrapper) > 0;
    }

    @Override
    public boolean updateOwner(Long id, Long ownerId, Long deptId, LocalDateTime at) {
        LambdaUpdateWrapper<CustomerEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CustomerEntity::getId, id)
                .ne(CustomerEntity::getStatus, "MERGED")
                .set(CustomerEntity::getOwnerId, ownerId)
                .set(deptId != null, CustomerEntity::getDeptId, deptId)
                .set(CustomerEntity::getPoolStatus, PoolStatus.OWNED.name())
                .set(CustomerEntity::getPoolAt, at);
        return customerMapper.update(null, wrapper) > 0;
    }

    @Override
    public void touchFollowUp(Long id, LocalDateTime at) {
        LambdaUpdateWrapper<CustomerEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CustomerEntity::getId, id).set(CustomerEntity::getLastFollowUpAt, at);
        customerMapper.update(null, wrapper);
    }

    @Override
    public List<Customer> findRecycleCandidates(LocalDateTime followUpBefore, String levels,
                                                Long deptId, int limit) {
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerEntity::getStatus, "ACTIVE")
                .eq(CustomerEntity::getPoolStatus, PoolStatus.OWNED.name())
                .and(w -> w.isNull(CustomerEntity::getLastFollowUpAt)
                        .lt(CustomerEntity::getCreateTime, followUpBefore)
                        .or().lt(CustomerEntity::getLastFollowUpAt, followUpBefore));
        if (StringUtils.hasText(levels)) {
            List<String> levelCodes = java.util.Arrays.stream(levels.split(","))
                    .map(String::trim).filter(StringUtils::hasText)
                    .map(CustomerLevel::canonical).toList();
            if (!levelCodes.isEmpty()) {
                wrapper.in(CustomerEntity::getLevel, levelCodes);
            }
        }
        if (deptId != null) {
            wrapper.eq(CustomerEntity::getDeptId, deptId);
        }
        wrapper.orderByAsc(CustomerEntity::getLastFollowUpAt);
        Page<CustomerEntity> page = customerMapper.selectPage(
                new Page<>(1, Math.max(1, Math.min(limit, RECYCLE_LIMIT_MAX))), wrapper);
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    /**
     * 构建分页查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<CustomerEntity> filter(CustomerQuery query) {
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(CustomerEntity::getCustomerCode, query.getKeyword())
                    .or().like(CustomerEntity::getName, query.getKeyword())
                    .or().like(CustomerEntity::getShortName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CustomerEntity::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getLevel())) {
            wrapper.eq(CustomerEntity::getLevel, CustomerLevel.canonical(query.getLevel()));
        }
        if (StringUtils.hasText(query.getSource())) {
            wrapper.eq(CustomerEntity::getSource, query.getSource());
        }
        if (StringUtils.hasText(query.getIndustry())) {
            wrapper.eq(CustomerEntity::getIndustry, query.getIndustry());
        }
        if (StringUtils.hasText(query.getPoolStatus())) {
            wrapper.eq(CustomerEntity::getPoolStatus, PoolStatus.from(query.getPoolStatus()).name());
        }
        if (query.getOwnerId() != null) {
            wrapper.eq(CustomerEntity::getOwnerId, query.getOwnerId());
        }
        if (query.getCreatedFrom() != null) {
            wrapper.ge(CustomerEntity::getCreateTime, query.getCreatedFrom());
        }
        if (query.getCreatedTo() != null) {
            wrapper.le(CustomerEntity::getCreateTime, query.getCreatedTo());
        }
        return wrapper.orderByDesc(CustomerEntity::getCreateTime);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Customer toDomain(CustomerEntity entity) {
        Customer customer = new Customer();
        customer.setId(entity.getId());
        customer.setCustomerCode(entity.getCustomerCode());
        customer.setName(entity.getName());
        customer.setShortName(entity.getShortName());
        customer.setStatus(entity.getStatus());
        customer.setOwnerId(entity.getOwnerId());
        customer.setDeptId(entity.getDeptId());
        customer.setLevel(entity.getLevel());
        customer.setSource(entity.getSource());
        customer.setIndustry(entity.getIndustry());
        customer.setAddress(entity.getAddress());
        customer.setTags(entity.getTags());
        customer.setMergedIntoId(entity.getMergedIntoId());
        customer.setWebsite(entity.getWebsite());
        customer.setScale(entity.getScale());
        customer.setTaxNo(entity.getTaxNo());
        customer.setProvince(entity.getProvince());
        customer.setCity(entity.getCity());
        customer.setRegion(entity.getRegion());
        customer.setNormalizedName(entity.getNormalizedName());
        customer.setNormalizedDomain(entity.getNormalizedDomain());
        customer.setNormalizedTaxNo(entity.getNormalizedTaxNo());
        customer.setVersion(entity.getVersion());
        customer.setPoolStatus(entity.getPoolStatus());
        customer.setPoolAt(entity.getPoolAt());
        customer.setLastFollowUpAt(entity.getLastFollowUpAt());
        customer.setCreateTime(entity.getCreateTime());
        customer.setUpdateTime(entity.getUpdateTime());
        return customer;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param customer 领域模型
     */
    private void fill(CustomerEntity entity, Customer customer) {
        entity.setCustomerCode(customer.getCustomerCode());
        entity.setName(customer.getName());
        entity.setShortName(customer.getShortName());
        entity.setStatus(customer.getStatus());
        entity.setOwnerId(customer.getOwnerId());
        entity.setDeptId(customer.getDeptId());
        entity.setLevel(customer.getLevel());
        entity.setSource(customer.getSource());
        entity.setIndustry(customer.getIndustry());
        entity.setAddress(customer.getAddress());
        entity.setTags(customer.getTags());
        entity.setMergedIntoId(customer.getMergedIntoId());
        entity.setWebsite(customer.getWebsite());
        entity.setScale(customer.getScale());
        entity.setTaxNo(customer.getTaxNo());
        entity.setProvince(customer.getProvince());
        entity.setCity(customer.getCity());
        entity.setRegion(customer.getRegion());
        entity.setNormalizedName(customer.getNormalizedName());
        entity.setNormalizedDomain(customer.getNormalizedDomain());
        entity.setNormalizedTaxNo(customer.getNormalizedTaxNo());
        entity.setVersion(customer.getVersion() == null ? 0 : customer.getVersion());
        entity.setPoolStatus(customer.getPoolStatus());
        entity.setPoolAt(customer.getPoolAt());
        entity.setLastFollowUpAt(customer.getLastFollowUpAt());
    }
}
