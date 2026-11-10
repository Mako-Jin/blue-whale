package com.bluewhale.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.agent.entity.AgentFact;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 结构化事实 Mapper（由 starter 的 {@code @MapperScan("com.bluewhale.**.mapper")} 统一注册）。
 *
 * @author BlueWhale
 */
public interface AgentFactMapper extends BaseMapper<AgentFact> {

    /**
     * 物理删除指定类型的事实（重建时先清空该类型，避免逻辑删除行无限累积）。
     *
     * @param factType 事实类型
     * @return 删除行数
     */
    @Delete("DELETE FROM agent_fact WHERE fact_type = #{factType}")
    int purgeByType(@Param("factType") String factType);
}
