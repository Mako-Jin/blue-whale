package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.archive.client.FileArchiveClient;
import com.bluewhale.archive.client.WorkflowArchiveClient;
import com.bluewhale.archive.dto.ArchiveItemEditRequest;
import com.bluewhale.archive.dto.ArchiveItemQueryRequest;
import com.bluewhale.archive.entity.ArchiveCategory;
import com.bluewhale.archive.entity.ArchiveFile;
import com.bluewhale.archive.entity.ArchiveItem;
import com.bluewhale.archive.mapper.ArchiveCategoryMapper;
import com.bluewhale.archive.mapper.ArchiveFileMapper;
import com.bluewhale.archive.mapper.ArchiveItemMapper;
import com.bluewhale.archive.remote.RemoteInstanceDetail;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.support.CategoryPathResolver;
import com.bluewhale.archive.vo.ArchiveDownloadVO;
import com.bluewhale.archive.vo.ArchiveFileVO;
import com.bluewhale.archive.vo.ArchiveItemDetailVO;
import com.bluewhale.archive.vo.ArchiveItemVO;
import com.bluewhale.archive.vo.ArchiveStatsVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 档案条目服务：检索、整理、确认归档、销毁、代理下载与统计。
 *
 * <p>整理仅允许待整理条目；确认归档按「门类编码 + 年度」分配唯一档号，
 * 并以 {@code item_status} 条件更新防止并发重复取号；已归档条目不随源实例变更改写。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArchiveItemService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 标题最大长度 */
    private static final int MAX_TITLE_LENGTH = 256;

    /** 摘要最大长度 */
    private static final int MAX_SUMMARY_LENGTH = 2000;

    /** 关键词最大长度 */
    private static final int MAX_KEYWORDS_LENGTH = 512;

    /** 下载文件名兜底值 */
    private static final String DEFAULT_FILE_NAME = "archive-file";

    /** 下载 MIME 兜底值 */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /** 统计分组键缺失时的占位名 */
    private static final String UNKNOWN_GROUP = "未设置";

    /** 统计分组查询的键别名 */
    private static final String ALIAS_KEY = "k";

    /** 统计分组查询的计数别名 */
    private static final String ALIAS_COUNT = "c";

    private final ArchiveItemMapper itemMapper;

    private final ArchiveFileMapper fileMapper;

    private final ArchiveCategoryMapper categoryMapper;

    private final ArchiveNumberService numberService;

    private final WorkflowArchiveClient workflowClient;

    private final FileArchiveClient fileClient;

    /**
     * 分页查询档案条目。
     *
     * @param request 查询条件
     * @return 条目分页结果（含门类名称）
     */
    public PageResult<ArchiveItemVO> page(ArchiveItemQueryRequest request) {
        LambdaQueryWrapper<ArchiveItem> wrapper = new LambdaQueryWrapper<>();
        String keyword = StringUtils.hasText(request.getKeyword()) ? request.getKeyword().trim() : null;
        if (keyword != null) {
            wrapper.and(query -> query.like(ArchiveItem::getArchiveNo, keyword)
                    .or().like(ArchiveItem::getTitle, keyword)
                    .or().like(ArchiveItem::getSummary, keyword)
                    .or().like(ArchiveItem::getKeywords, keyword));
        }
        wrapper.eq(request.getCategoryId() != null, ArchiveItem::getCategoryId, request.getCategoryId());
        wrapper.eq(request.getYear() != null, ArchiveItem::getYear, request.getYear());
        wrapper.eq(request.getSecurityLevel() != null, ArchiveItem::getSecurityLevel, request.getSecurityLevel());
        wrapper.eq(request.getItemStatus() != null, ArchiveItem::getItemStatus, request.getItemStatus());
        wrapper.orderByDesc(ArchiveItem::getCreateTime).orderByDesc(ArchiveItem::getId);
        long current = Math.max(request.getCurrent(), 1L);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<ArchiveItem> page = itemMapper.selectPage(new Page<>(current, size), wrapper);
        Map<Long, String> categoryNames = categoryNames(page.getRecords());
        List<ArchiveItemVO> records = page.getRecords().stream()
                .map(item -> toVo(item, categoryNames.get(item.getCategoryId())))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 查询条目详情（含文件引用与来源实例当前状态）。
     *
     * @param id 条目ID
     * @return 条目详情
     */
    public ArchiveItemDetailVO detail(Long id) {
        ArchiveItem item = requireItem(id);
        ArchiveItemDetailVO vo = new ArchiveItemDetailVO();
        vo.setItem(toVo(item, categoryName(item.getCategoryId())));
        vo.setFiles(filesOf(id));
        vo.setSourceStatus(sourceStatus(item));
        return vo;
    }

    /**
     * 整理待整理条目：门类、标题、密级、保管期限、摘要与关键词。
     *
     * @param id 条目ID
     * @param request 整理请求
     * @return 整理后的条目视图
     */
    @Transactional(rollbackFor = Exception.class)
    public ArchiveItemVO edit(Long id, ArchiveItemEditRequest request) {
        ArchiveItem item = requireItem(id);
        requirePending(item);
        ArchiveCategory category = requireEnabledCategory(request.getCategoryId());
        ArchiveItem update = new ArchiveItem();
        update.setId(id);
        update.setCategoryId(category.getId());
        update.setCategoryPath(CategoryPathResolver.resolve(categoryMapper, category));
        if (StringUtils.hasText(request.getTitle())) {
            update.setTitle(normalize(request.getTitle(), MAX_TITLE_LENGTH, "档案标题"));
        }
        if (request.getSecurityLevel() != null) {
            update.setSecurityLevel(request.getSecurityLevel());
        }
        if (request.getRetentionYears() != null) {
            update.setRetentionYears(request.getRetentionYears());
            if (item.getFinishedAt() != null) {
                update.setExpireAt(item.getFinishedAt().plusYears(request.getRetentionYears()));
            }
        }
        if (StringUtils.hasText(request.getSummary())) {
            update.setSummary(normalize(request.getSummary(), MAX_SUMMARY_LENGTH, "档案摘要"));
        }
        if (StringUtils.hasText(request.getKeywords())) {
            update.setKeywords(normalize(request.getKeywords(), MAX_KEYWORDS_LENGTH, "关键词"));
        }
        itemMapper.updateById(update);
        return toVo(itemMapper.selectById(id), category.getName());
    }

    /**
     * 确认归档：分配唯一档号并置为已归档；已归档条目重复确认时直接返回。
     *
     * @param id 条目ID
     * @param operatorId 操作人ID
     * @return 归档后的条目视图
     */
    @Transactional(rollbackFor = Exception.class)
    public ArchiveItemVO confirm(Long id, Long operatorId) {
        ArchiveItem item = requireItem(id);
        if (Objects.equals(item.getItemStatus(), ArchiveConstants.ITEM_STATUS_ARCHIVED)) {
            return toVo(item, categoryName(item.getCategoryId()));
        }
        requirePending(item);
        ArchiveCategory category = requireEnabledCategory(item.getCategoryId());
        int year = resolveYear(item);
        String archiveNo = numberService.next(category.getCode(), year);
        ArchiveItem update = new ArchiveItem();
        update.setArchiveNo(archiveNo);
        update.setYear(year);
        update.setItemStatus(ArchiveConstants.ITEM_STATUS_ARCHIVED);
        update.setArchivedBy(operatorId);
        update.setArchivedAt(LocalDateTime.now());
        int rows = itemMapper.update(update, new LambdaUpdateWrapper<ArchiveItem>()
                .eq(ArchiveItem::getId, id)
                .eq(ArchiveItem::getItemStatus, ArchiveConstants.ITEM_STATUS_PENDING));
        if (rows == 0) {
            ArchiveItem latest = requireItem(id);
            if (Objects.equals(latest.getItemStatus(), ArchiveConstants.ITEM_STATUS_ARCHIVED)) {
                return toVo(latest, category.getName());
            }
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "条目状态已变更，请刷新后重试");
        }
        ArchiveItem latest = requireItem(id);
        return toVo(latest, category.getName());
    }

    /**
     * 销毁登记：已归档条目置为已销毁；已销毁条目重复调用幂等返回。
     *
     * @param id 条目ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void destroy(Long id) {
        ArchiveItem item = requireItem(id);
        if (Objects.equals(item.getItemStatus(), ArchiveConstants.ITEM_STATUS_DESTROYED)) {
            return;
        }
        if (!Objects.equals(item.getItemStatus(), ArchiveConstants.ITEM_STATUS_ARCHIVED)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "待整理条目不可销毁，请先确认归档");
        }
        ArchiveItem update = new ArchiveItem();
        update.setItemStatus(ArchiveConstants.ITEM_STATUS_DESTROYED);
        int rows = itemMapper.update(update, new LambdaUpdateWrapper<ArchiveItem>()
                .eq(ArchiveItem::getId, id)
                .eq(ArchiveItem::getItemStatus, ArchiveConstants.ITEM_STATUS_ARCHIVED));
        if (rows == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "条目状态已变更，请刷新后重试");
        }
    }

    /**
     * 下载条目下的文件：先校验文件确属该条目，再经文件服务内网代理读取。
     *
     * @param id 条目ID
     * @param fileId 文件引用ID
     * @return 下载载荷（文件名、MIME 与字节内容）
     */
    public ArchiveDownloadVO download(Long id, Long fileId) {
        requireItem(id);
        ArchiveFile file = fileMapper.selectById(fileId);
        if (file == null || !Objects.equals(file.getItemId(), id)) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "档案文件不存在");
        }
        ArchiveDownloadVO vo = new ArchiveDownloadVO();
        vo.setFileName(StringUtils.hasText(file.getOriginalName()) ? file.getOriginalName() : DEFAULT_FILE_NAME);
        vo.setContentType(StringUtils.hasText(file.getContentType())
                ? file.getContentType() : DEFAULT_CONTENT_TYPE);
        vo.setContent(fileClient.content(file.getFileId()));
        return vo;
    }

    /**
     * 统计条目总数、各状态数量与年度/密级/门类分布。
     *
     * @return 统计视图
     */
    public ArchiveStatsVO stats() {
        ArchiveStatsVO vo = new ArchiveStatsVO();
        vo.setTotal(itemMapper.selectCount(null));
        vo.setPending(countByStatus(ArchiveConstants.ITEM_STATUS_PENDING));
        vo.setArchived(countByStatus(ArchiveConstants.ITEM_STATUS_ARCHIVED));
        vo.setDestroyed(countByStatus(ArchiveConstants.ITEM_STATUS_DESTROYED));
        vo.setByYear(groupCount("year"));
        vo.setBySecurity(groupCount("security_level"));
        vo.setByCategory(groupCount("category_id"));
        return vo;
    }

    /**
     * 查询条目，不存在时抛出 {@code NOT_FOUND}。
     *
     * @param id 条目ID
     * @return 条目实体
     */
    private ArchiveItem requireItem(Long id) {
        ArchiveItem item = id == null ? null : itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "档案条目不存在");
        }
        return item;
    }

    /**
     * 校验条目处于待整理状态。
     *
     * @param item 条目实体
     */
    private static void requirePending(ArchiveItem item) {
        if (!Objects.equals(item.getItemStatus(), ArchiveConstants.ITEM_STATUS_PENDING)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "仅待整理条目可执行该操作");
        }
    }

    /**
     * 查询启用状态的门类。
     *
     * @param categoryId 门类ID
     * @return 门类实体
     */
    private ArchiveCategory requireEnabledCategory(Long categoryId) {
        ArchiveCategory category = categoryId == null ? null : categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "归档门类不存在");
        }
        if (!Objects.equals(category.getStatus(), ArchiveConstants.STATUS_ENABLED)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "归档门类已停用");
        }
        return category;
    }

    /**
     * 计算归档年度：优先条目年度，其次来源办结年度，最后当前年度。
     *
     * @param item 条目实体
     * @return 归档年度
     */
    private static int resolveYear(ArchiveItem item) {
        if (item.getYear() != null) {
            return item.getYear();
        }
        return item.getFinishedAt() != null ? item.getFinishedAt().getYear() : LocalDate.now().getYear();
    }

    /**
     * 归一化并校验文本长度。
     *
     * @param raw 原始文本
     * @param maxLength 最大长度
     * @param fieldName 字段中文名（用于错误提示）
     * @return 去空白后的文本
     */
    private static String normalize(String raw, int maxLength, String fieldName) {
        String value = raw.trim();
        if (value.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(),
                    fieldName + "不能超过 " + maxLength + " 字");
        }
        return value;
    }

    /**
     * 按状态统计条目数量。
     *
     * @param itemStatus 条目状态
     * @return 数量
     */
    private Long countByStatus(int itemStatus) {
        return itemMapper.selectCount(new LambdaQueryWrapper<ArchiveItem>()
                .eq(ArchiveItem::getItemStatus, itemStatus));
    }

    /**
     * 按指定列分组统计数量。
     *
     * @param column 统计列（数据库列名）
     * @return 分组值 → 数量（保留插入顺序）
     */
    private Map<String, Long> groupCount(String column) {
        List<Map<String, Object>> rows = itemMapper.selectMaps(new QueryWrapper<ArchiveItem>()
                .select(column + " AS " + ALIAS_KEY, "COUNT(*) AS " + ALIAS_COUNT)
                .groupBy(column)
                .orderByAsc(column));
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = row.get(ALIAS_KEY) == null ? UNKNOWN_GROUP : String.valueOf(row.get(ALIAS_KEY));
            result.put(key, asLong(row.get(ALIAS_COUNT)));
        }
        return result;
    }

    /**
     * 数值安全转换。
     *
     * @param value 原始值
     * @return 长整型数值；非数值返回 0
     */
    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 读取来源实例当前状态；工作流不可达时返回 {@code null}，不阻断详情查询。
     *
     * @param item 条目实体
     * @return 来源实例状态
     */
    private Integer sourceStatus(ArchiveItem item) {
        if (!ArchiveConstants.SOURCE_WORKFLOW.equals(item.getSourceType()) || item.getSourceId() == null) {
            return null;
        }
        try {
            RemoteInstanceDetail detail = workflowClient.detail(Long.valueOf(item.getSourceId()));
            return detail == null ? null : detail.getStatus();
        } catch (RuntimeException e) {
            log.warn("读取来源实例状态失败: sourceId={}, error={}", item.getSourceId(), e.getMessage());
            return null;
        }
    }

    /**
     * 查询条目的文件引用（按排序号、ID 升序）。
     *
     * @param itemId 条目ID
     * @return 文件引用列表
     */
    private List<ArchiveFileVO> filesOf(Long itemId) {
        return fileMapper.selectList(new LambdaQueryWrapper<ArchiveFile>()
                        .eq(ArchiveFile::getItemId, itemId)
                        .orderByAsc(ArchiveFile::getSort)
                        .orderByAsc(ArchiveFile::getId))
                .stream().map(ArchiveItemService::toFileVo).toList();
    }

    /**
     * 查询单个门类名称。
     *
     * @param categoryId 门类ID
     * @return 门类名称；不存在时为 {@code null}
     */
    private String categoryName(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        ArchiveCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    /**
     * 批量查询条目涉及的门类名称。
     *
     * @param items 条目列表
     * @return 门类ID → 门类名称
     */
    private Map<Long, String> categoryNames(List<ArchiveItem> items) {
        Set<Long> categoryIds = new HashSet<>();
        for (ArchiveItem item : items) {
            if (item.getCategoryId() != null) {
                categoryIds.add(item.getCategoryId());
            }
        }
        if (categoryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>(categoryIds.size());
        for (ArchiveCategory category : categoryMapper.selectBatchIds(categoryIds)) {
            names.put(category.getId(), category.getName());
        }
        return names;
    }

    /**
     * 条目实体转视图。
     *
     * @param entity 条目实体
     * @param categoryName 门类名称，可为 {@code null}
     * @return 条目视图
     */
    private static ArchiveItemVO toVo(ArchiveItem entity, String categoryName) {
        ArchiveItemVO vo = new ArchiveItemVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setArchiveNo(entity.getArchiveNo());
        vo.setTitle(entity.getTitle());
        vo.setCategoryId(entity.getCategoryId() == null ? null : String.valueOf(entity.getCategoryId()));
        vo.setCategoryName(categoryName);
        vo.setCategoryPath(entity.getCategoryPath());
        vo.setYear(entity.getYear());
        vo.setRetentionYears(entity.getRetentionYears());
        vo.setExpireAt(entity.getExpireAt());
        vo.setSecurityLevel(entity.getSecurityLevel());
        vo.setSourceType(entity.getSourceType());
        vo.setSourceId(entity.getSourceId());
        vo.setDefinitionCode(entity.getDefinitionCode());
        vo.setBusinessKey(entity.getBusinessKey());
        vo.setInitiator(entity.getInitiator());
        vo.setFinishedAt(entity.getFinishedAt());
        vo.setItemStatus(entity.getItemStatus());
        vo.setSummary(entity.getSummary());
        vo.setKeywords(entity.getKeywords());
        vo.setSnapshotFileId(entity.getSnapshotFileId() == null ? null : String.valueOf(entity.getSnapshotFileId()));
        vo.setSnapshotHash(entity.getSnapshotHash());
        vo.setArchivedAt(entity.getArchivedAt());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 文件引用实体转视图。
     *
     * @param entity 文件引用实体
     * @return 文件视图
     */
    private static ArchiveFileVO toFileVo(ArchiveFile entity) {
        ArchiveFileVO vo = new ArchiveFileVO();
        vo.setId(String.valueOf(entity.getId()));
        vo.setFileId(entity.getFileId() == null ? null : String.valueOf(entity.getFileId()));
        vo.setFileRole(entity.getFileRole());
        vo.setOriginalName(entity.getOriginalName());
        vo.setFileSize(entity.getFileSize());
        vo.setContentType(entity.getContentType());
        vo.setMd5(entity.getMd5());
        vo.setSort(entity.getSort());
        return vo;
    }
}
