package com.bluewhale.sts.auth.authentication;

import com.bluewhale.sts.auth.common.LoginLockPolicy;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 登录失败计数与锁定的写入器 —— <b>必须跑在独立事务里</b>。
 *
 * <p><b>为什么不能与登录共用事务：</b>登录失败时 {@code AuthService.login} 会抛
 * {@code BusinessException}（{@code RuntimeException}），它把**整个登录事务**标记为回滚。
 * 失败计数若写在同一事务里，就会连同这次回滚一起消失 —— 下次请求读到的仍是初值，
 * 阈值永远达不到、账号永远不会被锁定，而且**连"账号已被锁定"那条 WARN 都不会打印**
 * （它也在阈值之后）。表象是"代码看起来实现了锁定，库里却什么都没有"。
 *
 * <p>这与 {@code AuditRecorder} 是同一条理由的两种表现：
 * **"因为这次操作失败而产生的记录"，恰恰不能跟着这次失败一起回滚** ——
 * 审计要留住"越权尝试"，这里要留住"这次口令错了"。
 *
 * <p><b>为什么是独立 Bean 而不是本类里加个 {@code @Transactional} 方法：</b>
 * 同类内部调用（self-invocation）不经过代理，注解会被静默忽略，事务也就不会新开。
 */
@Service
public class LoginAttemptRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptRecorder.class);

    private final UserMapper userMapper;
    private final LoginLockPolicy lockPolicy;

    public LoginAttemptRecorder(UserMapper userMapper, LoginLockPolicy lockPolicy) {
        this.userMapper = userMapper;
        this.lockPolicy = lockPolicy;
    }

    /**
     * 记一次登录失败；达到阈值则按退避策略锁定。
     *
     * <p><b>按主键重新读取用户</b>，而不是接收调用方传进来的实体：本方法在另一个事务里执行，
     * 调用方那个实体属于已被挂起的那个事务，它的 {@code failedAttempts} 可能是过期的。
     * 重新读取拿到的是已提交的最新值 —— 这正是"连续失败"能够连续的前提。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, long now) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }

        int attempts = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
        user.setFailedAttempts(attempts);

        if (attempts >= lockPolicy.maxFailedAttempts()) {
            int level = (user.getLockLevel() == null ? 0 : user.getLockLevel()) + 1;
            Duration lockDuration = lockPolicy.lockDurationOf(level);

            user.setLockLevel(level);
            user.setLockedUntil(now + lockDuration.toMillis());
            user.setLockReason("连续登录失败 " + attempts + " 次");
            // 计数归零：本次锁定已经生效，下一轮要重新累加到阈值才再次触发。
            // 不归零的后果是"锁定期一过、再错一次就立刻再锁"——
            // 看似更严，实则把"偶尔手滑"与"持续攻击"判成了同一件事。
            user.setFailedAttempts(0);

            log.warn("账号因连续登录失败被锁定 | userId={} | attempts={} | level={} | 时长={}",
                    userId, attempts, level, lockDuration);
        }

        userMapper.updateById(user);
    }

}
