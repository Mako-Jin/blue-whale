package com.bluewhale.crm.customer.application.service.impl;

import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.command.FollowUpCreateCommand;
import com.bluewhale.crm.customer.application.service.CustomerFollowUpAppService;
import com.bluewhale.crm.customer.application.view.FollowUpView;
import com.bluewhale.crm.customer.domain.model.Customer;
import com.bluewhale.crm.customer.domain.model.CustomerFollowUp;
import com.bluewhale.crm.customer.domain.model.FollowUpChannel;
import com.bluewhale.crm.customer.domain.repository.CustomerFollowUpRepository;
import com.bluewhale.crm.customer.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户跟进应用服务实现（append-only）。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CustomerFollowUpAppServiceImpl implements CustomerFollowUpAppService {

    private final CustomerRepository customerRepository;

    private final CustomerFollowUpRepository followUpRepository;

    private final CrmProperties properties;

    @Override
    public PageResult<FollowUpView> page(Long customerId, PageQuery pageQuery, CrmActor actor) {
        assertGate();
        requireCustomer(customerId, actor);
        long size = Math.max(1, Math.min(pageQuery.getSize(), properties.getFollowUpPageLimit()));
        pageQuery.setSize(size);
        long total = followUpRepository.count(customerId);
        List<FollowUpView> views = followUpRepository.page(customerId, pageQuery).stream()
                .map(FollowUpView::from)
                .toList();
        return PageResult.of(views, total, pageQuery.getCurrent(), size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowUpView create(FollowUpCreateCommand command, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        if (command.getCustomerId() == null) {
            throw CrmErrorCode.exception(CrmErrorCode.FOLLOW_UP_CUSTOMER_REQUIRED);
        }
        Customer customer = requireCustomer(command.getCustomerId(), actor);
        if (customer.merged()) {
            throw CrmErrorCode.exception(CrmErrorCode.CUSTOMER_MERGED_READONLY);
        }
        if (!StringUtils.hasText(command.getContent())) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "跟进内容不能为空");
        }
        CustomerFollowUp followUp = new CustomerFollowUp();
        followUp.setCustomerId(customer.getId());
        followUp.setOpportunityId(command.getOpportunityId());
        followUp.setChannel(FollowUpChannel.from(command.getChannel()).name());
        followUp.setContent(command.getContent().trim());
        followUp.setResult(trimToNull(command.getResult()));
        followUp.setNextAction(trimToNull(command.getNextAction()));
        followUp.setNextActionAt(command.getNextActionAt());
        followUp.setCreatedBy(userId);
        followUpRepository.insert(followUp);
        customerRepository.touchFollowUp(customer.getId(), LocalDateTime.now());
        return FollowUpView.from(followUp);
    }

    /**
     * 加载客户并校验数据范围。
     *
     * @param customerId 客户 ID
     * @param actor 当前操作人
     * @return 客户
     */
    private Customer requireCustomer(Long customerId, CrmActor actor) {
        Customer customer = customerRepository.findById(customerId)
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
}
