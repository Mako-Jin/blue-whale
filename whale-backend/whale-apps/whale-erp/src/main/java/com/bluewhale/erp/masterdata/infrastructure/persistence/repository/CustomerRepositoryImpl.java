package com.bluewhale.erp.masterdata.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.masterdata.domain.model.Customer;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;
import com.bluewhale.erp.masterdata.domain.repository.CustomerRepository;
import com.bluewhale.erp.masterdata.infrastructure.persistence.entity.CustomerEntity;
import com.bluewhale.erp.masterdata.infrastructure.persistence.mapper.CustomerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 客户业务主数据仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerMapper customerMapper;

    @Override
    public List<Customer> page(MasterDataQuery query) {
        Page<CustomerEntity> page = customerMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), filter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(MasterDataQuery query) {
        return customerMapper.selectCount(filter(query));
    }

    @Override
    public Optional<Customer> findById(Long id) {
        return Optional.ofNullable(customerMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<Customer> findByCode(String code) {
        return Optional.ofNullable(customerMapper.selectOne(new LambdaQueryWrapper<CustomerEntity>()
                .eq(CustomerEntity::getCode, code))).map(this::toDomain);
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

    /**
     * 构建查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<CustomerEntity> filter(MasterDataQuery query) {
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(CustomerEntity::getCode, query.getKeyword())
                    .or().like(CustomerEntity::getName, query.getKeyword()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(CustomerEntity::getStatus, query.getStatus());
        }
        return wrapper.orderByAsc(CustomerEntity::getCode);
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
        customer.setCode(entity.getCode());
        customer.setName(entity.getName());
        customer.setShortName(entity.getShortName());
        customer.setTaxNo(entity.getTaxNo());
        customer.setContactName(entity.getContactName());
        customer.setContactPhone(entity.getContactPhone());
        customer.setCreditLimit(entity.getCreditLimit());
        customer.setStatus(entity.getStatus());
        customer.setRemark(entity.getRemark());
        return customer;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param customer 领域模型
     */
    private void fill(CustomerEntity entity, Customer customer) {
        entity.setCode(customer.getCode());
        entity.setName(customer.getName());
        entity.setShortName(customer.getShortName());
        entity.setTaxNo(customer.getTaxNo());
        entity.setContactName(customer.getContactName());
        entity.setContactPhone(customer.getContactPhone());
        entity.setCreditLimit(customer.getCreditLimit());
        entity.setStatus(customer.getStatus());
        entity.setRemark(customer.getRemark());
    }
}
