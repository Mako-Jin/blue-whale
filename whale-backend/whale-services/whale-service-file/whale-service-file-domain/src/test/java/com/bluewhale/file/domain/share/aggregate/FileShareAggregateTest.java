package com.bluewhale.file.domain.share.aggregate;

import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.file.domain.share.model.vo.SharePassword;
import com.bluewhale.file.domain.share.model.vo.ShareStatus;
import com.bluewhale.file.domain.share.model.vo.ShareToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileShareAggregate} 与分享令牌、密码值对象单元测试。
 *
 * @author BlueWhale
 */
class FileShareAggregateTest {

    private static final String PASSWORD = "whale-1234";

    @Test
    @DisplayName("create：有效期为空/次数为 0 时归一化为长期有效与不限次数")
    void shouldNormalizeEmptyExpiryAndUnlimitedDownloads() {
        FileShareAggregate share = FileShareAggregate.create(1L, "a.txt", ShareToken.generate().value(),
                null, null, 0, 9L);

        assertNull(share.getExpireTime());
        assertNull(share.getMaxDownloads());
        assertFalse(share.requiresPassword());
        assertEquals(ShareStatus.ACTIVE, share.getStatus());
        assertEquals(43, share.getToken().length());
    }

    @Test
    @DisplayName("create：失效时间早于当前时间时拒绝")
    void shouldRejectPastExpiry() {
        DomainException ex = assertThrows(DomainException.class, () -> FileShareAggregate.create(1L, "a.txt",
                ShareToken.generate().value(), null, LocalDateTime.now().minusMinutes(1), null, 9L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("ShareToken：非法长度或字符集拒绝")
    void shouldRejectIllegalToken() {
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), assertThrows(DomainException.class,
                () -> ShareToken.of("short")).getCode());
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), assertThrows(DomainException.class,
                () -> ShareToken.of("bad token with space")).getCode());
    }

    @Test
    @DisplayName("revoke：幂等，已撤销不再变化")
    void shouldRevokeIdempotently() {
        FileShareAggregate share = FileShareAggregate.create(1L, "a.txt", ShareToken.generate().value(),
                null, null, null, 9L);

        assertTrue(share.revoke());
        assertFalse(share.revoke());
        assertEquals(ShareStatus.REVOKED, share.getStatus());
    }

    @Test
    @DisplayName("密码：哈希可往返校验，错误密码不匹配")
    void shouldHashAndMatchPassword() {
        String hash = SharePassword.hash(PASSWORD);

        assertTrue(SharePassword.matches(PASSWORD, hash));
        assertFalse(SharePassword.matches("whale-9999", hash));
        assertFalse(SharePassword.matches(null, hash));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), assertThrows(DomainException.class,
                () -> SharePassword.hash("abc")).getCode());
    }

    @Test
    @DisplayName("访问校验：过期、撤销、次数耗尽均按不存在处理")
    void shouldRejectInactiveShares() {
        LocalDateTime now = LocalDateTime.now();
        FileShareAggregate expired = FileShareAggregate.reconstitute(1L, 1L, "a.txt",
                ShareToken.generate().value(), null, now.minusMinutes(1), null, 0, ShareStatus.ACTIVE,
                9L, now, now);
        assertEquals(ErrorCode.NOT_FOUND.getCode(),
                assertThrows(DomainException.class, () -> expired.assertAccessible(now)).getCode());

        FileShareAggregate revoked = FileShareAggregate.reconstitute(2L, 1L, "a.txt",
                ShareToken.generate().value(), null, null, null, 0, ShareStatus.REVOKED, 9L, now, now);
        assertEquals(ErrorCode.NOT_FOUND.getCode(),
                assertThrows(DomainException.class, () -> revoked.assertAccessible(now)).getCode());

        FileShareAggregate exhausted = FileShareAggregate.reconstitute(3L, 1L, "a.txt",
                ShareToken.generate().value(), null, null, 3, 3, ShareStatus.ACTIVE, 9L, now, now);
        assertEquals(ErrorCode.NOT_FOUND.getCode(),
                assertThrows(DomainException.class, () -> exhausted.assertAccessible(now)).getCode());
    }

    @Test
    @DisplayName("密码校验：缺失与错误均抛 FORBIDDEN，无密码直接通过")
    void shouldCheckPassword() {
        LocalDateTime now = LocalDateTime.now();
        FileShareAggregate share = FileShareAggregate.reconstitute(1L, 1L, "a.txt",
                ShareToken.generate().value(), SharePassword.hash(PASSWORD), null, null, 0,
                ShareStatus.ACTIVE, 9L, now, now);

        share.assertPassword(PASSWORD);
        assertEquals(ErrorCode.FORBIDDEN.getCode(),
                assertThrows(DomainException.class, () -> share.assertPassword(null)).getCode());
        assertEquals(ErrorCode.FORBIDDEN.getCode(),
                assertThrows(DomainException.class, () -> share.assertPassword("wrong-pass")).getCode());

        FileShareAggregate open = FileShareAggregate.reconstitute(2L, 1L, "a.txt",
                ShareToken.generate().value(), null, null, null, 0, ShareStatus.ACTIVE, 9L, now, now);
        open.assertPassword(null);
    }

    @Test
    @DisplayName("管理校验：创建人本人或管理权限可管理，其余按不存在处理")
    void shouldCheckManageable() {
        LocalDateTime now = LocalDateTime.now();
        FileShareAggregate share = FileShareAggregate.reconstitute(1L, 1L, "a.txt",
                ShareToken.generate().value(), null, null, null, 0, ShareStatus.ACTIVE, 9L, now, now);

        share.assertManageableBy(9L, false);
        share.assertManageableBy(100L, true);
        assertEquals(ErrorCode.NOT_FOUND.getCode(),
                assertThrows(DomainException.class, () -> share.assertManageableBy(100L, false)).getCode());
    }
}
