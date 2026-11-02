package com.bluewhale.contract.converter;

import com.bluewhale.contract.entity.CtContractFlow;
import com.bluewhale.contract.entity.CtContractParty;
import com.bluewhale.contract.entity.CtContractVersion;
import com.bluewhale.contract.vo.ContractFlowVO;
import com.bluewhale.contract.vo.ContractPartyVO;
import com.bluewhale.contract.vo.ContractVersionVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 合同实体转换器单元测试（覆盖率门禁补齐）。
 *
 * <p>校验 MapStruct 生成实现的字段对齐与空值语义；String 型 ID 由服务层显式转换，
 * 转换器本身应保持为空。</p>
 *
 * @author BlueWhale
 */
class ContractConverterTest {

    /** 固定时间，便于断言时间字段透传 */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0);

    /**
     * 相对方实体字段完整映射，字符串 ID 由调用方补齐。
     */
    @Test
    void shouldMapParty() {
        CtContractParty party = new CtContractParty();
        party.setId(2L);
        party.setContractId(1L);
        party.setPartyType("A");
        party.setName("甲方");
        party.setCreditCode("91310000MA1");
        party.setContact("张三");
        party.setPhone("13800000000");
        party.setSignerUserId(6L);
        party.setSignerName("张三");

        ContractPartyVO vo = ContractConverter.INSTANCE.toPartyVo(party);

        assertEquals("A", vo.getPartyType());
        assertEquals("甲方", vo.getName());
        assertEquals("91310000MA1", vo.getCreditCode());
        assertEquals("张三", vo.getContact());
        assertEquals("13800000000", vo.getPhone());
        assertEquals("张三", vo.getSignerName());
        assertNull(vo.getId());
        assertNull(vo.getContractId());
        assertNull(vo.getSignerUserId());
        assertNull(ContractConverter.INSTANCE.toPartyVo(null));
    }

    /**
     * 版本实体字段完整映射，字符串 ID 与文件ID由调用方补齐。
     */
    @Test
    void shouldMapVersion() {
        CtContractVersion version = new CtContractVersion();
        version.setId(3L);
        version.setContractId(1L);
        version.setVersionNo(2);
        version.setFileId(5L);
        version.setSha256("abc");
        version.setChangeNote("修订");
        version.setStatus("DRAFT");
        version.setCreateTime(FIXED_TIME);

        ContractVersionVO vo = ContractConverter.INSTANCE.toVersionVo(version);

        assertEquals(2, vo.getVersionNo());
        assertEquals("abc", vo.getSha256());
        assertEquals("修订", vo.getChangeNote());
        assertEquals("DRAFT", vo.getStatus());
        assertEquals(FIXED_TIME, vo.getCreateTime());
        assertNull(vo.getId());
        assertNull(vo.getContractId());
        assertNull(vo.getFileId());
        assertNull(ContractConverter.INSTANCE.toVersionVo(null));
    }

    /**
     * 流程记录字段完整映射，字符串 ID 由调用方补齐。
     */
    @Test
    void shouldMapFlow() {
        CtContractFlow flow = new CtContractFlow();
        flow.setId(4L);
        flow.setContractId(1L);
        flow.setVersionId(3L);
        flow.setProcessInstanceId(7L);
        flow.setDefinitionId(10L);
        flow.setStage("APPROVAL");
        flow.setStatus("RUNNING");
        flow.setCreateTime(FIXED_TIME);

        ContractFlowVO vo = ContractConverter.INSTANCE.toFlowVo(flow);

        assertEquals("APPROVAL", vo.getStage());
        assertEquals("RUNNING", vo.getStatus());
        assertEquals(FIXED_TIME, vo.getCreateTime());
        assertNull(vo.getId());
        assertNull(vo.getContractId());
        assertNull(vo.getVersionId());
        assertNull(vo.getProcessInstanceId());
        assertNull(vo.getDefinitionId());
        assertNull(ContractConverter.INSTANCE.toFlowVo(null));
    }
}
