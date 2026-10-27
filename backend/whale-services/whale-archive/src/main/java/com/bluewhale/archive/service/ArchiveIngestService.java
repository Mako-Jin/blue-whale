package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.archive.client.FileArchiveClient;
import com.bluewhale.archive.config.ArchiveProperties;
import com.bluewhale.archive.dto.ArchiveIngestRequest;
import com.bluewhale.archive.entity.ArchiveFile;
import com.bluewhale.archive.entity.ArchiveItem;
import com.bluewhale.archive.entity.ArchiveSourceRecord;
import com.bluewhale.archive.mapper.ArchiveFileMapper;
import com.bluewhale.archive.mapper.ArchiveItemMapper;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.support.ArchiveSnapshotBuilder;
import com.bluewhale.archive.vo.ArchiveIngestResultVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 业务系统快照入档服务。
 *
 * <p>与工作流增量同步共用落库链路（{@link ArchiveItemStore} + {@code archive_source_record} 台账），
 * 差异仅在于来源为业务服务主动推送：快照内容由调用方给出，档案服务负责上传快照文件、
 * 计算 SHA-256 并生成「待整理」条目，档号仍由档案人员确认归档时统一分配。</p>
 *
 * <p>幂等键 {@code (sourceType, sourceId)}：重复推送直接返回既有条目，已归档条目内容不被改写。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveIngestService {

    /** 允许推送的来源类型白名单（当前仅合同） */
    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(ArchiveConstants.SOURCE_CONTRACT);

    /** 快照内容上限（1MB） */
    private static final int MAX_SNAPSHOT_BYTES = 1024 * 1024;

    /** 标题最大长度（与 archive_item.title 对齐） */
    private static final int MAX_TITLE_LENGTH = 255;

    /** 定义编码兜底值 */
    private static final String DEFAULT_DEFINITION_CODE = "CONTRACT";

    /** 原件 MIME 兜底值 */
    private static final String DEFAULT_ORIGINAL_CONTENT_TYPE = "application/octet-stream";

    private final ArchiveItemMapper itemMapper;

    private final ArchiveFileMapper fileMapper;

    private final ArchiveItemStore itemStore;

    private final FileArchiveClient fileClient;

    private final ArchiveSnapshotBuilder snapshotBuilder;

    private final ArchiveProperties archiveProperties;

    /**
     * 接收业务快照并生成档案条目。
     *
     * @param request 快照归档请求
     * @return 归档结果
     */
    public ArchiveIngestResultVO ingest(ArchiveIngestRequest request) {
        String sourceType = normalizeSourceType(request.getSourceType());
        String sourceId = request.getSourceId().trim();
        ArchiveItem existing = findBySource(sourceType, sourceId);
        if (existing != null) {
            lockExisting(existing);
            log.info("业务快照重复推送，幂等返回: sourceType={}, sourceId={}, itemId={}",
                    sourceType, sourceId, existing.getId());
            return toResult(existing, false);
        }
        byte[] snapshot = snapshotBytes(request.getSnapshotJson());
        String snapshotHash = snapshotBuilder.sha256(snapshot);
        String fileName = resolveFileName(request, sourceType, sourceId);
        Long snapshotFileId = fileClient.upload(snapshot, fileName, archiveProperties.getBucket());
        ArchiveItem item = buildItem(request, sourceType, sourceId, snapshotFileId, snapshotHash);
        List<ArchiveFile> refs = buildFileRefs(request, snapshotFileId, fileName, snapshot.length);
        lockFiles(refs);
        try {
            itemStore.persist(item, refs, buildRecord(sourceType, sourceId));
        } catch (DuplicateKeyException ex) {
            ArchiveItem concurrent = findBySource(sourceType, sourceId);
            if (concurrent != null) {
                log.info("业务快照并发重复，幂等返回: sourceType={}, sourceId={}", sourceType, sourceId);
                return toResult(concurrent, false);
            }
            throw ex;
        }
        log.info("业务快照已入档: sourceType={}, sourceId={}, itemId={}, snapshotFileId={}",
                sourceType, sourceId, item.getId(), snapshotFileId);
        return toResult(item, true);
    }

    /**
     * 校验并归一化来源类型。
     *
     * @param rawType 原始来源类型
     * @return 大写的合法来源类型
     */
    private String normalizeSourceType(String rawType) {
        String sourceType = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SOURCE_TYPES.contains(sourceType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的来源类型: " + rawType);
        }
        return sourceType;
    }

    /**
     * 按幂等键查询既有条目。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源ID
     * @return 条目；不存在返回 {@code null}
     */
    private ArchiveItem findBySource(String sourceType, String sourceId) {
        return itemMapper.selectOne(new LambdaQueryWrapper<ArchiveItem>()
                .eq(ArchiveItem::getSourceType, sourceType)
                .eq(ArchiveItem::getSourceId, sourceId)
                .last("LIMIT 1"));
    }

    /**
     * 校验并读取快照字节。
     *
     * @param snapshotJson 快照 JSON 文本
     * @return 快照字节
     */
    private byte[] snapshotBytes(String snapshotJson) {
        byte[] snapshot = snapshotJson == null ? new byte[0] : snapshotJson.getBytes(StandardCharsets.UTF_8);
        if (snapshot.length == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "快照内容不能为空");
        }
        if (snapshot.length > MAX_SNAPSHOT_BYTES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "快照内容超过 1MB 上限");
        }
        return snapshot;
    }

    /**
     * 解析快照文件名。
     *
     * @param request 归档请求
     * @param sourceType 来源类型
     * @param sourceId 来源ID
     * @return 快照文件名
     */
    private String resolveFileName(ArchiveIngestRequest request, String sourceType, String sourceId) {
        return StringUtils.hasText(request.getFileName())
                ? request.getFileName().trim()
                : sourceType + "-" + sourceId + ArchiveConstants.SNAPSHOT_FILE_SUFFIX;
    }

    /**
     * 构造档案条目（待整理）。
     *
     * @param request 归档请求
     * @param sourceType 来源类型
     * @param sourceId 来源ID
     * @param snapshotFileId 快照文件ID
     * @param snapshotHash 快照摘要
     * @return 档案条目
     */
    private ArchiveItem buildItem(ArchiveIngestRequest request, String sourceType, String sourceId,
                                  Long snapshotFileId, String snapshotHash) {
        LocalDateTime finishedAt = request.getFinishedAt() == null ? LocalDateTime.now() : request.getFinishedAt();
        ArchiveItem item = new ArchiveItem();
        item.setTitle(truncate(request.getTitle().trim(), MAX_TITLE_LENGTH));
        item.setSourceType(sourceType);
        item.setSourceId(sourceId);
        item.setDefinitionCode(StringUtils.hasText(request.getDefinitionCode())
                ? request.getDefinitionCode().trim() : DEFAULT_DEFINITION_CODE);
        item.setBusinessKey(trimToNull(request.getBusinessKey()));
        item.setInitiator(trimToNull(request.getInitiator()));
        item.setFinishedAt(finishedAt);
        item.setYear(finishedAt.getYear());
        item.setSecurityLevel(request.getSecurityLevel() == null
                ? ArchiveConstants.SECURITY_INTERNAL : request.getSecurityLevel());
        item.setSummary(trimToNull(request.getSummary()));
        item.setKeywords(trimToNull(request.getKeywords()));
        item.setSnapshotFileId(snapshotFileId);
        item.setSnapshotHash(snapshotHash);
        item.setItemStatus(ArchiveConstants.ITEM_STATUS_PENDING);
        return item;
    }

    /**
     * 构造文件引用：快照在前，原件（可选）随后。
     *
     * @param request 归档请求
     * @param snapshotFileId 快照文件ID
     * @param fileName 快照文件名
     * @param snapshotSize 快照字节数
     * @return 文件引用列表
     */
    private List<ArchiveFile> buildFileRefs(ArchiveIngestRequest request, Long snapshotFileId,
                                            String fileName, int snapshotSize) {
        List<ArchiveFile> refs = new ArrayList<>();
        ArchiveFile snapshotRef = new ArchiveFile();
        snapshotRef.setFileId(snapshotFileId);
        snapshotRef.setFileRole(ArchiveConstants.FILE_ROLE_SNAPSHOT);
        snapshotRef.setOriginalName(fileName);
        snapshotRef.setFileSize((long) snapshotSize);
        snapshotRef.setContentType(ArchiveConstants.SNAPSHOT_CONTENT_TYPE);
        snapshotRef.setSort(0);
        refs.add(snapshotRef);
        if (request.getOriginalFileId() != null) {
            ArchiveFile originalRef = new ArchiveFile();
            originalRef.setFileId(request.getOriginalFileId());
            originalRef.setFileRole(ArchiveConstants.FILE_ROLE_ORIGINAL);
            originalRef.setOriginalName(trimToNull(request.getOriginalFileName()));
            originalRef.setFileSize(request.getOriginalFileSize());
            originalRef.setContentType(StringUtils.hasText(request.getOriginalContentType())
                    ? request.getOriginalContentType().trim() : DEFAULT_ORIGINAL_CONTENT_TYPE);
            originalRef.setSort(1);
            refs.add(originalRef);
        }
        return refs;
    }

    /**
     * 锁定新增入档的快照与原件引用（归档不可变）。
     *
     * <p>与工作流同步口径一致：先锁定文件再落库，锁定失败则不产生档案条目，
     * 由业务侧重试；文件服务的锁定接口幂等，重试不会重复变更。</p>
     *
     * @param refs 文件引用
     */
    private void lockFiles(List<ArchiveFile> refs) {
        for (ArchiveFile ref : refs) {
            if (ref.getFileId() != null) {
                fileClient.lock(ref.getFileId());
            }
        }
    }

    /**
     * 重复推送时补锁既有条目的文件，用于修复历史未锁定条目。
     *
     * @param item 既有档案条目
     */
    private void lockExisting(ArchiveItem item) {
        List<ArchiveFile> refs = fileMapper.selectList(new LambdaQueryWrapper<ArchiveFile>()
                .eq(ArchiveFile::getItemId, item.getId()));
        if (refs.isEmpty() && item.getSnapshotFileId() != null) {
            fileClient.lock(item.getSnapshotFileId());
            return;
        }
        lockFiles(refs);
    }

    /**
     * 构造同步台账（成功态）。
     *
     * @param sourceType 来源类型
     * @param sourceId 来源ID
     * @return 台账记录
     */
    private ArchiveSourceRecord buildRecord(String sourceType, String sourceId) {
        ArchiveSourceRecord record = new ArchiveSourceRecord();
        record.setSourceType(sourceType);
        record.setSourceId(sourceId);
        record.setSyncStatus(ArchiveConstants.SYNC_STATUS_SUCCESS);
        record.setRetryCount(0);
        record.setLastSyncAt(LocalDateTime.now());
        return record;
    }

    /**
     * 转换为返回视图。
     *
     * @param item 档案条目
     * @param created 是否本次新建
     * @return 归档结果
     */
    private ArchiveIngestResultVO toResult(ArchiveItem item, boolean created) {
        ArchiveIngestResultVO vo = new ArchiveIngestResultVO();
        vo.setItemId(String.valueOf(item.getId()));
        vo.setSourceType(item.getSourceType());
        vo.setSourceId(item.getSourceId());
        vo.setItemStatus(item.getItemStatus());
        vo.setSnapshotFileId(item.getSnapshotFileId() == null ? null : String.valueOf(item.getSnapshotFileId()));
        vo.setSnapshotHash(item.getSnapshotHash());
        vo.setCreated(created);
        return vo;
    }

    /**
     * 截断超长文本。
     *
     * @param text 原文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /**
     * 去空白并将空串转为 {@code null}。
     *
     * @param text 原文本
     * @return 处理后的文本
     */
    private String trimToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
