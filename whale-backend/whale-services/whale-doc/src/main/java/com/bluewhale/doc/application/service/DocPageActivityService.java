package com.bluewhale.doc.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageActivity;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageActivityMapper;
import com.bluewhale.doc.interfaces.vo.DocActivityVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 页面动态应用服务。
 *
 * <p>动态仅记录动作、操作人与摘要，不记录正文；写入失败只告警，不影响主业务事务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPageActivityService {

    /** 摘要最大长度 */
    private static final int MAX_DETAIL_LENGTH = 500;

    /** 单页最大分页大小 */
    private static final long MAX_PAGE_SIZE = 100L;

    private final DocPageActivityMapper activityMapper;

    /**
     * 记录页面动态（失败降级）。
     *
     * @param pageId  页面ID
     * @param action  动作
     * @param actorId 操作人ID
     * @param detail  动作摘要
     */
    public void record(Long pageId, String action, Long actorId, String detail) {
        if (pageId == null || action == null) {
            return;
        }
        try {
            DocPageActivity activity = new DocPageActivity();
            activity.setPageId(pageId);
            activity.setAction(action);
            activity.setActorId(actorId);
            activity.setDetail(truncate(detail));
            activityMapper.insert(activity);
        } catch (RuntimeException ex) {
            log.warn("记录文档动态失败: pageId={}, action={}, reason={}", pageId, action, ex.getMessage());
        }
    }

    /**
     * 分页查询页面动态（按时间倒序）。
     *
     * @param pageId  页面ID
     * @param current 当前页
     * @param size    每页大小
     * @return 动态分页
     */
    public PageResult<DocActivityVO> page(Long pageId, long current, long size) {
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Page<DocPageActivity> activityPage = activityMapper.selectPage(
                new Page<>(Math.max(current, 1L), pageSize),
                Wrappers.<DocPageActivity>lambdaQuery()
                        .eq(DocPageActivity::getPageId, pageId)
                        .orderByDesc(DocPageActivity::getCreateTime)
                        .orderByDesc(DocPageActivity::getId));
        List<DocActivityVO> records = activityPage.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, activityPage.getTotal(), activityPage.getCurrent(), activityPage.getSize());
    }

    /**
     * 实体转动态视图。
     *
     * @param activity 动态
     * @return 动态视图
     */
    private DocActivityVO toVO(DocPageActivity activity) {
        DocActivityVO vo = new DocActivityVO();
        vo.setId(activity.getId());
        vo.setAction(activity.getAction());
        vo.setActorId(activity.getActorId());
        vo.setDetail(activity.getDetail());
        vo.setCreateTime(activity.getCreateTime());
        return vo;
    }

    /**
     * 截断摘要到最大长度。
     *
     * @param detail 摘要
     * @return 截断后的摘要
     */
    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        String trimmed = detail.trim();
        return trimmed.length() <= MAX_DETAIL_LENGTH ? trimmed : trimmed.substring(0, MAX_DETAIL_LENGTH);
    }
}
