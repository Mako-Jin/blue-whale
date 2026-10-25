package com.bluewhale.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.agent.entity.AgentKnowledgeFile;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

/**
 * 文件知识库采集台账 Mapper。
 *
 * @author BlueWhale
 */
public interface AgentKnowledgeFileMapper extends BaseMapper<AgentKnowledgeFile> {

    /**
     * 物理清空采集台账（全量重建时使用，绕过逻辑删除）。
     */
    @Delete("DELETE FROM agent_knowledge_file")
    void deleteAllRows();

    /**
     * 统计全部已索引知识块数。
     *
     * @return 知识块总数
     */
    @Select("SELECT COALESCE(SUM(chunk_count), 0) FROM agent_knowledge_file WHERE deleted = 0")
    long sumChunkCount();
}
