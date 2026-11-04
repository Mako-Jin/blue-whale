package com.bluewhale.crm.customer.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.customer.application.command.AddressSaveCommand;
import com.bluewhale.crm.customer.application.command.ContactSaveCommand;
import com.bluewhale.crm.customer.application.command.CustomerLevelCommand;
import com.bluewhale.crm.customer.application.command.CustomerSaveCommand;
import com.bluewhale.crm.customer.application.command.CustomerStatusCommand;
import com.bluewhale.crm.customer.application.command.ProfileSaveCommand;
import com.bluewhale.crm.customer.application.view.ContactView;
import com.bluewhale.crm.customer.application.view.Customer360View;
import com.bluewhale.crm.customer.application.view.CustomerAddressView;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.customer.application.view.CustomerProfileView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.application.view.DuplicateCandidateView;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;

import java.util.List;

/**
 * 客户主数据应用服务（M1）。
 *
 * @author BlueWhale
 */
public interface CustomerAppService {

    /**
     * 分页查询客户（自动应用数据范围）。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     * @return 客户分页
     */
    PageResult<CustomerView> page(CustomerQuery query, CrmActor actor);

    /**
     * 查询客户详情。
     *
     * @param id 客户 ID
     * @param actor 当前操作人
     * @return 客户详情
     */
    CustomerDetailView detail(Long id, CrmActor actor);

    /**
     * 新建客户。
     *
     * @param command 新建命令
     * @param actor 当前操作人
     * @return 客户视图
     */
    CustomerView create(CustomerSaveCommand command, CrmActor actor);

    /**
     * 编辑客户。
     *
     * @param id 客户 ID
     * @param command 编辑命令
     * @param actor 当前操作人
     * @return 客户视图
     */
    CustomerView update(Long id, CustomerSaveCommand command, CrmActor actor);

    /**
     * 变更客户状态。
     *
     * @param id 客户 ID
     * @param command 状态命令
     * @param actor 当前操作人
     * @return 客户视图
     */
    CustomerView changeStatus(Long id, CustomerStatusCommand command, CrmActor actor);

    /**
     * 变更客户等级。
     *
     * @param id 客户 ID
     * @param command 等级命令
     * @param actor 当前操作人
     * @return 客户视图
     */
    CustomerView changeLevel(Long id, CustomerLevelCommand command, CrmActor actor);

    /**
     * 客户查重。
     *
     * @param name 客户名称
     * @param website 官网
     * @param taxNo 税号
     * @param phone 联系人电话
     * @param email 联系人邮箱
     * @param excludeId 排除的客户 ID
     * @param actor 当前操作人
     * @return 查重候选分页
     */
    PageResult<DuplicateCandidateView> duplicateCheck(String name, String website, String taxNo,
                                                      String phone, String email, Long excludeId, CrmActor actor);

    /**
     * 逻辑删除客户。
     *
     * @param id 客户 ID
     * @param actor 当前操作人
     */
    void delete(Long id, CrmActor actor);

    /**
     * 查询客户联系人。
     *
     * @param customerId 客户 ID
     * @param actor 当前操作人
     * @return 联系人列表
     */
    List<ContactView> listContacts(Long customerId, CrmActor actor);

    /**
     * 新建联系人。
     *
     * @param customerId 客户 ID
     * @param command 联系人命令
     * @param actor 当前操作人
     * @return 联系人视图
     */
    ContactView createContact(Long customerId, ContactSaveCommand command, CrmActor actor);

    /**
     * 编辑联系人。
     *
     * @param contactId 联系人 ID
     * @param command 联系人命令
     * @param actor 当前操作人
     * @return 联系人视图
     */
    ContactView updateContact(Long contactId, ContactSaveCommand command, CrmActor actor);

    /**
     * 删除联系人（主联系人需先转移）。
     *
     * @param contactId 联系人 ID
     * @param actor 当前操作人
     */
    void deleteContact(Long contactId, CrmActor actor);

    /**
     * 查询客户地址。
     *
     * @param customerId 客户 ID
     * @param actor 当前操作人
     * @return 地址列表
     */
    List<CustomerAddressView> listAddresses(Long customerId, CrmActor actor);

    /**
     * 新建客户地址。
     *
     * @param customerId 客户 ID
     * @param command 地址命令
     * @param actor 当前操作人
     * @return 地址视图
     */
    CustomerAddressView createAddress(Long customerId, AddressSaveCommand command, CrmActor actor);

    /**
     * 编辑客户地址。
     *
     * @param addressId 地址 ID
     * @param command 地址命令
     * @param actor 当前操作人
     * @return 地址视图
     */
    CustomerAddressView updateAddress(Long addressId, AddressSaveCommand command, CrmActor actor);

    /**
     * 删除客户地址。
     *
     * @param addressId 地址 ID
     * @param actor 当前操作人
     */
    void deleteAddress(Long addressId, CrmActor actor);

    /**
     * 查询客户画像。
     *
     * @param customerId 客户 ID
     * @param actor 当前操作人
     * @return 画像视图
     */
    CustomerProfileView getProfile(Long customerId, CrmActor actor);

    /**
     * 保存客户画像。
     *
     * @param customerId 客户 ID
     * @param command 画像命令
     * @param actor 当前操作人
     * @return 画像视图
     */
    CustomerProfileView saveProfile(Long customerId, ProfileSaveCommand command, CrmActor actor);

    /**
     * 客户 360 聚合。
     *
     * @param customerId 客户 ID
     * @param actor 当前操作人
     * @return 360 视图
     */
    Customer360View customer360(Long customerId, CrmActor actor);
}
