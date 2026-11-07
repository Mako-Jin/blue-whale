package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerImportExportAppService;
import com.bluewhale.crm.customer.application.view.CustomerImportPreviewView;
import com.bluewhale.crm.customer.application.view.CustomerImportResultView;
import com.bluewhale.crm.customer.application.view.CustomerImportRowView;
import com.bluewhale.crm.customer.domain.model.Contact;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerCsv;
import com.bluewhale.crm.customer.domain.model.CustomerLevel;
import com.bluewhale.crm.customer.domain.model.CustomerNormalizer;
import com.bluewhale.crm.customer.domain.model.CustomerOwnerLog;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.customer.domain.model.CustomerStatus;
import com.bluewhale.crm.customer.domain.model.PoolStatus;
import com.bluewhale.crm.customer.domain.repository.CustomerContactRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerPoolRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CSV 客户导入导出应用服务实现（M1-9）。
 *
 * <p>导入执行与预检使用同一套逐行判定逻辑：预检只读；执行在事务内仅创建
 * {@code OK} 行，并对并发期间产生的唯一键冲突做跳过处理，保证重复导入幂等。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CustomerImportExportAppServiceImpl implements CustomerImportExportAppService {

    /** 行状态：通过 */
    private static final String STATUS_OK = "OK";

    /** 行状态：校验失败 */
    private static final String STATUS_INVALID = "INVALID";

    /** 行状态：重复 */
    private static final String STATUS_DUPLICATE = "DUPLICATE";

    /** 编码年份格式 */
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    /** 导出时间格式 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 编码流水长度 */
    private static final int CODE_SEQUENCE_LENGTH = 6;

    private final CustomerRepository customerRepository;

    private final CustomerContactRepository contactRepository;

    private final CustomerPoolRepository poolRepository;

    private final CrmProperties properties;

    @Override
    public CustomerImportPreviewView preview(String content, CrmActor actor) {
        assertGate();
        List<EvaluatedRow> evaluated = evaluate(content);
        CustomerImportPreviewView view = new CustomerImportPreviewView();
        view.setTotal(evaluated.size());
        view.setMaxRows(properties.getCustomerImportMaxRows());
        for (EvaluatedRow row : evaluated) {
            if (STATUS_OK.equals(row.status())) {
                view.setValid(view.getValid() + 1);
            } else if (STATUS_DUPLICATE.equals(row.status())) {
                view.setDuplicate(view.getDuplicate() + 1);
            } else {
                view.setInvalid(view.getInvalid() + 1);
            }
            view.getRows().add(toRowView(row));
        }
        return view;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerImportResultView execute(String content, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        List<EvaluatedRow> evaluated = evaluate(content);
        CustomerImportResultView result = new CustomerImportResultView();
        String prefix = properties.getCustomerCodePrefix() + "-"
                + YEAR_FORMAT.format(LocalDate.now()) + "-";
        long sequence = nextSequence(prefix);
        for (EvaluatedRow evaluatedRow : evaluated) {
            if (!STATUS_OK.equals(evaluatedRow.status())) {
                result.setSkipped(result.getSkipped() + 1);
                result.getRows().add(toRowView(evaluatedRow));
                continue;
            }
            CustomerCsv.Row row = evaluatedRow.row();
            try {
                Customer customer = buildCustomer(row, userId, actor, prefix + String.format(
                        Locale.ROOT, "%0" + CODE_SEQUENCE_LENGTH + "d", sequence));
                customerRepository.insert(customer);
                writeOwnerLog(customer.getId(), customer.getOwnerId(), userId);
                result.setCreated(result.getCreated() + 1);
                sequence++;
            } catch (DuplicateKeyException exception) {
                result.setSkipped(result.getSkipped() + 1);
                result.getRows().add(toRowView(evaluatedRow, "客户编码或唯一键冲突，已跳过"));
            } catch (RuntimeException exception) {
                result.setFailed(result.getFailed() + 1);
                result.getRows().add(toRowView(evaluatedRow, "写入失败：" + exception.getMessage()));
            }
        }
        return result;
    }

    @Override
    public String export(CustomerQuery query, CrmActor actor) {
        assertGate();
        CustomerQuery condition = query == null ? new CustomerQuery() : query;
        Long userId = actor.requireUserId();
        if (!actor.allDataScope()) {
            condition.setOwnerId(userId);
        }
        long total = customerRepository.count(condition);
        int maxRows = properties.getCustomerExportMaxRows();
        if (total > maxRows) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_EXPORT_INVALID,
                    "导出条数 " + total + " 超过单次上限 " + maxRows);
        }
        condition.setCurrent(1L);
        condition.setSize(maxRows);
        List<CustomerCsv.Row> rows = customerRepository.page(condition).stream()
                .map(customer -> toExportRow(customer))
                .toList();
        return CustomerCsv.export(rows, actor.canViewSensitive());
    }

    /**
     * 解析并逐行判定（只读，不落库）。
     *
     * @param content CSV 内容
     * @return 判定结果
     */
    private List<EvaluatedRow> evaluate(String content) {
        List<CustomerCsv.Row> parsed = CustomerCsv.parse(content);
        int maxRows = properties.getCustomerImportMaxRows();
        if (maxRows > 0 && parsed.size() > maxRows) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_IMPORT_INVALID,
                    "导入行数 " + parsed.size() + " 超过单次上限 " + maxRows);
        }
        Set<String> names = new HashSet<>();
        Set<String> taxNos = new HashSet<>();
        Set<String> domains = new HashSet<>();
        Set<String> phones = new HashSet<>();
        Set<String> emails = new HashSet<>();
        List<EvaluatedRow> evaluated = new ArrayList<>();
        for (CustomerCsv.Row row : parsed) {
            List<String> errors = CustomerCsv.validate(row);
            if (!errors.isEmpty()) {
                evaluated.add(new EvaluatedRow(row, STATUS_INVALID, String.join("；", errors)));
                continue;
            }
            String normalizedName = CustomerNormalizer.normalizeName(row.getName());
            String normalizedTaxNo = CustomerNormalizer.normalizeTaxNo(row.getTaxNo());
            String normalizedDomain = CustomerNormalizer.normalizeDomain(row.getWebsite());
            String normalizedPhone = CustomerNormalizer.normalizePhone(row.getPhone());
            String normalizedEmail = CustomerNormalizer.normalizeEmail(row.getEmail());
            String duplicateReason = duplicateReason(normalizedName, normalizedTaxNo, normalizedDomain,
                    normalizedPhone, normalizedEmail, names, taxNos, domains, phones, emails);
            if (duplicateReason != null) {
                evaluated.add(new EvaluatedRow(row, STATUS_DUPLICATE, duplicateReason));
                continue;
            }
            register(normalizedName, normalizedTaxNo, normalizedDomain, normalizedPhone, normalizedEmail,
                    names, taxNos, domains, phones, emails);
            evaluated.add(new EvaluatedRow(row, STATUS_OK, null));
        }
        return evaluated;
    }

    /**
     * 判断行是否重复（文件内已出现或库内已存在）。
     *
     * @param normalizedName 归一化名称
     * @param normalizedTaxNo 归一化税号
     * @param normalizedDomain 归一化域名
     * @param normalizedPhone 归一化电话
     * @param normalizedEmail 归一化邮箱
     * @param names 文件内名称集合
     * @param taxNos 文件内税号集合
     * @param domains 文件内域名集合
     * @param phones 文件内电话集合
     * @param emails 文件内邮箱集合
     * @return 重复原因；不重复返回 null
     */
    private String duplicateReason(String normalizedName, String normalizedTaxNo, String normalizedDomain,
                                   String normalizedPhone, String normalizedEmail, Set<String> names,
                                   Set<String> taxNos, Set<String> domains, Set<String> phones,
                                   Set<String> emails) {
        if ((normalizedName != null && names.contains(normalizedName))
                || (normalizedTaxNo != null && taxNos.contains(normalizedTaxNo))
                || (normalizedDomain != null && domains.contains(normalizedDomain))) {
            return "名称/税号/域名与文件内其他行重复";
        }
        List<Customer> existing = customerRepository.findHardDuplicates(
                normalizedName, normalizedTaxNo, normalizedDomain, null);
        if (!existing.isEmpty()) {
            String namesInDb = existing.stream().map(Customer::getName).distinct()
                    .limit(3).reduce((left, right) -> left + "、" + right).orElse("");
            return "名称/税号/域名与已有客户重复：" + namesInDb;
        }
        if ((normalizedPhone != null && phones.contains(normalizedPhone))
                || (normalizedEmail != null && emails.contains(normalizedEmail))) {
            return "手机/邮箱与文件内其他行重复";
        }
        List<Long> existingCustomers = contactRepository.findCustomerIdsByNormalized(
                normalizedPhone, normalizedEmail, null);
        if (!existingCustomers.isEmpty()) {
            return "手机/邮箱与已有联系人重复";
        }
        return null;
    }

    /**
     * 登记文件内查重键。
     *
     * @param normalizedName 归一化名称
     * @param normalizedTaxNo 归一化税号
     * @param normalizedDomain 归一化域名
     * @param normalizedPhone 归一化电话
     * @param normalizedEmail 归一化邮箱
     * @param names 名称集合
     * @param taxNos 税号集合
     * @param domains 域名集合
     * @param phones 电话集合
     * @param emails 邮箱集合
     */
    private void register(String normalizedName, String normalizedTaxNo, String normalizedDomain,
                          String normalizedPhone, String normalizedEmail, Set<String> names,
                          Set<String> taxNos, Set<String> domains, Set<String> phones, Set<String> emails) {
        if (normalizedName != null) {
            names.add(normalizedName);
        }
        if (normalizedTaxNo != null) {
            taxNos.add(normalizedTaxNo);
        }
        if (normalizedDomain != null) {
            domains.add(normalizedDomain);
        }
        if (normalizedPhone != null) {
            phones.add(normalizedPhone);
        }
        if (normalizedEmail != null) {
            emails.add(normalizedEmail);
        }
    }

    /**
     * 构建待落库客户。
     *
     * @param row CSV 行
     * @param userId 当前用户
     * @param actor 当前操作人
     * @param code 客户编码
     * @return 客户
     */
    private Customer buildCustomer(CustomerCsv.Row row, Long userId, CrmActor actor, String code) {
        Customer customer = new Customer();
        customer.setCustomerCode(code);
        customer.setName(row.getName());
        customer.setShortName(trimToNull(row.getShortName()));
        customer.setStatus(CustomerStatus.ACTIVE.name());
        customer.setOwnerId(actor.allDataScope() && row.getOwnerId() != null ? row.getOwnerId() : userId);
        customer.setLevel(StringUtils.hasText(row.getLevel())
                ? CustomerLevel.canonical(row.getLevel())
                : CustomerLevel.canonical(properties.getDefaultCustomerLevel()));
        customer.setSource(trimToNull(row.getSource()));
        customer.setIndustry(trimToNull(row.getIndustry()));
        customer.setAddress(trimToNull(row.getAddress()));
        customer.setTags(trimToNull(row.getTags()));
        customer.setWebsite(trimToNull(row.getWebsite()));
        customer.setTaxNo(trimToNull(row.getTaxNo()));
        customer.setProvince(trimToNull(row.getProvince()));
        customer.setCity(trimToNull(row.getCity()));
        customer.setRegion(trimToNull(row.getDistrict()));
        customer.setNormalizedName(CustomerNormalizer.normalizeName(row.getName()));
        customer.setNormalizedTaxNo(CustomerNormalizer.normalizeTaxNo(row.getTaxNo()));
        customer.setNormalizedDomain(CustomerNormalizer.normalizeDomain(row.getWebsite()));
        customer.setVersion(0);
        customer.setPoolStatus(PoolStatus.OWNED.name());
        return customer;
    }

    /**
     * 生成下一个编码流水。
     *
     * @param prefix 编码前缀
     * @return 流水号
     */
    private long nextSequence(String prefix) {
        String maxCode = customerRepository.findMaxCode(prefix)
                .orElse(prefix + "0".repeat(CODE_SEQUENCE_LENGTH));
        try {
            return Long.parseLong(maxCode.substring(maxCode.length() - CODE_SEQUENCE_LENGTH)) + 1;
        } catch (NumberFormatException exception) {
            return 1L;
        }
    }

    /**
     * 写入导入留痕。
     *
     * @param customerId 客户 ID
     * @param ownerId 负责人
     * @param operatorId 操作人
     */
    private void writeOwnerLog(Long customerId, Long ownerId, Long operatorId) {
        CustomerOwnerLog log = new CustomerOwnerLog();
        log.setCustomerId(customerId);
        log.setAction("IMPORT");
        log.setFromOwnerId(null);
        log.setToOwnerId(ownerId);
        log.setOperatorId(operatorId);
        log.setRemark("CSV 导入");
        poolRepository.insertOwnerLog(log);
    }

    /**
     * 客户转导出 CSV 行。
     *
     * @param customer 客户
     * @return CSV 行
     */
    private CustomerCsv.Row toExportRow(Customer customer) {
        CustomerCsv.Row row = new CustomerCsv.Row();
        row.setCustomerCode(customer.getCustomerCode());
        row.setName(customer.getName());
        row.setShortName(customer.getShortName());
        row.setIndustry(customer.getIndustry());
        row.setSource(customer.getSource());
        row.setLevel(customer.getLevel());
        row.setProvince(customer.getProvince());
        row.setCity(customer.getCity());
        row.setDistrict(customer.getRegion());
        row.setTaxNo(customer.getTaxNo());
        row.setWebsite(customer.getWebsite());
        row.setTags(customer.getTags());
        row.setOwnerId(customer.getOwnerId());
        Contact contact = primaryContact(customer.getId());
        if (contact != null) {
            row.setPhone(contact.getPhone());
            row.setEmail(contact.getEmail());
        }
        row.setCreateTime(customer.getCreateTime() == null ? null : TIME_FORMAT.format(customer.getCreateTime()));
        return row;
    }

    /**
     * 取客户主联系人（无主联系人时取第一条）。
     *
     * @param customerId 客户 ID
     * @return 联系人；无联系人返回 null
     */
    private Contact primaryContact(Long customerId) {
        List<Contact> contacts = contactRepository.list(customerId);
        return contacts.stream().filter(contact -> Boolean.TRUE.equals(contact.getPrimary()))
                .findFirst().orElse(contacts.isEmpty() ? null : contacts.get(0));
    }

    /**
     * 判定结果转视图。
     *
     * @param evaluated 判定结果
     * @return 行视图
     */
    private CustomerImportRowView toRowView(EvaluatedRow evaluated) {
        return toRowView(evaluated, evaluated.reason());
    }

    /**
     * 判定结果转视图（指定原因）。
     *
     * @param evaluated 判定结果
     * @param reason 原因
     * @return 行视图
     */
    private CustomerImportRowView toRowView(EvaluatedRow evaluated, String reason) {
        CustomerImportRowView view = new CustomerImportRowView();
        view.setLineNumber(evaluated.row().getLineNumber());
        view.setName(evaluated.row().getName());
        view.setStatus(evaluated.status());
        view.setReason(reason);
        return view;
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

    /**
     * 校验门禁开关。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 行判定结果。
     *
     * @param row CSV 行
     * @param status 状态
     * @param reason 原因
     */
    private record EvaluatedRow(CustomerCsv.Row row, String status, String reason) {
    }
}
