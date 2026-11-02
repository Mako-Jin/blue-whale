package com.bluewhale.contract.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.contract.client.ArchiveInternalClient;
import com.bluewhale.contract.client.ContractApprovalClient;
import com.bluewhale.contract.config.ContractApprovalProperties;
import com.bluewhale.contract.config.ContractArchiveProperties;
import com.bluewhale.contract.dto.ContractPartyRequest;
import com.bluewhale.contract.dto.ContractQueryRequest;
import com.bluewhale.contract.dto.ContractSaveRequest;
import com.bluewhale.contract.dto.ContractVersionSaveRequest;
import com.bluewhale.contract.dto.OperatorContext;
import com.bluewhale.contract.entity.CtContract;
import com.bluewhale.contract.entity.CtContractFlow;
import com.bluewhale.contract.entity.CtContractParty;
import com.bluewhale.contract.entity.CtContractVersion;
import com.bluewhale.contract.mapper.CtContractFlowMapper;
import com.bluewhale.contract.mapper.CtContractMapper;
import com.bluewhale.contract.mapper.CtContractPartyMapper;
import com.bluewhale.contract.mapper.CtContractVersionMapper;
import com.bluewhale.contract.vo.ContractVO;
import com.bluewhale.contract.vo.ContractVersionVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 合同主流程服务单元测试（覆盖率门禁补齐）。
 *
 * <p>覆盖分页过滤与参数归一化、编号生成与冲突重试、审批发起 / 回读 / 撤回、
 * 状态机校验、归档快照推送与版本修订，Mapper 与内网客户端全部以 Mock 隔离。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ContractServiceImplTest {

    /** 固定时间，便于断言映射结果 */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 11, 2, 10, 0);

    /** 流程定义ID */
    private static final long DEFINITION_ID = 10L;

    /** 操作人上下文 */
    private static final OperatorContext OPERATOR =
            new OperatorContext("42", "zhangsan", "contract:add", "user");

    @Mock
    private CtContractMapper contractMapper;

    @Mock
    private CtContractPartyMapper partyMapper;

    @Mock
    private CtContractVersionMapper versionMapper;

    @Mock
    private CtContractFlowMapper flowMapper;

    @Mock
    private ContractApprovalClient approvalClient;

    @Mock
    private ArchiveInternalClient archiveClient;

    /** 审批配置 */
    private ContractApprovalProperties approvalProperties;

    /** 归档配置 */
    private ContractArchiveProperties archiveProperties;

    /** 被测服务 */
    private ContractServiceImpl service;

    /**
     * 初始化 MyBatis-Plus 表元数据，供 Lambda 查询/更新条件解析列名。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CtContract.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CtContractParty.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CtContractVersion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), CtContractFlow.class);
    }

    /**
     * 构建被测服务与真实配置对象。
     */
    @BeforeEach
    void setUp() {
        approvalProperties = new ContractApprovalProperties();
        approvalProperties.setDefinitionId(DEFINITION_ID);
        archiveProperties = new ContractArchiveProperties();
        service = new ContractServiceImpl(contractMapper, partyMapper, versionMapper, flowMapper,
                approvalClient, approvalProperties, archiveClient, archiveProperties);
    }

    /**
     * 分页查询命中全部过滤条件，且页码/每页条数按下限与上限归一化。
     */
    @Test
    void pageShouldApplyFiltersAndClampSize() {
        ContractQueryRequest request = new ContractQueryRequest();
        request.setCode("HT");
        request.setTitle("采购");
        request.setCategory("PURCHASE");
        request.setStatus("DRAFT");
        request.setAmountMin(new BigDecimal("10"));
        request.setAmountMax(new BigDecimal("100"));
        request.setExpiringSoon(true);
        request.setCurrent(0L);
        request.setSize(500L);
        CtContract contract = contract(1L, "DRAFT");
        contract.setExpireDate(LocalDate.now().plusDays(10));
        when(contractMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<CtContract> page = invocation.getArgument(0);
            page.setTotal(5L);
            page.setRecords(List.of(contract));
            return page;
        });

        PageResult<ContractVO> result = service.page(request);

        assertEquals(1, result.getRecords().size());
        assertEquals(5L, result.getTotal());
        assertEquals(1L, result.getCurrent());
        assertEquals(200L, result.getSize());
        assertEquals(10L, result.getRecords().get(0).getDaysToExpire());
    }

    /**
     * 分页查询忽略空过滤条件，保留默认每页条数。
     */
    @Test
    void pageShouldSkipOptionalFilters() {
        ContractQueryRequest request = new ContractQueryRequest();
        when(contractMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<CtContract> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        PageResult<ContractVO> result = service.page(request);

        assertEquals(0, result.getRecords().size());
        assertEquals(1L, result.getCurrent());
        assertEquals(10L, result.getSize());
    }

    /**
     * 详情聚合相对方、版本与流程记录，并转换为字符串 ID。
     */
    @Test
    void getDetailShouldAggregateRelatedRecords() {
        CtContract contract = contract(1L, "DRAFT");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        when(partyMapper.selectList(any())).thenReturn(List.of(party(2L, 1L)));
        when(versionMapper.selectList(any())).thenReturn(List.of(version(3L, 1L, 1)));
        when(flowMapper.selectList(any())).thenReturn(List.of(flow(4L, 1L, 7L)));

        ContractVO vo = service.getDetail(1L);

        assertEquals("1", vo.getId());
        assertNull(vo.getDaysToExpire());
        assertEquals("2", vo.getParties().get(0).getId());
        assertEquals("3", vo.getVersions().get(0).getId());
        assertEquals("7", vo.getFlows().get(0).getProcessInstanceId());
        verify(approvalClient, never()).fetchStatus(any());
    }

    /**
     * 详情记录不存在抛出 404。
     */
    @Test
    void getDetailShouldThrowWhenContractAbsent() {
        BizException exception = assertThrows(BizException.class, () -> service.getDetail(2L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 审批中的详情回读到「已通过」后推进合同状态。
     */
    @Test
    void getDetailShouldSyncApprovedInstance() {
        CtContract contract = contract(1L, "APPROVING");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        CtContractFlow flow = flow(4L, 1L, 7L);
        when(flowMapper.selectList(any())).thenReturn(List.of(flow));
        when(approvalClient.fetchStatus(7L)).thenReturn(1);

        ContractVO vo = service.getDetail(1L);

        assertEquals("APPROVED", vo.getStatus());
        assertEquals("APPROVED", flow.getStatus());
        assertEquals("APPROVED", vo.getFlows().get(0).getStatus());
        verify(flowMapper).updateById(flow);
        verify(contractMapper).updateById(contract);
    }

    /**
     * 审批中的详情回读到「已驳回」后推进合同状态。
     */
    @Test
    void getDetailShouldSyncRejectedInstance() {
        CtContract contract = contract(1L, "APPROVING");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        CtContractFlow flow = flow(4L, 1L, 7L);
        when(flowMapper.selectList(any())).thenReturn(List.of(flow));
        when(approvalClient.fetchStatus(7L)).thenReturn(2);

        ContractVO vo = service.getDetail(1L);

        assertEquals("REJECTED", vo.getStatus());
        assertEquals("REJECTED", flow.getStatus());
    }

    /**
     * 审批中的详情回读到「已撤销」后推进合同状态。
     */
    @Test
    void getDetailShouldSyncCancelledInstance() {
        CtContract contract = contract(1L, "APPROVING");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        CtContractFlow flow = flow(4L, 1L, 7L);
        when(flowMapper.selectList(any())).thenReturn(List.of(flow));
        when(approvalClient.fetchStatus(7L)).thenReturn(3);

        ContractVO vo = service.getDetail(1L);

        assertEquals("WITHDRAWN", vo.getStatus());
        assertEquals("CANCELLED", flow.getStatus());
    }

    /**
     * 流程实例仍在运行时保持审批中状态，不写库。
     */
    @Test
    void getDetailShouldKeepStatusWhenInstanceRunning() {
        CtContract contract = contract(1L, "APPROVING");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        CtContractFlow flow = flow(4L, 1L, 7L);
        when(flowMapper.selectList(any())).thenReturn(List.of(flow));
        when(approvalClient.fetchStatus(7L)).thenReturn(0);

        ContractVO vo = service.getDetail(1L);

        assertEquals("APPROVING", vo.getStatus());
        verify(contractMapper, never()).updateById(any(CtContract.class));
        verify(flowMapper, never()).updateById(any(CtContractFlow.class));
    }

    /**
     * 状态回读返回空值时不做任何推进。
     */
    @Test
    void getDetailShouldKeepStatusWhenInstanceStatusMissing() {
        CtContract contract = contract(1L, "APPROVING");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        CtContractFlow flow = flow(4L, 1L, 7L);
        when(flowMapper.selectList(any())).thenReturn(List.of(flow));
        when(approvalClient.fetchStatus(7L)).thenReturn(null);

        ContractVO vo = service.getDetail(1L);

        assertEquals("APPROVING", vo.getStatus());
        verify(contractMapper, never()).updateById(any(CtContract.class));
    }

    /**
     * 审批中但无流程记录时跳过回读。
     */
    @Test
    void getDetailShouldSkipSyncWithoutFlows() {
        CtContract contract = contract(1L, "APPROVING");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        when(partyMapper.selectList(any())).thenReturn(List.of());
        when(versionMapper.selectList(any())).thenReturn(List.of());
        when(flowMapper.selectList(any())).thenReturn(List.of());

        ContractVO vo = service.getDetail(1L);

        assertEquals("APPROVING", vo.getStatus());
        verify(approvalClient, never()).fetchStatus(any());
    }

    /**
     * 工作流回读异常仅告警，不影响详情读取。
     */
    @Test
    void getDetailShouldIgnoreSyncFailure() {
        CtContract contract = contract(1L, "APPROVING");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        CtContractFlow flow = flow(4L, 1L, 7L);
        when(flowMapper.selectList(any())).thenReturn(List.of(flow));
        when(approvalClient.fetchStatus(7L)).thenThrow(new IllegalStateException("workflow down"));

        ContractVO vo = service.getDetail(1L);

        assertEquals("APPROVING", vo.getStatus());
        verify(contractMapper, never()).updateById(any(CtContract.class));
    }

    /**
     * 新增合同：编号生成、默认字段、相对方与初始版本落库。
     */
    @Test
    void createShouldPersistContractPartiesAndInitialVersion() {
        when(contractMapper.selectMaxCodeByPrefix(anyString())).thenReturn(null);
        when(contractMapper.countByCodeIncludingDeleted(anyString())).thenReturn(0L);
        when(contractMapper.insert(any(CtContract.class))).thenAnswer(invocation -> {
            CtContract entity = invocation.getArgument(0);
            entity.setId(11L);
            entity.setCreateTime(FIXED_TIME);
            return 1;
        });
        ContractSaveRequest request = saveRequest();
        request.setOwnerId(null);
        request.setCurrency(" ");
        request.setEffectiveDate(LocalDate.now());

        ContractVO vo = service.create(request, OPERATOR);

        assertEquals("11", vo.getId());
        assertEquals("HT-CG-" + LocalDate.now().getYear() + "-000001", vo.getCode());
        assertEquals("采购合同", vo.getTitle());
        assertEquals("DRAFT", vo.getStatus());
        assertEquals(1, vo.getCurrentVersion());
        assertEquals("CNY", vo.getCurrency());
        assertEquals("42", vo.getOwnerId());
        ArgumentCaptor<CtContractVersion> versionCaptor = ArgumentCaptor.forClass(CtContractVersion.class);
        verify(versionMapper).insert(versionCaptor.capture());
        assertEquals(1, versionCaptor.getValue().getVersionNo());
        assertEquals("初始版本", versionCaptor.getValue().getChangeNote());
        assertEquals(5L, versionCaptor.getValue().getFileId());
        assertEquals("DRAFT", versionCaptor.getValue().getStatus());
        verify(partyMapper, times(1)).insert(any(CtContractParty.class));
    }

    /**
     * 新增合同保留请求中的负责人与币种，操作人 ID 非法时回落到请求值。
     */
    @Test
    void createShouldUseProvidedOwnerAndCurrency() {
        when(contractMapper.selectMaxCodeByPrefix(anyString())).thenReturn("HT-CG-2026-000009");
        when(contractMapper.countByCodeIncludingDeleted(anyString())).thenReturn(0L);
        when(contractMapper.insert(any(CtContract.class))).thenReturn(1);
        ContractSaveRequest request = saveRequest();
        request.setOwnerId(5L);
        request.setCurrency("USD");
        request.setParties(null);

        ContractVO vo = service.create(request, new OperatorContext("abc", "lisi", null, null));

        assertEquals("5", vo.getOwnerId());
        assertEquals("USD", vo.getCurrency());
        assertTrue(vo.getCode().endsWith("-000010"));
        verify(partyMapper, never()).insert(any(CtContractParty.class));
    }

    /**
     * 操作人缺失时负责人留空，币种回落默认值。
     */
    @Test
    void createShouldFallBackOwnerWhenOperatorMissing() {
        when(contractMapper.selectMaxCodeByPrefix(anyString())).thenReturn("HT-CG-2026-000001");
        when(contractMapper.countByCodeIncludingDeleted(anyString())).thenReturn(0L);
        when(contractMapper.insert(any(CtContract.class))).thenReturn(1);
        ContractSaveRequest request = saveRequest();
        request.setOwnerId(null);
        request.setCurrency(null);

        ContractVO vo = service.create(request, null);

        assertNull(vo.getOwnerId());
        assertEquals("CNY", vo.getCurrency());
    }

    /**
     * 新增合同日期区间非法时拒绝落库。
     */
    @Test
    void createShouldRejectInvalidDateRange() {
        ContractSaveRequest request = saveRequest();
        request.setEffectiveDate(LocalDate.now());
        request.setExpireDate(LocalDate.now().minusDays(1));

        BizException exception = assertThrows(BizException.class, () -> service.create(request, OPERATOR));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(contractMapper, never()).insert(any(CtContract.class));
    }

    /**
     * 编号流水段无法解析时按 0 起算。
     */
    @Test
    void createShouldFallBackWhenSequenceUnparseable() {
        when(contractMapper.selectMaxCodeByPrefix(anyString()))
                .thenReturn("HT-CG-2026-", "HT-CG", "HT-CG-2026-abc");
        when(contractMapper.countByCodeIncludingDeleted(anyString())).thenReturn(0L);
        when(contractMapper.insert(any(CtContract.class))).thenReturn(1);
        ContractSaveRequest request = saveRequest();
        request.setCategory("CUSTOM");

        ContractVO first = service.create(request, new OperatorContext(null, "wangwu", null, null));
        ContractVO second = service.create(request, OPERATOR);
        ContractVO third = service.create(request, OPERATOR);

        assertTrue(first.getCode().startsWith("HT-QT-"));
        assertTrue(first.getCode().endsWith("-000001"));
        assertTrue(second.getCode().endsWith("-000001"));
        assertTrue(third.getCode().endsWith("-000001"));
        assertNull(first.getOwnerId());
    }

    /**
     * 编号已被占用时顺延流水号。
     */
    @Test
    void createShouldSkipOccupiedCode() {
        when(contractMapper.selectMaxCodeByPrefix(anyString())).thenReturn("HT-CG-2026-000001");
        when(contractMapper.countByCodeIncludingDeleted(anyString())).thenReturn(1L, 0L);
        when(contractMapper.insert(any(CtContract.class))).thenReturn(1);

        ContractVO vo = service.create(saveRequest(), OPERATOR);

        assertTrue(vo.getCode().endsWith("-000003"));
    }

    /**
     * 连续 1000 个候选编号均被占用时抛系统异常。
     */
    @Test
    void createShouldThrowWhenSequenceExhausted() {
        when(contractMapper.selectMaxCodeByPrefix(anyString())).thenReturn("HT-CG-2026-999999");
        when(contractMapper.countByCodeIncludingDeleted(anyString())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class, () -> service.create(saveRequest(), OPERATOR));

        assertEquals(ErrorCode.SYSTEM_ERROR.getCode(), exception.getCode());
        verify(contractMapper, never()).insert(any(CtContract.class));
    }

    /**
     * 更新草稿：覆盖字段、重建相对方并写回。
     */
    @Test
    void updateShouldReplacePartiesAndPersistDraft() {
        CtContract contract = contract(1L, "DRAFT");
        contract.setCurrency("USD");
        contract.setFileId(3L);
        when(contractMapper.selectById(1L)).thenReturn(contract);
        ContractSaveRequest request = saveRequest();
        request.setCurrency(" ");
        request.setOwnerId(null);
        request.setFileId(null);
        request.setParties(List.of(partyRequest("A", "甲方"), partyRequest("B", "乙方")));

        ContractVO vo = service.update(1L, request);

        assertEquals("USD", vo.getCurrency());
        assertEquals("3", vo.getFileId());
        assertEquals("采购合同", vo.getTitle());
        verify(partyMapper).delete(any());
        verify(partyMapper, times(2)).insert(any(CtContractParty.class));
        verify(contractMapper).updateById(contract);
    }

    /**
     * 更新草稿时应用请求中的负责人、币种与正文文件。
     */
    @Test
    void updateShouldApplyProvidedCurrencyFileAndOwner() {
        CtContract contract = contract(1L, "DRAFT");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        ContractSaveRequest request = saveRequest();
        request.setCurrency("EUR");
        request.setOwnerId(7L);
        request.setFileId(9L);
        request.setBizType("ORDER");
        request.setBizId("B-1");

        ContractVO vo = service.update(1L, request);

        assertEquals("EUR", vo.getCurrency());
        assertEquals("7", vo.getOwnerId());
        assertEquals("9", vo.getFileId());
        assertEquals("ORDER", vo.getBizType());
        assertEquals("B-1", vo.getBizId());
        assertEquals(contract.getCode(), vo.getCode());
    }

    /**
     * 非草稿状态不允许编辑。
     */
    @Test
    void updateShouldRejectNonDraft() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "APPROVED"));

        BizException exception = assertThrows(BizException.class,
                () -> service.update(1L, saveRequest()));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(contractMapper, never()).updateById(any(CtContract.class));
    }

    /**
     * 删除草稿：先清相对方再逻辑删除合同。
     */
    @Test
    void deleteShouldRemoveDraftContract() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "DRAFT"));

        service.delete(1L);

        verify(partyMapper).delete(any());
        verify(contractMapper).deleteById(1L);
    }

    /**
     * 非草稿状态不允许删除。
     */
    @Test
    void deleteShouldRejectNonDraft() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "SIGNING"));

        BizException exception = assertThrows(BizException.class, () -> service.delete(1L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verify(contractMapper, never()).deleteById(1L);
    }

    /**
     * 删除不存在的合同抛出 404。
     */
    @Test
    void deleteShouldThrowWhenContractAbsent() {
        BizException exception = assertThrows(BizException.class, () -> service.delete(2L));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
    }

    /**
     * 提交审批：工作流未启用时拒绝。
     */
    @Test
    void submitShouldRejectWhenApprovalDisabled() {
        approvalProperties.setEnabled(false);
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "DRAFT"));

        BizException exception = assertThrows(BizException.class,
                () -> service.submit(1L, OPERATOR));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(approvalClient);
    }

    /**
     * 提交审批：未配置流程定义时拒绝。
     */
    @Test
    void submitShouldRejectWhenDefinitionMissing() {
        approvalProperties.setDefinitionId(null);
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "REJECTED"));

        BizException exception = assertThrows(BizException.class,
                () -> service.submit(1L, OPERATOR));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(approvalClient);
    }

    /**
     * 提交审批：非允许状态拒绝。
     */
    @Test
    void submitShouldRejectNonSubmittableStatus() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "APPROVING"));

        BizException exception = assertThrows(BizException.class,
                () -> service.submit(1L, OPERATOR));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(approvalClient);
    }

    /**
     * 提交审批：发起实例、撤销运行中流程并记录审批流。
     */
    @Test
    void submitShouldStartInstanceAndCancelRunningFlows() {
        CtContract contract = contract(1L, "WITHDRAWN");
        contract.setCurrentVersion(2);
        contract.setAmount(null);
        when(contractMapper.selectById(1L)).thenReturn(contract);
        when(approvalClient.start(eq(DEFINITION_ID), anyString(), anyString(), anyMap(), any()))
                .thenReturn(7L);
        CtContractFlow running = flow(8L, 1L, 8L);
        running.setStatus("RUNNING");
        CtContractFlow noInstance = flow(9L, 1L, null);
        when(flowMapper.selectList(any())).thenReturn(List.of(running, noInstance));
        when(versionMapper.selectList(any())).thenReturn(List.of(version(55L, 1L, 2)));
        doThrow(new IllegalStateException("workflow down")).when(approvalClient).cancel(eq(8L), any());

        ContractVO vo = service.submit(1L, OPERATOR);

        assertEquals("APPROVING", vo.getStatus());
        ArgumentCaptor<Map<String, Object>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> businessKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(approvalClient).start(eq(DEFINITION_ID), eq("采购合同"), businessKeyCaptor.capture(),
                variablesCaptor.capture(), eq(OPERATOR));
        assertEquals("contract:1:v2", businessKeyCaptor.getValue());
        assertEquals(BigDecimal.ZERO, variablesCaptor.getValue().get("amount"));
        assertEquals("PURCHASE", variablesCaptor.getValue().get("category"));
        ArgumentCaptor<CtContractFlow> flowCaptor = ArgumentCaptor.forClass(CtContractFlow.class);
        verify(flowMapper).insert(flowCaptor.capture());
        assertEquals("APPROVAL", flowCaptor.getValue().getStage());
        assertEquals("RUNNING", flowCaptor.getValue().getStatus());
        assertEquals(55L, flowCaptor.getValue().getVersionId());
        assertEquals(7L, flowCaptor.getValue().getProcessInstanceId());
        assertEquals(DEFINITION_ID, flowCaptor.getValue().getDefinitionId());
        verify(approvalClient).cancel(eq(8L), any());
        verify(flowMapper, times(2)).updateById(any(CtContractFlow.class));
        verify(contractMapper).updateById(contract);
    }

    /**
     * 提交审批：无历史版本且无金额时仍可发起，版本ID留空。
     */
    @Test
    void submitShouldStartInstanceWithoutVersion() {
        CtContract contract = contract(1L, "DRAFT");
        contract.setCurrentVersion(1);
        contract.setAmount(null);
        when(contractMapper.selectById(1L)).thenReturn(contract);
        when(approvalClient.start(eq(DEFINITION_ID), anyString(), anyString(), anyMap(), any()))
                .thenReturn(9L);
        when(flowMapper.selectList(any())).thenReturn(List.of());
        when(versionMapper.selectList(any())).thenReturn(List.of());

        ContractVO vo = service.submit(1L, OPERATOR);

        assertEquals("APPROVING", vo.getStatus());
        ArgumentCaptor<CtContractFlow> flowCaptor = ArgumentCaptor.forClass(CtContractFlow.class);
        verify(flowMapper).insert(flowCaptor.capture());
        assertNull(flowCaptor.getValue().getVersionId());
        assertEquals(9L, flowCaptor.getValue().getProcessInstanceId());
        verify(approvalClient, never()).cancel(any(), any());
    }

    /**
     * 撤回审批：撤销流程实例并置为已撤回。
     */
    @Test
    void withdrawShouldCancelAndMarkWithdrawn() {
        CtContract contract = contract(1L, "APPROVING");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        CtContractFlow running = flow(8L, 1L, 9L);
        when(flowMapper.selectList(any())).thenReturn(List.of(running));

        ContractVO vo = service.withdraw(1L, OPERATOR);

        assertEquals("WITHDRAWN", vo.getStatus());
        assertEquals("CANCELLED", running.getStatus());
        verify(approvalClient).cancel(9L, OPERATOR);
        verify(flowMapper).updateById(running);
        verify(contractMapper).updateById(contract);
    }

    /**
     * 撤回审批：非审批中状态拒绝。
     */
    @Test
    void withdrawShouldRejectNonApproving() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "DRAFT"));

        BizException exception = assertThrows(BizException.class,
                () -> service.withdraw(1L, OPERATOR));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 终止合同：已签署状态允许终止。
     */
    @Test
    void terminateShouldMarkTerminated() {
        CtContract contract = contract(1L, "SIGNED");
        when(contractMapper.selectById(1L)).thenReturn(contract);

        ContractVO vo = service.terminate(1L);

        assertEquals("TERMINATED", vo.getStatus());
        verify(contractMapper).updateById(contract);
    }

    /**
     * 终止合同：草稿状态拒绝。
     */
    @Test
    void terminateShouldRejectDraft() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "DRAFT"));

        BizException exception = assertThrows(BizException.class, () -> service.terminate(1L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 合同生效：已签署状态允许生效。
     */
    @Test
    void effectiveShouldMarkEffective() {
        CtContract contract = contract(1L, "SIGNED");
        when(contractMapper.selectById(1L)).thenReturn(contract);

        ContractVO vo = service.effective(1L);

        assertEquals("EFFECTIVE", vo.getStatus());
    }

    /**
     * 合同生效：审批通过状态拒绝。
     */
    @Test
    void effectiveShouldRejectNonSigned() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "APPROVED"));

        BizException exception = assertThrows(BizException.class, () -> service.effective(1L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 归档合同：推送快照并回填档案条目ID。
     */
    @Test
    void archiveShouldPushSnapshotWhenEnabled() {
        CtContract contract = contract(1L, "EFFECTIVE");
        when(contractMapper.selectById(1L)).thenReturn(contract);
        when(partyMapper.selectList(any())).thenReturn(List.of(party(2L, 1L)));
        when(archiveClient.ingest(any(), any())).thenReturn(88L);

        ContractVO vo = service.archive(1L);

        assertEquals("ARCHIVED", vo.getStatus());
        assertEquals("88", vo.getArchiveItemId());
        verify(archiveClient).ingest(eq(contract), any());
        verify(contractMapper).updateById(contract);
    }

    /**
     * 归档合同：关闭归档对接时仅做状态流转。
     */
    @Test
    void archiveShouldSkipSnapshotWhenDisabled() {
        archiveProperties.setEnabled(false);
        CtContract contract = contract(1L, "EFFECTIVE");
        when(contractMapper.selectById(1L)).thenReturn(contract);

        ContractVO vo = service.archive(1L);

        assertEquals("ARCHIVED", vo.getStatus());
        assertNull(vo.getArchiveItemId());
        verifyNoInteractions(archiveClient);
    }

    /**
     * 归档合同：非生效状态拒绝。
     */
    @Test
    void archiveShouldRejectNonEffective() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "SIGNED"));

        BizException exception = assertThrows(BizException.class, () -> service.archive(1L));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(archiveClient);
    }

    /**
     * 版本列表按合同查询并映射为字符串 ID。
     */
    @Test
    void listVersionsShouldMapVersions() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "DRAFT"));
        when(versionMapper.selectList(any())).thenReturn(List.of(version(3L, 1L, 1), version(4L, 1L, 2)));

        List<ContractVersionVO> versions = service.listVersions(1L);

        assertEquals(2, versions.size());
        assertEquals("3", versions.get(0).getId());
        assertEquals("1", versions.get(0).getContractId());
    }

    /**
     * 版本列表：合同不存在抛出 404。
     */
    @Test
    void listVersionsShouldThrowWhenContractAbsent() {
        BizException exception = assertThrows(BizException.class, () -> service.listVersions(null));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), exception.getCode());
        verify(versionMapper, never()).selectList(any());
    }

    /**
     * 修订合同：旧版本置为被替代，版本号递增并沿用当前正文。
     */
    @Test
    void createVersionShouldSupersedePreviousAndBumpVersion() {
        CtContract contract = contract(1L, "DRAFT");
        contract.setCurrentVersion(2);
        when(contractMapper.selectById(1L)).thenReturn(contract);
        ContractVersionSaveRequest request = new ContractVersionSaveRequest();
        request.setSha256("abc");

        ContractVersionVO vo = service.createVersion(1L, request);

        assertEquals(3, vo.getVersionNo());
        assertEquals("5", vo.getFileId());
        assertEquals("abc", vo.getSha256());
        assertEquals("修订", vo.getChangeNote());
        assertEquals("DRAFT", vo.getStatus());
        verify(contractMapper).updateById(contract);
    }

    /**
     * 修订合同：新正文与变更说明写入，版本号从空值起算。
     */
    @Test
    void createVersionShouldApplyRequestFileAndNote() {
        CtContract contract = contract(1L, "REJECTED");
        contract.setCurrentVersion(null);
        when(contractMapper.selectById(1L)).thenReturn(contract);
        ContractVersionSaveRequest request = new ContractVersionSaveRequest();
        request.setFileId(77L);
        request.setChangeNote("调整条款");

        ContractVersionVO vo = service.createVersion(1L, request);

        assertEquals(1, vo.getVersionNo());
        assertEquals("77", vo.getFileId());
        assertEquals("调整条款", vo.getChangeNote());
        assertEquals(77L, contract.getFileId());
    }

    /**
     * 修订合同：审批中状态拒绝。
     */
    @Test
    void createVersionShouldRejectNonEditableStatus() {
        when(contractMapper.selectById(1L)).thenReturn(contract(1L, "APPROVING"));

        BizException exception = assertThrows(BizException.class,
                () -> service.createVersion(1L, new ContractVersionSaveRequest()));

        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    /**
     * 构造合同实体。
     *
     * @param id     主键
     * @param status 状态
     * @return 合同实体
     */
    private CtContract contract(Long id, String status) {
        CtContract contract = new CtContract();
        contract.setId(id);
        contract.setCode("HT-CG-2026-000001");
        contract.setTitle("采购合同");
        contract.setCategory("PURCHASE");
        contract.setAmount(new BigDecimal("100.00"));
        contract.setCurrency("CNY");
        contract.setStatus(status);
        contract.setCurrentVersion(1);
        contract.setFileId(5L);
        contract.setCreateTime(FIXED_TIME);
        return contract;
    }

    /**
     * 构造合同保存请求。
     *
     * @return 保存请求
     */
    private ContractSaveRequest saveRequest() {
        ContractSaveRequest request = new ContractSaveRequest();
        request.setTitle(" 采购合同 ");
        request.setCategory("PURCHASE");
        request.setAmount(new BigDecimal("100.00"));
        request.setFileId(5L);
        request.setRemark("年度采购");
        request.setParties(List.of(partyRequest("A", "甲方")));
        return request;
    }

    /**
     * 构造相对方请求。
     *
     * @param type 相对方类型
     * @param name 相对方名称
     * @return 相对方请求
     */
    private ContractPartyRequest partyRequest(String type, String name) {
        ContractPartyRequest request = new ContractPartyRequest();
        request.setPartyType(type);
        request.setName(name);
        request.setCreditCode("91310000MA1");
        request.setContact("张三");
        request.setPhone("13800000000");
        request.setSignerName("张三");
        return request;
    }

    /**
     * 构造相对方实体。
     *
     * @param id         主键
     * @param contractId 合同ID
     * @return 相对方实体
     */
    private CtContractParty party(Long id, Long contractId) {
        CtContractParty party = new CtContractParty();
        party.setId(id);
        party.setContractId(contractId);
        party.setPartyType("A");
        party.setName("甲方");
        party.setCreditCode("91310000MA1");
        return party;
    }

    /**
     * 构造版本实体。
     *
     * @param id         主键
     * @param contractId 合同ID
     * @param versionNo  版本号
     * @return 版本实体
     */
    private CtContractVersion version(Long id, Long contractId, int versionNo) {
        CtContractVersion version = new CtContractVersion();
        version.setId(id);
        version.setContractId(contractId);
        version.setVersionNo(versionNo);
        version.setFileId(5L);
        version.setStatus("DRAFT");
        version.setCreateTime(FIXED_TIME);
        return version;
    }

    /**
     * 构造流程记录实体。
     *
     * @param id         主键
     * @param contractId 合同ID
     * @param instanceId 工作流实例ID
     * @return 流程记录
     */
    private CtContractFlow flow(Long id, Long contractId, Long instanceId) {
        CtContractFlow flow = new CtContractFlow();
        flow.setId(id);
        flow.setContractId(contractId);
        flow.setProcessInstanceId(instanceId);
        flow.setDefinitionId(DEFINITION_ID);
        flow.setStage("APPROVAL");
        flow.setStatus("RUNNING");
        flow.setCreateTime(FIXED_TIME);
        return flow;
    }
}
