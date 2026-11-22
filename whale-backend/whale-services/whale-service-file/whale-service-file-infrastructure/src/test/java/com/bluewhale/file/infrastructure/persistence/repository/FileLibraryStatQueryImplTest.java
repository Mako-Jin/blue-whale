package com.bluewhale.file.infrastructure.persistence.repository;

import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.BucketCount;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.DayCount;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.LibraryStat;
import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery.RecycleStat;
import com.bluewhale.file.mapper.FileStatMapper;
import com.bluewhale.file.po.stat.FileBucketCountPo;
import com.bluewhale.file.po.stat.FileDayCountPo;
import com.bluewhale.file.po.stat.FileLibraryStatPo;
import com.bluewhale.file.po.stat.FileRecycleStatPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link FileLibraryStatQueryImpl} 藏书殿概览统计单元测试：覆盖参数缺失、窗口非法、
 * Mapper 返回空集合与脏值等分支，验证空值折 0 与口径转换。
 *
 * @author BlueWhale
 */
class FileLibraryStatQueryImplTest {

    private static final Long TENANT_ID = 7L;
    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 20);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 11, 21);
    private static final LocalDateTime FROM = DAY_1.atStartOfDay();
    private static final LocalDateTime TO = DAY_2.atStartOfDay();

    private final FileStatMapper statMapper = mock(FileStatMapper.class);

    private final FileLibraryStatQueryImpl query = new FileLibraryStatQueryImpl(statMapper);

    @Test
    @DisplayName("load：租户为空返回全 0，不访问 Mapper")
    void shouldReturnZeroWhenTenantMissingOnLoad() {
        LibraryStat stat = query.load(null);

        assertEquals(new LibraryStat(0L, 0L), stat);
        verifyNoInteractions(statMapper);
    }

    @Test
    @DisplayName("load：聚合行缺失返回全 0")
    void shouldReturnZeroWhenLibraryRowMissing() {
        when(statMapper.selectLibraryStat(TENANT_ID)).thenReturn(null);

        assertEquals(new LibraryStat(0L, 0L), query.load(TENANT_ID));
    }

    @Test
    @DisplayName("load：空值折 0，正常值透传")
    void shouldFoldNullsAndPassThroughOnLoad() {
        when(statMapper.selectLibraryStat(TENANT_ID)).thenReturn(new FileLibraryStatPo());
        assertEquals(new LibraryStat(0L, 0L), query.load(TENANT_ID));

        FileLibraryStatPo normal = new FileLibraryStatPo();
        normal.setRootFiles(12L);
        normal.setTotalBytes(2048L);
        when(statMapper.selectLibraryStat(TENANT_ID)).thenReturn(normal);
        assertEquals(new LibraryStat(12L, 2048L), query.load(TENANT_ID));
    }

    @Test
    @DisplayName("countCreated：参数缺失或窗口反向返回 0，合法窗口透传 Mapper")
    void shouldHandleCountCreated() {
        assertEquals(0L, query.countCreated(null, FROM, TO));
        assertEquals(0L, query.countCreated(TENANT_ID, null, TO));
        assertEquals(0L, query.countCreated(TENANT_ID, FROM, null));
        assertEquals(0L, query.countCreated(TENANT_ID, FROM, FROM));
        assertEquals(0L, query.countCreated(TENANT_ID, TO, FROM));

        when(statMapper.countCreated(TENANT_ID, FROM, TO)).thenReturn(9L);
        assertEquals(9L, query.countCreated(TENANT_ID, FROM, TO));
        verify(statMapper).countCreated(TENANT_ID, FROM, TO);
    }

    @Test
    @DisplayName("countCreatedByDay：参数缺失或日期窗反向返回空列表")
    void shouldReturnEmptyDaysForInvalidWindow() {
        assertTrue(query.countCreatedByDay(null, DAY_1, DAY_2).isEmpty());
        assertTrue(query.countCreatedByDay(TENANT_ID, null, DAY_2).isEmpty());
        assertTrue(query.countCreatedByDay(TENANT_ID, DAY_1, null).isEmpty());
        assertTrue(query.countCreatedByDay(TENANT_ID, DAY_1, DAY_1).isEmpty());
        assertTrue(query.countCreatedByDay(TENANT_ID, DAY_2, DAY_1).isEmpty());
        verifyNoInteractions(statMapper);
    }

    @Test
    @DisplayName("countCreatedByDay：Mapper 返回 null 或空列表时返回空列表")
    void shouldReturnEmptyDaysWhenMapperEmpty() {
        when(statMapper.countCreatedByDay(TENANT_ID, FROM, TO)).thenReturn(null);
        assertTrue(query.countCreatedByDay(TENANT_ID, DAY_1, DAY_2).isEmpty());

        when(statMapper.countCreatedByDay(TENANT_ID, FROM, TO)).thenReturn(List.of());
        assertTrue(query.countCreatedByDay(TENANT_ID, DAY_1, DAY_2).isEmpty());
    }

    @Test
    @DisplayName("countCreatedByDay：跳过空行与空日期，空文件数折 0")
    void shouldMapDaysAndSkipDirtyRows() {
        FileDayCountPo nullDay = new FileDayCountPo();
        nullDay.setFiles(3L);
        FileDayCountPo nullFiles = new FileDayCountPo();
        nullFiles.setDay(DAY_1);
        FileDayCountPo normal = new FileDayCountPo();
        normal.setDay(DAY_2);
        normal.setFiles(5L);
        when(statMapper.countCreatedByDay(TENANT_ID, FROM, TO))
                .thenReturn(Arrays.asList(null, nullDay, nullFiles, normal));

        List<DayCount> days = query.countCreatedByDay(TENANT_ID, DAY_1, DAY_2);

        assertEquals(List.of(new DayCount(DAY_1, 0L), new DayCount(DAY_2, 5L)), days);
    }

    @Test
    @DisplayName("recycle：参数缺失返回全 0")
    void shouldReturnZeroWhenRecycleArgsMissing() {
        assertEquals(new RecycleStat(0L, 0L, 0L), query.recycle(null, FROM));
        assertEquals(new RecycleStat(0L, 0L, 0L), query.recycle(TENANT_ID, null));
        verifyNoInteractions(statMapper);
    }

    @Test
    @DisplayName("recycle：聚合行缺失返回全 0，脏值折 0，正常值透传")
    void shouldMapRecycleStat() {
        when(statMapper.selectRecycleStat(TENANT_ID, FROM)).thenReturn(null);
        assertEquals(new RecycleStat(0L, 0L, 0L), query.recycle(TENANT_ID, FROM));

        when(statMapper.selectRecycleStat(TENANT_ID, FROM)).thenReturn(new FileRecycleStatPo());
        assertEquals(new RecycleStat(0L, 0L, 0L), query.recycle(TENANT_ID, FROM));

        FileRecycleStatPo normal = new FileRecycleStatPo();
        normal.setRecycleFiles(4L);
        normal.setRecycleBytes(1024L);
        normal.setStaleFiles(2L);
        when(statMapper.selectRecycleStat(TENANT_ID, FROM)).thenReturn(normal);
        assertEquals(new RecycleStat(4L, 1024L, 2L), query.recycle(TENANT_ID, FROM));
    }

    @Test
    @DisplayName("byBucket：租户为空返回空列表")
    void shouldReturnEmptyBucketsWhenTenantMissing() {
        assertTrue(query.byBucket(null).isEmpty());
        verifyNoInteractions(statMapper);
    }

    @Test
    @DisplayName("byBucket：Mapper 返回 null 或空列表时返回空列表")
    void shouldReturnEmptyBucketsWhenMapperEmpty() {
        when(statMapper.countByBucket(TENANT_ID)).thenReturn(null);
        assertTrue(query.byBucket(TENANT_ID).isEmpty());

        when(statMapper.countByBucket(TENANT_ID)).thenReturn(List.of());
        assertTrue(query.byBucket(TENANT_ID).isEmpty());
    }

    @Test
    @DisplayName("byBucket：跳过空行，空桶名归 UNKNOWN，空文件数折 0")
    void shouldMapBucketsAndSkipDirtyRows() {
        FileBucketCountPo nullBucket = new FileBucketCountPo();
        nullBucket.setFiles(2L);
        FileBucketCountPo blankBucket = new FileBucketCountPo();
        blankBucket.setBucket("  ");
        blankBucket.setFiles(3L);
        FileBucketCountPo nullFiles = new FileBucketCountPo();
        nullFiles.setBucket("docs");
        FileBucketCountPo normal = new FileBucketCountPo();
        normal.setBucket("media");
        normal.setFiles(9L);
        when(statMapper.countByBucket(TENANT_ID))
                .thenReturn(Arrays.asList(null, nullBucket, blankBucket, nullFiles, normal));

        List<BucketCount> buckets = query.byBucket(TENANT_ID);

        assertEquals(List.of(new BucketCount("UNKNOWN", 2L), new BucketCount("UNKNOWN", 3L),
                new BucketCount("docs", 0L), new BucketCount("media", 9L)), buckets);
    }
}
