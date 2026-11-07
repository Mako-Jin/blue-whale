package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerMergeAppService;
import com.bluewhale.crm.customer.application.view.CustomerMergeResultView;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.domain.model.Contact;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerAddress;
import com.bluewhale.crm.customer.domain.model.CustomerMergeCommand;
import com.bluewhale.crm.customer.domain.model.CustomerMergePreview;
import com.bluewhale.crm.customer.domain.model.CustomerOwnerLog;
import com.bluewhale.crm.customer.domain.model.CustomerStatus;
import com.bluewhale.crm.customer.domain.port.MergeReferencePort;
import com.bluewhale.crm.customer.domain.repository.CustomerAddressRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerContactRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerFollowUpRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerPoolRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerProfileRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户合并应用服务实现（M1-4）。
 *
 * <p>合并只做引用重写与归属迁移，不删除任何数据。源客户合并后状态置为
 * {@code MERGED} 并记录 {@code mergedIntoId}，业务上下文通过
 * {@link MergeReferencePort} 在自身 infrastructure 层完成表级更新。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CustomerMergeAppServiceImpl implements CustomerMergeAppService {

    private final CustomerRepository customerRepository;

    private final CustomerContactRepository contactRepository;

    private final CustomerAddressRepository addressRepository;

    private final CustomerProfileRepository profileRepository;

    private final CustomerFollowUpRepository followUpRepository;

    private final CustomerPoolRepository poolRepository;

    private final CrmProperties properties;

    private final List<MergeReferencePort> mergeReferencePorts;

    @Override
    public CustomerMergePreview preview(CustomerMergeCommand command, CrmActor actor) {
        assertGate();
        validateCommand(command);
        Customer source = requireCustomer(command.getSourceId(), actor);
        Customer target = requireCustomer(command.getTargetId(), actor);
        if (source.merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGE_INVALID, "源客户已合并，不能重复预览");
        }
        if (target.merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGE_INVALID, "目标客户已合并，不能作为合并目标");
        }
        boolean sourcePrimary = hasPrimary(contactRepository.list(source.getId()));
        boolean targetPrimary = hasPrimary(contactRepository.list(target.getId()));
        CustomerMergePreview preview = new CustomerMergePreview();
        preview.setSourceId(source.getId());
        preview.setSourceCode(source.getCustomerCode());
        preview.setSourceName(source.getName());
        preview.setSourceStatus(source.getStatus());
        preview.setTargetId(target.getId());
        preview.setTargetCode(target.getCustomerCode());
        preview.setTargetName(target.getName());
        preview.setTargetStatus(target.getStatus());
        preview.setReferences(countReferences(source.getId(), target.getId()));
        preview.setWarnings(conflictWarnings(sourcePrimary, targetPrimary));
        return preview;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerMergeResultView merge(CustomerMergeCommand command, CrmActor actor) {
        assertGate();
        Long operatorId = actor.requireUserId();
        validateCommand(command);
        Customer source = requireCustomer(command.getSourceId(), actor);
        Customer target = requireCustomer(command.getTargetId(), actor);
        if (source.merged()) {
            if (target.getId().equals(source.getMergedIntoId())) {
                CustomerMergeResultView result = new CustomerMergeResultView();
                result.setSourceId(source.getId());
                result.setTargetId(target.getId());
                result.setIdempotent(true);
                result.setTarget(CustomerView.from(target, actor.canViewSensitive()));
                return result;
            }
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGE_INVALID, "源客户已合并到其他客户");
        }
        if (target.merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGE_INVALID, "目标客户已合并，不能作为合并目标");
        }

        List<Contact> sourceContacts = contactRepository.list(source.getId());
        List<Contact> targetContacts = contactRepository.list(target.getId());
        boolean sourcePrimary = hasPrimary(sourceContacts);
        boolean targetPrimary = hasPrimary(targetContacts);
        List<String> warnings = conflictWarnings(sourcePrimary, targetPrimary);
        Map<String, Long> references = rewriteReferences(source.getId(), target.getId());

        int migratedContacts = migrateContacts(sourceContacts, target.getId(), targetPrimary,
                Boolean.TRUE.equals(command.getConfirmPrimaryContactConflict()));
        int migratedAddresses = migrateAddresses(source.getId(), target.getId());
        boolean profileMigrated = migrateProfile(source.getId(), target.getId());
        int migratedFollowUps = followUpRepository.reassignCustomer(source.getId(), target.getId());

        source.setStatus(CustomerStatus.MERGED.name());
        source.setMergedIntoId(target.getId());
        customerRepository.update(source);
        writeOwnerLog(target.getId(), "MERGE", source.getOwnerId(), target.getOwnerId(), operatorId,
                mergeRemark(source, command.getOperatorNote()));

        CustomerMergeResultView result = new CustomerMergeResultView();
        result.setSourceId(source.getId());
        result.setTargetId(target.getId());
        result.setTarget(CustomerView.from(customerRepository.findById(target.getId()).orElse(target),
                actor.canViewSensitive()));
        result.setReferences(references);
        result.setMigratedContacts(migratedContacts);
        result.setMigratedAddresses(migratedAddresses);
        result.setMigratedFollowUps(migratedFollowUps);
        result.setProfileMigrated(profileMigrated);
        result.setWarnings(warnings);
        return result;
    }

    /**
     * 迁移联系人：源客户联系人全部改挂目标客户；主联系人按目标现状降级或保留。
     *
     * @param sourceContacts 源联系人
     * @param targetId 目标客户 ID
     * @param targetHasPrimary 目标是否已有主联系人
     * @param confirm 是否确认保留源主联系人
     * @return 迁移数量
     */
    private int migrateContacts(List<Contact> sourceContacts, Long targetId,
                                boolean targetHasPrimary, boolean confirm) {
        int migrated = 0;
        for (Contact contact : sourceContacts) {
            contact.setCustomerId(targetId);
            if (Boolean.TRUE.equals(contact.getPrimary())) {
                contact.setPrimary(!targetHasPrimary && confirm);
            }
            contactRepository.update(contact);
            migrated++;
        }
        return migrated;
    }

    /**
     * 迁移地址：改挂目标客户，并避免默认收货 / 账单地址冲突。
     *
     * @param sourceId 源客户 ID
     * @param targetId 目标客户 ID
     * @return 迁移数量
     */
    private int migrateAddresses(Long sourceId, Long targetId) {
        List<CustomerAddress> targetAddresses = addressRepository.list(targetId);
        boolean targetShipping = targetAddresses.stream()
                .anyMatch(address -> Boolean.TRUE.equals(address.getDefaultShipping()));
        boolean targetBilling = targetAddresses.stream()
                .anyMatch(address -> Boolean.TRUE.equals(address.getDefaultBilling()));
        List<CustomerAddress> sourceAddresses = addressRepository.list(sourceId);
        for (CustomerAddress address : sourceAddresses) {
            address.setCustomerId(targetId);
            if (targetShipping && Boolean.TRUE.equals(address.getDefaultShipping())) {
                address.setDefaultShipping(false);
            }
            if (targetBilling && Boolean.TRUE.equals(address.getDefaultBilling())) {
                address.setDefaultBilling(false);
            }
            addressRepository.update(address);
        }
        return sourceAddresses.size();
    }

    /**
     * 迁移画像：仅当目标客户尚无画像时整体改归属，避免覆盖目标已有画像。
     *
     * @param sourceId 源客户 ID
     * @param targetId 目标客户 ID
     * @return 是否迁移
     */
    private boolean migrateProfile(Long sourceId, Long targetId) {
        if (profileRepository.findByCustomerId(targetId).isPresent()) {
            return false;
        }
        if (profileRepository.findByCustomerId(sourceId).isEmpty()) {
            return false;
        }
        profileRepository.reassignCustomer(sourceId, targetId);
        return true;
    }

    /**
     * 汇总各上下文引用数量。
     *
     * @param sourceId 源客户 ID
     * @param targetId 目标客户 ID
     * @return 引用数量
     */
    private Map<String, Long> countReferences(Long sourceId, Long targetId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MergeReferencePort port : mergeReferencePorts) {
            Map<String, Long> part = port.countReferences(sourceId, targetId);
            if (part != null) {
                part.forEach((key, value) -> counts.merge(key, value == null ? 0L : value, Long::sum));
            }
        }
        return counts;
    }

    /**
     * 汇总各上下文引用重写结果。
     *
     * @param sourceId 源客户 ID
     * @param targetId 目标客户 ID
     * @return 重写数量
     */
    private Map<String, Long> rewriteReferences(Long sourceId, Long targetId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MergeReferencePort port : mergeReferencePorts) {
            Map<String, Long> part = port.rewriteReferences(sourceId, targetId);
            if (part != null) {
                part.forEach((key, value) -> counts.merge(key, value == null ? 0L : value, Long::sum));
            }
        }
        return counts;
    }

    /**
     * 判断联系人列表中是否存在主联系人。
     *
     * @param contacts 联系人列表
     * @return 存在返回 true
     */
    private boolean hasPrimary(List<Contact> contacts) {
        return contacts.stream().anyMatch(contact -> Boolean.TRUE.equals(contact.getPrimary()));
    }

    /**
     * 构建合并冲突提示。
     *
     * @param sourcePrimary 源是否有主联系人
     * @param targetPrimary 目标是否有主联系人
     * @return 提示列表
     */
    private List<String> conflictWarnings(boolean sourcePrimary, boolean targetPrimary) {
        List<String> warnings = new ArrayList<>();
        if (sourcePrimary && targetPrimary) {
            warnings.add("源客户与目标客户均有主联系人，合并后源主联系人将降级为非主联系人");
        } else if (sourcePrimary) {
            warnings.add("目标客户暂无主联系人，确认后源客户主联系人可保留为主联系人");
        }
        return warnings;
    }

    /**
     * 构建负责人留痕备注。
     *
     * @param source 源客户
     * @param operatorNote 操作备注
     * @return 备注文本
     */
    private String mergeRemark(Customer source, String operatorNote) {
        String remark = "合并源客户 " + source.getCustomerCode() + "(" + source.getId() + ")";
        return StringUtils.hasText(operatorNote) ? remark + "：" + operatorNote.trim() : remark;
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
     * 校验合并命令。
     *
     * @param command 合并命令
     */
    private void validateCommand(CustomerMergeCommand command) {
        if (command == null || command.getSourceId() == null || command.getTargetId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGE_INVALID, "源客户与目标客户均不能为空");
        }
        if (command.getSourceId().equals(command.getTargetId())) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGE_INVALID);
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
     * 校验门禁开关。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }
}
