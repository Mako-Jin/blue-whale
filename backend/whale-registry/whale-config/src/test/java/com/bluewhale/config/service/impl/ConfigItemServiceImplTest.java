package com.bluewhale.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.config.dto.ConfigItemCreateRequest;
import com.bluewhale.config.dto.ConfigItemQueryRequest;
import com.bluewhale.config.dto.ConfigItemRollbackRequest;
import com.bluewhale.config.dto.ConfigItemUpdateRequest;
import com.bluewhale.config.dto.OperatorInfo;
import com.bluewhale.config.entity.ConfigItem;
import com.bluewhale.config.entity.ConfigItemHistory;
import com.bluewhale.config.mapper.ConfigItemHistoryMapper;
import com.bluewhale.config.mapper.ConfigItemMapper;
import com.bluewhale.config.vo.ConfigItemHistoryVO;
import com.bluewhale.config.vo.ConfigItemVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConfigItemServiceImpl} 分页、创建校验、值变更版本、回滚与历史单元测试。
 *
 * @author BlueWhale
 */
class ConfigItemServiceImplTest {

    private static final int PARAM_ERROR_CODE = 400;
    private static final int NOT_FOUND_CODE = 404;

    private final ConfigItemMapper configItemMapper = mock(ConfigItemMapper.class);

    private final ConfigItemHistoryMapper configItemHistoryMapper = mock(ConfigItemHistoryMapper.class);

    private final ConfigItemServiceImpl configItemService =
            new ConfigItemServiceImpl(configItemMapper, configItemHistoryMapper);

    /**
     * 构造配置项实体桩对象。
     *
     * @param id      主键ID
     * @param key     配置键
     * @param value   配置值
     * @param version 版本号
     * @return 配置项实体
     */
    private static ConfigItem item(Long id, String key, String value, int version) {
        ConfigItem entity = new ConfigItem();
        entity.setId(id);
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        entity.setValueType("STRING");
        entity.setVersion(version);
        entity.setStatus(1);
        return entity;
    }

    /**
     * 构造创建请求桩对象。
     *
     * @param key 配置键
     * @return 创建请求
     */
    private static ConfigItemCreateRequest createRequest(String key) {
        ConfigItemCreateRequest request = new ConfigItemCreateRequest();
        request.setConfigKey(key);
        request.setConfigValue("v1");
        return request;
    }

    @Test
    @DisplayName("page：按条件分页查询并映射结果")
    void shouldPageWithConditions() {
        ConfigItem entity = item(1L, "order.pay.timeout", "30", 2);
        Page<ConfigItem> page = new Page<>(1L, 10L);
        page.setRecords(List.of(entity));
        page.setTotal(1L);
        when(configItemMapper.selectPage(ArgumentMatchers.<Page<ConfigItem>>any(),
                ArgumentMatchers.<Wrapper<ConfigItem>>any())).thenReturn(page);

        ConfigItemQueryRequest request = new ConfigItemQueryRequest();
        request.setKeyword("order");
        PageResult<ConfigItemVO> result = configItemService.page(request);

        assertEquals(1L, result.getTotal());
        assertEquals("order.pay.timeout", result.getRecords().get(0).getConfigKey());
        assertEquals(2, result.getRecords().get(0).getVersion());
    }

    @Test
    @DisplayName("getDetail：配置项不存在抛 NOT_FOUND")
    void shouldThrowWhenDetailMissing() {
        when(configItemMapper.selectById(99L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> configItemService.getDetail(99L));

        assertEquals(NOT_FOUND_CODE, ex.getCode());
    }

    @Test
    @DisplayName("create：配置键冲突抛参数错误且不落库")
    void shouldRejectDuplicateKey() {
        when(configItemMapper.selectCount(ArgumentMatchers.<Wrapper<ConfigItem>>any())).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> configItemService.create(createRequest("order.pay.timeout"), null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(configItemMapper, never()).insert(any(ConfigItem.class));
        verify(configItemHistoryMapper, never()).insert(any(ConfigItemHistory.class));
    }

    @Test
    @DisplayName("create：成功落库置版本 1 并落 CREATE 历史")
    void shouldCreateWithInitialVersionAndHistory() {
        when(configItemMapper.selectCount(ArgumentMatchers.<Wrapper<ConfigItem>>any())).thenReturn(0L);
        when(configItemMapper.selectById(any())).thenReturn(item(100L, "order.pay.timeout", "30", 1));

        ConfigItemCreateRequest request = createRequest("order.pay.timeout");
        request.setConfigValue("30");
        request.setValueType("number");
        OperatorInfo operator = OperatorInfo.from("42", "admin");
        ConfigItemVO vo = configItemService.create(request, operator);

        assertEquals(1, vo.getVersion());
        ArgumentCaptor<ConfigItem> captor = ArgumentCaptor.forClass(ConfigItem.class);
        verify(configItemMapper).insert(captor.capture());
        assertEquals("NUMBER", captor.getValue().getValueType());
        ArgumentCaptor<ConfigItemHistory> historyCaptor = ArgumentCaptor.forClass(ConfigItemHistory.class);
        verify(configItemHistoryMapper).insert(historyCaptor.capture());
        assertEquals("CREATE", historyCaptor.getValue().getOperation());
        assertEquals(1, historyCaptor.getValue().getVersion());
        assertNull(historyCaptor.getValue().getOldValue());
        assertEquals(42L, historyCaptor.getValue().getOperatorId());
        assertEquals("admin", historyCaptor.getValue().getOperator());
    }

    @Test
    @DisplayName("create：NUMBER 类型值非法拒绝创建")
    void shouldRejectInvalidNumberValue() {
        ConfigItemCreateRequest request = createRequest("order.pay.timeout");
        request.setValueType("NUMBER");
        request.setConfigValue("abc");

        BizException ex = assertThrows(BizException.class,
                () -> configItemService.create(request, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(configItemMapper, never()).insert(any(ConfigItem.class));
    }

    @Test
    @DisplayName("create：BOOLEAN 类型值非法拒绝创建")
    void shouldRejectInvalidBooleanValue() {
        ConfigItemCreateRequest request = createRequest("feature.enabled");
        request.setValueType("BOOLEAN");
        request.setConfigValue("yes");

        BizException ex = assertThrows(BizException.class,
                () -> configItemService.create(request, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(configItemMapper, never()).insert(any(ConfigItem.class));
    }

    @Test
    @DisplayName("create：状态仅支持 0/1")
    void shouldRejectInvalidStatus() {
        ConfigItemCreateRequest request = createRequest("feature.enabled");
        request.setStatus(2);

        BizException ex = assertThrows(BizException.class,
                () -> configItemService.create(request, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("update：值变更版本号 +1 并落 UPDATE 历史")
    void shouldBumpVersionOnValueChange() {
        ConfigItem existing = item(1L, "order.pay.timeout", "30", 2);
        ConfigItem updated = item(1L, "order.pay.timeout", "60", 3);
        when(configItemMapper.selectById(1L)).thenReturn(existing, updated);
        when(configItemMapper.updateById(any(ConfigItem.class))).thenReturn(1);

        ConfigItemUpdateRequest request = new ConfigItemUpdateRequest();
        request.setConfigValue("60");
        ConfigItemVO vo = configItemService.update(1L, request, null);

        assertEquals(3, vo.getVersion());
        ArgumentCaptor<ConfigItemHistory> captor = ArgumentCaptor.forClass(ConfigItemHistory.class);
        verify(configItemHistoryMapper).insert(captor.capture());
        assertEquals("UPDATE", captor.getValue().getOperation());
        assertEquals("30", captor.getValue().getOldValue());
        assertEquals("60", captor.getValue().getNewValue());
    }

    @Test
    @DisplayName("update：仅改说明不产生版本与历史")
    void shouldNotBumpVersionWithoutValueChange() {
        ConfigItem existing = item(1L, "order.pay.timeout", "30", 2);
        ConfigItem updated = item(1L, "order.pay.timeout", "30", 2);
        updated.setDescription("支付超时秒数");
        when(configItemMapper.selectById(1L)).thenReturn(existing, updated);
        when(configItemMapper.updateById(any(ConfigItem.class))).thenReturn(1);

        ConfigItemUpdateRequest request = new ConfigItemUpdateRequest();
        request.setDescription("支付超时秒数");
        ConfigItemVO vo = configItemService.update(1L, request, null);

        assertEquals(2, vo.getVersion());
        verify(configItemHistoryMapper, never()).insert(any(ConfigItemHistory.class));
    }

    @Test
    @DisplayName("update：NUMBER 类型新值非法拒绝更新且不落库")
    void shouldRejectInvalidNumberOnUpdate() {
        when(configItemMapper.selectById(1L)).thenReturn(item(1L, "order.pay.timeout", "30", 2));

        ConfigItemUpdateRequest request = new ConfigItemUpdateRequest();
        request.setConfigValue("not-a-number");
        request.setValueType("NUMBER");

        BizException ex = assertThrows(BizException.class,
                () -> configItemService.update(1L, request, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(configItemMapper, never()).updateById(any(ConfigItem.class));
    }

    @Test
    @DisplayName("delete：逻辑删除并落 DELETE 历史")
    void shouldDeleteWithHistory() {
        when(configItemMapper.selectById(1L)).thenReturn(item(1L, "order.pay.timeout", "30", 2));

        configItemService.delete(1L, null);

        verify(configItemMapper).deleteById(1L);
        ArgumentCaptor<ConfigItemHistory> captor = ArgumentCaptor.forClass(ConfigItemHistory.class);
        verify(configItemHistoryMapper).insert(captor.capture());
        assertEquals("DELETE", captor.getValue().getOperation());
        assertNull(captor.getValue().getNewValue());
    }

    @Test
    @DisplayName("listHistory：按版本倒序返回并映射视图对象")
    void shouldListHistory() {
        ConfigItemHistory history = new ConfigItemHistory();
        history.setId(900L);
        history.setItemId(1L);
        history.setConfigKey("order.pay.timeout");
        history.setOperation("UPDATE");
        history.setOldValue("30");
        history.setNewValue("60");
        history.setVersion(3);
        Page<ConfigItemHistory> page = new Page<>(1L, 200L);
        page.setRecords(List.of(history));
        page.setTotal(1L);
        when(configItemMapper.selectById(1L)).thenReturn(item(1L, "order.pay.timeout", "60", 3));
        when(configItemHistoryMapper.selectPage(ArgumentMatchers.<Page<ConfigItemHistory>>any(),
                ArgumentMatchers.<Wrapper<ConfigItemHistory>>any())).thenReturn(page);

        List<ConfigItemHistoryVO> list = configItemService.listHistory(1L);

        assertEquals(1, list.size());
        assertEquals("UPDATE", list.get(0).getOperation());
        assertEquals(3, list.get(0).getVersion());
    }

    @Test
    @DisplayName("rollback：目标版本等于当前版本拒绝回滚")
    void shouldRejectRollbackToCurrentVersion() {
        when(configItemMapper.selectById(1L)).thenReturn(item(1L, "order.pay.timeout", "60", 3));

        ConfigItemRollbackRequest request = new ConfigItemRollbackRequest();
        request.setTargetVersion(3);

        BizException ex = assertThrows(BizException.class,
                () -> configItemService.rollback(1L, request, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(configItemMapper, never()).updateById(any(ConfigItem.class));
    }

    @Test
    @DisplayName("rollback：目标版本超出范围拒绝回滚")
    void shouldRejectRollbackOutOfRange() {
        when(configItemMapper.selectById(1L)).thenReturn(item(1L, "order.pay.timeout", "60", 3));

        ConfigItemRollbackRequest request = new ConfigItemRollbackRequest();
        request.setTargetVersion(9);

        BizException ex = assertThrows(BizException.class,
                () -> configItemService.rollback(1L, request, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
    }

    @Test
    @DisplayName("rollback：目标版本无值（DELETE 历史）拒绝回滚")
    void shouldRejectRollbackToValuelessVersion() {
        when(configItemMapper.selectById(1L)).thenReturn(item(1L, "order.pay.timeout", "60", 3));
        when(configItemHistoryMapper.selectOne(ArgumentMatchers.<Wrapper<ConfigItemHistory>>any()))
                .thenReturn(null);

        ConfigItemRollbackRequest request = new ConfigItemRollbackRequest();
        request.setTargetVersion(2);

        BizException ex = assertThrows(BizException.class,
                () -> configItemService.rollback(1L, request, null));

        assertEquals(PARAM_ERROR_CODE, ex.getCode());
        verify(configItemMapper, never()).updateById(any(ConfigItem.class));
    }

    @Test
    @DisplayName("rollback：成功恢复目标值并落 ROLLBACK 历史（版本继续 +1）")
    void shouldRollbackToTargetVersion() {
        ConfigItem existing = item(1L, "order.pay.timeout", "60", 3);
        ConfigItem rolledBack = item(1L, "order.pay.timeout", "30", 4);
        when(configItemMapper.selectById(1L)).thenReturn(existing, rolledBack);
        when(configItemMapper.updateById(any(ConfigItem.class))).thenReturn(1);
        ConfigItemHistory target = new ConfigItemHistory();
        target.setId(900L);
        target.setItemId(1L);
        target.setConfigKey("order.pay.timeout");
        target.setOperation("UPDATE");
        target.setNewValue("30");
        target.setVersion(2);
        when(configItemHistoryMapper.selectOne(ArgumentMatchers.<Wrapper<ConfigItemHistory>>any()))
                .thenReturn(target);

        ConfigItemRollbackRequest request = new ConfigItemRollbackRequest();
        request.setTargetVersion(2);
        ConfigItemVO vo = configItemService.rollback(1L, request, OperatorInfo.from("42", "admin"));

        assertEquals("30", vo.getConfigValue());
        assertEquals(4, vo.getVersion());
        ArgumentCaptor<ConfigItemHistory> captor = ArgumentCaptor.forClass(ConfigItemHistory.class);
        verify(configItemHistoryMapper).insert(captor.capture());
        assertEquals("ROLLBACK", captor.getValue().getOperation());
        assertEquals("60", captor.getValue().getOldValue());
        assertEquals("30", captor.getValue().getNewValue());
        assertEquals(4, captor.getValue().getVersion());
    }
}
