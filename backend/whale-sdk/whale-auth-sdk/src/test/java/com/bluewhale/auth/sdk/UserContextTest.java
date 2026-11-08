package com.bluewhale.auth.sdk;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link UserContext} 与 {@link RequirePermission} 单元测试。
 *
 * @author BlueWhale
 */
class UserContextTest {

    /**
     * 清理线程上下文，避免污染其它用例。
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("UserContext：set/get/clear 生命周期")
    void shouldSetGetAndClearUser() {
        assertNull(UserContext.getUser());

        Object user = new Object();
        UserContext.setUser(user);
        assertSame(user, UserContext.getUser());

        UserContext.clear();
        assertNull(UserContext.getUser());
    }

    @Test
    @DisplayName("RequirePermission：默认不填权限码且要求登录")
    void shouldExposeAnnotationDefaults() throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod("read");
        RequirePermission permission = method.getAnnotation(RequirePermission.class);

        assertEquals("", permission.value());
        assertEquals(true, permission.requireLogin());

        Method write = SampleController.class.getDeclaredMethod("write");
        RequirePermission writePermission = write.getAnnotation(RequirePermission.class);
        assertEquals("sys:demo:write", writePermission.value());
    }

    /** 注解反射用样例类 */
    private static class SampleController {
        @RequirePermission
        void read() {
        }

        @RequirePermission("sys:demo:write")
        void write() {
        }
    }
}
