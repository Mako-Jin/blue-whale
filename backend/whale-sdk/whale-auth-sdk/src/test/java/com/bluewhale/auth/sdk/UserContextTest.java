package com.bluewhale.auth.sdk;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录用户上下文与权限注解默认值测试。
 */
class UserContextTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void setGetAndClearInCurrentThread() {
        UserContext.setUser("alice");
        assertEquals("alice", UserContext.getUser());
        UserContext.clear();
        assertNull(UserContext.getUser());
    }

    @Test
    void contextIsIsolatedBetweenThreads() throws InterruptedException {
        UserContext.setUser("alice");
        AtomicReference<Object> captured = new AtomicReference<>("unset");
        Thread thread = new Thread(() -> captured.set(UserContext.getUser()));
        thread.start();
        thread.join();
        assertNull(captured.get());
        assertEquals("alice", UserContext.getUser());
    }

    @Test
    void requirePermissionExposesDeclaredValues() throws NoSuchMethodException {
        RequirePermission defaults = SecuredResource.class.getDeclaredMethod("list")
                .getAnnotation(RequirePermission.class);
        assertEquals("", defaults.value());
        assertTrue(defaults.requireLogin());

        RequirePermission explicit = SecuredResource.class.getDeclaredMethod("detail")
                .getAnnotation(RequirePermission.class);
        assertEquals("oa:leave:view", explicit.value());
        assertFalse(explicit.requireLogin());
    }

    /**
     * 权限注解测试载体。
     */
    private static final class SecuredResource {

        /**
         * 使用默认权限注解。
         */
        @RequirePermission
        void list() {
        }

        /**
         * 使用显式权限注解。
         */
        @RequirePermission(value = "oa:leave:view", requireLogin = false)
        void detail() {
        }
    }
}
