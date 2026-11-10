package com.bluewhale.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.agent.entity.AgentKnowledgeFile;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 事实索引全量重建后调用：将已索引台账重置为待处理，
     * 交由下一轮文件知识同步重新采集（全量重建会清空全部索引块）。
     *
     * @return 受影响行数
     */
    @Update("UPDATE agent_knowledge_file SET index_status = 'PENDING' WHERE deleted = 0 AND index_status = 'INDEXED'")
    int resetIndexedToPending();
}
