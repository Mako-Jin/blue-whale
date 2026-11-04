package com.bluewhale.crm.customer.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.crm.customer.domain.model.Contact;
import com.bluewhale.crm.customer.domain.repository.CustomerContactRepository;
import com.bluewhale.crm.customer.infrastructure.persistence.entity.ContactEntity;
import com.bluewhale.crm.customer.infrastructure.persistence.mapper.ContactMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 联系人仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class CustomerContactRepositoryImpl implements CustomerContactRepository {

    private final ContactMapper contactMapper;

    @Override
    public List<Contact> list(Long customerId) {
        LambdaQueryWrapper<ContactEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContactEntity::getCustomerId, customerId)
                .orderByDesc(ContactEntity::getIsPrimary)
                .orderByDesc(ContactEntity::getUpdateTime);
        return contactMapper.selectList(wrapper).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Contact> findById(Long id) {
        return Optional.ofNullable(contactMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void insert(Contact contact) {
        ContactEntity entity = new ContactEntity();
        fill(entity, contact);
        contactMapper.insert(entity);
        contact.setId(entity.getId());
    }

    @Override
    public void update(Contact contact) {
        ContactEntity entity = new ContactEntity();
        entity.setId(contact.getId());
        fill(entity, contact);
        contactMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        contactMapper.deleteById(id);
    }

    @Override
    public void clearPrimary(Long customerId, Long excludeId) {
        LambdaUpdateWrapper<ContactEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ContactEntity::getCustomerId, customerId)
                .eq(ContactEntity::getIsPrimary, Boolean.TRUE)
                .set(ContactEntity::getIsPrimary, Boolean.FALSE);
        if (excludeId != null) {
            wrapper.ne(ContactEntity::getId, excludeId);
        }
        contactMapper.update(null, wrapper);
    }

    @Override
    public boolean belongsTo(Long id, Long customerId) {
        LambdaQueryWrapper<ContactEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContactEntity::getId, id).eq(ContactEntity::getCustomerId, customerId);
        return contactMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<Long> findCustomerIdsByNormalized(String normalizedPhone, String normalizedEmail,
                                                   Long excludeCustomerId) {
        if (!StringUtils.hasText(normalizedPhone) && !StringUtils.hasText(normalizedEmail)) {
            return List.of();
        }
        LambdaQueryWrapper<ContactEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            boolean first = true;
            if (StringUtils.hasText(normalizedPhone)) {
                w.eq(ContactEntity::getNormalizedPhone, normalizedPhone);
                first = false;
            }
            if (StringUtils.hasText(normalizedEmail)) {
                if (!first) {
                    w.or();
                }
                w.eq(ContactEntity::getNormalizedEmail, normalizedEmail);
            }
        });
        if (excludeCustomerId != null) {
            wrapper.ne(ContactEntity::getCustomerId, excludeCustomerId);
        }
        return contactMapper.selectList(wrapper).stream()
                .map(ContactEntity::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private Contact toDomain(ContactEntity entity) {
        Contact contact = new Contact();
        contact.setId(entity.getId());
        contact.setCustomerId(entity.getCustomerId());
        contact.setName(entity.getName());
        contact.setPhone(entity.getPhone());
        contact.setEmail(entity.getEmail());
        contact.setWechat(entity.getWechat());
        contact.setTitle(entity.getTitle());
        contact.setDecisionRole(entity.getDecisionRole());
        contact.setDecisionMaker(entity.getIsDecisionMaker());
        contact.setPrimary(entity.getIsPrimary());
        contact.setStatus(entity.getStatus());
        contact.setNormalizedPhone(entity.getNormalizedPhone());
        contact.setNormalizedEmail(entity.getNormalizedEmail());
        contact.setVersion(entity.getVersion());
        return contact;
    }

    /**
     * 领域模型写入实体。
     *
     * @param entity 实体
     * @param contact 领域模型
     */
    private void fill(ContactEntity entity, Contact contact) {
        entity.setCustomerId(contact.getCustomerId());
        entity.setName(contact.getName());
        entity.setPhone(contact.getPhone());
        entity.setEmail(contact.getEmail());
        entity.setWechat(contact.getWechat());
        entity.setTitle(contact.getTitle());
        entity.setDecisionRole(contact.getDecisionRole());
        entity.setIsDecisionMaker(Boolean.TRUE.equals(contact.getDecisionMaker()));
        entity.setIsPrimary(Boolean.TRUE.equals(contact.getPrimary()));
        entity.setStatus(contact.getStatus());
        entity.setNormalizedPhone(contact.getNormalizedPhone());
        entity.setNormalizedEmail(contact.getNormalizedEmail());
        entity.setVersion(contact.getVersion() == null ? 0 : contact.getVersion());
    }
}
