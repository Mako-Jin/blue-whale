package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.dto.AnnouncementPublishRequest;
import com.bluewhale.message.dto.AnnouncementQueryRequest;
import com.bluewhale.message.po.MessageAnnouncementPo;
import com.bluewhale.message.po.MessageRecordPo;
import com.bluewhale.message.enums.AnnouncementStatus;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.enums.MessageStatus;
import com.bluewhale.message.mapper.MessageAnnouncementMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.service.MessageAnnouncementService;
import com.bluewhale.message.vo.AnnouncementVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统公告服务实现。
 *
 * <p>发布 = 落一条逻辑消息记录（channels=INAPP、status=SENT）+ 一条公告行；
 * 展开交给 {@code AnnouncementExpander} 在收件箱读取时按需进行。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageAnnouncementServiceImpl implements MessageAnnouncementService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 缺省业务类型：前端可据此区分公告与定向站内信 */
    private static final String DEFAULT_BIZ_TYPE = "SYSTEM_ANNOUNCE";

    private final MessageAnnouncementMapper announcementMapper;

    private final MessageRecordMapper recordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementVO publish(AnnouncementPublishRequest request, Long publisherId) {
        MessageRecordPo record = new MessageRecordPo();
        record.setBizType(StringUtils.hasText(request.getBizType())
                ? request.getBizType().trim() : DEFAULT_BIZ_TYPE);
        record.setTitle(request.getTitle().trim());
        record.setContent(request.getContent().trim());
        record.setChannels(MessageChannel.INAPP.name());
        record.setStatus(MessageStatus.SENT.name());
        recordMapper.insert(record);

        MessageAnnouncementPo announcement = new MessageAnnouncementPo();
        announcement.setRecordId(record.getId());
        announcement.setStatus(AnnouncementStatus.PUBLISHED.name());
        announcement.setPublisherId(publisherId);
        announcement.setPublishTime(LocalDateTime.now());
        announcementMapper.insert(announcement);
        log.info("系统公告已发布: announcementId={}, recordId={}, publisher={}",
                announcement.getId(), record.getId(), publisherId);
        return toVo(announcement, record);
    }

    @Override
    public PageResult<AnnouncementVO> page(AnnouncementQueryRequest request) {
        LambdaQueryWrapper<MessageAnnouncementPo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getStatus())) {
            AnnouncementStatus status = parseStatus(request.getStatus());
            wrapper.eq(MessageAnnouncementPo::getStatus, status.name());
        }
        wrapper.orderByDesc(MessageAnnouncementPo::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<MessageAnnouncementPo> page = announcementMapper.selectPage(
                new Page<>(request.getCurrent(), size), wrapper);
        List<MessageAnnouncementPo> announcements = page.getRecords();
        if (announcements.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), page.getCurrent(), page.getSize());
        }
        List<Long> recordIds = announcements.stream()
                .map(MessageAnnouncementPo::getRecordId)
                .distinct()
                .toList();
        Map<Long, MessageRecordPo> records = recordMapper.selectBatchIds(recordIds).stream()
                .collect(Collectors.toMap(MessageRecordPo::getId, record -> record, (first, second) -> first));
        List<AnnouncementVO> rows = announcements.stream()
                .map(announcement -> toVo(announcement, records.get(announcement.getRecordId())))
                .toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        MessageAnnouncementPo announcement = requireAnnouncement(id);
        announcement.setStatus(AnnouncementStatus.REVOKED.name());
        announcementMapper.updateById(announcement);
        log.info("系统公告已撤回: announcementId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireAnnouncement(id);
        announcementMapper.deleteById(id);
        log.info("系统公告已删除: announcementId={}", id);
    }

    /**
     * 按 ID 查询未删除公告。
     *
     * @param id 公告ID
     * @return 公告实体
     */
    private MessageAnnouncementPo requireAnnouncement(Long id) {
        MessageAnnouncementPo announcement = id == null ? null : announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "message.announcement.not-found",
                    "系统公告不存在: " + id, id);
        }
        return announcement;
    }

    /**
     * 解析公告状态文本（大小写不敏感）。
     *
     * @param raw 原始文本
     * @return 公告状态
     */
    private static AnnouncementStatus parseStatus(String raw) {
        try {
            return AnnouncementStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "message.announcement.status-unrecognized",
                    "无法识别的公告状态: " + raw, raw);
        }
    }

    /**
     * 公告与消息记录合成视图。
     *
     * @param announcement 公告实体
     * @param record       消息记录（可能因逻辑删除为空）
     * @return 公告视图
     */
    private static AnnouncementVO toVo(MessageAnnouncementPo announcement, MessageRecordPo record) {
        AnnouncementVO vo = new AnnouncementVO();
        vo.setId(announcement.getId());
        vo.setRecordId(announcement.getRecordId());
        vo.setStatus(announcement.getStatus());
        vo.setPublisherId(announcement.getPublisherId());
        vo.setPublishTime(announcement.getPublishTime());
        vo.setCreateTime(announcement.getCreateTime());
        vo.setUpdateTime(announcement.getUpdateTime());
        if (record != null) {
            vo.setTitle(record.getTitle());
            vo.setContent(record.getContent());
            vo.setBizType(record.getBizType());
        }
        return vo;
    }
}
