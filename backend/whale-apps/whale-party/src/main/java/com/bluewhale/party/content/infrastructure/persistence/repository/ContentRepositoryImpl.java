package com.bluewhale.party.content.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.party.content.domain.model.AuditAction;
import com.bluewhale.party.content.domain.model.ContentAudit;
import com.bluewhale.party.content.domain.model.ContentPortalQuery;
import com.bluewhale.party.content.domain.model.ContentQuery;
import com.bluewhale.party.content.domain.model.ContentRead;
import com.bluewhale.party.content.domain.model.ContentScope;
import com.bluewhale.party.content.domain.model.ContentStatus;
import com.bluewhale.party.content.domain.model.ContentVisibility;
import com.bluewhale.party.content.domain.model.PartyContent;
import com.bluewhale.party.content.domain.repository.ContentRepository;
import com.bluewhale.party.content.infrastructure.persistence.entity.ContentAuditEntity;
import com.bluewhale.party.content.infrastructure.persistence.entity.ContentReadEntity;
import com.bluewhale.party.content.infrastructure.persistence.entity.PartyContentEntity;
import com.bluewhale.party.content.infrastructure.persistence.mapper.ContentAuditMapper;
import com.bluewhale.party.content.infrastructure.persistence.mapper.ContentReadMapper;
import com.bluewhale.party.content.infrastructure.persistence.mapper.PartyContentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 党建内容仓储实现（MyBatis-Plus）。
 *
 * <p>支部范围与附件以逗号分隔存字符串：数量上限分别 20 与 10，不需要单独建关联表；
 * 阅读范围查询用 {@code CONCAT(',', branch_scope, ',') LIKE ?} 做精确匹配，
 * 避免「支部 1 命中支部 12」这类前缀误判。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class ContentRepositoryImpl implements ContentRepository {

    private final PartyContentMapper partyContentMapper;

    private final ContentAuditMapper contentAuditMapper;

    private final ContentReadMapper contentReadMapper;

    @Override
    public List<PartyContent> page(ContentQuery query, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<PartyContentEntity> page = partyContentMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), manageFilter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long count(ContentQuery query) {
        return partyContentMapper.selectCount(manageFilter(query));
    }

    @Override
    public List<PartyContent> portalPage(ContentPortalQuery query, ContentScope scope, long offset, int size) {
        long current = Math.max(offset / Math.max(size, 1) + 1, 1L);
        Page<PartyContentEntity> page = partyContentMapper.selectPage(
                new Page<>(current, Math.max(size, 1)), portalFilter(query, scope));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long portalCount(ContentPortalQuery query, ContentScope scope) {
        return partyContentMapper.selectCount(portalFilter(query, scope));
    }

    @Override
    public Optional<PartyContent> findById(Long id) {
        return Optional.ofNullable(partyContentMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Long save(PartyContent content) {
        PartyContentEntity entity = toEntity(content);
        if (entity.getId() == null) {
            partyContentMapper.insert(entity);
        } else {
            partyContentMapper.updateById(entity);
        }
        content.setId(entity.getId());
        content.setCreateTime(entity.getCreateTime());
        content.setUpdateTime(entity.getUpdateTime());
        return entity.getId();
    }

    @Override
    public List<ContentAudit> listAudits(Long contentId) {
        return contentAuditMapper.selectList(new LambdaQueryWrapper<ContentAuditEntity>()
                        .eq(ContentAuditEntity::getContentId, contentId)
                        .orderByAsc(ContentAuditEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Long saveAudit(ContentAudit audit) {
        ContentAuditEntity entity = new ContentAuditEntity();
        entity.setContentId(audit.getContentId());
        entity.setAction(audit.getAction() == null ? AuditAction.SUBMIT.name() : audit.getAction().name());
        entity.setFromStatus(audit.getFromStatus());
        entity.setToStatus(audit.getToStatus());
        entity.setOpinion(audit.getOpinion());
        entity.setOperatorId(audit.getOperatorId());
        entity.setOperatorName(audit.getOperatorName());
        contentAuditMapper.insert(entity);
        audit.setId(entity.getId());
        audit.setCreateTime(entity.getCreateTime());
        return entity.getId();
    }

    @Override
    public boolean recordRead(ContentRead read) {
        ContentReadEntity existing = contentReadMapper.selectOne(new LambdaQueryWrapper<ContentReadEntity>()
                .eq(ContentReadEntity::getContentId, read.getContentId())
                .eq(ContentReadEntity::getUserId, read.getUserId()));
        ContentReadEntity entity = new ContentReadEntity();
        entity.setContentId(read.getContentId());
        entity.setUserId(read.getUserId());
        entity.setUserName(read.getUserName());
        entity.setReadAt(read.getReadAt());
        if (existing == null) {
            contentReadMapper.insert(entity);
            read.setId(entity.getId());
            read.setCreateTime(entity.getCreateTime());
            return true;
        }
        entity.setId(existing.getId());
        contentReadMapper.updateById(entity);
        read.setId(existing.getId());
        return false;
    }

    @Override
    public List<ContentRead> listReads(Long contentId) {
        return contentReadMapper.selectList(new LambdaQueryWrapper<ContentReadEntity>()
                        .eq(ContentReadEntity::getContentId, contentId)
                        .orderByDesc(ContentReadEntity::getReadAt)
                        .orderByDesc(ContentReadEntity::getId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void incrementReadCount(Long contentId) {
        partyContentMapper.update(null, new LambdaUpdateWrapper<PartyContentEntity>()
                .eq(PartyContentEntity::getId, contentId)
                .setSql("read_count = read_count + 1"));
    }

    /**
     * 管理列表查询条件（含全部状态）。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<PartyContentEntity> manageFilter(ContentQuery query) {
        LambdaQueryWrapper<PartyContentEntity> wrapper = new LambdaQueryWrapper<>();
        if (query == null) {
            return wrapper.orderByDesc(PartyContentEntity::getId);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(PartyContentEntity::getTitle, keyword)
                    .or().like(PartyContentEntity::getCategory, keyword));
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(PartyContentEntity::getCategory, query.getCategory().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(PartyContentEntity::getStatus,
                    query.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(query.getVisibility())) {
            wrapper.eq(PartyContentEntity::getVisibility,
                    query.getVisibility().trim().toUpperCase(Locale.ROOT));
        }
        if (query.getScopeAuthorId() != null) {
            // 非审核人：自己的稿件（含未审）+ 全部已发布，别人的待审稿不可见
            wrapper.and(w -> w.eq(PartyContentEntity::getAuthorId, query.getScopeAuthorId())
                    .or().eq(PartyContentEntity::getStatus, ContentStatus.PUBLISHED.name()));
        }
        return wrapper.orderByDesc(PartyContentEntity::getId);
    }

    /**
     * 阅读列表查询条件（仅已发布 + 当前用户可见范围）。
     *
     * @param query 查询条件
     * @param scope 阅读范围
     * @return 查询包装器
     */
    private LambdaQueryWrapper<PartyContentEntity> portalFilter(ContentPortalQuery query, ContentScope scope) {
        LambdaQueryWrapper<PartyContentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PartyContentEntity::getStatus, ContentStatus.PUBLISHED.name());
        if (query != null && StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(PartyContentEntity::getTitle, keyword)
                    .or().like(PartyContentEntity::getCategory, keyword));
        }
        if (query != null && StringUtils.hasText(query.getCategory())) {
            wrapper.eq(PartyContentEntity::getCategory, query.getCategory().trim());
        }
        ContentScope safeScope = scope == null ? ContentScope.none() : scope;
        if (!safeScope.isManager()) {
            wrapper.and(w -> {
                w.eq(PartyContentEntity::getVisibility, ContentVisibility.ALL_STAFF.name());
                if (safeScope.isAllMembers()) {
                    w.or().eq(PartyContentEntity::getVisibility, ContentVisibility.ALL_MEMBERS.name());
                }
                for (Long branchId : safeScope.getBranchIds()) {
                    w.or(q -> q.eq(PartyContentEntity::getVisibility, ContentVisibility.BRANCHES.name())
                            .apply("CONCAT(',', branch_scope, ',') LIKE ?", "%," + branchId + ",%"));
                }
            });
        }
        return wrapper.orderByDesc(PartyContentEntity::getPublishedAt)
                .orderByDesc(PartyContentEntity::getId);
    }

    /**
     * 实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private PartyContent toDomain(PartyContentEntity entity) {
        PartyContent content = new PartyContent();
        content.setId(entity.getId());
        content.setCategory(entity.getCategory());
        content.setTitle(entity.getTitle());
        content.setSummary(entity.getSummary());
        content.setCoverFileId(entity.getCoverFileId());
        content.setBody(entity.getBody());
        content.setAttachmentFileIds(parseIds(entity.getAttachmentFileIds()));
        content.setVisibility(ContentVisibility.parse(entity.getVisibility()));
        content.setBranchIds(parseIds(entity.getBranchScope()));
        content.setStatus(ContentStatus.parse(entity.getStatus()));
        content.setAuthorId(entity.getAuthorId());
        content.setAuthorName(entity.getAuthorName());
        content.setSubmitTime(entity.getSubmitTime());
        content.setPublishedAt(entity.getPublishedAt());
        content.setOfflineAt(entity.getOfflineAt());
        content.setReadCount(entity.getReadCount());
        content.setRemark(entity.getRemark());
        content.setCreateTime(entity.getCreateTime());
        content.setUpdateTime(entity.getUpdateTime());
        return content;
    }

    /**
     * 领域模型转实体。
     *
     * @param content 领域模型
     * @return 实体
     */
    private PartyContentEntity toEntity(PartyContent content) {
        PartyContentEntity entity = new PartyContentEntity();
        entity.setId(content.getId());
        entity.setCategory(content.getCategory());
        entity.setTitle(content.getTitle());
        entity.setSummary(content.getSummary());
        entity.setCoverFileId(content.getCoverFileId());
        entity.setBody(content.getBody());
        entity.setAttachmentFileIds(joinIds(content.getAttachmentFileIds()));
        entity.setVisibility(content.getVisibility() == null
                ? ContentVisibility.ALL_MEMBERS.name() : content.getVisibility().name());
        entity.setBranchScope(joinIds(content.getBranchIds()));
        entity.setStatus(content.getStatus() == null ? ContentStatus.DRAFT.name() : content.getStatus().name());
        entity.setAuthorId(content.getAuthorId());
        entity.setAuthorName(content.getAuthorName());
        entity.setSubmitTime(content.getSubmitTime());
        entity.setPublishedAt(content.getPublishedAt());
        entity.setOfflineAt(content.getOfflineAt());
        entity.setReadCount(content.getReadCount());
        entity.setRemark(content.getRemark());
        return entity;
    }

    /**
     * 审核实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ContentAudit toDomain(ContentAuditEntity entity) {
        ContentAudit audit = new ContentAudit();
        audit.setId(entity.getId());
        audit.setContentId(entity.getContentId());
        audit.setAction(AuditAction.parse(entity.getAction()));
        audit.setFromStatus(entity.getFromStatus());
        audit.setToStatus(entity.getToStatus());
        audit.setOpinion(entity.getOpinion());
        audit.setOperatorId(entity.getOperatorId());
        audit.setOperatorName(entity.getOperatorName());
        audit.setCreateTime(entity.getCreateTime());
        return audit;
    }

    /**
     * 阅读实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private ContentRead toDomain(ContentReadEntity entity) {
        ContentRead read = new ContentRead();
        read.setId(entity.getId());
        read.setContentId(entity.getContentId());
        read.setUserId(entity.getUserId());
        read.setUserName(entity.getUserName());
        read.setReadAt(entity.getReadAt());
        read.setCreateTime(entity.getCreateTime());
        return read;
    }

    /**
     * 逗号分隔 ID → 列表。
     *
     * @param text 文本
     * @return 列表
     */
    private List<Long> parseIds(String text) {
        List<Long> ids = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return ids;
        }
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException exception) {
                // 历史脏数据跳过，不让一条坏记录拖垮整页
            }
        }
        return ids;
    }

    /**
     * ID 列表 → 逗号分隔。
     *
     * @param ids 列表
     * @return 文本
     */
    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().filter(id -> id != null && id > 0)
                .map(String::valueOf).collect(Collectors.joining(","));
    }
}
