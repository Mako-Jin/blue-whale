package com.bluewhale.contract.client;

import com.bluewhale.contract.config.ContractArchiveProperties;
import com.bluewhale.contract.entity.CtContract;
import com.bluewhale.contract.entity.CtContractParty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 合同快照入档请求体构造测试。
 *
 * @author BlueWhale
 */
class ArchiveInternalClientTest {

    /** JSON 解析器 */
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void buildPayloadContainsSourceAndArchiveFields() {
        ContractArchiveProperties properties = new ContractArchiveProperties();
        properties.setSecurityLevel(3);
        ArchiveInternalClient client = new ArchiveInternalClient(properties);

        Map<String, Object> payload = client.buildPayload(contract(), List.of(party()));

        assertEquals("CONTRACT", payload.get("sourceType"));
        assertEquals("1001", payload.get("sourceId"));
        assertEquals("采购框架合同", payload.get("title"));
        assertEquals("HT-CG-2026-000001", payload.get("businessKey"));
        assertEquals("CONTRACT", payload.get("definitionCode"));
        assertEquals(3, payload.get("securityLevel"));
        assertEquals("CONTRACT-HT-CG-2026-000001.json", payload.get("fileName"));
        assertEquals("9001", String.valueOf(payload.get("originalFileId")));
    }

    @Test
    void snapshotJsonCarriesContractAndParties() throws Exception {
        ArchiveInternalClient client = new ArchiveInternalClient(new ContractArchiveProperties());

        Map<String, Object> payload = client.buildPayload(contract(), List.of(party()));
        String snapshotJson = String.valueOf(payload.get("snapshotJson"));
        JsonNode snapshot = JSON.readTree(snapshotJson);

        assertEquals("HT-CG-2026-000001", snapshot.get("code").asText());
        assertEquals("ARCHIVED", snapshot.get("status").asText());
        assertEquals("2026-01-01", snapshot.get("effectiveDate").asText());
        assertEquals(0, new BigDecimal("120000.00").compareTo(snapshot.get("amount").decimalValue()));
        assertEquals(1, snapshot.get("parties").size());
        assertEquals("甲方公司", snapshot.get("parties").get(0).get("name").asText());
        assertTrue(snapshotJson.length() > 0);
    }

    @Test
    void snapshotWithoutFileOmitsOriginalFile() throws Exception {
        ArchiveInternalClient client = new ArchiveInternalClient(new ContractArchiveProperties());
        CtContract contract = contract();
        contract.setFileId(null);

        Map<String, Object> payload = client.buildPayload(contract, List.of());

        assertNotNull(payload.get("snapshotJson"));
        assertTrue(!payload.containsKey("originalFileId"));
        JsonNode snapshot = JSON.readTree(String.valueOf(payload.get("snapshotJson")));
        assertEquals(0, snapshot.get("parties").size());
        assertTrue(snapshot.get("fileId").isNull());
    }

    private CtContract contract() {
        CtContract contract = new CtContract();
        contract.setId(1001L);
        contract.setCode("HT-CG-2026-000001");
        contract.setTitle("采购框架合同");
        contract.setCategory("PURCHASE");
        contract.setStatus("ARCHIVED");
        contract.setAmount(new BigDecimal("120000.00"));
        contract.setCurrency("CNY");
        contract.setCurrentVersion(1);
        contract.setFileId(9001L);
        contract.setOwnerId(1L);
        contract.setEffectiveDate(LocalDate.of(2026, 1, 1));
        return contract;
    }

    private CtContractParty party() {
        CtContractParty party = new CtContractParty();
        party.setContractId(1001L);
        party.setPartyType("A");
        party.setName("甲方公司");
        party.setCreditCode("91310000MA1FL00000");
        party.setSignerName("张三");
        return party;
    }
}
