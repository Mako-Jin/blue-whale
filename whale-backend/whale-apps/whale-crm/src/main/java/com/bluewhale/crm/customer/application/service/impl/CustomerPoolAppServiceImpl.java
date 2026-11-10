package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.command.PoolAssignCommand;
import com.bluewhale.crm.customer.application.command.PoolRuleSaveCommand;
import com.bluewhale.crm.customer.application.service.CustomerPoolAppService;
import com.bluewhale.crm.customer.application.view.CustomerView;
import com.bluewhale.crm.customer.application.view.OwnerLogView;
import com.bluewhale.crm.customer.application.view.PoolRecycleResultView;
import com.bluewhale.crm.customer.application.view.PoolRuleView;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerLevel;
import com.bluewhale.crm.customer.domain.model.CustomerOwnerLog;
import com.bluewhale.crm.customer.domain.model.CustomerPoolRule;
import com.bluewhale.crm.customer.domain.model.CustomerQuery;
import com.bluewhale.crm.customer.domain.model.PoolStatus;
import com.bluewhale.crm.customer.domain.repository.CustomerPoolRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 客户公海应用服务实现。
 *
 * <p>{@code noOpportunityDays} 字段随商机上下文（M2）接入后生效；本期仅按
 * 「最近跟进时间」与等级回收，规则中的该阈值暂不参与判定。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CustomerPoolAppServiceImpl implements CustomerPoolAppService {

    /** 默认规则名称 */
    private static final String DEFAULT_RULE_NAME = "默认公海规则";

    /** 负责人变更历史条数 */
    private static final int OWNER_LOG_LIMIT = 20;

    private final CustomerRepository customerRepository;

    private final CustomerPoolRepository poolRepository;

    private final CrmProperties properties;

    @Override
    public PageResult<CustomerView> page(CustomerQuery query, CrmActor actor) {
        assertGate();
        query.setPoolStatus(PoolStatus.IN_POOL.name());
        long total = customerRepository.count(query);
        List<CustomerView> views = customerRepository.page(query).stream()
                .map(customer -> CustomerView.from(customer, actor.canViewSensitive()))
                .toList();
        return PageResult.of(views, total, query.getCurrent(), query.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerView claim(Long customerId, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        Customer customer = load(customerId);
        if (customer.merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGED_READONLY);
        }
        if (!customer.inPool() && customer.getOwnerId() != null) {
            throw CrmErrorCode.exception(CrmErrorCode.POOL_CLAIM_CONFLICT);
        }
        CustomerPoolRule rule = poolRepository.findEnabled().orElse(null);
        if (rule != null && rule.getDailyClaimLimit() != null && rule.getDailyClaimLimit() > 0) {
            long claimed = poolRepository.countClaims(userId, LocalDateTime.now().toLocalDate().atStartOfDay());
            if (claimed >= rule.getDailyClaimLimit()) {
                throw CrmErrorCode.exception(CrmErrorCode.POOL_CLAIM_LIMIT);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        if (!customerRepository.claim(customerId, userId, now)) {
            throw CrmErrorCode.exception(CrmErrorCode.POOL_CLAIM_CONFLICT);
        }
        writeLog(customerId, "CLAIM", customer.getOwnerId(), userId, userId, null);
        return CustomerView.from(load(customerId), actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerView release(Long customerId, String remark, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        Customer customer = load(customerId);
        if (!actor.allDataScope() && !userId.equals(customer.getOwnerId())) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_NOT_FOUND);
        }
        if (customer.merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGED_READONLY);
        }
        if (customer.inPool()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_STATE_CONFLICT, "客户已在公海，无需重复释放");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!customerRepository.release(customerId, now)) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_STATE_CONFLICT);
        }
        writeLog(customerId, "RELEASE", customer.getOwnerId(), null, userId, remark);
        return CustomerView.from(load(customerId), actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerView assign(Long customerId, PoolAssignCommand command, CrmActor actor) {
        assertGate();
        Long operatorId = actor.requireUserId();
        if (!actor.allDataScope()) {
            throw CrmErrorCode.exception(CrmErrorCode.DATA_SCOPE_DENIED);
        }
        if (command.getOwnerId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_OWNER_REQUIRED);
        }
        Customer customer = load(customerId);
        if (customer.merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGED_READONLY);
        }
        if (!customerRepository.updateOwner(customerId, command.getOwnerId(),
                command.getDeptId(), LocalDateTime.now())) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_STATE_CONFLICT);
        }
        writeLog(customerId, "ASSIGN", customer.getOwnerId(), command.getOwnerId(),
                operatorId, command.getRemark());
        return CustomerView.from(load(customerId), actor.canViewSensitive());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoolRecycleResultView recycle(CrmActor actor) {
        assertGate();
        if (!actor.allDataScope()) {
            throw CrmErrorCode.exception(CrmErrorCode.DATA_SCOPE_DENIED);
        }
        LocalDateTime now = LocalDateTime.now();
        PoolRecycleResultView result = new PoolRecycleResultView();
        result.setExecutedAt(now);
        CustomerPoolRule rule = poolRepository.findEnabled().orElseGet(CustomerPoolRule::defaults);
        result.setRuleName(rule.getRuleName());
        if (!rule.active() || rule.getInactiveDays() == null || rule.getInactiveDays() <= 0) {
            result.setRecycledCount(0);
            return result;
        }
        LocalDateTime threshold = now.minusDays(rule.getInactiveDays());
        List<Customer> candidates = customerRepository.findRecycleCandidates(
                threshold, rule.getLevels(), rule.getDeptId(), properties.getPoolRecycleBatchSize());
        int recycled = 0;
        for (Customer candidate : candidates) {
            if (customerRepository.release(candidate.getId(), now)) {
                writeLog(candidate.getId(), "RECYCLE", candidate.getOwnerId(), null,
                        actor.getUserId(), "按规则自动回收");
                recycled++;
            }
        }
        rule.setLastRunAt(now);
        rule.setRecycleCount(recycled);
        poolRepository.saveRule(rule);
        result.setRecycledCount(recycled);
        return result;
    }

    @Override
    public PoolRuleView getRule(CrmActor actor) {
        assertGate();
        return PoolRuleView.from(poolRepository.findEnabled().orElseGet(CustomerPoolRule::defaults));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoolRuleView saveRule(PoolRuleSaveCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        if (!actor.allDataScope()) {
            throw CrmErrorCode.exception(CrmErrorCode.DATA_SCOPE_DENIED);
        }
        CustomerPoolRule rule = command.getId() == null
                ? poolRepository.findEnabled().orElseGet(CustomerPoolRule::defaults)
                : poolRepository.findById(command.getId())
                        .orElseThrow(() -> CrmErrorCode.exception(CrmErrorCode.POOL_RULE_NOT_FOUND));
        rule.setId(command.getId() == null ? rule.getId() : command.getId());
        rule.setRuleName(StringUtils.hasText(command.getRuleName())
                ? command.getRuleName().trim() : DEFAULT_RULE_NAME);
        rule.setInactiveDays(nonNegative(command.getInactiveDays(), rule.getInactiveDays()));
        rule.setNoOpportunityDays(nonNegative(command.getNoOpportunityDays(), rule.getNoOpportunityDays()));
        rule.setLevels(normalizeLevels(command.getLevels(), rule.getLevels()));
        rule.setDeptId(command.getDeptId() == null ? rule.getDeptId() : command.getDeptId());
        rule.setDailyClaimLimit(nonNegative(command.getDailyClaimLimit(), rule.getDailyClaimLimit()));
        rule.setEnabled(command.getEnabled() == null ? Boolean.TRUE.equals(rule.getEnabled()) : command.getEnabled());
        if (rule.getRecycleCount() == null) {
            rule.setRecycleCount(0);
        }
        poolRepository.saveRule(rule);
        return PoolRuleView.from(rule);
    }

    @Override
    public List<OwnerLogView> ownerLogs(Long customerId, CrmActor actor) {
        assertGate();
        loadScoped(customerId, actor);
        return poolRepository.listOwnerLogs(customerId, OWNER_LOG_LIMIT).stream()
                .map(OwnerLogView::from).toList();
    }

    /**
     * 加载客户并校验数据范围。
     *
     * @param customerId 客户 ID
     * @param actor 当前操作人
     * @return 客户
     */
    private Customer loadScoped(Long customerId, CrmActor actor) {
        Customer customer = load(customerId);
        if (!actor.allDataScope()) {
            Long userId = actor.getUserId();
            if (userId == null || !userId.equals(customer.getOwnerId())) {
                throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_NOT_FOUND);
            }
        }
        return customer;
    }

    /**
     * 按主键加载客户。
     *
     * @param customerId 客户 ID
     * @return 客户
     */
    private Customer load(Long customerId) {
        if (customerId == null) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "客户 ID 不能为空");
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> CrmErrorCode.exception(CrmErrorCode.CUSTOMER_NOT_FOUND));
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
    private void writeLog(Long customerId, String action, Long fromOwnerId, Long toOwnerId,
                          Long operatorId, String remark) {
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
     * 非负数校验。
     *
     * @param value 新值
     * @param fallback 保底值
     * @return 非负整数
     */
    private Integer nonNegative(Integer value, Integer fallback) {
        int candidate = value == null ? (fallback == null ? 0 : fallback) : value;
        if (candidate < 0) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "公海规则数值不能为负数");
        }
        return candidate;
    }

    /**
     * 规范化适用等级列表。
     *
     * @param levels 新等级列表
     * @param fallback 保底等级列表
     * @return 规范等级（逗号分隔）
     */
    private String normalizeLevels(String levels, String fallback) {
        String text = StringUtils.hasText(levels) ? levels : fallback;
        if (!StringUtils.hasText(text)) {
            return "C,D";
        }
        String normalized = String.join(",", Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(CustomerLevel::canonical)
                .distinct()
                .toList());
        return normalized.isEmpty() ? "C,D" : normalized;
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
