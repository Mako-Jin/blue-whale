package com.bluewhale.agent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.agent.entity.AgentKnowledgeDoc;
import com.bluewhale.agent.mapper.AgentKnowledgeDocMapper;
import com.bluewhale.agent.support.knowledge.KnowledgeConstants;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档知识库采集台账服务：台账读写、增量游标、分页与统计。
 *
 * <p>游标取台账中最大的 {@code source_update_time}；所有变更（含墓碑置
 * {@code SKIPPED}）都会更新该字段，保证重启后可续传。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentKnowledgeDocService {

    /** 单页最大条数 */
    private static final int MAX_PAGE_SIZE = 200;

    private final AgentKnowledgeDocMapper mapper;

    /**
     * 按页面ID查询台账。
     *
     * @param pageId 页面ID
     * @return 台账；不存在返回 {@code null}
     */
    public AgentKnowledgeDoc find(Long pageId) {
        if (pageId == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<AgentKnowledgeDoc>()
                .eq(AgentKnowledgeDoc::getPageId, pageId)
                .last("LIMIT 1"));
    }

    /**
     * 保存台账（新增或更新）。
     *
     * @param record 台账
     */
    public void save(AgentKnowledgeDoc record) {
        if (record.getId() == null) {
            mapper.insert(record);
        } else {
            mapper.updateById(record);
        }
    }

    /**
     * 读取增量游标（台账中最大的页面更新时间）。
     *
     * @return 游标；无记录返回 {@code null}
     */
    public LocalDateTime cursor() {
        AgentKnowledgeDoc latest = mapper.selectOne(new LambdaQueryWrapper<AgentKnowledgeDoc>()
                .isNotNull(AgentKnowledgeDoc::getSourceUpdateTime)
                .orderByDesc(AgentKnowledgeDoc::getSourceUpdateTime)
                .orderByDesc(AgentKnowledgeDoc::getId)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getSourceUpdateTime();
    }

    /**
     * 查询到达重试时间的失败台账。
     *
     * @param now         当前时间
     * @param maxAttempts 重试次数上限
     * @param limit       单批上限
     * @return 待重试台账
     */
    public List<AgentKnowledgeDoc> retryCandidates(LocalDateTime now, int maxAttempts, int limit) {
        return mapper.selectList(new LambdaQueryWrapper<AgentKnowledgeDoc>()
                .eq(AgentKnowledgeDoc::getIndexStatus, KnowledgeConstants.STATUS_FAILED)
                .lt(AgentKnowledgeDoc::getAttempts, maxAttempts)
                .and(wrapper -> wrapper.isNull(AgentKnowledgeDoc::getNextRetryAt)
                        .or().le(AgentKnowledgeDoc::getNextRetryAt, now))
                .orderByAsc(AgentKnowledgeDoc::getNextRetryAt)
                .last("LIMIT " + Math.max(limit, 1)));
    }

    /**
     * 分页查询采集台账。
     *
     * @param keyword 标题关键词（可空）
     * @param status  状态过滤（可空）
     * @param current 页码
     * @param size    每页条数
     * @return 分页结果
     */
    public PageResult<AgentKnowledgeDoc> page(String keyword, String status, long current, long size) {
        long pageCurrent = Math.max(current, 1L);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        LambdaQueryWrapper<AgentKnowledgeDoc> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(keyword), AgentKnowledgeDoc::getTitle, keyword);
        wrapper.eq(StringUtils.hasText(status), AgentKnowledgeDoc::getIndexStatus, status);
        wrapper.orderByDesc(AgentKnowledgeDoc::getUpdateTime).orderByDesc(AgentKnowledgeDoc::getId);
        Page<AgentKnowledgeDoc> page = mapper.selectPage(new Page<>(pageCurrent, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 统计指定状态台账数。
     *
     * @param status 状态
     * @return 数量
     */
    public long countByStatus(String status) {
        return mapper.selectCount(new LambdaQueryWrapper<AgentKnowledgeDoc>()
                .eq(AgentKnowledgeDoc::getIndexStatus, status));
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
     * 索引全量重建后，将已索引台账重置为待处理以便下一轮同步自愈重建。
     *
     * @return 重置行数
     */
    public int resetIndexedToPending() {
        return mapper.resetIndexedToPending();
    }
}
