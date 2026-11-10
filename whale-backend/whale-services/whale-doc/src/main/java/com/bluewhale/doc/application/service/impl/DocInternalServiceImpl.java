package com.bluewhale.doc.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocInternalService;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocPageChangeRow;
import com.bluewhale.doc.interfaces.vo.DocInternalChangeVO;
import com.bluewhale.doc.interfaces.vo.DocInternalPageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内网知识源同步应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class DocInternalServiceImpl implements DocInternalService {

    /** 增量同步单页最大条数 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 逻辑删除标记：已删除 */
    private static final int DELETED_YES = 1;

    /** 空间状态：启用 */
    private static final int SPACE_ACTIVE = 1;

    private final DocPageMapper pageMapper;

    @Override
    public PageResult<DocInternalChangeVO> changes(LocalDateTime since, long current, long size) {
        Long tenantId = TenantContext.requireId();
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        long pageNo = Math.max(current, 1L);
        long offset = (pageNo - 1) * pageSize;
        long total = pageMapper.countChanges(tenantId, since);
        if (total == 0) {
            return PageResult.of(List.of(), 0L, pageNo, pageSize);
        }
        List<DocInternalChangeVO> records = pageMapper.changes(tenantId, since, offset, (int) pageSize)
                .stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(records, total, pageNo, pageSize);
    }

    @Override
    public DocInternalPageVO detail(Long pageId) {
        DocInternalPageVO page = pageMapper.selectInternalPage(TenantContext.requireId(), pageId);
        if (page == null) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        return page;
    }

    /**
     * 变更行转视图。
     *
     * @param row 变更行
     * @return 变更视图
     */
    private DocInternalChangeVO toVO(DocPageChangeRow row) {
        DocInternalChangeVO vo = new DocInternalChangeVO();
        vo.setPageId(row.getPageId());
        vo.setSpaceId(row.getSpaceId());
        vo.setSpaceName(row.getSpaceName());
        vo.setTitle(row.getTitle());
        vo.setSlug(row.getSlug());
        vo.setStatus(row.getStatus());
        vo.setRevision(row.getRevision());
        vo.setRestricted(row.getIsRestricted() != null && row.getIsRestricted() == 1);
        vo.setDeleted(row.getDeleted() != null && row.getDeleted() == DELETED_YES);
        vo.setSpaceVisibility(row.getVisibility());
        vo.setSpaceActive(row.getSpaceStatus() != null && row.getSpaceStatus() == SPACE_ACTIVE
                && (row.getSpaceDeleted() == null || row.getSpaceDeleted() == 0));
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
