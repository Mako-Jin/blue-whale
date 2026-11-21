package com.bluewhale.file.domain.file.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 藏书殿（文件与存储）统计端口：三维王城总控 T5.4 的数据来源。
 *
 * <p><b>为什么不用列表接口的 total。</b>文件列表按 {@code parent_id IS NULL} 只列版本组根文件，
 * 且带筛选条件时 total 会跟着变；总控要的是"这个租户一共存了多少、占了多少盘"，
 * 不能跟着某次筛选走。两个口径必须分开写，才能各自说清楚。</p>
 *
 * <p><b>「文件数」与「占用空间」故意不同口径。</b>文件数只数根文件（与人看到的列表条数一致），
 * 占用空间算**全部行**（含各版本行）——版本行不会再出现在列表里，但它实实在在占着存储。
 * 两个数都用"全部行"或都用"根行"，一个会虚高、一个会骗人：前者让人以为文件变多了，
 * 后者让人以为盘还很空。</p>
 *
 * <p><b>租户显式传参，不依赖线程上下文。</b>行级租户插件在上下文为空时直接放行
 * （见 {@code ConfiguredTenantLineHandler#ignoreTable}），总控走服务间调用，
 * 上下文不保证每段都在——一旦为空，统计会退化成全库聚合，把别家租户的文件数报给这一家，
 * 既不报错也查不出来。故 {@code tenantId} 为 {@code null} 时一律返回 0 / 空列表。</p>
 *
 * @author BlueWhale
 */
public interface FileLibraryStatQuery {

    /**
     * 库存总量：根文件数与全部行的字节合计
     *
     * @param tenantId 租户 ID，{@code null} 时返回全 0
     * @return 库存总量（未删除文件）
     */
    LibraryStat load(Long tenantId);

    /**
     * 时间窗内新增的根文件数
     *
     * <p>窗口由调用方给（今日 = {@code [今天 00:00, 明天 00:00)}），不在 SQL 里写
     * {@code CURDATE()}：应用与库时区不一致时，"今天上传的"会变成"昨天下午到今天晚上"。</p>
     *
     * @param tenantId 租户 ID，{@code null} 时返回 0
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 新增根文件数
     */
    long countCreated(Long tenantId, LocalDateTime from, LocalDateTime to);

    /**
     * 按天统计新增根文件数
     *
     * <p>一次查询同时供"今日新增"与"近 7 日趋势"使用：分两次查会在跨零点时对不上。</p>
     *
     * @param tenantId 租户 ID，{@code null} 时返回空列表
     * @param fromDay 起始日（含）
     * @param toDay 截止日（不含）
     * @return 每天一条；无数据时返回空列表
     */
    List<DayCount> countCreatedByDay(Long tenantId, LocalDate fromDay, LocalDate toDay);

    /**
     * 回收站统计
     *
     * @param tenantId 租户 ID，{@code null} 时返回全 0
     * @param staleBefore 超期判定线：删除时间早于它即为"该清未清"
     * @return 回收站统计
     */
    RecycleStat recycle(Long tenantId, LocalDateTime staleBefore);

    /**
     * 按桶统计根文件数
     *
     * @param tenantId 租户 ID，{@code null} 时返回空列表
     * @return 每个桶一条，按文件数倒序
     */
    List<BucketCount> byBucket(Long tenantId);

    /**
     * 库存总量
     *
     * @param files 根文件数（与列表条数同口径）
     * @param bytes 占用字节数（含版本行与回收站以外未删除行）
     * @author BlueWhale
     */
    record LibraryStat(long files, long bytes) {
    }

    /**
     * 回收站统计
     *
     * @param files 回收站根文件数
     * @param bytes 回收站占用字节数（含该文件的历史版本行）
     * @param staleFiles 已过保留期但仍未清理的根文件数
     * @author BlueWhale
     */
    record RecycleStat(long files, long bytes, long staleFiles) {
    }

    /**
     * 单日新增
     *
     * @param day 日期
     * @param files 新增根文件数
     * @author BlueWhale
     */
    record DayCount(LocalDate day, long files) {
    }

    /**
     * 单桶文件数
     *
     * @param bucket 桶名称
     * @param files 根文件数
     * @author BlueWhale
     */
    record BucketCount(String bucket, long files) {
    }
}
