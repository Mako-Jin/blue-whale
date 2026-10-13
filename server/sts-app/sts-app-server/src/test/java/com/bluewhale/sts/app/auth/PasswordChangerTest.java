package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.authentication.PasswordChanger;
import com.bluewhale.sts.auth.authentication.PasswordHistoryEntity;
import com.bluewhale.sts.auth.authentication.PasswordHistoryMapper;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.PasswordPolicy;
import com.bluewhale.sts.auth.common.enums.PasswordChangeSource;
import com.bluewhale.sts.auth.common.enums.Status;
import com.bluewhale.sts.auth.common.codec.PasswordHasher;
import com.bluewhale.sts.auth.tenant.TenantEntity;
import com.bluewhale.sts.auth.tenant.TenantMapper;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PasswordChanger} 的三件事与裁剪行为的测试。
 *
 * <p><b>为什么不与接口测试合并：</b>这里问的是"三件事本身对不对"（顺序、原子性、裁剪边界），
 * 而 {@code ChangePasswordApiTest} 问的是"接线对不对"（旧口令验了没有、会话撤了没有）。
 * 把两者混在一个用例里，任何一条红了都看不出是哪一层坏的。
 *
 * <p><b>刻意不加 {@code @Transactional}：</b>本类断言的核心之一是"校验失败时库里一片安静"
 * 与"历史真的落了库"。包进测试事务之后，未提交的写在同一事务内可见，
 * "根本没写进库"会被完全掩盖 —— 与 {@code PasswordHardeningApiTest} 同一个理由。
 * 代价是真的写库（h2 内存库，随进程消失），所以用户名一律带时间戳后缀。
 */
@SpringBootTest
class PasswordChangerTest {

    @Autowired
    private PasswordChanger passwordChanger;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordHistoryMapper passwordHistoryMapper;

    @Autowired
    private TenantMapper tenantMapper;

    /** 保留条数从配置读，不写死 5 —— 配置改了用例要跟着走，而不是变成一个假的失败 */
    @Autowired
    private PasswordPolicy passwordPolicy;

    @Autowired
    private AuthProperties properties;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    @DisplayName("强度不足：40003，且口令与历史都不动（没有\"历史记了、口令没换\"的中间状态）")
    void shouldRejectWeakPasswordWithoutSideEffect() {
        UserEntity user = newUser("Seed@1234");
        String hashBefore = user.getPasswordHash();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> change(user, "abc", PasswordChangeSource.SELF));

        assertEquals(AuthResultCode.PASSWORD_POLICY_VIOLATION.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("长度至少"), "提示要逐条列出缺哪几项，实际：" + ex.getMessage());

        UserEntity reloaded = userMapper.selectById(user.getId());
        assertEquals(hashBefore, reloaded.getPasswordHash(), "校验没过就不该改口令");
        assertEquals(0, historyCount(user.getId()), "校验没过就不该写历史");
    }

    @Test
    @DisplayName("与当前口令重复：40914，且不写历史")
    void shouldRejectCurrentPasswordReuseWithoutHistory() {
        UserEntity user = newUser("Seed@1234");

        // 当前口令不在历史表里 —— 它必须被单独比对。
        // 漏掉这一步的后果是"改了一轮又改回来"会静默成功，而那正是防重用要拦的事。
        BusinessException ex = assertThrows(BusinessException.class,
                () -> change(user, "Seed@1234", PasswordChangeSource.SELF));

        assertEquals(AuthResultCode.PASSWORD_REUSED.getCode(), ex.getCode());
        assertEquals(0, historyCount(user.getId()));
    }

    @Test
    @DisplayName("与历史口令重复：40914")
    void shouldRejectHistoricalPasswordReuse() {
        UserEntity user = newUser("Seed@1234");
        change(user, "Second@1234", PasswordChangeSource.SELF);

        // 此刻 Seed@1234 已被替换，进了历史表 —— 它仍必须被挡住
        BusinessException ex = assertThrows(BusinessException.class,
                () -> change(user, "Seed@1234", PasswordChangeSource.SELF));

        assertEquals(AuthResultCode.PASSWORD_REUSED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("成功：新哈希生效、被替换的那个进历史（带来源与时间）")
    void shouldRecordReplacedPasswordInHistory() {
        UserEntity user = newUser("Seed@1234");
        String hashBefore = user.getPasswordHash();
        Long updatedBefore = user.getPasswordUpdatedAt();

        long now = System.currentTimeMillis();
        passwordChanger.change(user, "Second@1234", PasswordChangeSource.SELF, now);

        UserEntity reloaded = userMapper.selectById(user.getId());
        assertNotEquals(hashBefore, reloaded.getPasswordHash(), "口令必须真的换掉");
        assertEquals("PBKDF2-SHA256", reloaded.getPasswordAlgorithm());
        assertEquals(now, reloaded.getPasswordUpdatedAt(), "更新时刻必须跟着走，否则\"口令多久没换\"无从判断");

        PasswordHistoryEntity history = latestHistory(user.getId());
        assertEquals(hashBefore, history.getPasswordHash(), "历史记的必须是**被替换下来的那个**");
        assertEquals(PasswordChangeSource.SELF.wireName(), history.getChangeSource(),
                "\"本人改的\"与\"管理员重置的\"必须分得开，否则安全时间线上缺一半");
        assertEquals(now, history.getChangeTime());
    }

    @Test
    @DisplayName("管理员重置走同一条路：source 记为 ADMIN_RESET，历史一样落库")
    void shouldRecordAdminResetSource() {
        UserEntity user = newUser("Seed@1234");

        change(user, "AdminSet@1234", PasswordChangeSource.ADMIN_RESET);

        assertEquals(PasswordChangeSource.ADMIN_RESET.wireName(), latestHistory(user.getId()).getChangeSource());
    }

    @Test
    @DisplayName("历史按**条数**裁剪：超出保留数的旧口令被挤出窗口，随即可以被重新使用")
    void shouldTrimHistoryByCount() {
        int keep = passwordPolicy.historyCount();
        assertTrue(keep > 0, "本用例依赖 historyCount > 0；若配置为 0 则整个防重用检查是关闭的");

        UserEntity user = newUser("Seed@1234");
        // 改 keep+1 次才能把最初那个口令挤出窗口：
        // 第 n 次改密把"第 n-1 个口令"记进历史，所以改 keep 次之后历史**恰好**是 keep 条、
        // 一次裁剪都没发生（最初那个仍在窗口里）。多改一次，窗口才真的滑动一格。
        for (int i = 1; i <= keep + 1; i++) {
            change(user, "Round" + i + "@1234", PasswordChangeSource.SELF);
        }

        assertEquals(keep, historyCount(user.getId()),
                "历史条数应恒等于保留数，而不是随改密次数无限增长");

        // 最初那个口令已不在当前、也不在保留窗口内 ⇒ 它必须可以被重新使用。
        // 这一条同时证明裁剪真的执行了：只断言条数的话，"删错了哪几条"看不出来。
        change(user, "Seed@1234", PasswordChangeSource.SELF);
        assertEquals(keep, historyCount(user.getId()), "再改一次之后仍应维持在保留数");
    }

    @Test
    @DisplayName("策略没开时：新实体不带到期时刻，已有的一列**原样留着** —— 写 null 是个不会落库的动作")
    void shouldLeaveExpiryUntouchedWhenPolicyDisabled() {
        for (int days : new int[]{0, -1}) {
            AuthProperties config = new AuthProperties();
            config.getPassword().setExpireDays(days);

            // 两个 Mapper 传 null：assignNewPassword 只改内存、不落库 ——
            // 那正是它被单独拆出来的理由之一。这里要问的只是它算出什么、改了哪些字段。
            PasswordChanger changer = new PasswordChanger(
                    null, passwordHasher, new PasswordPolicy(config), null);

            UserEntity fresh = new UserEntity();
            changer.assignNewPassword(fresh, "Seed@1234", System.currentTimeMillis());
            // 0 与负数一律按“不启用”：负数不能当成“很久以前就过期”——
            // 那是把一处配置笔误放大成一次全员登不进来，而它的表现（所有人都 40111）
            // 离原因（一个减号）非常远。
            assertNull(fresh.getPasswordExpiresAt(), "expire-days=" + days + " 时不该落下到期时刻");

            // 已有值则原样留着 —— 这一条**刻意**不是在断言“被清成 null”。
            // MyBatis-Plus 的 updateById 默认跳过 null 字段（本仓未改这个全局策略），
            // 所以 setPasswordExpiresAt(null) 在改密 / 重置这两条路上根本不会落库。
            // 写成一句干净话只会让人相信“关掉策略之后残留被清掉了”；
            // 真正让残留失效的是判断那一侧：PasswordPolicy#isExpired 先看策略有没有开。
            // 谁要是哪天“顺手”补上那句 setPasswordExpiresAt(null)，这条断言会红 ——
            // 那正是要他先读一眼 docs/decisions/0045。
            UserEntity existing = new UserEntity();
            existing.setPasswordExpiresAt(123L);
            changer.assignNewPassword(existing, "Seed@1234", System.currentTimeMillis());
            assertEquals(123L, existing.getPasswordExpiresAt(), "策略没开时不去动那一列");
        }
    }

    // ==================== 辅助 ====================

    private void change(UserEntity user, String newPassword, PasswordChangeSource source) {
        passwordChanger.change(user, newPassword, source, System.currentTimeMillis());
    }

    /** 建一个真实落库的用户 —— 直接走 insert，不经过管理接口：本类要测的是 changer 本身 */
    private UserEntity newUser(String initialPassword) {
        TenantEntity tenant = tenantMapper.selectOne(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getTenantCode, properties.getSeedAdmin().getTenantCode()));
        String username = "chg_" + Long.toString(System.nanoTime(), 36);

        UserEntity entity = new UserEntity();
        entity.setTenantId(tenant.getId());
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setStatus(Status.ENABLED.wireName());
        entity.setMustChangePassword(0);
        entity.setFailedAttempts(0);
        entity.setLockLevel(0);
        passwordChanger.assignNewPassword(entity, initialPassword, System.currentTimeMillis());
        userMapper.insert(entity);
        return entity;
    }

    private long historyCount(Long userId) {
        Long count = passwordHistoryMapper.selectCount(Wrappers.<PasswordHistoryEntity>lambdaQuery()
                .eq(PasswordHistoryEntity::getUserId, userId));
        return count == null ? 0L : count;
    }

    private PasswordHistoryEntity latestHistory(Long userId) {
        PasswordHistoryEntity history = passwordHistoryMapper.selectOne(
                Wrappers.<PasswordHistoryEntity>lambdaQuery()
                        .eq(PasswordHistoryEntity::getUserId, userId)
                        .orderByDesc(PasswordHistoryEntity::getId)
                        .last("LIMIT 1"));
        assertTrue(history != null, "应存在一条历史记录");
        return history;
    }

}
