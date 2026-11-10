package com.bluewhale.mall.common.sequence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 业务编号流水 Mapper。
 *
 * <p>取号必须是<b>当前读</b>：先 {@code SELECT ... FOR UPDATE} 拿到最新已发号并锁行，
 * 再原子自增。若先普通 SELECT 再 UPDATE，并发事务会读到同一旧值、各自 +1 后写回同一新值，
 * 最终发出重复订单号（REPEATABLE READ 下重试也读不到新值）。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface MallSequenceMapper extends BaseMapper<MallSequenceEntity> {

    /**
     * 锁定并读取当前已发号。
     *
     * @param seqKey 编号键
     * @param seqDate 周期标识
     * @return 当前已发到的最末流水号；该键尚未取过号时返回 null
     */
    @Select("SELECT current_seq FROM mall_sequence WHERE seq_key = #{seqKey} AND seq_date = #{seqDate} FOR UPDATE")
    Long lockCurrentSeq(@Param("seqKey") String seqKey, @Param("seqDate") String seqDate);

    /**
     * 流水号自增一。
     *
     * @param seqKey 编号键
     * @param seqDate 周期标识
     * @return 影响行数
     */
    @Update("UPDATE mall_sequence SET current_seq = current_seq + 1 WHERE seq_key = #{seqKey} AND seq_date = #{seqDate}")
    int increment(@Param("seqKey") String seqKey, @Param("seqDate") String seqDate);
}
