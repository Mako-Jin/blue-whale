package com.bluewhale.file.infrastructure.persistence.repository;

import com.bluewhale.file.domain.file.repository.FileLibraryStatQuery;
import com.bluewhale.file.mapper.FileStatMapper;
import com.bluewhale.file.po.stat.FileBucketCountPo;
import com.bluewhale.file.po.stat.FileDayCountPo;
import com.bluewhale.file.po.stat.FileLibraryStatPo;
import com.bluewhale.file.po.stat.FileRecycleStatPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link FileLibraryStatQuery} 的 MyBatis 实现：把 SQL 的空值、脏值统一折成 0。
 *
 * <p><b>为什么不把口径写进 provider。</b>"根文件才计数、版本行也占盘"这件事
 * 以后配额报表、容量告警都要用；口径散到调用方，就会出现"总控说 1.2TB、
 * 配额页说 0.9TB"这种没法解释的分歧。</p>
 *
 * <p>窗口不合法（{@code from >= to}、参数缺失）一律返回 0 / 空列表，
 * 不让 {@code BETWEEN} 反向区间静默返回空结果——那样看起来像"这天没新增"，
 * 实际是参数写错了。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class FileLibraryStatQueryImpl implements FileLibraryStatQuery {

    /** 统计实现 */
    private final FileStatMapper statMapper;

    @Override
    public LibraryStat load(Long tenantId) {
        if (tenantId == null) {
            return new LibraryStat(0L, 0L);
        }
        FileLibraryStatPo row = statMapper.selectLibraryStat(tenantId);
        if (row == null) {
            return new LibraryStat(0L, 0L);
        }
        return new LibraryStat(zeroIfNull(row.getRootFiles()), zeroIfNull(row.getTotalBytes()));
    }

    @Override
    public long countCreated(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return 0L;
        }
        return statMapper.countCreated(tenantId, from, to);
    }

    @Override
    public List<DayCount> countCreatedByDay(Long tenantId, LocalDate fromDay, LocalDate toDay) {
        if (tenantId == null || fromDay == null || toDay == null || !fromDay.isBefore(toDay)) {
            return List.of();
        }
        List<FileDayCountPo> rows = statMapper.countCreatedByDay(tenantId,
                fromDay.atStartOfDay(), toDay.atStartOfDay());
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<DayCount> result = new ArrayList<>(rows.size());
        for (FileDayCountPo row : rows) {
            if (row == null || row.getDay() == null) {
                /* 日期为空的聚合行说明 SQL 里 DATE() 落到了非法值，直接跳过：
                   塞进趋势图会把空标签画在最左边，看起来像"很久以前有一天没人上传"。 */
                continue;
            }
            result.add(new DayCount(row.getDay(), zeroIfNull(row.getFiles())));
        }
        return List.copyOf(result);
    }

    @Override
    public RecycleStat recycle(Long tenantId, LocalDateTime staleBefore) {
        if (tenantId == null || staleBefore == null) {
            return new RecycleStat(0L, 0L, 0L);
        }
        FileRecycleStatPo row = statMapper.selectRecycleStat(tenantId, staleBefore);
        if (row == null) {
            return new RecycleStat(0L, 0L, 0L);
        }
        return new RecycleStat(zeroIfNull(row.getRecycleFiles()),
                zeroIfNull(row.getRecycleBytes()), zeroIfNull(row.getStaleFiles()));
    }

    @Override
    public List<BucketCount> byBucket(Long tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        List<FileBucketCountPo> rows = statMapper.countByBucket(tenantId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<BucketCount> result = new ArrayList<>(rows.size());
        for (FileBucketCountPo row : rows) {
            if (row == null) {
                continue;
            }
            /* 桶名可能为空（历史数据），归成 UNKNOWN 而不是丢掉：
               丢掉会让"各桶合计"与"文件总数"对不上，而这类差额最难解释。 */
            String bucket = row.getBucket() == null || row.getBucket().isBlank()
                    ? "UNKNOWN" : row.getBucket();
            result.add(new BucketCount(bucket, zeroIfNull(row.getFiles())));
        }
        return List.copyOf(result);
    }

    /**
     * 判定时间窗是否可用
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻
     * @param to 截止时刻
     * @return {@code true} 表示参数不全或区间为空
     */
    private boolean invalidWindow(Long tenantId, LocalDateTime from, LocalDateTime to) {
        return tenantId == null || from == null || to == null || !from.isBefore(to);
    }

    /**
     * 空值折 0
     *
     * @param value 聚合值
     * @return 原值；{@code null} 时 0
     */
    private long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }
}
