package com.bluewhale.archive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.archive.entity.ArchiveSequence;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 档号流水 Mapper。
 *
 * <p>使用 MySQL 单语句原子自增：首次插入时以 {@code LAST_INSERT_ID(1)} 把流水置为 1，
 * 已存在时 {@code ON DUPLICATE KEY UPDATE current_value = LAST_INSERT_ID(current_value + 1)}；
 * 随后在同一连接上读取 {@code LAST_INSERT_ID()} 得到本次分配值。该写法依赖唯一键
 * {@code uk_category_year} 行锁，天然支持并发。</p>
 *
 * @author BlueWhale
 */
public interface ArchiveSequenceMapper extends BaseMapper<ArchiveSequence> {

    /**
     * 按「门类编码 + 年度」原子分配一个流水号，并把本次分配值写入连接级 {@code LAST_INSERT_ID}。
     *
     * @param id 主键ID（雪花算法，仅在首次插入时使用）
     * @param categoryCode 门类编码
     * @param year 归档年度
     * @return 受影响行数（插入 1 / 更新 2，仅用于调试）
     */
    @Insert("INSERT INTO archive_sequence (id, category_code, year, current_value, create_time, update_time, deleted) "
            + "VALUES (#{id}, #{categoryCode}, #{year}, LAST_INSERT_ID(1), NOW(), NOW(), 0) "
            + "ON DUPLICATE KEY UPDATE current_value = LAST_INSERT_ID(current_value + 1), update_time = NOW()")
    int allocate(@Param("id") Long id, @Param("categoryCode") String categoryCode, @Param("year") int year);

    /**
     * 读取同一连接上最近一次 {@link #allocate} 分配的流水号。
     *
     * @return 本次分配的流水号
     */
    @Select("SELECT LAST_INSERT_ID()")
    long lastAllocatedValue();
}
