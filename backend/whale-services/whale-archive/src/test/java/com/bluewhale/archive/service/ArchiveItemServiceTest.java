package com.bluewhale.archive.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.vo.ArchiveItemVO;
import com.bluewhale.archive.vo.ArchiveStatsVO;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 档案条目服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ArchiveItemServiceTest {

    /**
     * 单元测试无 MyBatis 上下文：手动初始化表信息，使 Lambda 条件可生成 SQL 片段用于断言。
     */
    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ArchiveItem.class);
    }

    /** 条目ID */
    private static final long ITEM_ID = 100L;

    /** 门类ID */
    private static final long CATEGORY_ID = 9L;

    /** 文件引用ID */
    private static final long FILE_REF_ID = 501L;

    /** 文件服务文件ID */
    private static final long FILE_ID = 9001L;

    /** 归档年度 */
    private static final int YEAR = 2026;

    /** 超限页大小 */
    private static final long HUGE_PAGE_SIZE = 5000L;

    /** 单页上限 */
    private static final long MAX_PAGE_SIZE = 200L;

    @Mock
    private ArchiveItemMapper itemMapper;

    @Mock
    private ArchiveFileMapper fileMapper;

    @Mock
    private ArchiveCategoryMapper categoryMapper;

    @Mock
    private ArchiveNumberService numberService;

    @Mock
    private WorkflowArchiveClient workflowClient;

    @Mock
    private FileArchiveClient fileClient;

    @InjectMocks
    private ArchiveItemService itemService;

    /**
     * 构造待整理条目。
     *
     * @return 条目实体
     */
    private static ArchiveItem pendingItem() {
        ArchiveItem item = new ArchiveItem();
        item.setId(ITEM_ID);
        item.setTitle("采购合同归档");
        item.setCategoryId(CATEGORY_ID);
        item.setItemStatus(ArchiveConstants.ITEM_STATUS_PENDING);
        item.setYear(YEAR);
        item.setFinishedAt(LocalDateTime.of(YEAR, 3, 1, 10, 0));
        return item;
    }

    /**
     * 构造启用门类。
     *
     * @return 门类实体
     */
    private static ArchiveCategory enabledCategory() {
        ArchiveCategory category = new ArchiveCategory();
        category.setId(CATEGORY_ID);
        category.setCode("GL");
        category.setName("管理类");
        category.setParentId(ArchiveConstants.ROOT_PARENT_ID);
        category.setStatus(ArchiveConstants.STATUS_ENABLED);
        return category;
    }

    @Test
    @DisplayName("分页查询：size 超限收敛到 200 并补全门类名称")
    void pageClampsSizeAndFillsCategoryName() {
        ArchiveItemQueryRequest request = new ArchiveItemQueryRequest();
        request.setSize(HUGE_PAGE_SIZE);
        request.setKeyword(" 合同 ");
        ArchiveItem item = pendingItem();
        Page<ArchiveItem> page = new Page<>(1, MAX_PAGE_SIZE);
        page.setRecords(List.of(item));
        page.setTotal(1L);
        when(itemMapper.selectPage(any(), any())).thenReturn(page);
        when(categoryMapper.selectBatchIds(any())).thenReturn(List.of(enabledCategory()));

        PageResult<ArchiveItemVO> result = itemService.page(request, false);

        ArgumentCaptor<Page<ArchiveItem>> captor = ArgumentCaptor.forClass(Page.class);
        verify(itemMapper).selectPage(captor.capture(), nullable(Wrapper.class));
        assertEquals(MAX_PAGE_SIZE, captor.getValue().getSize());
        assertEquals(1L, result.getTotal());
        assertEquals("管理类", result.getRecords().get(0).getCategoryName());
    }

    @Test
    @DisplayName("整理：已归档条目拒绝修改")
    void editRejectsArchivedItem() {
        ArchiveItem item = pendingItem();
        item.setItemStatus(ArchiveConstants.ITEM_STATUS_ARCHIVED);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
        ArchiveItemEditRequest request = new ArchiveItemEditRequest();
        request.setCategoryId(CATEGORY_ID);

        assertThrows(BizException.class, () -> itemService.edit(ITEM_ID, request));
        verify(itemMapper, never()).updateById(any(ArchiveItem.class));
    }

    @Test
    @DisplayName("整理：待整理条目更新门类路径与保管期限")
    void editUpdatesPendingItem() {
        ArchiveItem item = pendingItem();
        ArchiveItem updated = pendingItem();
        updated.setCategoryPath("GL");
        updated.setRetentionYears(10);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item, updated);
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(enabledCategory());
        ArchiveItemEditRequest request = new ArchiveItemEditRequest();
        request.setCategoryId(CATEGORY_ID);
        request.setRetentionYears(10);
        request.setTitle("合同档案");

        ArchiveItemVO vo = itemService.edit(ITEM_ID, request);

        assertEquals("GL", vo.getCategoryPath());
        assertEquals(10, vo.getRetentionYears());
        verify(itemMapper).updateById(any(ArchiveItem.class));
    }

    @Test
    @DisplayName("确认归档：分配档号并置为已归档")
    void confirmAllocatesArchiveNo() {
        ArchiveItem item = pendingItem();
        ArchiveItem archived = pendingItem();
        archived.setItemStatus(ArchiveConstants.ITEM_STATUS_ARCHIVED);
        archived.setArchiveNo("BW-GL-2026-000001");
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item, archived);
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(enabledCategory());
        when(numberService.next("GL", YEAR)).thenReturn("BW-GL-2026-000001");
        when(itemMapper.update(any(), any())).thenReturn(1);

        ArchiveItemVO vo = itemService.confirm(ITEM_ID, 7L);

        assertEquals("BW-GL-2026-000001", vo.getArchiveNo());
        assertEquals(ArchiveConstants.ITEM_STATUS_ARCHIVED, vo.getItemStatus());
    }

    @Test
    @DisplayName("确认归档：锁定条目下全部文件（原件不可删除）")
    void confirmLocksItemFiles() {
        ArchiveItem item = pendingItem();
        ArchiveItem archived = pendingItem();
        archived.setItemStatus(ArchiveConstants.ITEM_STATUS_ARCHIVED);
        archived.setArchiveNo("BW-GL-2026-000001");
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item, archived);
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(enabledCategory());
        when(numberService.next("GL", YEAR)).thenReturn("BW-GL-2026-000001");
        when(itemMapper.update(any(), any())).thenReturn(1);
        when(fileMapper.selectList(any())).thenReturn(List.of(fileRef()));

        itemService.confirm(ITEM_ID, 7L);

        verify(fileClient).lock(FILE_ID);
    }

    @Test
    @DisplayName("确认归档：文件锁定失败时抛出异常，事务回滚等待重试")
    void confirmFailsWhenLockFails() {
        ArchiveItem item = pendingItem();
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(enabledCategory());
        when(numberService.next("GL", YEAR)).thenReturn("BW-GL-2026-000001");
        when(itemMapper.update(any(), any())).thenReturn(1);
        when(fileMapper.selectList(any())).thenReturn(List.of(fileRef()));
        doThrow(new IllegalStateException("文件服务不可用")).when(fileClient).lock(FILE_ID);

        assertThrows(IllegalStateException.class, () -> itemService.confirm(ITEM_ID, 7L));
    }

    @Test
    @DisplayName("确认归档：已归档条目重复确认幂等且不再取号")
    void confirmExistingArchivedIsIdempotent() {
        ArchiveItem item = pendingItem();
        item.setItemStatus(ArchiveConstants.ITEM_STATUS_ARCHIVED);
        item.setArchiveNo("BW-GL-2026-000002");
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(categoryMapper.selectById(CATEGORY_ID)).thenReturn(enabledCategory());

        ArchiveItemVO vo = itemService.confirm(ITEM_ID, 7L);

        assertEquals("BW-GL-2026-000002", vo.getArchiveNo());
        verify(numberService, never()).next(anyString(), any(Integer.class));
    }

    @Test
    @DisplayName("销毁：待整理条目拒绝销毁")
    void destroyRejectsPendingItem() {
        when(itemMapper.selectById(ITEM_ID)).thenReturn(pendingItem());

        assertThrows(BizException.class, () -> itemService.destroy(ITEM_ID));
        verify(itemMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("下载：文件不属于该条目时拒绝")
    void downloadRejectsForeignFile() {
        when(itemMapper.selectById(ITEM_ID)).thenReturn(pendingItem());
        ArchiveFile file = new ArchiveFile();
        file.setId(FILE_REF_ID);
        file.setItemId(999L);
        when(fileMapper.selectById(FILE_REF_ID)).thenReturn(file);

        assertThrows(BizException.class, () -> itemService.download(ITEM_ID, FILE_REF_ID, false));
        verify(fileClient, never()).content(anyLong());
    }

    @Test
    @DisplayName("详情：秘密级条目无秘密级权限时按不存在处理，有权限时可见")
    void detailShouldHideSecretItemWithoutPermission() {
        ArchiveItem item = pendingItem();
        item.setSecurityLevel(ArchiveConstants.SECURITY_SECRET);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);

        BizException ex = assertThrows(BizException.class, () -> itemService.detail(ITEM_ID, false));
        assertEquals(404, ex.getCode());

        assertNotNull(itemService.detail(ITEM_ID, true).getItem());
    }

    @Test
    @DisplayName("下载：秘密级条目无秘密级权限时不调用文件服务")
    void downloadShouldBlockSecretItemWithoutPermission() {
        ArchiveItem item = pendingItem();
        item.setSecurityLevel(ArchiveConstants.SECURITY_SECRET);
        when(itemMapper.selectById(ITEM_ID)).thenReturn(item);
        when(fileMapper.selectById(FILE_REF_ID)).thenReturn(fileRef());

        assertThrows(BizException.class, () -> itemService.download(ITEM_ID, FILE_REF_ID, false));
        verify(fileClient, never()).content(anyLong());
    }

    @Test
    @DisplayName("分页：无秘密级权限时 SQL 追加 security_level 过滤，有权限时不追加")
    void pageShouldScopeSecretItemsByPermission() {
        ArchiveItemQueryRequest request = new ArchiveItemQueryRequest();
        when(itemMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10));

        ArgumentCaptor<Wrapper<ArchiveItem>> captor = ArgumentCaptor.forClass(Wrapper.class);
        itemService.page(request, false);
        verify(itemMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("security_level <"));

        itemService.page(request, true);
        verify(itemMapper, times(2)).selectPage(any(), captor.capture());
        assertFalse(captor.getValue().getSqlSegment().contains("security_level <"));
    }

    @Test
    @DisplayName("统计：汇总状态数量与年度/密级/门类分布")
    void statsAggregates() {
        when(itemMapper.selectCount(nullable(Wrapper.class))).thenReturn(10L, 3L, 5L, 2L);
        when(itemMapper.selectMaps(any()))
                .thenReturn(List.of(row(YEAR, 6L), row(null, 4L)))
                .thenReturn(List.of(row(ArchiveConstants.SECURITY_INTERNAL, 7L)))
                .thenReturn(List.of(row(CATEGORY_ID, 8L)));

        ArchiveStatsVO stats = itemService.stats(false);

        assertEquals(10L, stats.getTotal());
        assertEquals(3L, stats.getPending());
        assertEquals(5L, stats.getArchived());
        assertEquals(2L, stats.getDestroyed());
        assertEquals(6L, stats.getByYear().get(String.valueOf(YEAR)));
        assertEquals(4L, stats.getByYear().get("未设置"));
        assertEquals(7L, stats.getBySecurity().get(String.valueOf(ArchiveConstants.SECURITY_INTERNAL)));
        assertEquals(8L, stats.getByCategory().get(String.valueOf(CATEGORY_ID)));
        assertNotNull(stats.getByYear());
    }

    /**
     * 构造条目下的文件引用。
     *
     * @return 文件引用
     */
    private static ArchiveFile fileRef() {
        ArchiveFile file = new ArchiveFile();
        file.setId(FILE_REF_ID);
        file.setItemId(ITEM_ID);
        file.setFileId(FILE_ID);
        file.setFileRole(ArchiveConstants.FILE_ROLE_ORIGINAL);
        return file;
    }

    /**
     * 构造分组统计行。
     *
     * @param key 分组键
     * @param count 数量
     * @return 统计行
     */
    private static Map<String, Object> row(Object key, long count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("k", key);
        row.put("c", count);
        return row;
    }
}
