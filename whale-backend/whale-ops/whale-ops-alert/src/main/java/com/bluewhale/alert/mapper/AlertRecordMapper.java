package com.bluewhale.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.alert.po.AlertRecordPo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 告警记录 Mapper
 *
 * <p>不用标注 {@code @Mapper}：由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描。</p>
 *
 * @author BlueWhale
 */
public interface AlertRecordMapper extends BaseMapper<AlertRecordPo> {

    /**
     * 物理删除超过保留期的已恢复 / 已静默记录。
     *
     * <p>告警记录是运行数据而非配置，逻辑删除无法释放容量，因此保留策略直接物理删除。
     * 语句带 {@code LIMIT}，由调用方按批执行，避免大事务与主从延迟。</p>
     *
     * @param threshold 时间阈值（trigger_time 早于该值）
     * @param limit     单批最大行数
     * @return 删除行数
     */
    @Delete("DELETE FROM alert_record WHERE status IN ('RECOVERED', 'SILENCED')"
            + " AND trigger_time < #{threshold} LIMIT #{limit}")
    int purgeBefore(@Param("threshold") LocalDateTime threshold, @Param("limit") int limit);
}
