package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.archive.client.FileArchiveClient;
import com.bluewhale.archive.client.FormArchiveClient;
import com.bluewhale.archive.client.WorkflowArchiveClient;
import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.entity.ArchiveCategory;
import com.bluewhale.archive.entity.ArchiveFile;
import com.bluewhale.archive.entity.ArchiveItem;
import com.bluewhale.archive.entity.ArchiveRule;
import com.bluewhale.archive.entity.ArchiveSourceRecord;
import com.bluewhale.archive.entity.ArchiveSyncState;
import com.bluewhale.archive.mapper.ArchiveCategoryMapper;
import com.bluewhale.archive.mapper.ArchiveItemMapper;
import com.bluewhale.archive.mapper.ArchiveRuleMapper;
import com.bluewhale.archive.mapper.ArchiveSourceRecordMapper;
import com.bluewhale.archive.mapper.ArchiveSyncStateMapper;
import com.bluewhale.archive.remote.RemoteApprovedInstance;
import com.bluewhale.archive.remote.RemoteApprovedPage;
import com.bluewhale.archive.remote.RemoteFileMeta;
import com.bluewhale.archive.remote.RemoteFormArchive;
import com.bluewhale.archive.remote.RemoteInstanceDetail;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.support.ArchiveSnapshotBuilder;
import com.bluewhale.archive.support.AttachmentExtractor;
import com.bluewhale.archive.support.CategoryPathResolver;
import com.bluewhale.archive.vo.ArchiveSyncResultVO;
import com.bluewhale.archive.vo.ArchiveSyncStatusVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工作流归档同步服务。
 *
 * <p>同步流程：按 {@code update_time} 游标增量拉取审批通过实例 → 读取表单与附件 →
 * 生成不可变快照并上传 → 锁定快照与附件 → 命中规则自动归档（否则进入待整理）→
 * 写入条目、文件引用与同步台账。{@code (source_type, source_id)} 唯一键保证幂等，
 * 失败台账按轮重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveSyncService {

    /** 工作流实例状态：已通过 */
    private static final int INSTANCE_APPROVED = 1;

    /** 出参：已归档 */
    private static final int OUTCOME_ARCHIVED = 1;

    /** 出参：待整理 */
    private static final int OUTCOME_PENDING = 2;

    /** 出参：跳过 */
    private static final int OUTCOME_SKIPPED = 3;

    /** 出参：失败（已记录台账，下一轮重试） */
    private static final int OUTCOME_FAILED = 4;

    /** 业务键中表单ID前缀分隔符 */
    private static final String BUSINESS_KEY_SEPARATOR = ":";

    /** 同步结果摘要格式 */
    private static final String SUMMARY_FORMAT = "pulled=%d,archived=%d,pending=%d,skipped=%d,failed=%d";

    /** 并发保护：同一时刻仅允许一轮同步 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ArchiveRuleMapper ruleMapper;

    private final ArchiveCategoryMapper categoryMapper;

    private final ArchiveItemMapper itemMapper;

    private final ArchiveSourceRecordMapper sourceRecordMapper;

    private final ArchiveSyncStateMapper syncStateMapper;

    private final WorkflowArchiveClient workflowClient;

    private final FormArchiveClient formClient;

    private final FileArchiveClient fileClient;

    private final ArchiveSnapshotBuilder snapshotBuilder;

    private final ArchiveNumberService numberService;

    private final ArchiveItemStore itemStore;

    private final ArchiveProperties archiveProperties;

    /**
     * 执行一轮同步（手动触发或定时触发）。
     *
     * @return 本轮同步结果
     * @throws BizException 已有同步在运行时抛出
     */
    public ArchiveSyncResultVO runOnce() {
        if (!running.compareAndSet(false, true)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "档案同步任务正在执行");
        }
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            ArchiveSyncState state = loadOrCreateState();
            LocalDateTime cursor = state.getCursorTime();
            SyncCounters counters = new SyncCounters();
            retryFailed(counters);
            cursor = pullApproved(counters, cursor);
            state.setCursorTime(cursor);
            state.setLastRunAt(startedAt);
            state.setLastResult(summarize(counters));
            saveState(state);
            ArchiveSyncResultVO result = new ArchiveSyncResultVO();
            result.setPulled(counters.pulled);
            result.setArchived(counters.archived);
            result.setPending(counters.pending);
            result.setSkipped(counters.skipped);
            result.setFailed(counters.failed);
            result.setCursorTime(cursor);
            result.setStartedAt(startedAt);
            result.setFinishedAt(LocalDateTime.now());
            log.info("档案同步完成: {}", state.getLastResult());
            return result;
        } finally {
            running.set(false);
        }
    }

    /**
     * 查询同步运行状态与条目/台账统计。
     *
     * @return 同步状态
     */
    public ArchiveSyncStatusVO status() {
        ArchiveSyncState state = findState();
        ArchiveSyncStatusVO vo = new ArchiveSyncStatusVO();
        vo.setEnabled(archiveProperties.getSync().isEnabled());
        vo.setCron(archiveProperties.getSync().getCron());
        vo.setCursorTime(state == null ? null : state.getCursorTime());
        vo.setLastRunAt(state == null ? null : state.getLastRunAt());
        vo.setLastResult(state == null ? null : state.getLastResult());
        vo.setArchivedCount(countItems(ArchiveConstants.ITEM_STATUS_ARCHIVED));
        vo.setPendingCount(countItems(ArchiveConstants.ITEM_STATUS_PENDING));
        vo.setFailedCount(countFailedRecords());
        return vo;
    }

    /**
     * 重试失败台账（按重试次数上限过滤）。
     *
     * @param counters 本轮计数
     */
    private void retryFailed(SyncCounters counters) {
        int maxAttempts = Math.max(archiveProperties.getSync().getMaxAttempts(), 1);
        List<ArchiveSourceRecord> records = sourceRecordMapper.selectList(new LambdaQueryWrapper<ArchiveSourceRecord>()
                .eq(ArchiveSourceRecord::getSourceType, ArchiveConstants.SOURCE_WORKFLOW)
                .eq(ArchiveSourceRecord::getSyncStatus, ArchiveConstants.SYNC_STATUS_FAILED)
                .lt(ArchiveSourceRecord::getRetryCount, maxAttempts)
                .orderByAsc(ArchiveSourceRecord::getUpdateTime)
                .last("LIMIT " + Math.max(archiveProperties.getSync().getRetryBatchSize(), 1)));
        for (ArchiveSourceRecord record : records) {
            Long instanceId = parseSourceId(record.getSourceId());
            if (instanceId == null) {
                continue;
            }
            applyOutcome(counters, syncOne(instanceId));
        }
    }

    /**
     * 按游标分页拉取已通过实例并逐条同步。
     *
     * @param counters 本轮计数
     * @param cursor 起始游标
     * @return 本轮结束游标
     */
    private LocalDateTime pullApproved(SyncCounters counters, LocalDateTime cursor) {
        int pageSize = Math.max(archiveProperties.getSync().getPageSize(), 1);
        long current = 1;
        LocalDateTime next = cursor;
        while (true) {
            RemoteApprovedPage page = workflowClient.approved(next, null, current, pageSize);
            List<RemoteApprovedInstance> records = page == null || page.getRecords() == null
                    ? List.of() : page.getRecords();
            if (records.isEmpty()) {
                return next;
            }
            for (RemoteApprovedInstance instance : records) {
                if (instance == null || instance.getId() == null) {
                    continue;
                }
                counters.pulled++;
                applyOutcome(counters, syncOne(instance.getId()));
                next = maxCursor(next, instance.getUpdateTime());
            }
            if (records.size() < pageSize) {
                return next;
            }
            current++;
        }
    }

    /**
     * 同步单个工作流实例。
     *
     * @param instanceId 工作流实例ID
     * @return 结果码：1 已归档 / 2 待整理 / 3 跳过
     */
    private int syncOne(Long instanceId) {
        String sourceId = String.valueOf(instanceId);
        ArchiveSourceRecord record = findRecord(sourceId);
        if (record != null && Objects.equals(record.getSyncStatus(), ArchiveConstants.SYNC_STATUS_SUCCESS)) {
            return OUTCOME_SKIPPED;
        }
        int maxAttempts = Math.max(archiveProperties.getSync().getMaxAttempts(), 1);
        if (record != null && record.getRetryCount() != null && record.getRetryCount() >= maxAttempts) {
            return OUTCOME_SKIPPED;
        }
        if (existsItem(sourceId)) {
            markSuccess(record, sourceId);
            return OUTCOME_SKIPPED;
        }
        try {
            return archive(instanceId, sourceId, record);
        } catch (DuplicateKeyException e) {
            markSuccess(record, sourceId);
            return OUTCOME_SKIPPED;
        } catch (Exception e) {
            recordFailure(record, sourceId, e);
            return OUTCOME_FAILED;
        }
    }

    /**
     * 生成快照并落库归档条目。
     *
     * @param instanceId 工作流实例ID
     * @param sourceId 来源ID字符串
     * @param record 已有台账（可为 {@code null}）
     * @return 结果码：1 已归档 / 2 待整理
     */
    private int archive(Long instanceId, String sourceId, ArchiveSourceRecord record) {
        RemoteInstanceDetail detail = workflowClient.detail(instanceId);
        if (detail == null || !Objects.equals(detail.getStatus(), INSTANCE_APPROVED)) {
            markSuccess(record, sourceId);
            return OUTCOME_SKIPPED;
        }
        RemoteFormArchive form = loadForm(detail.getBusinessKey());
        List<Long> attachmentIds = AttachmentExtractor.extract(
                form == null || form.getData() == null ? null : form.getData().getFields(),
                form == null ? null : form.getFieldDefinitions());
        List<RemoteFileMeta> attachments = loadFiles(attachmentIds);
        LocalDateTime now = LocalDateTime.now();
        byte[] snapshot = snapshotBuilder.build(detail, form, attachments, now);
        String snapshotHash = snapshotBuilder.sha256(snapshot);
        String snapshotName = snapshotBuilder.fileName(detail, now);
        Long snapshotFileId = fileClient.upload(snapshot, snapshotName, archiveProperties.getBucket());
        lockFiles(attachments, snapshotFileId);
        ArchiveRule rule = findRule(detail.getDefinitionCode());
        ArchiveCategory category = rule == null || rule.getCategoryId() == null
                ? null : categoryMapper.selectById(rule.getCategoryId());
        LocalDateTime finishedAt = resolveFinishedAt(detail);
        int year = finishedAt.getYear();
        boolean autoArchive = rule != null
                && Objects.equals(rule.getAutoArchive(), ArchiveConstants.AUTO_ARCHIVE_ON)
                && category != null;
        ArchiveItem item = buildItem(detail, rule, category, year, finishedAt, snapshotFileId, snapshotHash,
                autoArchive, now);
        List<ArchiveFile> fileRefs = buildFileRefs(snapshotFileId, snapshotName, snapshot.length, attachments);
        itemStore.persist(item, fileRefs, buildRecord(record, sourceId));
        return autoArchive ? OUTCOME_ARCHIVED : OUTCOME_PENDING;
    }

    /**
     * 构造档案条目实体。
     *
     * @param detail 实例归档详情
     * @param rule 命中的规则（可为 {@code null}）
     * @param category 门类（可为 {@code null}）
     * @param year 归档年度
     * @param finishedAt 办结时间
     * @param snapshotFileId 快照文件ID
     * @param snapshotHash 快照摘要
     * @param autoArchive 是否自动归档
     * @param now 当前时间
     * @return 档案条目
     */
    private ArchiveItem buildItem(RemoteInstanceDetail detail, ArchiveRule rule, ArchiveCategory category, int year,
                                  LocalDateTime finishedAt, Long snapshotFileId, String snapshotHash,
                                  boolean autoArchive, LocalDateTime now) {
        ArchiveItem item = new ArchiveItem();
        item.setTitle(detail.getTitle());
        item.setSourceType(ArchiveConstants.SOURCE_WORKFLOW);
        item.setSourceId(String.valueOf(detail.getId()));
        item.setDefinitionCode(detail.getDefinitionCode());
        item.setBusinessKey(detail.getBusinessKey());
        item.setInitiator(detail.getInitiator());
        item.setFinishedAt(finishedAt);
        item.setYear(year);
        item.setSnapshotFileId(snapshotFileId);
        item.setSnapshotHash(snapshotHash);
        item.setSecurityLevel(resolveSecurityLevel(rule, category));
        item.setRetentionYears(resolveRetentionYears(rule, category));
        item.setExpireAt(resolveExpireAt(item.getRetentionYears(), finishedAt));
        if (category != null) {
            item.setCategoryId(category.getId());
            item.setCategoryPath(CategoryPathResolver.resolve(categoryMapper, category));
        }
        if (autoArchive) {
            item.setArchiveNo(numberService.next(category.getCode(), year));
            item.setItemStatus(ArchiveConstants.ITEM_STATUS_ARCHIVED);
            item.setArchivedAt(now);
        } else {
            item.setItemStatus(ArchiveConstants.ITEM_STATUS_PENDING);
        }
        return item;
    }

    /**
     * 构造文件引用列表：快照在前，附件按提取顺序随后（条目ID在落库时回填）。
     *
     * @param snapshotFileId 快照文件ID
     * @param snapshotName 快照文件名
     * @param snapshotSize 快照字节数
     * @param attachments 附件元数据
     * @return 文件引用列表
     */
    private List<ArchiveFile> buildFileRefs(Long snapshotFileId, String snapshotName, int snapshotSize,
                                            List<RemoteFileMeta> attachments) {
        List<ArchiveFile> refs = new ArrayList<>();
        ArchiveFile snapshotRef = new ArchiveFile();
        snapshotRef.setFileId(snapshotFileId);
        snapshotRef.setFileRole(ArchiveConstants.FILE_ROLE_SNAPSHOT);
        snapshotRef.setOriginalName(snapshotName);
        snapshotRef.setFileSize((long) snapshotSize);
        snapshotRef.setContentType(ArchiveConstants.SNAPSHOT_CONTENT_TYPE);
        snapshotRef.setSort(0);
        refs.add(snapshotRef);
        int sort = 1;
        for (RemoteFileMeta meta : attachments) {
            ArchiveFile ref = new ArchiveFile();
            ref.setFileId(meta.getId());
            ref.setFileRole(ArchiveConstants.FILE_ROLE_ATTACHMENT);
            ref.setOriginalName(meta.getOriginalName());
            ref.setFileSize(meta.getFileSize());
            ref.setContentType(meta.getContentType());
            ref.setMd5(meta.getMd5());
            ref.setSort(sort++);
            refs.add(ref);
        }
        return refs;
    }

    /**
     * 锁定快照与全部附件（归档不可变）。
     *
     * @param attachments 附件元数据
     * @param snapshotFileId 快照文件ID
     */
    private void lockFiles(List<RemoteFileMeta> attachments, Long snapshotFileId) {
        for (RemoteFileMeta meta : attachments) {
            if (meta.getId() != null) {
                fileClient.lock(meta.getId());
            }
        }
        fileClient.lock(snapshotFileId);
    }

    /**
     * 读取附件元数据，丢弃已不存在或禁用的文件。
     *
     * @param fileIds 文件ID列表
     * @return 存在的文件元数据
     */
    private List<RemoteFileMeta> loadFiles(List<Long> fileIds) {
        List<RemoteFileMeta> metas = new ArrayList<>();
        for (Long fileId : fileIds) {
            RemoteFileMeta meta = fileClient.detail(fileId);
            if (meta == null) {
                log.warn("档案同步附件不存在，已跳过: fileId={}", fileId);
                continue;
            }
            metas.add(meta);
        }
        return metas;
    }

    /**
     * 读取业务键对应的表单数据；业务键非表单数据ID时返回 {@code null}。
     *
     * @param businessKey 流程实例业务键
     * @return 表单归档详情；无表单返回 {@code null}
     */
    private RemoteFormArchive loadForm(String businessKey) {
        Long dataId = parseFormDataId(businessKey);
        if (dataId == null) {
            return null;
        }
        return formClient.detail(dataId);
    }

    /**
     * 解析业务键中的表单数据ID：支持纯数字与 {@code prefix:数字} 两种形态。
     *
     * @param businessKey 业务键
     * @return 表单数据ID；无法解析返回 {@code null}
     */
    private static Long parseFormDataId(String businessKey) {
        if (!StringUtils.hasText(businessKey)) {
            return null;
        }
        String value = businessKey.trim();
        int separatorIndex = value.lastIndexOf(BUSINESS_KEY_SEPARATOR);
        if (separatorIndex >= 0 && separatorIndex < value.length() - 1) {
            value = value.substring(separatorIndex + 1);
        }
        try {
            long id = Long.parseLong(value);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 查询启用中的归档规则（同编码多条时取最早创建的一条）。
     *
     * @param definitionCode 流程定义编码
     * @return 命中的规则；未命中返回 {@code null}
     */
    private ArchiveRule findRule(String definitionCode) {
        if (!StringUtils.hasText(definitionCode)) {
            return null;
        }
        List<ArchiveRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<ArchiveRule>()
                .eq(ArchiveRule::getDefinitionCode, definitionCode)
                .eq(ArchiveRule::getEnabled, ArchiveConstants.STATUS_ENABLED)
                .orderByAsc(ArchiveRule::getId));
        return rules.isEmpty() ? null : rules.get(0);
    }

    /**
     * 解析办结时间：优先实例办结字段，缺失时回落实例更新时间。
     *
     * @param detail 实例归档详情
     * @return 办结时间
     */
    private static LocalDateTime resolveFinishedAt(RemoteInstanceDetail detail) {
        if (detail.getFinishedAt() != null) {
            return detail.getFinishedAt();
        }
        return detail.getUpdateTime() == null ? LocalDateTime.now() : detail.getUpdateTime();
    }

    /**
     * 解析密级：规则优先，其次门类默认，最后内部。
     *
     * @param rule 命中规则
     * @param category 门类
     * @return 密级
     */
    private static int resolveSecurityLevel(ArchiveRule rule, ArchiveCategory category) {
        if (rule != null && rule.getSecurityLevel() != null) {
            return rule.getSecurityLevel();
        }
        if (category != null && category.getSecurityLevel() != null) {
            return category.getSecurityLevel();
        }
        return ArchiveConstants.SECURITY_INTERNAL;
    }

    /**
     * 解析保管期限：规则优先，其次门类默认（空表示长期）。
     *
     * @param rule 命中规则
     * @param category 门类
     * @return 保管期限（年），可为 {@code null}
     */
    private static Integer resolveRetentionYears(ArchiveRule rule, ArchiveCategory category) {
        if (rule != null && rule.getRetentionYears() != null) {
            return rule.getRetentionYears();
        }
        return category == null ? null : category.getRetentionYears();
    }

    /**
     * 计算保管到期时间。
     *
     * @param retentionYears 保管期限（年）
     * @param finishedAt 办结时间
     * @return 到期时间；长期保管返回 {@code null}
     */
    private static LocalDateTime resolveExpireAt(Integer retentionYears, LocalDateTime finishedAt) {
        if (retentionYears == null || retentionYears <= 0) {
            return null;
        }
        return finishedAt.plusYears(retentionYears);
    }

    /**
     * 构造成功台账（新增或复用已有台账）。
     *
     * @param existing 已有台账（可为 {@code null}）
     * @param sourceId 来源ID
     * @return 台账实体
     */
    private static ArchiveSourceRecord buildRecord(ArchiveSourceRecord existing, String sourceId) {
        ArchiveSourceRecord record = existing == null ? new ArchiveSourceRecord() : existing;
        if (existing == null) {
            record.setSourceType(ArchiveConstants.SOURCE_WORKFLOW);
            record.setSourceId(sourceId);
            record.setRetryCount(0);
        }
        record.setSyncStatus(ArchiveConstants.SYNC_STATUS_SUCCESS);
        record.setLastError(null);
        record.setLastSyncAt(LocalDateTime.now());
        return record;
    }

    /**
     * 标记来源同步成功（用于无需归档的跳过场景）。
     *
     * @param record 已有台账（可为 {@code null}）
     * @param sourceId 来源ID
     */
    private void markSuccess(ArchiveSourceRecord record, String sourceId) {
        itemStore.saveRecord(buildRecord(record, sourceId));
    }

    /**
     * 记录同步失败并累计重试次数。
     *
     * @param record 已有台账（可为 {@code null}）
     * @param sourceId 来源ID
     * @param error 异常
     */
    private void recordFailure(ArchiveSourceRecord record, String sourceId, Exception error) {
        ArchiveSourceRecord failed = record == null ? new ArchiveSourceRecord() : record;
        if (record == null) {
            failed.setSourceType(ArchiveConstants.SOURCE_WORKFLOW);
            failed.setSourceId(sourceId);
        }
        int retryCount = failed.getRetryCount() == null ? 0 : failed.getRetryCount();
        failed.setSyncStatus(ArchiveConstants.SYNC_STATUS_FAILED);
        failed.setRetryCount(retryCount + 1);
        failed.setLastError(truncate(error.getMessage() == null ? error.getClass().getSimpleName()
                : error.getMessage()));
        failed.setLastSyncAt(LocalDateTime.now());
        itemStore.saveRecord(failed);
        log.warn("档案同步失败: sourceId={}, retry={}, error={}", sourceId, failed.getRetryCount(),
                failed.getLastError());
    }

    /**
     * 查询来源同步台账。
     *
     * @param sourceId 来源ID
     * @return 台账；不存在返回 {@code null}
     */
    private ArchiveSourceRecord findRecord(String sourceId) {
        return sourceRecordMapper.selectOne(new LambdaQueryWrapper<ArchiveSourceRecord>()
                .eq(ArchiveSourceRecord::getSourceType, ArchiveConstants.SOURCE_WORKFLOW)
                .eq(ArchiveSourceRecord::getSourceId, sourceId)
                .last("LIMIT 1"));
    }

    /**
     * 判断是否已存在该来源的档案条目。
     *
     * @param sourceId 来源ID
     * @return 存在返回 {@code true}
     */
    private boolean existsItem(String sourceId) {
        return itemMapper.selectCount(new LambdaQueryWrapper<ArchiveItem>()
                .eq(ArchiveItem::getSourceType, ArchiveConstants.SOURCE_WORKFLOW)
                .eq(ArchiveItem::getSourceId, sourceId)) > 0;
    }

    /**
     * 统计指定状态的条目数。
     *
     * @param itemStatus 条目状态
     * @return 条目数
     */
    private long countItems(int itemStatus) {
        return itemMapper.selectCount(new LambdaQueryWrapper<ArchiveItem>()
                .eq(ArchiveItem::getItemStatus, itemStatus));
    }

    /**
     * 统计失败待重试来源数。
     *
     * @return 失败台账数
     */
    private long countFailedRecords() {
        return sourceRecordMapper.selectCount(new LambdaQueryWrapper<ArchiveSourceRecord>()
                .eq(ArchiveSourceRecord::getSyncStatus, ArchiveConstants.SYNC_STATUS_FAILED));
    }

    /**
     * 加载或初始化同步游标。
     *
     * @return 游标实体
     */
    private ArchiveSyncState loadOrCreateState() {
        ArchiveSyncState state = findState();
        if (state != null) {
            return state;
        }
        state = new ArchiveSyncState();
        state.setName(ArchiveConstants.SYNC_CURSOR_WORKFLOW_APPROVED);
        return state;
    }

    /**
     * 查询同步游标。
     *
     * @return 游标实体；不存在返回 {@code null}
     */
    private ArchiveSyncState findState() {
        return syncStateMapper.selectOne(new LambdaQueryWrapper<ArchiveSyncState>()
                .eq(ArchiveSyncState::getName, ArchiveConstants.SYNC_CURSOR_WORKFLOW_APPROVED)
                .last("LIMIT 1"));
    }

    /**
     * 保存同步游标（新增或更新）。
     *
     * @param state 游标实体
     */
    private void saveState(ArchiveSyncState state) {
        if (state.getId() == null) {
            syncStateMapper.insert(state);
        } else {
            syncStateMapper.updateById(state);
        }
    }

    /**
     * 结果码累加到计数器。
     *
     * @param counters 本轮计数
     * @param outcome 结果码
     */
    private static void applyOutcome(SyncCounters counters, int outcome) {
        if (outcome == OUTCOME_ARCHIVED) {
            counters.archived++;
        } else if (outcome == OUTCOME_PENDING) {
            counters.pending++;
        } else if (outcome == OUTCOME_FAILED) {
            counters.failed++;
        } else {
            counters.skipped++;
        }
    }

    /**
     * 取两个游标中的较大者（忽略空值）。
     *
     * @param current 当前游标
     * @param candidate 候选游标
     * @return 较大游标
     */
    private static LocalDateTime maxCursor(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    /**
     * 解析来源ID为工作流实例ID。
     *
     * @param sourceId 来源ID字符串
     * @return 实例ID；非法返回 {@code null}
     */
    private static Long parseSourceId(String sourceId) {
        try {
            long id = Long.parseLong(sourceId);
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 截断失败原因以适配台账列宽。
     *
     * @param message 原始消息
     * @return 截断后的消息
     */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ArchiveConstants.MAX_ERROR_LENGTH
                ? message : message.substring(0, ArchiveConstants.MAX_ERROR_LENGTH);
    }

    /**
     * 生成同步结果摘要。
     *
     * @param counters 本轮计数
     * @return 摘要文本
     */
    private static String summarize(SyncCounters counters) {
        return String.format(Locale.ROOT, SUMMARY_FORMAT,
                counters.pulled, counters.archived, counters.pending, counters.skipped, counters.failed);
    }

    /**
     * 单轮同步计数器。
     *
     * @author BlueWhale
     */
    private static final class SyncCounters {

        /** 拉取条数 */
        private long pulled;

        /** 已归档条数 */
        private long archived;

        /** 待整理条数 */
        private long pending;

        /** 跳过条数 */
        private long skipped;

        /** 失败条数 */
        private long failed;
    }
}
