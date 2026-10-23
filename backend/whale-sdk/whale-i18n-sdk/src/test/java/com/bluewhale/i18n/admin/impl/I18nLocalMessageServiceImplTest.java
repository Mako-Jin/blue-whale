package com.bluewhale.i18n.admin.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.i18n.I18nCacheEvictHelper;
import com.bluewhale.i18n.admin.dto.I18nMessageQueryRequest;
import com.bluewhale.i18n.admin.dto.I18nMessageSaveRequest;
import com.bluewhale.i18n.admin.vo.I18nMessageVO;
import com.bluewhale.i18n.entity.SysI18nMessage;
import com.bluewhale.i18n.mapper.SysI18nMessageMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link I18nLocalMessageServiceImpl} 本地化管理单元测试
 *
 * @author BlueWhale
 */
class I18nLocalMessageServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    private final SysI18nMessageMapper i18nMessageMapper = mock(SysI18nMessageMapper.class);

    private final I18nCacheEvictHelper cacheEvictHelper = mock(I18nCacheEvictHelper.class);

    private final I18nLocalMessageServiceImpl i18nLocalMessageService =
            new I18nLocalMessageServiceImpl(i18nMessageMapper, cacheEvictHelper);

    /**
     * 构造保存请求桩对象。
     *
     * @param key     消息编码
     * @param locale  语言环境
     * @param content 消息内容
     * @return 保存请求
     */
    private static I18nMessageSaveRequest saveRequest(String key, String locale, String content) {
        I18nMessageSaveRequest request = new I18nMessageSaveRequest();
        request.setMessageKey(key);
        request.setLocale(locale);
        request.setMessageContent(content);
        request.setModule("auth");
        return request;
    }

    @Test
    @DisplayName("page：按条件调用分页查询并映射结果")
    void shouldPageWithConditions() {
        SysI18nMessage entity = new SysI18nMessage();
        entity.setId(1L);
        entity.setMessageKey("login.success");
        entity.setLocale("zh_CN");
        entity.setMessageContent("登录成功");
        Page<SysI18nMessage> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(i18nMessageMapper.selectPage(ArgumentMatchers.<Page<SysI18nMessage>>any(),
                ArgumentMatchers.<Wrapper<SysI18nMessage>>any())).thenReturn(page);

        I18nMessageQueryRequest request = new I18nMessageQueryRequest();
        request.setKeyword("login");
        request.setLocale("zh_CN");
        PageResult<I18nMessageVO> result = i18nLocalMessageService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("login.success", result.getRecords().get(0).getMessageKey());
        assertNotNull(result.getRecords().get(0).getMessageContent());
    }

    @Test
    @DisplayName("page：单页条数收敛到上限 200")
    void shouldClampPageSize() {
        Page<SysI18nMessage> page = new Page<>(1L, 200L);
        page.setRecords(List.of());
        page.setTotal(0L);
        when(i18nMessageMapper.selectPage(ArgumentMatchers.<Page<SysI18nMessage>>any(),
                ArgumentMatchers.<Wrapper<SysI18nMessage>>any())).thenReturn(page);

        I18nMessageQueryRequest request = new I18nMessageQueryRequest();
        request.setSize(9999L);
        i18nLocalMessageService.page(request);

        ArgumentCaptor<Page<SysI18nMessage>> captor = ArgumentCaptor.forClass(Page.class);
        verify(i18nMessageMapper).selectPage(captor.capture(), ArgumentMatchers.<Wrapper<SysI18nMessage>>any());
        assertEquals(200L, captor.getValue().getSize());
    }

    @Test
    @DisplayName("create：落库并清理对应缓存键")
    void shouldCreateAndEvictCache() {
        when(i18nMessageMapper.selectCount(ArgumentMatchers.<Wrapper<SysI18nMessage>>any())).thenReturn(0L);
        SysI18nMessage saved = new SysI18nMessage();
        saved.setId(100L);
        saved.setMessageKey("order.paid");
        saved.setLocale("zh_CN");
        saved.setMessageContent("订单已支付");
        when(i18nMessageMapper.selectById(any())).thenReturn(saved);

        I18nMessageVO vo = i18nLocalMessageService.create(saveRequest("order.paid", "zh_CN", "订单已支付"));

        assertEquals("order.paid", vo.getMessageKey());
        verify(i18nMessageMapper).insert(any(SysI18nMessage.class));
        verify(cacheEvictHelper).evict(eq("order.paid"), eq("zh_CN"));
    }

    @Test
    @DisplayName("create：唯一键冲突抛参数错误且不落库")
    void shouldRejectDuplicateCreate() {
        when(i18nMessageMapper.selectCount(ArgumentMatchers.<Wrapper<SysI18nMessage>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> i18nLocalMessageService.create(saveRequest("login.success", "zh_CN", "登录成功")));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(i18nMessageMapper, never()).insert(any(SysI18nMessage.class));
        verify(cacheEvictHelper, never())
                .evict(ArgumentMatchers.<String>any(), ArgumentMatchers.<String>any());
    }

    @Test
    @DisplayName("update：键未变更时仅清理一次缓存")
    void shouldUpdateWithSameKeyEvictOnce() {
        SysI18nMessage existing = new SysI18nMessage();
        existing.setId(8L);
        existing.setMessageKey("login.success");
        existing.setLocale("zh_CN");
        when(i18nMessageMapper.selectById(8L)).thenReturn(existing);
        when(i18nMessageMapper.selectCount(ArgumentMatchers.<Wrapper<SysI18nMessage>>any())).thenReturn(0L);

        i18nLocalMessageService.update(8L, saveRequest("login.success", "zh_CN", "登录成功（新）"));

        verify(i18nMessageMapper).updateById(any(SysI18nMessage.class));
        verify(cacheEvictHelper, times(1)).evict(eq("login.success"), eq("zh_CN"));
    }

    @Test
    @DisplayName("update：编码变更时同时清理新旧缓存键")
    void shouldUpdateAndEvictBothKeys() {
        SysI18nMessage existing = new SysI18nMessage();
        existing.setId(8L);
        existing.setMessageKey("login.success");
        existing.setLocale("zh_CN");
        when(i18nMessageMapper.selectById(8L)).thenReturn(existing);
        when(i18nMessageMapper.selectCount(ArgumentMatchers.<Wrapper<SysI18nMessage>>any())).thenReturn(0L);

        i18nLocalMessageService.update(8L, saveRequest("auth.login.success", "zh_CN", "登录成功"));

        verify(cacheEvictHelper).evict(eq("login.success"), eq("zh_CN"));
        verify(cacheEvictHelper).evict(eq("auth.login.success"), eq("zh_CN"));
    }

    @Test
    @DisplayName("getDetail：消息不存在抛 NOT_FOUND")
    void shouldThrowWhenDetailMissing() {
        when(i18nMessageMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> i18nLocalMessageService.getDetail(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("delete：逻辑删除并清理缓存")
    void shouldDeleteAndEvictCache() {
        SysI18nMessage existing = new SysI18nMessage();
        existing.setId(7L);
        existing.setMessageKey("login.success");
        existing.setLocale("en_US");
        when(i18nMessageMapper.selectById(7L)).thenReturn(existing);

        i18nLocalMessageService.delete(7L);

        verify(i18nMessageMapper).deleteById(7L);
        verify(cacheEvictHelper).evict(eq("login.success"), eq("en_US"));
    }

    @Test
    @DisplayName("delete：消息不存在抛 NOT_FOUND 且不删除")
    void shouldThrowWhenDeleteMissing() {
        when(i18nMessageMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> i18nLocalMessageService.delete(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
        verify(i18nMessageMapper, never()).deleteById(any());
    }
}
