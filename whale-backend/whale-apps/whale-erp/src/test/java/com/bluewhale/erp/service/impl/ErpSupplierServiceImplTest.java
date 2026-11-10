package com.bluewhale.erp.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.erp.dto.ErpSupplierSaveRequest;
import com.bluewhale.erp.entity.ErpSupplier;
import com.bluewhale.erp.mapper.ErpSupplierMapper;
import com.bluewhale.erp.vo.ErpSupplierVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ErpSupplierServiceImpl} 单元测试。
 *
 * @author BlueWhale
 */
class ErpSupplierServiceImplTest {

    private final ErpSupplierMapper mapper = mock(ErpSupplierMapper.class);

    private final ErpSupplierServiceImpl service = new ErpSupplierServiceImpl(mapper);

    @Test
    @DisplayName("新增供应商成功并回填主键")
    void shouldCreateSupplier() {
        doAnswer(invocation -> {
            ErpSupplier supplier = invocation.getArgument(0);
            supplier.setId(101L);
            return 1;
        }).when(mapper).insert(any(ErpSupplier.class));

        ErpSupplierVO vo = service.create(request("SUP-101", "供应商101", "MATERIAL"));

        assertEquals(101L, vo.getId());
        assertEquals("SUP-101", vo.getCode());
        assertEquals("供应商101", vo.getName());
        assertEquals("MATERIAL", vo.getCategory());
        assertEquals("ENABLED", vo.getStatus());
    }

    @Test
    @DisplayName("供应商编码重复时拒绝新增")
    void shouldRejectDuplicateCode() {
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> service.create(request("SUP-101", "供应商101", "MATERIAL")));

        assertEquals("供应商编码已存在", exception.getMessage());
        verify(mapper, Mockito.never()).insert(any(ErpSupplier.class));
    }

    @Test
    @DisplayName("更新供应商成功")
    void shouldUpdateSupplier() {
        ErpSupplier existing = new ErpSupplier();
        existing.setId(101L);
        existing.setCode("SUP-101");
        existing.setName("旧名称");
        existing.setCategory("MATERIAL");
        existing.setStatus("ENABLED");
        when(mapper.selectById(101L)).thenReturn(existing);

        ErpSupplierVO vo = service.update(101L, request("SUP-101", "新名称", "SERVICE"));

        assertEquals("新名称", vo.getName());
        assertEquals("SERVICE", vo.getCategory());
        verify(mapper).updateById(existing);
    }

    @Test
    @DisplayName("供应商不存在时更新/删除抛出 NOT_FOUND")
    void shouldRejectMissingSupplier() {
        when(mapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> service.getDetail(999L));
        assertThrows(BizException.class,
                () -> service.update(999L, request("SUP-999", "不存在", "OTHER")));
        assertThrows(BizException.class, () -> service.delete(999L));
        verify(mapper, Mockito.never()).deleteById(999L);
    }

    /**
     * 构造保存请求。
     *
     * @param code     供应商编码
     * @param name     供应商名称
     * @param category 分类
     * @return 请求
     */
    private static ErpSupplierSaveRequest request(String code, String name, String category) {
        ErpSupplierSaveRequest request = new ErpSupplierSaveRequest();
        request.setCode(code);
        request.setName(name);
        request.setShortName(name + "简称");
        request.setCategory(category);
        request.setContactName("张三");
        request.setContactPhone("13800000001");
        request.setContactEmail("supplier@example.com");
        request.setTaxNo("91310000XXXXXXXX1A");
        request.setBankName("示例银行");
        request.setBankAccount("6222000000000001");
        request.setAddress("上海市浦东新区示例路 1 号");
        request.setStatus("ENABLED");
        request.setRating("A");
        request.setRemark("单测");
        return request;
    }
}
