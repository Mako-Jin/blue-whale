package com.bluewhale.form.controller;

import com.bluewhale.form.service.FormDefinitionService;
import com.bluewhale.form.service.FormPublishService;
import com.bluewhale.form.vo.FormDefinitionVO;
import com.bluewhale.form.vo.FormDefinitionVersionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FormController} 复制、版本列表与回滚接口单元测试（standalone MockMvc）。
 *
 * @author BlueWhale
 */
class FormControllerTest {

    private final FormDefinitionService formDefinitionService = mock(FormDefinitionService.class);

    private final FormPublishService formPublishService = mock(FormPublishService.class);

    private final FormController controller = new FormController(formDefinitionService, formPublishService);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc：仅注册控制器与校验器，不装配权限拦截器
     */
    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
    }

    /**
     * 构造表单定义视图对象
     *
     * @param code 表单编码
     * @param name 表单名称
     * @param version 版本号
     * @return 表单定义视图对象
     */
    private static FormDefinitionVO definitionVo(String code, String name, int version) {
        FormDefinitionVO vo = new FormDefinitionVO();
        vo.setId(2L);
        vo.setCode(code);
        vo.setName(name);
        vo.setVersion(version);
        vo.setStatus(1);
        return vo;
    }

    @Test
    @DisplayName("POST /form/definition/{id}/copy：body 可空并返回新定义 JSON")
    void copyShouldReturnDefinition() throws Exception {
        when(formDefinitionService.copy(eq(1L), isNull()))
                .thenReturn(definitionVo("form-a_copy", "A表单（副本）", 1));

        mockMvc.perform(post("/form/definition/{id}/copy", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(2L))
                .andExpect(jsonPath("$.data.code").value("form-a_copy"))
                .andExpect(jsonPath("$.data.name").value("A表单（副本）"))
                .andExpect(jsonPath("$.data.version").value(1));

        verify(formDefinitionService).copy(eq(1L), isNull());
    }

    @Test
    @DisplayName("GET /form/definition/{id}/versions：返回版本号列表 JSON")
    void versionsShouldReturnList() throws Exception {
        FormDefinitionVersionVO latest = new FormDefinitionVersionVO();
        latest.setVersion(3);
        FormDefinitionVersionVO older = new FormDefinitionVersionVO();
        older.setVersion(2);
        when(formDefinitionService.listVersions(1L)).thenReturn(List.of(latest, older));

        mockMvc.perform(get("/form/definition/{id}/versions", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].version").value(3))
                .andExpect(jsonPath("$.data[1].version").value(2));

        verify(formDefinitionService).listVersions(1L);
    }

    @Test
    @DisplayName("POST /form/definition/{id}/rollback/{version}：返回回滚后的定义 JSON")
    void rollbackShouldReturnDefinition() throws Exception {
        when(formDefinitionService.rollback(1L, 2)).thenReturn(definitionVo("form-a", "旧版", 4));

        mockMvc.perform(post("/form/definition/{id}/rollback/{version}", 1L, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("form-a"))
                .andExpect(jsonPath("$.data.name").value("旧版"))
                .andExpect(jsonPath("$.data.version").value(4));

        verify(formDefinitionService).rollback(1L, 2);
    }
}
