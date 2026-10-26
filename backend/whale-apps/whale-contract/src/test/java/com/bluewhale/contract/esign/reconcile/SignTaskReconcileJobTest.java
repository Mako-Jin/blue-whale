package com.bluewhale.contract.esign.reconcile;

import com.bluewhale.contract.config.ContractEsignProperties;
import com.bluewhale.contract.esign.provider.EsignCallbackSigner;
import com.bluewhale.contract.esign.provider.EsignProvider;
import com.bluewhale.contract.esign.provider.EsignProviderRegistry;
import com.bluewhale.contract.esign.provider.EsignTaskStatus;
import com.bluewhale.contract.entity.CtSignTask;
import com.bluewhale.contract.mapper.CtSignTaskMapper;
import com.bluewhale.contract.service.EsignCallbackService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 签署回调补偿任务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class SignTaskReconcileJobTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SECRET = "unit-reconcile-secret";

    private static final String PROVIDER_TASK_ID = "ESIGNBAO-1";

    @Mock
    private CtSignTaskMapper signTaskMapper;

    @Mock
    private EsignProviderRegistry providerRegistry;

    @Mock
    private EsignCallbackService callbackService;

    @Mock
    private EsignProvider provider;

    private SignTaskReconcileJob job;

    @BeforeEach
    void setUp() {
        ContractEsignProperties properties = new ContractEsignProperties();
        properties.setCallbackSecret(SECRET);
        job = new SignTaskReconcileJob(signTaskMapper, providerRegistry, callbackService, properties);
    }

    @Test
    void reconcileShouldDeliverSignedCallbackWhenProviderFinished() throws Exception {
        when(signTaskMapper.selectList(any())).thenReturn(List.of(staleTask()));
        when(providerRegistry.require("esignbao")).thenReturn(provider);
        when(provider.name()).thenReturn("esignbao");
        when(provider.queryTask(PROVIDER_TASK_ID)).thenReturn(new EsignTaskStatus("FINISHED", null));

        job.reconcile();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(callbackService).handle(eq("esignbao"), body.capture(), headers.capture());
        JsonNode payload = JSON.readTree(body.getValue());
        assertEquals("SIGN", payload.get("action").asText());
        assertEquals("1001", payload.get("signTaskId").asText());
        assertEquals("RECONCILE-1001-SIGN", payload.get("providerEventId").asText());
        Map<String, String> captured = headers.getValue();
        String timestamp = captured.get(EsignCallbackSigner.HEADER_TIMESTAMP);
        String nonce = captured.get(EsignCallbackSigner.HEADER_NONCE);
        assertEquals(EsignCallbackSigner.sign(SECRET, timestamp, nonce, body.getValue()),
                captured.get(EsignCallbackSigner.HEADER_SIGNATURE));
    }

    @Test
    void reconcileShouldSkipWhenProviderStillSigning() {
        when(signTaskMapper.selectList(any())).thenReturn(List.of(staleTask()));
        when(providerRegistry.require("esignbao")).thenReturn(provider);
        when(provider.queryTask(PROVIDER_TASK_ID)).thenReturn(new EsignTaskStatus("SIGNING", null));

        job.reconcile();

        verify(callbackService, never()).handle(anyString(), anyString(), any());
    }

    @Test
    void reconcileShouldDeliverRefuseCallbackWithReason() throws Exception {
        when(signTaskMapper.selectList(any())).thenReturn(List.of(staleTask()));
        when(providerRegistry.require("esignbao")).thenReturn(provider);
        when(provider.name()).thenReturn("esignbao");
        when(provider.queryTask(PROVIDER_TASK_ID))
                .thenReturn(new EsignTaskStatus("REJECTED", "签署人拒绝"));

        job.reconcile();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(callbackService).handle(eq("esignbao"), body.capture(), any());
        JsonNode payload = JSON.readTree(body.getValue());
        assertEquals("REFUSE", payload.get("action").asText());
        assertEquals("签署人拒绝", payload.get("refuseReason").asText());
        assertTrue(payload.get("providerEventId").asText().endsWith("-REFUSE"));
    }

    @Test
    void reconcileShouldStopWhenSecretMissing() {
        ContractEsignProperties properties = new ContractEsignProperties();
        properties.setCallbackSecret("");
        SignTaskReconcileJob noSecret = new SignTaskReconcileJob(signTaskMapper, providerRegistry,
                callbackService, properties);

        noSecret.reconcile();

        verify(signTaskMapper, never()).selectList(any());
    }

    private CtSignTask staleTask() {
        CtSignTask task = new CtSignTask();
        task.setId(1001L);
        task.setContractId(2001L);
        task.setProvider("ESIGNBAO");
        task.setProviderTaskId(PROVIDER_TASK_ID);
        task.setStatus("SIGNING");
        task.setUpdateTime(LocalDateTime.now().minusMinutes(10));
        return task;
    }
}
