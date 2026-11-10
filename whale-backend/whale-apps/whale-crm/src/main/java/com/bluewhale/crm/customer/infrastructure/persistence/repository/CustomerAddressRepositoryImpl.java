package com.bluewhale.crm.customer.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.customer.domain.model.CustomerAddress;
import com.bluewhale.crm.customer.domain.repository.CustomerAddressRepository;
import com.bluewhale.crm.customer.infrastructure.persistence.entity.CustomerAddressEntity;
import com.bluewhale.crm.customer.infrastructure.persistence.mapper.CustomerAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 客户地址仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CustomerAddressRepositoryImpl implements CustomerAddressRepository {

    private final CustomerAddressMapper addressMapper;

    @Override
    public List<CustomerAddress> list(Long customerId) {
        LambdaQueryWrapper<CustomerAddressEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerAddressEntity::getCustomerId, customerId)
                .orderByDesc(CustomerAddressEntity::getIsDefaultShipping)
                .orderByDesc(CustomerAddressEntity::getIsDefaultBilling)
                .orderByDesc(CustomerAddressEntity::getUpdateTime);
        return addressMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<CustomerAddress> findById(Long id) {
        return Optional.ofNullable(addressMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void insert(CustomerAddress address) {
        CustomerAddressEntity entity = new CustomerAddressEntity();
        fill(entity, address);
        addressMapper.insert(entity);
        address.setId(entity.getId());
    }

    @Override
    public void update(CustomerAddress address) {
        CustomerAddressEntity entity = new CustomerAddressEntity();
        entity.setId(address.getId());
        fill(entity, address);
        addressMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        addressMapper.deleteById(id);
    }

    @Override
    public void clearDefault(Long customerId, boolean shipping, boolean billing, Long excludeId) {
        if (shipping) {
            LambdaUpdateWrapper<CustomerAddressEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(CustomerAddressEntity::getCustomerId, customerId)
                    .eq(CustomerAddressEntity::getIsDefaultShipping, Boolean.TRUE)
                    .set(CustomerAddressEntity::getIsDefaultShipping, Boolean.FALSE);
            if (excludeId != null) {
                wrapper.ne(CustomerAddressEntity::getId, excludeId);
            }
            addressMapper.update(null, wrapper);
        }
        if (billing) {
            LambdaUpdateWrapper<CustomerAddressEntity> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(CustomerAddressEntity::getCustomerId, customerId)
                    .eq(CustomerAddressEntity::getIsDefaultBilling, Boolean.TRUE)
                    .set(CustomerAddressEntity::getIsDefaultBilling, Boolean.FALSE);
            if (excludeId != null) {
                wrapper.ne(CustomerAddressEntity::getId, excludeId);
            }
            addressMapper.update(null, wrapper);
        }
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private CustomerAddress toDomain(CustomerAddressEntity entity) {
        CustomerAddress address = new CustomerAddress();
        address.setId(entity.getId());
        address.setCustomerId(entity.getCustomerId());
        address.setContactName(entity.getContactName());
        address.setPhone(entity.getPhone());
        address.setProvince(entity.getProvince());
        address.setCity(entity.getCity());
        address.setDistrict(entity.getDistrict());
        address.setDetail(entity.getDetail());
        address.setDefaultShipping(entity.getIsDefaultShipping());
        address.setDefaultBilling(entity.getIsDefaultBilling());
        address.setStatus(entity.getStatus());
        return address;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param address 领域模型
     */
    private void fill(CustomerAddressEntity entity, CustomerAddress address) {
        entity.setCustomerId(address.getCustomerId());
        entity.setContactName(address.getContactName());
        entity.setPhone(address.getPhone());
        entity.setProvince(address.getProvince());
        entity.setCity(address.getCity());
        entity.setDistrict(address.getDistrict());
        entity.setDetail(address.getDetail());
        entity.setIsDefaultShipping(Boolean.TRUE.equals(address.getDefaultShipping()));
        entity.setIsDefaultBilling(Boolean.TRUE.equals(address.getDefaultBilling()));
        entity.setStatus(address.getStatus());
    }
}
