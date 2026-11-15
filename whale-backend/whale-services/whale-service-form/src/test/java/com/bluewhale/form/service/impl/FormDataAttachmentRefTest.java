package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.mapper.FormAttachmentRefMapper;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormDataHistoryMapper;
import com.bluewhale.form.mapper.FormDataMapper;
import com.bluewhale.form.mapper.FormDefinitionMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.mapper.FormTableRegistryMapper;
import com.bluewhale.form.model.request.FormDataSubmitRequest;
import com.bluewhale.form.model.vo.FormAttachmentRefVO;
import com.bluewhale.form.po.FormAttachmentRefPo;
import com.bluewhale.form.po.FormDefinitionPo;
import com.bluewhale.form.po.FormTableRegistryPo;
import com.bluewhale.form.support.FormDataEngine;
import com.bluewhale.form.support.FormDataHistoryRecorder;
import com.bluewhale.form.support.FormMetadataCache;
import com.bluewhale.form.support.FormMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 附件引用索引维护与反查测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormDataAttachmentRefTest {

    private static final long TENANT = 1L;
    private static final String TABLE = "form_data_fm_file_v1";

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

    /**
     * 构造表单定义桩对象
     *
     * @return 表单定义
     */
    private static FormDefinitionPo definition() {
        FormDefinitionPo definition = new FormDefinitionPo();
        definition.setId(10L);
        definition.setCode("fm_file");
        definition.setVersion(1);
        definition.setSchemaJson("{\"fields\":[{\"key\":\"files\",\"label\":\"附件\",\"type\":\"attachment\"}]}");
        return definition;
    }

    /**
     * 构造物理表登记桩对象
     *
     * @return 登记实体
     */
    private static FormTableRegistryPo registry() {
        FormTableRegistryPo registry = new FormTableRegistryPo();
        registry.setDefinitionId(10L);
        registry.setDefinitionCode("fm_file");
        registry.setDefinitionVersion(1);
        registry.setPhysicalTable(TABLE);
        registry.setSchemaJson("{\"fields\":[{\"key\":\"files\",\"label\":\"附件\",\"type\":\"attachment\"}]}");
        return registry;
    }

    @Test
    @DisplayName("提交：按附件字段值写入引用索引")
    void shouldWriteAttachmentRefsOnSubmit() {
        when(formDefinitionMapper.selectById(10L)).thenReturn(definition());
        when(formTableRegistryMapper.selectOne(ArgumentMatchers.<Wrapper<FormTableRegistryPo>>any()))
                .thenReturn(registry());
        FormDataSubmitRequest request = new FormDataSubmitRequest();
        request.setDefinitionId(10L);
        request.setDataJson("{\"files\":[101,102,102]}");

        formDataService.submit(request, "lisi", TENANT);

        ArgumentCaptor<FormAttachmentRefPo> captor = ArgumentCaptor.forClass(FormAttachmentRefPo.class);
        verify(formAttachmentRefMapper, times(2)).insert(captor.capture());
        assertEquals(List.of(101L, 102L),
                captor.getAllValues().stream().map(FormAttachmentRefPo::getAttachmentId).toList());
        assertEquals("files", captor.getAllValues().get(0).getFieldKey());
        assertEquals(TENANT, captor.getAllValues().get(0).getTenantId());
    }

    @Test
    @DisplayName("反查：按附件ID返回引用列表")
    void shouldListAttachmentRefs() {
        FormAttachmentRefPo first = new FormAttachmentRefPo();
        first.setDataId(1L);
        first.setDefinitionCode("fm_file");
        first.setFieldKey("files");
        first.setAttachmentId(101L);
        when(formAttachmentRefMapper.selectList(ArgumentMatchers.<Wrapper<FormAttachmentRefPo>>any()))
                .thenReturn(List.of(first));

        List<FormAttachmentRefVO> refs = formDataService.listAttachmentRefs("101", TENANT);

        assertEquals(1, refs.size());
        assertEquals(1L, refs.get(0).getDataId());
        assertEquals("files", refs.get(0).getFieldKey());
    }

    @Test
    @DisplayName("反查：非数字附件ID返回参数错误")
    void shouldRejectNonNumericAttachmentId() {
        BizException e = assertThrows(BizException.class, () -> formDataService.listAttachmentRefs("abc", TENANT));

        assertEquals(400, e.getCode());
        verify(formAttachmentRefMapper, times(0)).selectList(any());
    }
}
