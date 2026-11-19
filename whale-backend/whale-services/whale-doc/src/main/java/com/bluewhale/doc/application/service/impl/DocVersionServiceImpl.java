package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.doc.application.service.DocVersionService;
import com.bluewhale.doc.application.service.support.DocPageMutationSupport;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.domain.model.DocPageStatus;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageVersion;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageVersionMapper;
import com.bluewhale.doc.interfaces.dto.DocVersionManualRequest;
import com.bluewhale.doc.interfaces.vo.DocPageSaveVO;
import com.bluewhale.doc.interfaces.vo.DocPageVersionVO;
import com.bluewhale.doc.support.DocAccessGuard;
import com.bluewhale.doc.support.DocContentSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 页面版本快照应用服务实现。
 *
 * <p>版本表 append-only；同一 revision 只保留一条快照。自动快照在 revision 达到配置间隔
 * 或距上次快照超过配置分钟数时触发。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocVersionServiceImpl implements DocVersionService {

    /** 手动快照类型 */
    private static final String KIND_MANUAL = "MANUAL";

    /** 自动快照类型 */
    private static final String KIND_AUTO = "AUTO";

    /** 恢复快照类型 */
    private static final String KIND_RESTORE = "RESTORE";

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final DocPageVersionMapper versionMapper;

    private final DocAccessGuard accessGuard;

    private final DocContentSupport contentSupport;

    private final DocPageMutationSupport mutationSupport;

    private final DocProperties docProperties;

    @Override
    public PageResult<DocPageVersionVO> page(Long pageId, long current, long size, Long userId) {
        DocPage page = requireReadablePage(pageId, userId, DocMemberRole.READER);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Page<DocPageVersion> versionPage = versionMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<DocPageVersion>lambdaQuery()
                        .eq(DocPageVersion::getPageId, page.getId())
                        .orderByDesc(DocPageVersion::getRevisionNo)
                        .orderByDesc(DocPageVersion::getId));
        List<DocPageVersionVO> records = versionPage.getRecords().stream()
                .map(version -> toVO(version, false))
                .toList();
        return PageResult.of(records, versionPage.getTotal(), versionPage.getCurrent(), versionPage.getSize());
    }

    @Override
    public DocPageVersionVO detail(Long pageId, Long revisionNo, Long userId) {
        DocPage page = requireReadablePage(pageId, userId, DocMemberRole.READER);
        DocPageVersion version = findVersion(page.getId(), revisionNo);
        if (version == null) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        return toVO(version, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageVersionVO manual(Long pageId, DocVersionManualRequest request, Long userId) {
        DocPage page = requireEditablePage(pageId, userId);
        String summary = request == null ? null : request.getChangeSummary();
        snapshot(page, KIND_MANUAL, summary, userId);
        DocPageVersion version = findVersion(page.getId(), page.getRevision());
        if (version == null) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        return toVO(version, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageSaveVO restore(Long pageId, Long revisionNo, Long userId) {
        DocPage page = requireEditablePage(pageId, userId);
        DocPageVersion version = findVersion(page.getId(), revisionNo);
        if (version == null) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        contentSupport.requireWithinLimit(version.getContentJson());
        String text = contentSupport.extractText(version.getContentJson());
        DocPage current = accessGuard.requirePage(pageId);
        DocPage updated = mutationSupport.apply(current, current.getRevision(), version.getTitle(),
                version.getContentJson(), text, userId);
        snapshot(updated, KIND_RESTORE, "恢复至版本 " + revisionNo, userId);
        log.info("页面版本已恢复: pageId={}, revision={}, newRevision={}, operator={}",
                pageId, revisionNo, updated.getRevision(), userId);
        return toSaveVO(updated);
    }

    @Override
    public void snapshot(DocPage page, String versionKind, String changeSummary, Long editorId) {
        if (page == null || page.getRevision() == null || page.getId() == null) {
            return;
        }
        if (findVersion(page.getId(), page.getRevision()) != null) {
            return;
        }
        DocPageVersion version = new DocPageVersion();
        version.setPageId(page.getId());
        version.setRevisionNo(page.getRevision());
        version.setTitle(page.getTitle());
        version.setContentJson(page.getContentJson());
        version.setContentText(page.getContentText());
        version.setVersionKind(versionKind);
        version.setChangeSummary(trimSummary(changeSummary));
        version.setEditorId(editorId);
        version.setContentHash(contentSupport.sha256(page.getContentJson()));
        versionMapper.insert(version);
    }

    @Override
    public void maybeAutoSnapshot(DocPage page, Long editorId) {
        if (page == null || page.getRevision() == null || page.getRevision() <= 0) {
            return;
        }
        boolean byRevision = shouldSnapshotByRevision(page.getRevision());
        boolean byTime = shouldSnapshotByTime(page.getId());
        if (byRevision || byTime) {
            snapshot(page, KIND_AUTO, null, editorId);
        }
    }

    @Override
    public String latestContentHash(Long pageId) {
        DocPageVersion latest = latestVersion(pageId);
        return latest == null ? null : latest.getContentHash();
    }

    /**
     * 判断 revision 是否达到自动快照间隔。
     *
     * @param revision 当前 revision
     * @return 达到间隔返回 {@code true}
     */
    private boolean shouldSnapshotByRevision(Long revision) {
        int step = docProperties.getAutoSnapshotRevisions();
        return step > 0 && revision % step == 0;
    }

    /**
     * 判断距最近一次快照是否超过时间间隔。
     *
     * <p>无历史快照时不触发时间型自动快照，避免首个保存占住 revision 的唯一位，
     * 使随后的发布（PUBLISH）能够保留为正式快照。</p>
     *
     * @param pageId 页面ID
     * @return 超过时间间隔返回 {@code true}
     */
    private boolean shouldSnapshotByTime(Long pageId) {
        int minutes = docProperties.getAutoSnapshotMinutes();
        if (minutes <= 0) {
            return false;
        }
        DocPageVersion latest = latestVersion(pageId);
        if (latest == null || latest.getCreateTime() == null) {
            return false;
        }
        return latest.getCreateTime().isBefore(LocalDateTime.now().minusMinutes(minutes));
    }

    /**
     * 读取页面并校验成员只读权限。
     *
     * @param pageId   页面ID
     * @param userId   用户ID
     * @param required 最小角色
     * @return 页面
     */
    private DocPage requireReadablePage(Long pageId, Long userId, DocMemberRole required) {
        DocPage page = accessGuard.requirePage(pageId);
        accessGuard.requireMember(page.getSpaceId(), userId, required);
        return page;
    }

    /**
     * 读取页面并校验编辑权限，归档页面不可恢复版本。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @return 页面
     */
    private DocPage requireEditablePage(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        accessGuard.requirePageEdit(page, userId);
        if (DocPageStatus.ARCHIVED.name().equals(page.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.page.archived-not-editable", "已归档页面不可编辑");
        }
        return page;
    }

    /**
     * 查询指定 revision 的版本。
     *
     * @param pageId     页面ID
     * @param revisionNo 快照 revision
     * @return 版本；不存在返回 {@code null}
     */
    private DocPageVersion findVersion(Long pageId, Long revisionNo) {
        if (revisionNo == null) {
            return null;
        }
        return versionMapper.selectOne(Wrappers.<DocPageVersion>lambdaQuery()
                .eq(DocPageVersion::getPageId, pageId)
                .eq(DocPageVersion::getRevisionNo, revisionNo));
    }

    /**
     * 查询最近一条版本快照。
     *
     * @param pageId 页面ID
     * @return 最近版本；无快照返回 {@code null}
     */
    private DocPageVersion latestVersion(Long pageId) {
        List<DocPageVersion> latest = versionMapper.selectList(Wrappers.<DocPageVersion>lambdaQuery()
                .eq(DocPageVersion::getPageId, pageId)
                .orderByDesc(DocPageVersion::getRevisionNo)
                .last("LIMIT 1"));
        return latest.isEmpty() ? null : latest.get(0);
    }

    /**
     * 截断变更说明。
     *
     * @param summary 原始说明
     * @return 截断后的说明
     */
    private String trimSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return null;
        }
        String trimmed = summary.trim();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }

    /**
     * 版本实体转视图。
     *
     * @param version      版本
     * @param withContent 是否返回正文
     * @return 版本视图
     */
    private DocPageVersionVO toVO(DocPageVersion version, boolean withContent) {
        DocPageVersionVO vo = new DocPageVersionVO();
        vo.setId(version.getId());
        vo.setPageId(version.getPageId());
        vo.setRevisionNo(version.getRevisionNo());
        vo.setTitle(version.getTitle());
        vo.setVersionKind(version.getVersionKind());
        vo.setChangeSummary(version.getChangeSummary());
        vo.setEditorId(version.getEditorId());
        vo.setContentJson(withContent ? version.getContentJson() : null);
        vo.setCreateTime(version.getCreateTime());
        return vo;
    }

    /**
     * 页面转保存结果。
     *
     * @param page 页面
     * @return 保存结果
     */
    private DocPageSaveVO toSaveVO(DocPage page) {
        DocPageSaveVO vo = new DocPageSaveVO();
        vo.setId(page.getId());
        vo.setRevision(page.getRevision());
        vo.setStatus(page.getStatus());
        vo.setSavedAt(page.getUpdateTime());
        return vo;
    }
}
