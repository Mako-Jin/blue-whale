package com.bluewhale.auth.controller;

import com.bluewhale.auth.application.menu.MenuAppService;
import com.bluewhale.auth.application.menu.command.SaveMenuCommand;
import com.bluewhale.auth.dto.MenuRequest;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.vo.MenuVO;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link MenuController} 控制器层单元测试（standalone MockMvc）。
 *
 * <p>覆盖：每个端点的 happy path（{@code code=0} 与预期数据形状）、请求体校验失败
 * （项目统一参数错误体 {@code code=400}）、以及各端点权限/审计注解反射契约。</p>
 *
 * @author BlueWhale
 */
class MenuControllerTest {

    private static final long MENU_ID = 11L;
    private static final int PARAM_ERROR_CODE = 400;

    private final MenuAppService menuAppService = mock(MenuAppService.class);
    private final MenuController controller = new MenuController(menuAppService);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：注册全局异常处理器与校验器，使校验失败返回统一响应体
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    /**
     * 构造菜单视图对象
     *
     * @return 菜单视图对象
     */
    private static MenuVO menuVO() {
        MenuVO vo = new MenuVO();
        vo.setId(MENU_ID);
        vo.setParentId(0L);
        vo.setName("系统管理");
        return vo;
    }

    /**
     * 断言控制器方法声明了预期的权限码
     *
     * @param methodName 方法名
     * @param permValue 期望的权限码
     * @param parameterTypes 方法参数类型
     * @throws NoSuchMethodException 方法不存在时抛出
     */
    private static void assertPermission(String methodName, String permValue, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = MenuController.class.getMethod(methodName, parameterTypes);
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).as("@RequirePermission 缺失: %s", methodName).isNotNull();
        assertThat(annotation.value()).as("@RequirePermission value 不符: %s", methodName).isEqualTo(permValue);
    }

    /**
     * 断言控制器方法声明了审计注解
     *
     * @param methodName 方法名
     * @param parameterTypes 方法参数类型
     * @throws NoSuchMethodException 方法不存在时抛出
     */
    private static void assertAuditLog(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        AuditLog annotation = MenuController.class.getMethod(methodName, parameterTypes).getAnnotation(AuditLog.class);
        assertThat(annotation).as("@AuditLog 缺失: %s", methodName).isNotNull();
    }

    @Test
    @DisplayName("GET /menu/tree：返回 code=0 与菜单树")
    void treeShouldReturnMenuTree() throws Exception {
        when(menuAppService.tree()).thenReturn(List.of(menuVO()));

        mockMvc.perform(get("/menu/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].id").value(MENU_ID))
                .andExpect(jsonPath("$.data[0].name").value("系统管理"));

        verify(menuAppService).tree();
    }

    @Test
    @DisplayName("GET /menu/{id}：返回 code=0 与菜单详情")
    void detailShouldReturnMenu() throws Exception {
        when(menuAppService.getDetail(MENU_ID)).thenReturn(menuVO());

        mockMvc.perform(get("/menu/{id}", MENU_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.name").value("系统管理"));

        verify(menuAppService).getDetail(MENU_ID);
    }

    @Test
    @DisplayName("POST /menu：合法请求创建成功并返回 code=0")
    void createShouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"系统管理\",\"menuType\":\"DIR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(menuAppService).create(any(SaveMenuCommand.class));
    }

    @Test
    @DisplayName("PUT /menu/{id}：合法请求更新成功并返回 code=0")
    void updateShouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/menu/{id}", MENU_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"系统管理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(menuAppService).update(eq(MENU_ID), any(SaveMenuCommand.class));
    }

    @Test
    @DisplayName("DELETE /menu/{id}：删除成功并返回 code=0")
    void deleteShouldReturnSuccess() throws Exception {
        mockMvc.perform(delete("/menu/{id}", MENU_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.SUCCESS.getCode()));

        verify(menuAppService).delete(MENU_ID);
    }

    @Test
    @DisplayName("POST /menu：菜单名称空白触发校验失败，返回统一参数错误体且不调用服务")
    void createShouldRejectBlankName() throws Exception {
        mockMvc.perform(post("/menu")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PARAM_ERROR_CODE))
                .andExpect(jsonPath("$.message").value("菜单名称不能为空"));

        verify(menuAppService, never()).create(any());
    }

    @Test
    @DisplayName("权限契约：各端点 @RequirePermission 与写操作 @AuditLog 均就位")
    void endpointsShouldDeclarePermissionAndAuditContract() throws Exception {
        assertPermission("tree", "sys:menu:list");
        assertPermission("detail", "sys:menu:list", Long.class);
        assertPermission("create", "sys:menu:add", MenuRequest.class);
        assertPermission("update", "sys:menu:edit", Long.class, MenuRequest.class);
        assertPermission("delete", "sys:menu:delete", Long.class);

        assertAuditLog("create", MenuRequest.class);
        assertAuditLog("update", Long.class, MenuRequest.class);
        assertAuditLog("delete", Long.class);
    }
}
