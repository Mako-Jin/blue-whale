package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormAttachmentRefMapper;
import com.bluewhale.form.mapper.FormDataHistoryMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.request.FormDataQueryRequest;
import com.bluewhale.form.model.vo.FormDataExportVO;
import com.bluewhale.form.po.FormDataPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataHistoryRecorder;
import com.bluewhale.form.support.FormMetadataCache;
import com.bluewhale.form.support.FormMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 表单数据导出脱敏单元测试：敏感字段按标记掩码，普通字段保持原值。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormDataExportSensitiveTest {

    private static final long TENANT = 1L;

    @Mock
    private FormDataMapper formDataMapper;

    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    @Mock
    private FormTableDefinitionMapper formTableDefinitionMapper;

    @Mock
    private FormTableRegistryMapper formTableRegistryMapper;

    @Mock
    private FormColumnMetaMapper formColumnMetaMapper;

    @Mock
    private FormDataHistoryMapper formDataHistoryMapper;

    @Mock
    private FormAttachmentRefMapper formAttachmentRefMapper;

    @Mock
    private FormDataHistoryRecorder formDataHistoryRecorder;

    @Mock
    private FormDataEngine formDataEngine;

    @Mock
    private FormMetrics formMetrics;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private FormMetadataCache metadataCache;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FormDataServiceImpl formDataService;

    @Test
    @DisplayName("export：敏感字段脱敏，普通字段保持原值")
    void shouldMaskSensitiveColumnOnExport() {
        FormDefinitionPo definition = new FormDefinitionPo();
        definition.setId(10L);
        definition.setCode("fm_person");
        definition.setName("人员登记");
        definition.setVersion(1);
        definition.setSchemaJson("{\"fields\":["
                + "{\"key\":\"phone\",\"label\":\"手机号\",\"type\":\"text\",\"sensitive\":true},"
                + "{\"key\":\"name\",\"label\":\"姓名\",\"type\":\"text\"}]}");
        when(formDefinitionMapper.selectList(any())).thenReturn(List.of(definition));

        FormDataPo data = new FormDataPo();
        data.setId(1L);
        data.setDefinitionCode("fm_person");
        data.setDefinitionVersion(1);
        data.setDataJson("{\"phone\":\"13812345678\",\"name\":\"张三\"}");
        Page<FormDataPo> page = new Page<>(1L, 10L);
        page.setRecords(List.of(data));
        when(formDataMapper.selectPage(any(), any())).thenReturn(page);

        FormDataQueryRequest request = new FormDataQueryRequest();
        request.setDefinitionCode("fm_person");
        FormDataExportVO vo = formDataService.export(request, TENANT, "csv");
        String content = new String(vo.getContent(), StandardCharsets.UTF_8);

        assertTrue(content.contains("138****5678"), "敏感字段应脱敏：" + content);
        assertFalse(content.contains("13812345678"), "敏感字段原值不应出现在导出结果");
        assertTrue(content.contains("张三"), "普通字段应保持原值");
    }
}
