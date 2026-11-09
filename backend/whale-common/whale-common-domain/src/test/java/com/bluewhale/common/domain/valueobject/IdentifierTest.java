package com.bluewhale.common.domain.valueobject;

import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 标识值对象的非空校验与值语义测试。
 */
class IdentifierTest {

    @Test
    void rejectsNullValue() {
        DomainException exception = assertThrows(DomainException.class, () -> new UserId(null));
        assertEquals(400, exception.getCode());
        assertEquals("标识值不能为空", exception.getMessage());
    }

    @Test
    void equalityIsBasedOnTypeAndValue() {
        assertEquals(new UserId(1L), new UserId(1L));
        assertEquals(new UserId(1L).hashCode(), new UserId(1L).hashCode());
        assertNotEquals(new UserId(1L), new UserId(2L));
        assertNotEquals(new UserId(1L), new TenantId(1L));
    }

    @Test
    void exposesValueAndReadableToString() {
        assertEquals(1L, new UserId(1L).getValue());
        assertEquals("UserId(1)", new UserId(1L).toString());
    }

    /**
     * 用户标识测试替身。
     */
    private static final class UserId extends Identifier<Long> {

        /** 序列化版本号 */
        private static final long serialVersionUID = 1L;

        /**
         * 构造器。
         *
         * @param value 用户 ID
         */
        private UserId(Long value) {
            super(value);
        }
    }

    /**
     * 租户标识测试替身：值相同但类型不同，用于验证值对象按类型区分。
     */
    private static final class TenantId extends Identifier<Long> {

        /** 序列化版本号 */
        private static final long serialVersionUID = 1L;

        /**
         * 构造器。
         *
         * @param value 租户 ID
         */
        private TenantId(Long value) {
            super(value);
        }
    }
}
