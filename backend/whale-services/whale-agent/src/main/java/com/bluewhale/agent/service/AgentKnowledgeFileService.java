package com.bluewhale.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.agent.entity.AgentKnowledgeFile;
import com.bluewhale.agent.mapper.AgentKnowledgeFileMapper;
import com.bluewhale.agent.support.knowledge.KnowledgeConstants;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件知识库采集台账服务：台账读写、增量游标、分页与统计。
 *
 * <p>游标取台账中最大的 {@code source_update_time}，不额外建状态表；
 * 所有变更（含删除/停用置 {@code SKIPPED}）都会更新该字段，保证重启后可续传。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentKnowledgeFileService {

    /** 单页最大条数 */
    private static final int MAX_PAGE_SIZE = 200;

    private final AgentKnowledgeFileMapper mapper;

    /**
     * 按文件ID查询台账。
     *
     * @param fileId 文件ID
     * @return 台账；不存在返回 {@code null}
     */
    public AgentKnowledgeFile find(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<AgentKnowledgeFile>()
                .eq(AgentKnowledgeFile::getFileId, fileId)
                .last("LIMIT 1"));
    }

    /**
     * 保存台账（新增或更新）。
     *
     * @param record 台账
     */
    public void save(AgentKnowledgeFile record) {
        if (record.getId() == null) {
            mapper.insert(record);
        } else {
            mapper.updateById(record);
        }
    }

    /**
     * 读取增量游标（台账中最大的文件更新时间）。
     *
     * @return 游标；无记录返回 {@code null}
     */
    public LocalDateTime cursor() {
        AgentKnowledgeFile latest = mapper.selectOne(new LambdaQueryWrapper<AgentKnowledgeFile>()
                .isNotNull(AgentKnowledgeFile::getSourceUpdateTime)
                .orderByDesc(AgentKnowledgeFile::getSourceUpdateTime)
                .orderByDesc(AgentKnowledgeFile::getId)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getSourceUpdateTime();
    }

    /**
     * 查询到达重试时间的失败台账。
     *
     * @param now 当前时间
     * @param maxAttempts 重试次数上限
     * @param limit 单批上限
     * @return 待重试台账
     */
    public List<AgentKnowledgeFile> retryCandidates(LocalDateTime now, int maxAttempts, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<AgentKnowledgeFile>()
                .eq(AgentKnowledgeFile::getIndexStatus, KnowledgeConstants.STATUS_FAILED)
                .lt(AgentKnowledgeFile::getAttempts, maxAttempts)
                .and(wrapper -> wrapper.isNull(AgentKnowledgeFile::getNextRetryAt)
                        .or().le(AgentKnowledgeFile::getNextRetryAt, now))
                .orderByAsc(AgentKnowledgeFile::getNextRetryAt)
                .last("LIMIT " + Math.max(limit, 1)));
    }

    /**
     * 分页查询采集台账。
     *
     * @param keyword 文件名关键词（可空）
     * @param status 状态过滤（可空）
     * @param source 来源过滤（可空）
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    public PageResult<AgentKnowledgeFile> page(String keyword, String status, String source,
                                               long current, long size) {
        long pageCurrent = Math.max(current, 1L);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        LambdaQueryWrapper<AgentKnowledgeFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(keyword), AgentKnowledgeFile::getOriginalName, keyword);
        wrapper.eq(StringUtils.hasText(status), AgentKnowledgeFile::getIndexStatus, status);
        wrapper.eq(StringUtils.hasText(source), AgentKnowledgeFile::getSource, source);
        wrapper.orderByDesc(AgentKnowledgeFile::getUpdateTime).orderByDesc(AgentKnowledgeFile::getId);
        Page<AgentKnowledgeFile> page = mapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 统计指定状态台账数。
     *
     * @param status 状态
     * @return 数量
     */
    public long countByStatus(String status) {
        return mapper.selectCount(new LambdaQueryWrapper<AgentKnowledgeFile>()
                .eq(AgentKnowledgeFile::getIndexStatus, status));
    }

    /**
     * 统计全部知识块数。
     *
     * @return 知识块总数
     */
    public long sumChunks() {
        return mapper.sumChunkCount();
    }

    /**
     * 物理清空台账（全量重建前调用）。
     */
    public void deleteAllRows() {
        mapper.deleteAllRows();
    }

    /**
     * 事实索引全量重建后，将已索引台账重置为待处理以便下一轮同步自愈重建。
     *
     * @return 重置行数
     */
    public int resetIndexedToPending() {
        return mapper.resetIndexedToPending();
    }
}
