package com.bluewhale.crm.commerce.infrastructure.merge;

import com.bluewhale.crm.commerce.infrastructure.client.ContractRewriteClient;
import com.bluewhale.crm.config.CrmContractProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ContractServiceMergeReferenceAdapter} 单元测试：开关门控与聚合 key。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceMergeReferenceAdapterTest {

    @Mock
    private ContractRewriteClient rewriteClient;

    private CrmContractProperties properties;

    private ContractServiceMergeReferenceAdapter adapter;

    /**
     * 构造被测适配器（默认开启合同服务）。
     */
    @BeforeEach
    void setUp() {
        properties = new CrmContractProperties();
        properties.setEnabled(true);
        adapter = new ContractServiceMergeReferenceAdapter(rewriteClient, properties);
    }

    @Test
    @DisplayName("开关关闭：计数与重写均返回空映射，不触达合同服务")
    void shouldSkipWhenDisabled() {
        properties.setEnabled(false);

        assertTrue(adapter.countReferences(1L, 2L).isEmpty());
        assertTrue(adapter.rewriteReferences(1L, 2L).isEmpty());
        verifyNoInteractions(rewriteClient);
    }

    @Test
    @DisplayName("开关开启：以「合同服务」key 汇报计数与重写行数")
    void shouldReportUnderContractServiceKey() {
        when(rewriteClient.countReferences(1L)).thenReturn(3L);
        when(rewriteClient.rewriteReferences(1L, 2L)).thenReturn(5L);

        Map<String, Long> counts = adapter.countReferences(1L, 2L);
        Map<String, Long> rewritten = adapter.rewriteReferences(1L, 2L);

        assertEquals(3L, counts.get("合同服务"));
        assertEquals(5L, rewritten.get("合同服务"));
    }
}
