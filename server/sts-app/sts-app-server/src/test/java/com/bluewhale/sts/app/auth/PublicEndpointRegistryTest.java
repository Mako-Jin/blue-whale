package com.bluewhale.sts.app.auth;

import com.bluewhale.sts.auth.authz.RequiresPermission;
import com.bluewhale.sts.auth.common.AuthPublicEndpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「哪些端点不参与权限判定」的守卫。
 *
 * <p>{@code AuthInterceptor} 的规则是**白名单反着写的**：方法或类上**没有**
 * {@code @RequiresPermission} 就一律放行（{@code docs/decisions/0026} 点名的根因）。
 * 规则有意如此（登录、健康检查、上报不能被要求权限码），但代价是
 * **新加一个端点而忘了写注解 = 静默公开** —— 不报错、不告警、评审时也看不出来。
 * 本片把那半边白名单收成 {@link AuthPublicEndpoints} 并在这里钉住：
 * 每个 {@code /api/**} 端点**要么有权限注解、要么在登记表里，恰好二选一**。
 *
 * <p><b>为什么枚举用的是框架的映射表，而不是扫描源码：</b>实现本片时我先用文本匹配
 * 扫了一遍 Controller，**三次给出三个不同的答案**（第一次把 {@code /api/auth/login}
 * 归成"有注解"，因为窗口里串到了上一个方法的注解；第二次连路由名都解析错了）。
 * 注解与"最终注册了哪条路由"之间隔着组合注解、类级 {@code @RequestMapping} 前缀、
 * 方法级路径拼接 —— 这些正是框架在做的事，重写一遍就是重写一个会漂移的副本。
 * 这里与 {@code AuthInterceptor} 读的是**同一个**映射表、用的是**同一个**
 * {@code AnnotatedElementUtils.findMergedAnnotation}，因此守卫与被守的规则同源。
 */
@SpringBootTest
class PublicEndpointRegistryTest {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("每个 /api 端点：有权限注解 或 登记为「不判权限」，恰好二选一")
    void everyApiEndpointShouldBeClassified() {
        Set<String> declared = new TreeSet<>(AuthPublicEndpoints.ALL);
        Set<String> unannotated = new TreeSet<>();
        Set<String> annotatedButDeclared = new TreeSet<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            boolean guarded = hasPermissionAnnotation(handler);

            for (String pattern : entry.getKey().getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                for (String method : httpMethodsOf(entry.getKey())) {
                    String key = method + " " + pattern;
                    if (guarded) {
                        if (declared.contains(key)) {
                            annotatedButDeclared.add(key);
                        }
                    } else {
                        unannotated.add(key);
                    }
                }
            }
        }

        assertThat(unannotated)
                .as("""
                        有端点不带 @RequiresPermission 却没登记 —— 它此刻是**静默公开**的。
                        两条路：① 它该受权限保护 → 加上 @RequiresPermission；
                        ② 它确实不该判权限（登录 / 健康检查 / 上报 / 自助）→ 登记进 AuthPublicEndpoints。"""
                        + "\n未登记：%s\n已登记：%s", diff(unannotated, declared), diff(declared, unannotated))
                .isEqualTo(declared);

        assertThat(annotatedButDeclared)
                .as("已登记为「不判权限」，但实际带了 @RequiresPermission —— 登记表已经过期，"
                        + "留着它等于留下一条不会被执行的假清单")
                .isEmpty();
    }

    @Test
    @DisplayName("登记表的形状：方法 + 空格 + 路径，且没有重复")
    void registryShapeShouldBeStable() {
        assertThat(AuthPublicEndpoints.ALL)
                .as("形状不统一会让守卫的相等比较失去意义（写法一变就匹配不上，而匹配不上更该报错）")
                .allMatch(value -> AuthPublicEndpoints.SHAPE.matcher(value).matches())
                .doesNotHaveDuplicates();
    }

    /** 与 {@code AuthInterceptor} 判定所用的**同一套**方法级 / 类级查找 */
    private boolean hasPermissionAnnotation(HandlerMethod handler) {
        return AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequiresPermission.class) != null
                || AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequiresPermission.class) != null;
    }

    /**
     * 取出映射声明的 HTTP 方法。
     *
     * <p>只在本片关心的 {@code /api/**} 上被调用 —— 框架自带的 {@code /error} 等映射
     * 不限定方法，那在本片射程之外。限定方法为空的 {@code /api} 映射在本仓不存在；
     * 真出现了就让它在这里显形，而不是被静默跳过一个端点。
     */
    private Set<String> httpMethodsOf(RequestMappingInfo info) {
        Set<String> methods = info.getMethodsCondition().getMethods().stream()
                .map(Enum::name)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(methods)
                .as("发现没有限定 HTTP 方法的映射 —— 它对所有方法可达，登记键无法表达，请先收窄")
                .isNotEmpty();
        return methods;
    }

    private Set<String> diff(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result.isEmpty() ? Set.of() : result;
    }

}
