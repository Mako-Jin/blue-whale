package com.bluewhale.crm.customer.domain.repository;

import com.bluewhale.crm.customer.domain.model.Contact;

import java.util.List;
import java.util.Optional;

/**
 * 客户联系人仓储（属于客户聚合内实体）。
 *
 * @author BlueWhale
 */
public interface CustomerContactRepository {

    /**
     * 查询客户全部联系人。
     *
     * @param customerId 客户 ID
     * @return 联系人列表
     */
    List<Contact> list(Long customerId);

    /**
     * 按主键查询联系人。
     *
     * @param id 主键
     * @return 联系人
     */
    Optional<Contact> findById(Long id);

    /**
     * 新增联系人。
     *
     * @param contact 联系人
     */
    void insert(Contact contact);

    /**
     * 更新联系人。
     *
     * @param contact 联系人
     */
    void update(Contact contact);

    /**
     * 逻辑删除联系人。
     *
     * @param id 主键
     */
    void delete(Long id);

    /**
     * 清除客户主联系人标记。
     *
     * @param customerId 客户 ID
     * @param excludeId 排除的联系人 ID，可为 null
     */
    void clearPrimary(Long customerId, Long excludeId);

    /**
     * 判断联系人是否属于指定客户。
     *
     * @param id 联系人 ID
     * @param customerId 客户 ID
     * @return 属于该客户返回 true
     */
    boolean belongsTo(Long id, Long customerId);

    /**
     * 按归一化电话 / 邮箱查找其他客户。
     *
     * @param normalizedPhone 归一化电话
     * @param normalizedEmail 归一化邮箱
     * @param excludeCustomerId 排除的客户 ID
     * @return 命中的客户 ID（去重）
     */
    List<Long> findCustomerIdsByNormalized(String normalizedPhone, String normalizedEmail, Long excludeCustomerId);
}
