package com.bluewhale.archive.service;

import com.bluewhale.archive.client.FileArchiveClient;
import com.bluewhale.archive.client.FormArchiveClient;
import com.bluewhale.archive.client.WorkflowArchiveClient;
import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.entity.ArchiveCategory;
import com.bluewhale.archive.entity.ArchiveItem;
import com.bluewhale.archive.entity.ArchiveRule;
import com.bluewhale.archive.entity.ArchiveSourceRecord;
import com.bluewhale.archive.mapper.ArchiveCategoryMapper;
import com.bluewhale.archive.mapper.ArchiveItemMapper;
import com.bluewhale.archive.mapper.ArchiveRuleMapper;
import com.bluewhale.archive.mapper.ArchiveSourceRecordMapper;
import com.bluewhale.archive.mapper.ArchiveSyncStateMapper;
import com.bluewhale.archive.remote.RemoteApprovedInstance;
import com.bluewhale.archive.remote.RemoteApprovedPage;
import com.bluewhale.archive.remote.RemoteFileMeta;
import com.bluewhale.archive.remote.RemoteFormArchive;
import com.bluewhale.archive.remote.RemoteFormData;
import com.bluewhale.archive.remote.RemoteFormField;
import com.bluewhale.archive.remote.RemoteInstanceDetail;
import com.bluewhale.archive.remote.RemoteTask;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.support.ArchiveSnapshotBuilder;
import com.bluewhale.archive.vo.ArchiveSyncResultVO;
import com.bluewhale.common.core.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 档案同步服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveSyncServiceTest {

    /** 实例ID */
    private static final long INSTANCE_ID = 7L;

    /** 表单数据ID */
    private static final long FORM_DATA_ID = 123L;

    /** 附件ID */
    private static final long ATTACHMENT_ID = 11L;

    /** 快照文件ID */
    private static final long SNAPSHOT_FILE_ID = 55L;

    /** 门类ID */
    private static final long CATEGORY_ID = 5L;

    /** 实例更新时间 */
    private static final LocalDateTime INSTANCE_UPDATE_TIME = LocalDateTime.of(2026, 5, 1, 10, 0);

    @Mock
    private ArchiveRuleMapper ruleMapper;

    @Mock
    private ArchiveCategoryMapper categoryMapper;

    @Mock
    private ArchiveItemMapper itemMapper;

    @Mock
    private ArchiveSourceRecordMapper sourceRecordMapper;

    @Mock
    private ArchiveSyncStateMapper syncStateMapper;

    @Mock
    private WorkflowArchiveClient workflowClient;

    @Mock
    private FormArchiveClient formClient;

    @Mock
    private FileArchiveClient fileClient;

    @Mock
    private ArchiveNumberService numberService;

    @Mock
    private ArchiveItemStore itemStore;

    private ArchiveSyncService service;

    @BeforeEach
    void setUp() {
        ArchiveProperties properties = new ArchiveProperties();
        properties.getSync().setPageSize(100);
        properties.getSync().setMaxAttempts(3);
        ArchiveSnapshotBuilder builder = new ArchiveSnapshotBuilder(new ObjectMapper().findAndRegisterModules());
        service = new ArchiveSyncService(ruleMapper, categoryMapper, itemMapper, sourceRecordMapper, syncStateMapper,
                workflowClient, formClient, fileClient, builder, numberService, itemStore, properties);
    }

    @Test
    @DisplayName("runOnce：已成功同步的实例直接跳过，不重复拉取详情")
    void shouldSkipAlreadySyncedInstance() {
        ArchiveSourceRecord success = new ArchiveSourceRecord();
        success.setId(1L);
        success.setSourceType(ArchiveConstants.SOURCE_WORKFLOW);
        success.setSourceId(String.valueOf(INSTANCE_ID));
        success.setSyncStatus(ArchiveConstants.SYNC_STATUS_SUCCESS);
        when(syncStateMapper.selectOne(any())).thenReturn(null);
        when(sourceRecordMapper.selectList(any())).thenReturn(List.of());
        when(workflowClient.approved(any(), any(), anyLong(), anyLong())).thenReturn(pageOf(INSTANCE_ID));
        when(sourceRecordMapper.selectOne(any())).thenReturn(success);

        ArchiveSyncResultVO result = service.runOnce();

        assertEquals(1, result.getPulled());
        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getArchived());
        verify(workflowClient, never()).detail(anyLong());
        verify(itemStore, never()).persist(any(), anyList(), any());
    }

    @Test
    @DisplayName("runOnce：命中自动归档规则时取档号并置为已归档")
    void shouldAutoArchiveWhenRuleHit() {
        stubSingleInstance();
        when(workflowClient.detail(INSTANCE_ID)).thenReturn(approvedDetail("DOC_APPROVE"));
        when(formClient.detail(FORM_DATA_ID)).thenReturn(formWithAttachment());
        when(fileClient.detail(ATTACHMENT_ID)).thenReturn(attachmentMeta());
        when(fileClient.upload(any(), anyString(), eq("whale-archive"))).thenReturn(SNAPSHOT_FILE_ID);
        ArchiveRule rule = new ArchiveRule();
        rule.setCategoryId(CATEGORY_ID);
        rule.setAutoArchive(ArchiveConstants.AUTO_ARCHIVE_ON);
        rule.setRetentionYears(10);
        rule.setSecurityLevel(ArchiveConstants.SECURITY_SECRET);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(category());
        when(numberService.next("GL", 2026)).thenReturn("BW-GL-2026-000001");

        ArchiveSyncResultVO result = service.runOnce();

        ArgumentCaptor<ArchiveItem> itemCaptor = ArgumentCaptor.forClass(ArchiveItem.class);
        ArgumentCaptor<ArchiveSourceRecord> recordCaptor = ArgumentCaptor.forClass(ArchiveSourceRecord.class);
        verify(itemStore).persist(itemCaptor.capture(), anyList(), recordCaptor.capture());
        ArchiveItem item = itemCaptor.getValue();
        assertEquals(ArchiveConstants.ITEM_STATUS_ARCHIVED, item.getItemStatus());
        assertEquals("BW-GL-2026-000001", item.getArchiveNo());
        assertEquals("GL", item.getCategoryPath());
        assertEquals(Integer.valueOf(ArchiveConstants.SECURITY_SECRET), item.getSecurityLevel());
        assertEquals(INSTANCE_UPDATE_TIME.plusYears(10), item.getExpireAt());
        assertEquals(Long.valueOf(SNAPSHOT_FILE_ID), item.getSnapshotFileId());
        assertEquals(64, item.getSnapshotHash().length());
        assertEquals(ArchiveConstants.SYNC_STATUS_SUCCESS, recordCaptor.getValue().getSyncStatus());
        verify(fileClient).lock(ATTACHMENT_ID);
        verify(fileClient).lock(SNAPSHOT_FILE_ID);
        assertEquals(1, result.getArchived());
        assertEquals(INSTANCE_UPDATE_TIME, result.getCursorTime());
    }

    @Test
    @DisplayName("runOnce：未命中规则时进入待整理，密级回落内部")
    void shouldFallbackToPendingWhenNoRule() {
        stubSingleInstance();
        when(workflowClient.detail(INSTANCE_ID)).thenReturn(approvedDetail(null));
        when(fileClient.upload(any(), anyString(), eq("whale-archive"))).thenReturn(SNAPSHOT_FILE_ID);

        ArchiveSyncResultVO result = service.runOnce();

        ArgumentCaptor<ArchiveItem> itemCaptor = ArgumentCaptor.forClass(ArchiveItem.class);
        verify(itemStore).persist(itemCaptor.capture(), anyList(), any());
        ArchiveItem item = itemCaptor.getValue();
        assertEquals(ArchiveConstants.ITEM_STATUS_PENDING, item.getItemStatus());
        assertNull(item.getArchiveNo());
        assertNull(item.getCategoryId());
        assertEquals(Integer.valueOf(ArchiveConstants.SECURITY_INTERNAL), item.getSecurityLevel());
        assertNull(item.getExpireAt());
        assertEquals(1, result.getPending());
    }

    @Test
    @DisplayName("runOnce：详情拉取失败时写失败台账并计为失败")
    void shouldRecordFailureWhenDetailFails() {
        stubSingleInstance();
        when(workflowClient.detail(INSTANCE_ID)).thenThrow(new BizException("工作流不可用"));

        ArchiveSyncResultVO result = service.runOnce();

        ArgumentCaptor<ArchiveSourceRecord> captor = ArgumentCaptor.forClass(ArchiveSourceRecord.class);
        verify(itemStore).saveRecord(captor.capture());
        ArchiveSourceRecord record = captor.getValue();
        assertEquals(ArchiveConstants.SYNC_STATUS_FAILED, record.getSyncStatus());
        assertEquals(Integer.valueOf(1), record.getRetryCount());
        assertEquals(1, result.getFailed());
        verify(itemStore, never()).persist(any(), anyList(), any());
    }

    @Test
    @DisplayName("runOnce：达到最大重试次数的台账不再拉取详情")
    void shouldStopRetryWhenAttemptsExhausted() {
        ArchiveSourceRecord failed = new ArchiveSourceRecord();
        failed.setId(9L);
        failed.setSourceType(ArchiveConstants.SOURCE_WORKFLOW);
        failed.setSourceId(String.valueOf(INSTANCE_ID));
        failed.setSyncStatus(ArchiveConstants.SYNC_STATUS_FAILED);
        failed.setRetryCount(3);
        when(syncStateMapper.selectOne(any())).thenReturn(null);
        when(sourceRecordMapper.selectList(any())).thenReturn(List.of());
        when(workflowClient.approved(any(), any(), anyLong(), anyLong())).thenReturn(pageOf(INSTANCE_ID));
        when(sourceRecordMapper.selectOne(any())).thenReturn(failed);

        ArchiveSyncResultVO result = service.runOnce();

        verify(workflowClient, never()).detail(anyLong());
        assertEquals(1, result.getSkipped());
    }

    @Test
    @DisplayName("runOnce：实例已撤销/驳回时不归档，仅记成功台账")
    void shouldSkipInstanceNotApproved() {
        stubSingleInstance();
        RemoteInstanceDetail detail = approvedDetail("DOC_APPROVE");
        detail.setStatus(3);
        when(workflowClient.detail(INSTANCE_ID)).thenReturn(detail);

        ArchiveSyncResultVO result = service.runOnce();

        assertEquals(1, result.getSkipped());
        assertEquals(0, result.getArchived());
        verify(itemStore, never()).persist(any(), anyList(), any());
        ArgumentCaptor<ArchiveSourceRecord> captor = ArgumentCaptor.forClass(ArchiveSourceRecord.class);
        verify(itemStore).saveRecord(captor.capture());
        assertEquals(ArchiveConstants.SYNC_STATUS_SUCCESS, captor.getValue().getSyncStatus());
    }

    /**
     * 桩化单实例拉取场景的公共依赖。
     */
    private void stubSingleInstance() {
        when(syncStateMapper.selectOne(any())).thenReturn(null);
        when(sourceRecordMapper.selectList(any())).thenReturn(List.of());
        when(workflowClient.approved(any(), any(), anyLong(), anyLong())).thenReturn(pageOf(INSTANCE_ID));
        when(sourceRecordMapper.selectOne(any())).thenReturn(null);
        when(itemMapper.selectCount(any())).thenReturn(0L);
    }

    /**
     * 构造单条已通过实例分页。
     *
     * @param instanceId 实例ID
     * @return 分页
     */
    private static RemoteApprovedPage pageOf(long instanceId) {
        RemoteApprovedInstance instance = new RemoteApprovedInstance();
        instance.setId(instanceId);
        instance.setUpdateTime(INSTANCE_UPDATE_TIME);
        instance.setFinishedAt(INSTANCE_UPDATE_TIME);
        RemoteApprovedPage page = new RemoteApprovedPage();
        page.setRecords(List.of(instance));
        page.setTotal(1);
        page.setCurrent(1);
        page.setSize(100);
        return page;
    }

    /**
     * 构造审批通过的实例详情。
     *
     * @param definitionCode 流程定义编码，{@code null} 表示无规则
     * @return 实例详情
     */
    private static RemoteInstanceDetail approvedDetail(String definitionCode) {
        RemoteInstanceDetail detail = new RemoteInstanceDetail();
        detail.setId(INSTANCE_ID);
        detail.setDefinitionCode(definitionCode);
        detail.setDefinitionVersion(1);
        detail.setTitle("报销审批");
        detail.setBusinessKey(String.valueOf(FORM_DATA_ID));
        detail.setInitiator("zhangsan");
        detail.setStatus(1);
        detail.setDefinitionJson("{\"nodes\":[]}");
        detail.setFinishedAt(INSTANCE_UPDATE_TIME);
        detail.setUpdateTime(INSTANCE_UPDATE_TIME);
        RemoteTask task = new RemoteTask();
        task.setId(1L);
        task.setNodeName("部门审批");
        task.setStatus(1);
        detail.setTasks(List.of(task));
        return detail;
    }

    /**
     * 构造含单个附件字段的表单详情。
     *
     * @return 表单归档详情
     */
    private static RemoteFormArchive formWithAttachment() {
        RemoteFormField field = new RemoteFormField();
        field.setKey("attachments");
        field.setType(ArchiveConstants.FORM_TYPE_ATTACHMENT);
        RemoteFormData data = new RemoteFormData();
        data.setId(FORM_DATA_ID);
        data.setFields(Map.of("attachments", List.of(ATTACHMENT_ID)));
        RemoteFormArchive form = new RemoteFormArchive();
        form.setData(data);
        form.setFieldDefinitions(List.of(field));
        return form;
    }

    /**
     * 构造附件元数据。
     *
     * @return 附件元数据
     */
    private static RemoteFileMeta attachmentMeta() {
        RemoteFileMeta meta = new RemoteFileMeta();
        meta.setId(ATTACHMENT_ID);
        meta.setOriginalName("invoice.pdf");
        meta.setFileSize(2048L);
        meta.setContentType("application/pdf");
        return meta;
    }

    /**
     * 构造门类。
     *
     * @return 门类实体
     */
    private static ArchiveCategory category() {
        ArchiveCategory category = new ArchiveCategory();
        category.setId(CATEGORY_ID);
        category.setCode("GL");
        category.setParentId(ArchiveConstants.ROOT_PARENT_ID);
        category.setRetentionYears(5);
        category.setSecurityLevel(ArchiveConstants.SECURITY_INTERNAL);
        return category;
    }
}
