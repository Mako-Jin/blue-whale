package com.bluewhale.auth.infrastructure.mail;

import com.bluewhale.auth.domain.event.AuthDomainEvent.ChangeType;
import com.bluewhale.auth.domain.event.UserDomainEvent;
import com.bluewhale.auth.domain.user.aggregate.User;
import com.bluewhale.auth.domain.user.repository.UserRepository;
import com.bluewhale.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.bluewhale.auth.application.service.UserAppService;

/**
 * 建用户后自动开邮箱。
 *
 * <p>挂在已有的 {@code CREATED} 领域事件上，而不是改 {@code UserAppService}：
 * 应用层不该知道「邮箱」这件事，开通是基础设施侧的可选副作用。</p>
 *
 * <p><b>三个不变式</b>：</p>
 * <ul>
 *   <li>只在事务提交后触发（{@code AFTER_COMMIT}）：用户没落库就开邮箱，会留下孤儿邮箱。</li>
 *   <li>异步执行：Mailcow 慢或不可达都不能拖慢建用户。</li>
 *   <li>失败只记日志、不重试、不回滚用户：邮箱没开成是「少个功能」，用户没建成是「事故」。
 *       漏开的账号需在邮箱账号页手工绑定，或调 {@code /mail/internal/provision} 补开。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class MailProvisionListener {

    private final UserRepository userRepository;

    private final MailProvisionClient provisionClient;

    private final ThreadPoolTaskExecutor executor;

    /**
     * 构造器注入
     *
     * @param userRepository 用户仓储（按 ID 跨租户读取，避免依赖调用线程的租户上下文）
     * @param provisionClient 邮箱代开客户端
     * @param executor 代开线程池
     */
    public MailProvisionListener(UserRepository userRepository,
                                 MailProvisionClient provisionClient,
                                 @Qualifier("mailProvisionExecutor") ThreadPoolTaskExecutor executor) {
        this.userRepository = userRepository;
        this.provisionClient = provisionClient;
        this.executor = executor;
    }

    /**
     * 监听用户领域事件：仅处理新建，且仅在启用代开时触发
     *
     * @param event 用户领域事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserEvent(UserDomainEvent event) {
        if (event.changeType() != ChangeType.CREATED || event.aggregateId() == null) {
            return;
        }
        if (!provisionClient.isEnabled()) {
            return;
        }
        Long userId = event.aggregateId();
        // 事务提交后仍在请求线程上，此时租户上下文通常可用；一并捕获，异步线程里还原
        Long tenantId = event.tenantId() != null ? event.tenantId() : TenantContext.get();
        try {
            executor.execute(() -> provisionQuietly(userId, tenantId));
        } catch (TaskRejectedException e) {
            log.error("邮箱代开队列已满，该用户未开通邮箱，请在邮箱账号页手工绑定: userId={}", userId);
        }
    }

    /**
     * 执行开通：所有异常在此收敛，不向线程池外抛
     *
     * @param userId 用户 ID
     * @param tenantId 租户 ID
     */
    private void provisionQuietly(Long userId, Long tenantId) {
        Long previous = TenantContext.get();
        try {
            if (tenantId != null) {
                TenantContext.set(tenantId);
            }
            User user = userRepository.findByIdAcrossTenants(userId).orElse(null);
            if (user == null) {
                log.warn("邮箱代开跳过：用户不存在或已删除, userId={}", userId);
                return;
            }
            Long effectiveTenantId = tenantId != null ? tenantId : user.getTenantId();
            String state = provisionClient.provision(new MailProvisionCommand(
                    effectiveTenantId, userId, user.getUsername().value(), user.getNickname()));
            log.info("建用户后自动开邮箱完成: userId={}, state={}", userId, state);
        } catch (RuntimeException e) {
            log.error("建用户后自动开邮箱失败，需人工补开: userId={}, reason={}", userId, e.getMessage());
        } finally {
            TenantContext.set(previous);
        }
    }
}
