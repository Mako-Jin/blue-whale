package com.bluewhale.agent.service;

import com.bluewhale.agent.dto.IndexMetaVO;
import com.bluewhale.agent.entity.AgentIndexMeta;
import com.bluewhale.agent.mapper.AgentIndexMetaMapper;
import com.bluewhale.agent.support.index.IndexStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 索引元数据服务：把最新一次构建结果写入 {@code agent_index_meta}（单行，id=1）。
 *
 * <p>数据库不可用时不阻断索引构建，仅记录告警；状态接口回落到进程内实时统计。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentIndexMetaService {

    /** 单行表固定主键 */
    private static final long META_ID = 1L;

    private final AgentIndexMetaMapper mapper;

    /**
     * 读取元数据（数据库不可用时返回 {@code null}）。
     *
     * @return 索引元数据
     */
    public AgentIndexMeta get() {
        try {
            return mapper.selectById(META_ID);
        } catch (RuntimeException e) {
            log.warn("读取索引元数据失败，回落进程内统计: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 读取元数据快照（数据库不可用时返回 {@code null}）。
     *
     * <p>供状态接口装配使用，避免 controller 直接依赖 {@link AgentIndexMeta} 实体（T-18）。</p>
     *
     * @return 索引元数据快照
     */
    public IndexMetaVO snapshot() {
        AgentIndexMeta meta = get();
        if (meta == null) {
            return null;
        }
        return IndexMetaVO.builder()
                .indexVersion(meta.getIndexVersion())
                .lastBuildAt(meta.getLastBuildAt())
                .build();
    }

    /**
     * 保存构建结果（覆盖式）。
     *
     * @param stats 索引统计
     * @param indexVersion 索引版本
     */
    public void save(IndexStats stats, String indexVersion) {
        try {
            AgentIndexMeta meta = mapper.selectById(META_ID);
            boolean insert = meta == null;
            if (insert) {
                meta = new AgentIndexMeta();
                meta.setId(META_ID);
            }
            meta.setIndexVersion(indexVersion);
            meta.setFileCount(stats.getFileCount());
            meta.setChunkCount(stats.getChunkCount());
            meta.setLastBuildAt(stats.getLastBuildAt());
            meta.setStatus(stats.getStatus());
            meta.setDimension(stats.getDimension());
            meta.setBuildMillis(stats.getBuildMillis());
            if (insert) {
                mapper.insert(meta);
            } else {
                mapper.updateById(meta);
            }
        } catch (RuntimeException e) {
            log.warn("写入索引元数据失败（不影响索引可用性）: {}", e.getMessage());
        }
    }
}
