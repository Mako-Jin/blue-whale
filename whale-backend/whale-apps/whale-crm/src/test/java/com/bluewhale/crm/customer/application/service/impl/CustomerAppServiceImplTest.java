package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.command.ContactSaveCommand;
import com.bluewhale.crm.customer.application.command.CustomerSaveCommand;
import com.bluewhale.crm.customer.application.command.CustomerStatusCommand;
import com.bluewhale.crm.customer.application.command.ProfileSaveCommand;
import com.bluewhale.crm.customer.application.view.ContactView;
import com.bluewhale.crm.customer.application.view.Customer360View;
import com.bluewhale.crm.customer.application.view.CustomerProfileView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.application.view.DuplicateCandidateView;
import com.bluewhale.crm.customer.domain.model.Contact;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerProfile;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.customer.domain.model.CustomerStatus;
import com.bluewhale.crm.customer.domain.model.DecisionChainEntry;
import com.bluewhale.crm.customer.domain.model.PoolStatus;
import com.bluewhale.crm.customer.domain.port.CustomerOpportunityPort;
import com.bluewhale.crm.customer.domain.repository.CustomerAddressRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerContactRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerFollowUpRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerPoolRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerProfileRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CustomerAppServiceImpl} 单元测试：门禁、数据范围、查重、编码、主数据唯一性与 360 降级。
 *
 * @author BlueWhale
 */
class CustomerAppServiceImplTest {

    private CustomerRepository customerRepository;

    private CustomerContactRepository contactRepository;

    private CustomerAddressRepository addressRepository;

    private CustomerProfileRepository profileRepository;

    private CustomerFollowUpRepository followUpRepository;

    private CustomerPoolRepository poolRepository;

    private CustomerOpportunityPort opportunityPort;

    private CrmProperties properties;

    private CustomerAppServiceImpl service;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        contactRepository = mock(CustomerContactRepository.class);
        addressRepository = mock(CustomerAddressRepository.class);
        profileRepository = mock(CustomerProfileRepository.class);
        followUpRepository = mock(CustomerFollowUpRepository.class);
        poolRepository = mock(CustomerPoolRepository.class);
        opportunityPort = mock(CustomerOpportunityPort.class);
        properties = new CrmProperties();
        service = new CustomerAppServiceImpl(customerRepository, contactRepository, addressRepository,
                profileRepository, followUpRepository, poolRepository, opportunityPort, properties);
    }

    @Test
    @DisplayName("门禁关闭：所有业务接口拒绝访问")
    void shouldRejectWhenGateClosed() {
        properties.setDemandGatePassed(false);
        BizException exception = assertThrows(BizException.class,
                () -> service.page(new CustomerQuery(), sales(7L)));
        assertEquals(CrmErrorCode.GATE_CLOSED, exception.getCode());
    }

    @Test
    @DisplayName("新建客户：系统生成 CUS-年-流水编码，税号按权限脱敏")
    void shouldCreateCustomerWithGeneratedCode() {
        when(customerRepository.findMaxCode(anyString())).thenReturn(Optional.empty());
        CustomerView view = service.create(customerCommand("蓝鲸科技有限公司", "91330100MA2ABCDE"), sales(7L));

        assertEquals("CUS-" + LocalDate.now().getYear() + "-000001", view.getCustomerCode());
        assertEquals("9133****BCDE", view.getTaxNo());
        assertEquals(7L, view.getOwnerId());
        assertEquals(CustomerStatus.ACTIVE.name(), view.getStatus());
        assertEquals("C", view.getLevel());
        assertEquals(PoolStatus.OWNED.name(), view.getPoolStatus());
        verify(poolRepository).insertOwnerLog(any());
    }

    @Test
    @DisplayName("新建客户：在最大编码基础上递增流水")
    void shouldUseNextSequenceFromMaxCode() {
        String prefix = "CUS-" + LocalDate.now().getYear() + "-";
        when(customerRepository.findMaxCode(prefix)).thenReturn(Optional.of(prefix + "000005"));
        CustomerView view = service.create(customerCommand("蓝鲸科技", null), sales(7L));
        assertEquals(prefix + "000006", view.getCustomerCode());
    }

    @Test
    @DisplayName("新建客户：名称 / 税号 / 域名硬冲突直接阻断")
    void shouldRejectHardDuplicate() {
        Customer existing = existingCustomer(1L, 7L);
        existing.setName("蓝鲸科技");
        when(customerRepository.findHardDuplicates(any(), any(), any(), any())).thenReturn(List.of(existing));

        BizException exception = assertThrows(BizException.class,
                () -> service.create(customerCommand("蓝鲸科技有限公司", null), sales(7L)));
        assertEquals(CrmErrorCode.CUSTOMER_HARD_DUPLICATE, exception.getCode());
        assertTrue(exception.getMessage().contains("蓝鲸科技"));
        verify(customerRepository, never()).insert(any());
    }

    @Test
    @DisplayName("新建客户：全量数据范围可指定负责人，销售只能归属本人")
    void shouldRespectOwnerScopeOnCreate() {
        CustomerSaveCommand command = customerCommand("蓝鲸科技", null);
        command.setOwnerId(99L);

        assertEquals(99L, service.create(command, manager(1L)).getOwnerId());
        assertEquals(7L, service.create(customerCommand("蓝鲸科技", null), sales(7L)).getOwnerId());
    }

    @Test
    @DisplayName("分页查询：非全量角色强制按本人过滤")
    void shouldApplyDataScopeOnPage() {
        when(customerRepository.count(any())).thenReturn(1L);
        when(customerRepository.page(any())).thenReturn(List.of(existingCustomer(1L, 7L)));
        CustomerQuery query = new CustomerQuery();

        PageResult<CustomerView> result = service.page(query, sales(7L));

        assertEquals(7L, query.getOwnerId());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("分页查询：销售主管可见全量，不改写负责人条件")
    void shouldKeepQueryForAllDataScope() {
        when(customerRepository.count(any())).thenReturn(0L);
        when(customerRepository.page(any())).thenReturn(List.of());
        CustomerQuery query = new CustomerQuery();

        service.page(query, manager(1L));

        assertNull(query.getOwnerId());
    }

    @Test
    @DisplayName("详情 / 编辑 / 删除：越权一律按客户不存在处理")
    void shouldHideOtherOwnersCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer(1L, 99L)));

        BizException exception = assertThrows(BizException.class, () -> service.detail(1L, sales(7L)));
        assertEquals(CrmErrorCode.CUSTOMER_NOT_FOUND, exception.getCode());
        assertThrows(BizException.class, () -> service.delete(1L, sales(7L)));
    }

    @Test
    @DisplayName("详情：返回档案、联系人、地址与空画像占位")
    void shouldBuildDetail() {
        Customer customer = existingCustomer(1L, 1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(profileRepository.findByCustomerId(1L)).thenReturn(Optional.empty());

        var detail = service.detail(1L, manager(1L));

        assertEquals(1L, detail.getCustomer().getId());
        assertEquals(0, detail.getContacts().size());
        assertEquals(0, detail.getAddresses().size());
        assertEquals(0, detail.getProfile().getVersion());
        assertTrue(detail.getProfile().getDecisionChain().isEmpty());
    }

    @Test
    @DisplayName("客户已合并：编辑与状态变更都被拒绝")
    void shouldRejectMergedCustomerWrites() {
        Customer customer = existingCustomer(1L, 1L);
        customer.setStatus(CustomerStatus.MERGED.name());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        BizException exception = assertThrows(BizException.class,
                () -> service.update(1L, customerCommand("蓝鲸科技", null), manager(1L)));
        assertEquals(CrmErrorCode.CUSTOMER_MERGED_READONLY, exception.getCode());

        CustomerStatusCommand command = new CustomerStatusCommand();
        command.setStatus(CustomerStatus.MERGED.name());
        exception = assertThrows(BizException.class, () -> service.changeStatus(1L, command, manager(1L)));
        assertEquals(CrmErrorCode.CUSTOMER_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("新增联系人：客户首个联系人自动成为主联系人")
    void shouldMakeFirstContactPrimary() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer(1L, 1L)));

        ContactView view = service.createContact(1L, contactCommand("张伟", null), manager(1L));

        assertTrue(view.getPrimary());
        verify(contactRepository).clearPrimary(1L, null);
        verify(contactRepository).insert(any());
    }

    @Test
    @DisplayName("新增联系人：电话 / 邮箱疑似重复需确认后放行")
    void shouldRequireConfirmationForSoftDuplicate() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer(1L, 1L)));
        when(contactRepository.findCustomerIdsByNormalized(any(), any(), anyLong())).thenReturn(List.of(2L));

        BizException exception = assertThrows(BizException.class,
                () -> service.createContact(1L, contactCommand("张伟", "13800138000"), manager(1L)));
        assertEquals(CrmErrorCode.CUSTOMER_SOFT_DUPLICATE, exception.getCode());

        ContactSaveCommand confirmed = contactCommand("张伟", "13800138000");
        confirmed.setConfirmedDuplicate(true);
        assertNotNull(service.createContact(1L, confirmed, manager(1L)));
        verify(contactRepository, times(1)).insert(any());
    }

    @Test
    @DisplayName("编辑联系人：主联系人不能直接取消")
    void shouldRejectPrimaryDemotion() {
        Contact contact = new Contact();
        contact.setId(1L);
        contact.setCustomerId(1L);
        contact.setPrimary(true);
        when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer(1L, 1L)));

        BizException exception = assertThrows(BizException.class,
                () -> service.updateContact(1L, contactCommand("张伟", null), manager(1L)));
        assertEquals(CrmErrorCode.CONTACT_PRIMARY_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("删除联系人：主联系人不能直接删除")
    void shouldRejectPrimaryDelete() {
        Contact contact = new Contact();
        contact.setId(1L);
        contact.setCustomerId(1L);
        contact.setPrimary(true);
        when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer(1L, 1L)));

        BizException exception = assertThrows(BizException.class, () -> service.deleteContact(1L, manager(1L)));
        assertEquals(CrmErrorCode.CONTACT_PRIMARY_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("保存画像：决策链联系人必须属于当前客户")
    void shouldRejectDecisionChainOutOfCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer(1L, 1L)));
        when(contactRepository.belongsTo(5L, 1L)).thenReturn(false);

        ProfileSaveCommand command = new ProfileSaveCommand();
        DecisionChainEntry entry = new DecisionChainEntry();
        entry.setContactId(5L);
        command.setDecisionChain(List.of(entry));

        BizException exception = assertThrows(BizException.class,
                () -> service.saveProfile(1L, command, manager(1L)));
        assertEquals(CrmErrorCode.PROFILE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("保存画像：版本号递增，敏感备注按权限脱敏")
    void shouldIncreaseProfileVersion() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer(1L, 1L)));
        CustomerProfile existing = new CustomerProfile();
        existing.setCustomerId(1L);
        existing.setVersion(2);
        when(profileRepository.findByCustomerId(1L)).thenReturn(Optional.of(existing));

        ProfileSaveCommand command = new ProfileSaveCommand();
        command.setSensitiveRemark("客户要求账期 60 天");
        CustomerProfileView view = service.saveProfile(1L, command, sales(1L));

        assertEquals(3, view.getVersion());
        assertEquals("******", view.getSensitiveRemark());
        verify(profileRepository).save(any());
    }

    @Test
    @DisplayName("查重：同时返回硬冲突与软冲突候选")
    void shouldReturnHardAndSoftCandidates() {
        Customer duplicate = existingCustomer(1L, 7L);
        duplicate.setNormalizedName("蓝鲸科技");
        when(customerRepository.findHardDuplicates(any(), any(), any(), any())).thenReturn(List.of(duplicate));
        when(contactRepository.findCustomerIdsByNormalized(any(), any(), any())).thenReturn(List.of(2L));
        when(customerRepository.findById(2L)).thenReturn(Optional.of(existingCustomer(2L, 7L)));

        PageResult<DuplicateCandidateView> result = service.duplicateCheck(
                "蓝鲸科技有限公司", null, null, "13800138000", null, null, manager(1L));

        assertEquals(2, result.getRecords().size());
        assertEquals("HARD", result.getRecords().get(0).getSeverity());
        assertEquals("名称", result.getRecords().get(0).getMatchedFields());
        assertEquals("SOFT", result.getRecords().get(1).getSeverity());
        assertEquals("电话", result.getRecords().get(1).getMatchedFields());
    }

    @Test
    @DisplayName("客户 360：商机端口异常时降级为空摘要并声明不可用")
    void shouldDegradeWhenOpportunityPortFails() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer(1L, 1L)));
        when(opportunityPort.summarize(1L)).thenThrow(new IllegalStateException("商机服务不可用"));

        Customer360View view = service.customer360(1L, manager(1L));

        assertNotNull(view.getOpportunitySummary());
        assertTrue(view.getUnavailableIntegrations().contains("商机摘要（读取失败，已降级为空）"));
        assertTrue(view.getUnavailableIntegrations().contains("合同（whale-contract 尚未联通）"));
        assertNotNull(view.getGeneratedAt());
    }

    @Test
    @DisplayName("客户 360：画像缺失时返回空画像占位")
    void shouldReturnEmptyProfileIn360() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existingCustomer(1L, 1L)));
        when(profileRepository.findByCustomerId(1L)).thenReturn(Optional.empty());

        Customer360View view = service.customer360(1L, manager(1L));

        assertEquals(0, view.getProfile().getVersion());
        assertFalse(view.getUnavailableIntegrations().isEmpty());
    }

    private static CrmActor sales(Long userId) {
        return new CrmActor(userId, "sales", Set.of("crm_sales"), Set.of(CrmPermissions.CUSTOMER_LIST));
    }

    private static CrmActor manager(Long userId) {
        return new CrmActor(userId, "manager", Set.of("crm_sales_manager"),
                Set.of(CrmPermissions.SENSITIVE_VIEW, CrmPermissions.CUSTOMER_LIST));
    }

    private static Customer existingCustomer(Long id, Long ownerId) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setName("蓝鲸科技");
        customer.setCustomerCode("CUS-2026-000001");
        customer.setStatus(CustomerStatus.ACTIVE.name());
        customer.setOwnerId(ownerId);
        customer.setLevel("C");
        customer.setPoolStatus(PoolStatus.OWNED.name());
        return customer;
    }

    private static CustomerSaveCommand customerCommand(String name, String taxNo) {
        CustomerSaveCommand command = new CustomerSaveCommand();
        command.setName(name);
        command.setTaxNo(taxNo);
        return command;
    }

    private static ContactSaveCommand contactCommand(String name, String phone) {
        ContactSaveCommand command = new ContactSaveCommand();
        command.setName(name);
        command.setPhone(phone);
        return command;
    }
}
