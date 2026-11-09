package com.bluewhale.iot.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.iot.config.IotProperties;
import com.bluewhale.iot.dto.RuleCondition;
import com.bluewhale.iot.dto.RuleSaveRequest;
import com.bluewhale.iot.dto.RuleVO;
import com.bluewhale.iot.entity.IotDevice;
import com.bluewhale.iot.entity.IotProduct;
import com.bluewhale.iot.entity.IotRule;
import com.bluewhale.iot.mapper.IotDeviceMapper;
import com.bluewhale.iot.mapper.IotProductMapper;
import com.bluewhale.iot.mapper.IotRuleMapper;
import com.bluewhale.iot.support.JsonCodec;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规则服务单测：作用域校验、条件序列化与启停。
 *
 * @author BlueWhale
 */
class IotRuleServiceTest {

    private static final Long PRODUCT_ID = 3L;

    private static final Long DEVICE_ID = 7L;

    private static final Long RULE_ID = 11L;

    private IotRuleMapper ruleMapper;

    private IotProductMapper productMapper;

    private IotDeviceMapper deviceMapper;

    private IotProperties properties;

    private IotRuleService service;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 更新条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), IotRule.class);
    }

    @BeforeEach
    void setUp() {
        ruleMapper = mock(IotRuleMapper.class);
        productMapper = mock(IotProductMapper.class);
        deviceMapper = mock(IotDeviceMapper.class);
        properties = new IotProperties();
        properties.setRuleSilenceSeconds(300);
        service = new IotRuleService(ruleMapper, productMapper, deviceMapper, properties);
    }

    @Test
    void createRejectsUnknownProduct() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.create(request(PRODUCT_ID, null)));
        assertEquals("产品不存在", error.getMessage());
        verify(ruleMapper, never()).insert(any(IotRule.class));
    }

    @Test
    void createRejectsDeviceOfAnotherProduct() {
        IotProduct product = product();
        IotDevice device = device();
        device.setProductId(99L);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device);

        BizException error = assertThrows(BizException.class, () -> service.create(request(PRODUCT_ID, DEVICE_ID)));
        assertEquals("设备不属于所选产品", error.getMessage());
    }

    @Test
    void createPersistsConditionAndDefaultActionConfig() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());

        service.create(request(PRODUCT_ID, null));

        ArgumentCaptor<IotRule> captor = ArgumentCaptor.forClass(IotRule.class);
        verify(ruleMapper).insert(captor.capture());
        IotRule saved = captor.getValue();
        assertEquals(1, saved.getEnabled());
        assertEquals(0L, saved.getFiredCount());
        assertEquals("alert", saved.getActionType());
        RuleCondition condition = IotRuleService.readCondition(saved.getConditionJson());
        assertEquals("temperature", condition.getIdentifier());
        assertEquals(">", condition.getOp());
        assertEquals(80D, condition.getValue());
        assertEquals(0, condition.getDurationSec());
        // 静默期未配置时落全局默认值
        assertEquals(300, IotRuleService.readActionConfig(saved.getActionConfig()).getSilenceSeconds());
    }

    @Test
    void createAcceptsDisabledFlag() {
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        RuleSaveRequest request = request(PRODUCT_ID, null);
        request.setEnabled(Boolean.FALSE);

        service.create(request);

        ArgumentCaptor<IotRule> captor = ArgumentCaptor.forClass(IotRule.class);
        verify(ruleMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getEnabled());
    }

    @Test
    void updateRejectsUnknownRule() {
        when(ruleMapper.selectById(RULE_ID)).thenReturn(null);

        assertThrows(BizException.class, () -> service.update(RULE_ID, request(null, null)));
    }

    @Test
    void setEnabledTogglesFlag() {
        when(ruleMapper.selectById(RULE_ID)).thenReturn(rule());

        service.setEnabled(RULE_ID, false);

        verify(ruleMapper).update(any(), any());
    }

    @Test
    void pageMapsConditionAndScopeNames() {
        when(ruleMapper.selectPage(any(), any())).thenReturn(pageWith(rule()));
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product());
        when(deviceMapper.selectById(DEVICE_ID)).thenReturn(device());

        PageResult<RuleVO> result = service.page(PRODUCT_ID, null, 1L, 20L);

        assertEquals(1, result.getRecords().size());
        RuleVO vo = result.getRecords().get(0);
        assertEquals("温湿度超限", vo.getName());
        assertEquals("温度传感器", vo.getProductName());
        assertEquals("sensor-001", vo.getDeviceName());
        assertTrue(vo.getCondition() != null && vo.getCondition().getValue() == 80D);
        assertEquals("WARN", vo.getActionConfig().getLevel());
    }

    /**
     * 构造分页返回（单条记录）。
     *
     * @param rule 规则实体
     * @return 分页对象
     */
    private static com.baomidou.mybatisplus.extension.plugins.pagination.Page<IotRule> pageWith(IotRule rule) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<IotRule> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1L, 20L);
        page.setRecords(List.of(rule));
        page.setTotal(1L);
        return page;
    }

    /**
     * 构造测试产品。
     *
     * @return 产品实体
     */
    private static IotProduct product() {
        IotProduct product = new IotProduct();
        product.setId(PRODUCT_ID);
        product.setCode("temp-sensor");
        product.setName("温度传感器");
        return product;
    }

    /**
     * 构造测试设备。
     *
     * @return 设备实体
     */
    private static IotDevice device() {
        IotDevice device = new IotDevice();
        device.setId(DEVICE_ID);
        device.setProductId(PRODUCT_ID);
        device.setDeviceName("sensor-001");
        device.setTenantId(1L);
        return device;
    }

    /**
     * 构造测试规则。
     *
     * @return 规则实体
     */
    private static IotRule rule() {
        IotRule rule = new IotRule();
        rule.setId(RULE_ID);
        rule.setName("温湿度超限");
        rule.setProductId(PRODUCT_ID);
        rule.setDeviceId(DEVICE_ID);
        rule.setConditionJson(JsonCodec.write(condition()));
        rule.setActionConfig("{\"level\":\"WARN\",\"silenceSeconds\":300}");
        rule.setActionType("alert");
        rule.setEnabled(1);
        rule.setFiredCount(2L);
        return rule;
    }

    /**
     * 构造规则条件。
     *
     * @return 条件对象
     */
    private static RuleCondition condition() {
        RuleCondition condition = new RuleCondition();
        condition.setIdentifier("temperature");
        condition.setOp(">");
        condition.setValue(80D);
        condition.setDurationSec(0);
        return condition;
    }

    /**
     * 构造保存请求。
     *
     * @param productId 产品ID
     * @param deviceId  设备ID
     * @return 保存请求
     */
    private static RuleSaveRequest request(Long productId, Long deviceId) {
        RuleSaveRequest request = new RuleSaveRequest();
        request.setName(" 温湿度超限 ");
        request.setProductId(productId);
        request.setDeviceId(deviceId);
        request.setCondition(condition());
        return request;
    }
}
