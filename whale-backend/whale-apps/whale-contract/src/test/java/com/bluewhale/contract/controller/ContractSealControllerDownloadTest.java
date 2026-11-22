package com.bluewhale.contract.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.contract.service.SealService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 印章原图导出接口：独立权限码、附件下载与禁止缓存响应头。
 *
 * @author BlueWhale
 */
class ContractSealControllerDownloadTest {

    /** 印章服务桩 */
    private SealService sealService;

    /** 被测 MVC */
    private MockMvc mockMvc;

    /**
     * 构建控制器与桩服务。
     */
    @BeforeEach
    void setUp() {
        sealService = mock(SealService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractSealController(sealService))
                .setApiVersionStrategy(ApiVersionTestSupport.strategy())
                .build();
    }

    /**
     * 导出接口必须声明独立下载权限，并写审计日志。
     *
     * @throws Exception 反射异常
     */
    @Test
    void downloadRequiresDedicatedPermissionAndAudit() throws Exception {
        Method method = ContractSealController.class.getMethod("download", Long.class);

        RequirePermission permission = method.getAnnotation(RequirePermission.class);
        assertNotNull(permission);
        assertEquals("sys:contract:seal:download", permission.value());
        assertNotNull(method.getAnnotation(AuditLog.class));
    }

    /**
     * 导出原图返回附件下载、no-store 与 nosniff，避免浏览器内联复用原章。
     *
     * @throws Exception 请求异常
     */
    @Test
    void downloadReturnsAttachmentWithoutCache() throws Exception {
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        when(sealService.downloadImage(5L)).thenReturn(new SealService.SealImage(
                png, "image/png", "seal-5.png"));

        mockMvc.perform(get("/contract/v1/seal/5/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("seal-5.png")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));
    }
}
