package com.bluewhale.party.common.sequence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 业务编号流水 Mapper。
 *
 * <p>取号分两步，两步都必须是<b>当前读</b>而不是快照读：
 * 先 {@code SELECT ... FOR UPDATE} 拿到最新已发号并锁住该行，
 * 再 {@code UPDATE ... current_seq = current_seq + 1} 原子自增。
 * 若先普通 SELECT 再 UPDATE，两个并发事务会读到同一个旧值，
 * 各自 +1 后写回同一个新值，最终发出重复编号（REPEATABLE READ 下重试也读不到新值）。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface SequenceMapper extends BaseMapper<SequenceEntity> {

    /**
     * 锁定并读取当前已发号。
     *
     * <p>租户条件由行级隔离插件自动追加，键值本身含租户维度，两者一致。</p>
     *
     * @param seqKey 编号键
     * @param seqDate 周期标识（空串表示不重置）
     * @return 当前已发到的最末流水号；该键尚未取过号时返回 null
     */
    @Select("SELECT current_seq FROM pty_sequence WHERE seq_key = #{seqKey} AND seq_date = #{seqDate} FOR UPDATE")
    Long lockCurrentSeq(@Param("seqKey") String seqKey, @Param("seqDate") String seqDate);

    /**
     * 流水号自增一。
     *
     * @param seqKey 编号键
     * @param seqDate 周期标识
     * @return 影响行数
     */
    @Update("UPDATE pty_sequence SET current_seq = current_seq + 1 WHERE seq_key = #{seqKey} AND seq_date = #{seqDate}")
    int increment(@Param("seqKey") String seqKey, @Param("seqDate") String seqDate);
}
