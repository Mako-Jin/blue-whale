package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.command.AddressSaveCommand;
import com.bluewhale.crm.customer.application.command.ContactSaveCommand;
import com.bluewhale.crm.customer.application.command.CustomerLevelCommand;
import com.bluewhale.crm.customer.application.command.CustomerSaveCommand;
import com.bluewhale.crm.customer.application.command.CustomerStatusCommand;
import com.bluewhale.crm.customer.application.command.ProfileSaveCommand;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.ContactView;
import com.bluewhale.crm.customer.application.view.Customer360View;
import com.bluewhale.crm.customer.application.view.CustomerAddressView;
import com.bluewhale.crm.customer.application.view.CustomerDetailView;
import com.bluewhale.crm.customer.application.view.CustomerProfileView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.application.view.DuplicateCandidateView;
import com.bluewhale.crm.customer.application.view.FollowUpView;
import com.bluewhale.crm.customer.application.view.OwnerLogView;
import com.bluewhale.crm.customer.domain.model.Contact;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerAddress;
import com.bluewhale.crm.customer.domain.model.CustomerLevel;
import com.bluewhale.crm.customer.domain.model.CustomerNormalizer;
import com.bluewhale.crm.customer.domain.model.CustomerOpportunitySummary;
import com.bluewhale.crm.customer.domain.model.CustomerOwnerLog;
import com.bluewhale.crm.customer.domain.model.CustomerProfile;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.customer.domain.model.CustomerStatus;
import com.bluewhale.crm.customer.domain.model.DecisionChainEntry;
import com.bluewhale.crm.customer.domain.model.DuplicateCandidate;
import com.bluewhale.crm.customer.domain.model.PoolStatus;
import com.bluewhale.crm.customer.domain.port.CustomerOpportunityPort;
import com.bluewhale.crm.customer.domain.repository.CustomerAddressRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerContactRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerFollowUpRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerPoolRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerProfileRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 客户主数据应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CustomerAppServiceImpl implements CustomerAppService {

    /** 查重候选上限 */
    private static final int DUPLICATE_LIMIT = 20;

    /** 360 最近跟进条数 */
    private static final int RECENT_FOLLOW_UP_LIMIT = 10;

    /** 360 负责人变更条数 */
    private static final int OWNER_LOG_LIMIT = 10;

    /** 编码年份格式 */
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    /** 编码流水长度 */
    private static final int CODE_SEQUENCE_LENGTH = 6;

    private final CustomerRepository customerRepository;

    private final CustomerContactRepository contactRepository;

    private final CustomerAddressRepository addressRepository;

    private final CustomerProfileRepository profileRepository;

    private final CustomerFollowUpRepository followUpRepository;

    private final CustomerPoolRepository poolRepository;

    private final CustomerOpportunityPort opportunityPort;

    private final CrmProperties properties;

    @Override
    public PageResult<CustomerView> page(CustomerQuery query, CrmActor actor) {
        assertGate();
        applyDataScope(query, actor);
        long total = customerRepository.count(query);
        List<CustomerView> views = customerRepository.page(query).stream()
                .map(customer -> CustomerView.from(customer, actor.canViewSensitive()))
                .toList();
        return PageResult.of(views, total, query.getCurrent(), query.getSize());
    }

    @Override
    public CustomerDetailView detail(Long id, CrmActor actor) {
        assertGate();
        Customer customer = requireCustomer(id, actor);
        CustomerDetailView detail = new CustomerDetailView();
        detail.setCustomer(CustomerView.from(customer, actor.canViewSensitive()));
        detail.setContacts(contactRepository.list(customer.getId()).stream()
                .map(contact -> ContactView.from(contact, actor.canViewSensitive())).toList());
        detail.setAddresses(addressRepository.list(customer.getId()).stream()
                .map(address -> CustomerAddressView.from(address, actor.canViewSensitive())).toList());
        detail.setProfile(profileRepository.findByCustomerId(customer.getId())
                .map(profile -> CustomerProfileView.from(profile, actor.canViewSensitive()))
                .orElseGet(() -> CustomerProfileView.empty(customer.getId())));
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerView create(CustomerSaveCommand command, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        String name = requireText(command.getName(), "客户名称不能为空");
        String code = StringUtils.hasText(command.getCustomerCode())
                ? command.getCustomerCode().trim() : nextCode();
        if (StringUtils.hasText(command.getCustomerCode())) {
            requireCodeAvailable(code, null);
        }
        String normalizedName = CustomerNormalizer.normalizeName(name);
        String normalizedTaxNo = CustomerNormalizer.normalizeTaxNo(command.getTaxNo());
        String normalizedDomain = CustomerNormalizer.normalizeDomain(command.getWebsite());
        rejectHardDuplicates(normalizedName, normalizedTaxNo, normalizedDomain, null);

        Customer customer = new Customer();
        customer.setCustomerCode(code);
        customer.setName(name);
        customer.setShortName(trimToNull(command.getShortName()));
        customer.setStatus(CustomerStatus.ACTIVE.name());
        customer.setOwnerId(actor.allDataScope() && command.getOwnerId() != null
                ? command.getOwnerId() : userId);
        customer.setDeptId(command.getDeptId());
        customer.setLevel(resolveLevel(command.getLevel()));
        customer.setSource(trimToNull(command.getSource()));
        customer.setIndustry(trimToNull(command.getIndustry()));
        customer.setAddress(trimToNull(command.getAddress()));
        customer.setTags(trimToNull(command.getTags()));
        customer.setWebsite(trimToNull(command.getWebsite()));
        customer.setScale(trimToNull(command.getScale()));
        customer.setTaxNo(trimToNull(command.getTaxNo()));
        customer.setProvince(trimToNull(command.getProvince()));
        customer.setCity(trimToNull(command.getCity()));
        customer.setRegion(trimToNull(command.getRegion()));
        customer.setNormalizedName(normalizedName);
        customer.setNormalizedTaxNo(normalizedTaxNo);
        customer.setNormalizedDomain(normalizedDomain);
        customer.setVersion(0);
        customer.setPoolStatus(PoolStatus.OWNED.name());
        try {
            customerRepository.insert(customer);
        } catch (DuplicateKeyException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_CODE_CONFLICT);
        }
        writeOwnerLog(customer.getId(), "CREATE", null, customer.getOwnerId(), userId, null);
        return CustomerView.from(customer, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerView update(Long id, CustomerSaveCommand command, CrmActor actor) {
        assertGate();
        Customer customer = requireCustomer(id, actor);
        if (customer.merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGED_READONLY);
        }
        String name = requireText(command.getName(), "客户名称不能为空");
        String code = StringUtils.hasText(command.getCustomerCode())
                ? command.getCustomerCode().trim() : customer.getCustomerCode();
        if (!code.equals(customer.getCustomerCode())) {
            requireCodeAvailable(code, customer.getId());
        }
        String normalizedName = CustomerNormalizer.normalizeName(name);
        String normalizedTaxNo = CustomerNormalizer.normalizeTaxNo(command.getTaxNo());
        String normalizedDomain = CustomerNormalizer.normalizeDomain(command.getWebsite());
        rejectHardDuplicates(normalizedName, normalizedTaxNo, normalizedDomain, customer.getId());

        customer.setCustomerCode(code);
        customer.setName(name);
        customer.setShortName(trimToNull(command.getShortName()));
        customer.setSource(trimToNull(command.getSource()));
        customer.setIndustry(trimToNull(command.getIndustry()));
        customer.setAddress(trimToNull(command.getAddress()));
        customer.setTags(trimToNull(command.getTags()));
        customer.setWebsite(trimToNull(command.getWebsite()));
        customer.setScale(trimToNull(command.getScale()));
        customer.setTaxNo(trimToNull(command.getTaxNo()));
        customer.setProvince(trimToNull(command.getProvince()));
        customer.setCity(trimToNull(command.getCity()));
        customer.setRegion(trimToNull(command.getRegion()));
        customer.setNormalizedName(normalizedName);
        customer.setNormalizedTaxNo(normalizedTaxNo);
        customer.setNormalizedDomain(normalizedDomain);
        if (StringUtils.hasText(command.getLevel())) {
            customer.changeLevel(command.getLevel());
        }
        customerRepository.update(customer);
        return CustomerView.from(customer, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerView changeStatus(Long id, CustomerStatusCommand command, CrmActor actor) {
        assertGate();
        Customer customer = requireCustomer(id, actor);
        if (customer.merged()) {
            CustomerStatus target = CustomerStatus.from(command.getStatus());
            if (target == CustomerStatus.MERGED) {
                throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_STATE_CONFLICT, "合并状态只能由合并流程写入");
            }
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGED_READONLY);
        }
        customer.changeStatus(command.getStatus());
        customerRepository.update(customer);
        return CustomerView.from(customer, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerView changeLevel(Long id, CustomerLevelCommand command, CrmActor actor) {
        assertGate();
        Customer customer = requireCustomer(id, actor);
        customer.changeLevel(command.getLevel());
        customerRepository.update(customer);
        return CustomerView.from(customer, actor.canViewSensitive());
    }

    @Override
    public PageResult<DuplicateCandidateView> duplicateCheck(String name, String website, String taxNo,
                                                             String phone, String email, Long excludeId,
                                                             CrmActor actor) {
        assertGate();
        String normalizedName = CustomerNormalizer.normalizeName(name);
        String normalizedTaxNo = CustomerNormalizer.normalizeTaxNo(taxNo);
        String normalizedDomain = CustomerNormalizer.normalizeDomain(website);
        String normalizedPhone = CustomerNormalizer.normalizePhone(phone);
        String normalizedEmail = CustomerNormalizer.normalizeEmail(email);
        List<DuplicateCandidate> candidates = new ArrayList<>();
        customerRepository.findHardDuplicates(normalizedName, normalizedTaxNo, normalizedDomain, excludeId)
                .forEach(customer -> candidates.add(hardCandidate(customer, normalizedName,
                        normalizedTaxNo, normalizedDomain)));
        contactRepository.findCustomerIdsByNormalized(normalizedPhone, normalizedEmail, excludeId).stream()
                .limit(DUPLICATE_LIMIT)
                .forEach(customerId -> customerRepository.findById(customerId)
                        .ifPresent(customer -> candidates.add(
                                softCandidate(customer, normalizedPhone, normalizedEmail))));
        List<DuplicateCandidateView> views = candidates.stream()
                .limit(DUPLICATE_LIMIT)
                .map(DuplicateCandidateView::from)
                .toList();
        return PageResult.of(views, views.size(), 1, DUPLICATE_LIMIT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, CrmActor actor) {
        assertGate();
        Customer customer = requireCustomer(id, actor);
        customerRepository.delete(customer.getId());
    }

    @Override
    public List<ContactView> listContacts(Long customerId, CrmActor actor) {
        assertGate();
        requireCustomer(customerId, actor);
        return contactRepository.list(customerId).stream()
                .map(contact -> ContactView.from(contact, actor.canViewSensitive()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContactView createContact(Long customerId, ContactSaveCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        Customer customer = requireCustomer(customerId, actor);
        assertWritable(customer);
        String name = requireText(command.getName(), "联系人姓名不能为空");
        String normalizedPhone = CustomerNormalizer.normalizePhone(command.getPhone());
        String normalizedEmail = CustomerNormalizer.normalizeEmail(command.getEmail());
        rejectSoftDuplicates(normalizedPhone, normalizedEmail, customerId, command.getConfirmedDuplicate());

        boolean existsPrimary = contactRepository.list(customerId).stream()
                .anyMatch(contact -> Boolean.TRUE.equals(contact.getPrimary()));
        boolean primary = Boolean.TRUE.equals(command.getPrimary()) || !existsPrimary;
        if (primary) {
            contactRepository.clearPrimary(customerId, null);
        }
        Contact contact = new Contact();
        contact.setCustomerId(customerId);
        contact.setName(name);
        contact.setPhone(trimToNull(command.getPhone()));
        contact.setEmail(trimToNull(command.getEmail()));
        contact.setWechat(trimToNull(command.getWechat()));
        contact.setTitle(trimToNull(command.getTitle()));
        contact.setDecisionRole(trimToNull(command.getDecisionRole()));
        contact.setDecisionMaker(Boolean.TRUE.equals(command.getDecisionMaker()));
        contact.setPrimary(primary);
        contact.setStatus(StringUtils.hasText(command.getStatus())
                ? command.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE");
        contact.setNormalizedPhone(normalizedPhone);
        contact.setNormalizedEmail(normalizedEmail);
        contact.setVersion(0);
        contactRepository.insert(contact);
        return ContactView.from(contact, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ContactView updateContact(Long contactId, ContactSaveCommand command, CrmActor actor) {
        assertGate();
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> CrmErrorCode.exception(CrmErrorCode.CONTACT_NOT_FOUND));
        Customer customer = requireCustomer(contact.getCustomerId(), actor);
        assertWritable(customer);
        String name = requireText(command.getName(), "联系人姓名不能为空");
        String normalizedPhone = CustomerNormalizer.normalizePhone(command.getPhone());
        String normalizedEmail = CustomerNormalizer.normalizeEmail(command.getEmail());
        rejectSoftDuplicates(normalizedPhone, normalizedEmail, customer.getId(),
                command.getConfirmedDuplicate());

        boolean wasPrimary = Boolean.TRUE.equals(contact.getPrimary());
        boolean wantPrimary = Boolean.TRUE.equals(command.getPrimary());
        if (wasPrimary && !wantPrimary) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTACT_PRIMARY_CONFLICT,
                    "主联系人不能直接取消，请先指定其他联系人为主联系人");
        }
        if (!wasPrimary && wantPrimary) {
            contactRepository.clearPrimary(customer.getId(), contact.getId());
        }
        contact.setName(name);
        contact.setPhone(trimToNull(command.getPhone()));
        contact.setEmail(trimToNull(command.getEmail()));
        contact.setWechat(trimToNull(command.getWechat()));
        contact.setTitle(trimToNull(command.getTitle()));
        contact.setDecisionRole(trimToNull(command.getDecisionRole()));
        contact.setDecisionMaker(Boolean.TRUE.equals(command.getDecisionMaker()));
        contact.setPrimary(wasPrimary || wantPrimary);
        if (StringUtils.hasText(command.getStatus())) {
            contact.setStatus(command.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        contact.setNormalizedPhone(normalizedPhone);
        contact.setNormalizedEmail(normalizedEmail);
        contactRepository.update(contact);
        return ContactView.from(contact, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteContact(Long contactId, CrmActor actor) {
        assertGate();
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> CrmErrorCode.exception(CrmErrorCode.CONTACT_NOT_FOUND));
        requireCustomer(contact.getCustomerId(), actor);
        if (Boolean.TRUE.equals(contact.getPrimary())) {
            throw CrmErrorCode.exception(CrmErrorCode.CONTACT_PRIMARY_CONFLICT,
                    "主联系人不能直接删除，请先指定其他联系人为主联系人");
        }
        contactRepository.delete(contactId);
    }

    @Override
    public List<CustomerAddressView> listAddresses(Long customerId, CrmActor actor) {
        assertGate();
        requireCustomer(customerId, actor);
        return addressRepository.list(customerId).stream()
                .map(address -> CustomerAddressView.from(address, actor.canViewSensitive()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerAddressView createAddress(Long customerId, AddressSaveCommand command, CrmActor actor) {
        assertGate();
        Customer customer = requireCustomer(customerId, actor);
        assertWritable(customer);
        CustomerAddress address = new CustomerAddress();
        address.setCustomerId(customerId);
        fillAddress(address, command);
        boolean shipping = Boolean.TRUE.equals(address.getDefaultShipping());
        boolean billing = Boolean.TRUE.equals(address.getDefaultBilling());
        if (shipping || billing) {
            addressRepository.clearDefault(customerId, shipping, billing, null);
        }
        addressRepository.insert(address);
        return CustomerAddressView.from(address, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerAddressView updateAddress(Long addressId, AddressSaveCommand command, CrmActor actor) {
        assertGate();
        CustomerAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> CrmErrorCode.exception(CrmErrorCode.ADDRESS_NOT_FOUND));
        Customer customer = requireCustomer(address.getCustomerId(), actor);
        assertWritable(customer);
        fillAddress(address, command);
        boolean shipping = Boolean.TRUE.equals(address.getDefaultShipping());
        boolean billing = Boolean.TRUE.equals(address.getDefaultBilling());
        if (shipping || billing) {
            addressRepository.clearDefault(address.getCustomerId(), shipping, billing, addressId);
        }
        addressRepository.update(address);
        return CustomerAddressView.from(address, actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long addressId, CrmActor actor) {
        assertGate();
        CustomerAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> CrmErrorCode.exception(CrmErrorCode.ADDRESS_NOT_FOUND));
        requireCustomer(address.getCustomerId(), actor);
        addressRepository.delete(addressId);
    }

    @Override
    public CustomerProfileView getProfile(Long customerId, CrmActor actor) {
        assertGate();
        requireCustomer(customerId, actor);
        return profileRepository.findByCustomerId(customerId)
                .map(profile -> CustomerProfileView.from(profile, actor.canViewSensitive()))
                .orElseGet(() -> CustomerProfileView.empty(customerId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerProfileView saveProfile(Long customerId, ProfileSaveCommand command, CrmActor actor) {
        assertGate();
        Customer customer = requireCustomer(customerId, actor);
        assertWritable(customer);
        validateDecisionChain(customerId, command.getDecisionChain());
        CustomerProfile profile = profileRepository.findByCustomerId(customerId)
                .orElseGet(CustomerProfile::new);
        profile.setCustomerId(customerId);
        profile.setPreference(trimToNull(command.getPreference()));
        profile.setPainPoints(trimToNull(command.getPainPoints()));
        profile.setDecisionChain(command.getDecisionChain() == null
                ? List.of() : command.getDecisionChain());
        profile.setKeyPerson(trimToNull(command.getKeyPerson()));
        profile.setSensitiveRemark(trimToNull(command.getSensitiveRemark()));
        profile.increaseVersion();
        profileRepository.save(profile);
        return CustomerProfileView.from(profile, actor.canViewSensitive());
    }

    @Override
    public Customer360View customer360(Long customerId, CrmActor actor) {
        assertGate();
        Customer customer = requireCustomer(customerId, actor);
        boolean allowPlain = actor.canViewSensitive();
        Customer360View view = new Customer360View();
        view.setCustomer(CustomerView.from(customer, allowPlain));
        view.setContacts(contactRepository.list(customerId).stream()
                .map(contact -> ContactView.from(contact, allowPlain)).toList());
        view.setProfile(profileRepository.findByCustomerId(customerId)
                .map(profile -> CustomerProfileView.from(profile, allowPlain))
                .orElseGet(() -> CustomerProfileView.empty(customerId)));
        view.setRecentFollowUps(followUpRepository.listRecent(customerId, RECENT_FOLLOW_UP_LIMIT).stream()
                .map(FollowUpView::from)
                .toList());
        List<String> unavailable = new ArrayList<>();
        unavailable.add("合同（whale-contract 尚未联通）");
        unavailable.add("订单与回款（ERP 尚未联通）");
        unavailable.add("工单与满意度（服务上下文 M4 未交付）");
        try {
            view.setOpportunitySummary(opportunityPort.summarize(customerId));
        } catch (RuntimeException exception) {
            view.setOpportunitySummary(new CustomerOpportunitySummary());
            unavailable.add("商机摘要（读取失败，已降级为空）");
        }
        view.setOwnerLogs(poolRepository.listOwnerLogs(customerId, OWNER_LOG_LIMIT).stream()
                .map(OwnerLogView::from).toList());
        view.setUnavailableIntegrations(unavailable);
        view.setGeneratedAt(LocalDateTime.now());
        return view;
    }

    /**
     * 应用数据范围：非全量角色只能查看本人负责的客户。
     *
     * @param query 查询条件
     * @param actor 当前操作人
     */
    private void applyDataScope(CustomerQuery query, CrmActor actor) {
        if (!actor.allDataScope()) {
            query.setOwnerId(actor.getUserId() == null ? -1L : actor.getUserId());
        }
    }

    /**
     * 加载客户并校验数据范围；越权按不存在处理。
     *
     * @param id 客户 ID
     * @param actor 当前操作人
     * @return 客户
     */
    private Customer requireCustomer(Long id, CrmActor actor) {
        if (id == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "客户 ID 不能为空");
        }
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> CrmErrorCode.exception(CrmErrorCode.CUSTOMER_NOT_FOUND));
        if (!actor.allDataScope()) {
            Long userId = actor.getUserId();
            if (userId == null || !userId.equals(customer.getOwnerId())) {
                throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_NOT_FOUND);
            }
        }
        return customer;
    }

    /**
     * 校验客户可写（未合并）。
     *
     * @param customer 客户
     */
    private void assertWritable(Customer customer) {
        if (customer.merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGED_READONLY);
        }
    }

    /**
     * 校验门禁开关。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 校验客户编码可用。
     *
     * @param code 客户编码
     * @param excludeId 排除的客户 ID
     */
    private void requireCodeAvailable(String code, Long excludeId) {
        Optional<Customer> existing = customerRepository.findByCode(code);
        if (existing.isPresent() && !existing.get().getId().equals(excludeId)) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_CODE_CONFLICT);
        }
    }

    /**
     * 拒绝硬冲突客户。
     *
     * @param normalizedName 归一化名称
     * @param normalizedTaxNo 归一化税号
     * @param normalizedDomain 归一化域名
     * @param excludeId 排除的客户 ID
     */
    private void rejectHardDuplicates(String normalizedName, String normalizedTaxNo,
                                      String normalizedDomain, Long excludeId) {
        List<Customer> duplicates = customerRepository.findHardDuplicates(
                normalizedName, normalizedTaxNo, normalizedDomain, excludeId);
        if (!duplicates.isEmpty()) {
            String names = duplicates.stream().map(Customer::getName).distinct()
                    .limit(3).reduce((left, right) -> left + "、" + right).orElse("");
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_HARD_DUPLICATE,
                    "存在同名、同税号或同域名的客户：" + names);
        }
    }

    /**
     * 拒绝软冲突联系人（同电话 / 同邮箱）。
     *
     * @param normalizedPhone 归一化电话
     * @param normalizedEmail 归一化邮箱
     * @param customerId 当前客户 ID
     * @param confirmed 是否已确认
     */
    private void rejectSoftDuplicates(String normalizedPhone, String normalizedEmail,
                                      Long customerId, Boolean confirmed) {
        List<Long> others = contactRepository.findCustomerIdsByNormalized(
                normalizedPhone, normalizedEmail, customerId);
        if (!others.isEmpty() && !Boolean.TRUE.equals(confirmed)) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_SOFT_DUPLICATE);
        }
    }

    /**
     * 校验决策链联系人归属。
     *
     * @param customerId 客户 ID
     * @param chain 决策链
     */
    private void validateDecisionChain(Long customerId, List<DecisionChainEntry> chain) {
        if (chain == null) {
            return;
        }
        for (DecisionChainEntry entry : chain) {
            if (entry.getContactId() == null || !contactRepository.belongsTo(entry.getContactId(), customerId)) {
                throw CrmErrorCode.exception(CrmErrorCode.PROFILE_INVALID,
                        "决策链联系人必须属于当前客户");
            }
        }
    }

    /**
     * 填充地址字段。
     *
     * @param address 地址
     * @param command 命令
     */
    private void fillAddress(CustomerAddress address, AddressSaveCommand command) {
        String detail = requireText(command.getDetail(), "详细地址不能为空");
        address.setContactName(trimToNull(command.getContactName()));
        address.setPhone(trimToNull(command.getPhone()));
        address.setProvince(trimToNull(command.getProvince()));
        address.setCity(trimToNull(command.getCity()));
        address.setDistrict(trimToNull(command.getDistrict()));
        address.setDetail(detail);
        address.setDefaultShipping(Boolean.TRUE.equals(command.getDefaultShipping()));
        address.setDefaultBilling(Boolean.TRUE.equals(command.getDefaultBilling()));
        address.setStatus(StringUtils.hasText(command.getStatus())
                ? command.getStatus().trim().toUpperCase(Locale.ROOT) : "ACTIVE");
    }

    /**
     * 生成客户编码：{@code 前缀-年-6位流水}。
     *
     * @return 客户编码
     */
    private String nextCode() {
        String prefix = properties.getCustomerCodePrefix() + "-"
                + YEAR_FORMAT.format(LocalDate.now()) + "-";
        String maxCode = customerRepository.findMaxCode(prefix).orElse(prefix + "0".repeat(CODE_SEQUENCE_LENGTH));
        long next = 1L;
        try {
            next = Long.parseLong(maxCode.substring(maxCode.length() - CODE_SEQUENCE_LENGTH)) + 1;
        } catch (NumberFormatException exception) {
            next = 1L;
        }
        return prefix + String.format(Locale.ROOT, "%0" + CODE_SEQUENCE_LENGTH + "d", next);
    }

    /**
     * 构建硬冲突候选。
     *
     * @param customer 客户
     * @param normalizedName 归一化名称
     * @param normalizedTaxNo 归一化税号
     * @param normalizedDomain 归一化域名
     * @return 查重候选
     */
    private DuplicateCandidate hardCandidate(Customer customer, String normalizedName,
                                             String normalizedTaxNo, String normalizedDomain) {
        List<String> fields = new ArrayList<>();
        if (normalizedName != null && normalizedName.equals(customer.getNormalizedName())) {
            fields.add("名称");
        }
        if (normalizedTaxNo != null && normalizedTaxNo.equals(customer.getNormalizedTaxNo())) {
            fields.add("税号");
        }
        if (normalizedDomain != null && normalizedDomain.equals(customer.getNormalizedDomain())) {
            fields.add("域名");
        }
        DuplicateCandidate candidate = new DuplicateCandidate();
        candidate.setCustomer(customer);
        candidate.setSeverity("HARD");
        candidate.setMatchedFields(String.join(",", fields));
        return candidate;
    }

    /**
     * 构建软冲突候选。
     *
     * @param customer 客户
     * @param normalizedPhone 归一化电话
     * @param normalizedEmail 归一化邮箱
     * @return 查重候选
     */
    private DuplicateCandidate softCandidate(Customer customer, String normalizedPhone, String normalizedEmail) {
        List<String> fields = new ArrayList<>();
        if (normalizedPhone != null) {
            fields.add("电话");
        }
        if (normalizedEmail != null) {
            fields.add("邮箱");
        }
        DuplicateCandidate candidate = new DuplicateCandidate();
        candidate.setCustomer(customer);
        candidate.setSeverity("SOFT");
        candidate.setMatchedFields(String.join(",", fields));
        return candidate;
    }

    /**
     * 解析客户等级；为空时取配置默认值。
     *
     * @param level 等级
     * @return 规范等级
     */
    private String resolveLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return CustomerLevel.canonical(properties.getDefaultCustomerLevel());
        }
        return CustomerLevel.canonical(level);
    }

    /**
     * 写入负责人变更留痕。
     *
     * @param customerId 客户 ID
     * @param action 动作
     * @param fromOwnerId 原负责人
     * @param toOwnerId 新负责人
     * @param operatorId 操作人
     * @param remark 备注
     */
    private void writeOwnerLog(Long customerId, String action, Long fromOwnerId,
                               Long toOwnerId, Long operatorId, String remark) {
        CustomerOwnerLog log = new CustomerOwnerLog();
        log.setCustomerId(customerId);
        log.setAction(action);
        log.setFromOwnerId(fromOwnerId);
        log.setToOwnerId(toOwnerId);
        log.setOperatorId(operatorId);
        log.setRemark(remark);
        poolRepository.insertOwnerLog(log);
    }

    /**
     * 必填文本校验。
     *
     * @param value 文本
     * @param message 提示
     * @return 去空白文本
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, message);
        }
        return value.trim();
    }

    /**
     * 去空白；空串按 null 处理。
     *
     * @param value 文本
     * @return 去空白文本或 null
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
