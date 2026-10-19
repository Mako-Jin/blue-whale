package com.bluewhale.form.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.form.dto.FormColumnMetaDTO;
import com.bluewhale.form.dto.FormTableDefinitionSaveRequest;
import com.bluewhale.form.entity.FormColumnMeta;
import com.bluewhale.form.entity.FormTableDefinition;
import com.bluewhale.form.mapper.FormColumnMetaMapper;
import com.bluewhale.form.mapper.FormTableDefinitionMapper;
import com.bluewhale.form.vo.FormTableDefinitionVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FormTableDefinitionServiceImpl} 表定义（含列）聚合 CRUD 单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class FormTableDefinitionServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;

    @Mock
    private FormTableDefinitionMapper formTableDefinitionMapper;

    @Mock
    private FormColumnMetaMapper formColumnMetaMapper;

    @InjectMocks
    private FormTableDefinitionServiceImpl formTableDefinitionService;

    /**
     * 构造列请求桩对象。
     *
     * @param code 列编码
     * @param dataType 存储类型
     * @return 列请求
     */
    private static FormColumnMetaDTO column(String code, String dataType) {
        FormColumnMetaDTO dto = new FormColumnMetaDTO();
        dto.setColumnCode(code);
        dto.setColumnName(code);
        dto.setDataType(dataType);
        return dto;
    }

    /**
     * 构造表定义保存请求桩对象。
     *
     * @param columns 列请求列表
     * @return 表定义保存请求
     */
    private static FormTableDefinitionSaveRequest saveRequest(List<FormColumnMetaDTO> columns) {
        FormTableDefinitionSaveRequest request = new FormTableDefinitionSaveRequest();
        request.setTableCode("biz_order");
        request.setTableName("业务订单");
        request.setColumns(columns);
        return request;
    }

    @Test
    @DisplayName("create：编码唯一时落库表定义并逐列落库")
    void shouldCreateTableAndColumns() {
        when(formTableDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableDefinition>>any()))
                .thenReturn(0L);

        FormTableDefinitionVO vo = formTableDefinitionService.create(
                saveRequest(List.of(column("amount", "number"), column("remark", "text"))));

        verify(formTableDefinitionMapper).insert(any(FormTableDefinition.class));
        verify(formColumnMetaMapper, times(2)).insert(any(FormColumnMeta.class));
        assertEquals("biz_order", vo.getTableCode());
        assertEquals(2, vo.getColumns().size());
    }

    @Test
    @DisplayName("create：表编码已存在抛参数错误且不落库")
    void shouldRejectDuplicateTableCode() {
        when(formTableDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableDefinition>>any()))
                .thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> formTableDefinitionService.create(saveRequest(List.of(column("amount", "number")))));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formTableDefinitionMapper, never()).insert(any(FormTableDefinition.class));
        verify(formColumnMetaMapper, never()).insert(any(FormColumnMeta.class));
    }

    @Test
    @DisplayName("create：非法存储类型抛参数错误")
    void shouldRejectInvalidDataType() {
        when(formTableDefinitionMapper.selectCount(ArgumentMatchers.<Wrapper<FormTableDefinition>>any()))
                .thenReturn(0L);

        BizException ex = assertThrows(BizException.class,
                () -> formTableDefinitionService.create(saveRequest(List.of(column("amount", "bad_type")))));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(formColumnMetaMapper, never()).insert(any(FormColumnMeta.class));
    }
}
