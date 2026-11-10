package com.bluewhale.doc.application.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocAclRole;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageAcl;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAclMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.interfaces.dto.DocPageAclUpdateRequest;
import com.bluewhale.doc.interfaces.vo.DocAclItemVO;
import com.bluewhale.doc.interfaces.vo.DocPageAclVO;
import com.bluewhale.doc.support.DocAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 页面级权限应用服务。
 *
 * <p>仅空间 OWNER/ADMIN 可查看与调整页面授权；启用页面级权限后，
 * 页面从公开检索、反向链接、最近访问与 AI 知识源中退出，仅 ACL 授权用户可见。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPageAclService {

    /** 单页授权用户上限 */
    private static final int MAX_GRANTS = 100;

    private final DocPageAclMapper aclMapper;

    private final DocPageMapper pageMapper;

    private final DocAccessGuard accessGuard;

    private final DocPageActivityService activityService;

    /**
     * 查询页面授权。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 页面授权
     */
    public DocPageAclVO get(Long pageId, Long userId) {
        DocPage page = requireManageable(pageId, userId);
        DocPageAclVO vo = new DocPageAclVO();
        vo.setPageId(pageId);
        vo.setRestricted(accessGuard.isRestricted(page));
        List<DocPageAcl> acls = aclMapper.selectList(Wrappers.<DocPageAcl>lambdaQuery()
                .eq(DocPageAcl::getPageId, pageId)
                .orderByAsc(DocPageAcl::getUserId));
        for (DocPageAcl acl : acls) {
            DocAclItemVO item = new DocAclItemVO();
            item.setUserId(acl.getUserId());
            item.setAclRole(acl.getAclRole());
            item.setCreateTime(acl.getCreateTime());
            vo.getItems().add(item);
        }
        return vo;
    }

    /**
     * 全量更新页面授权与受限开关。
     *
     * @param pageId  页面ID
     * @param request 授权内容
     * @param userId  当前用户ID
     * @return 更新后的授权
     */
    @Transactional(rollbackFor = Exception.class)
    public DocPageAclVO update(Long pageId, DocPageAclUpdateRequest request, Long userId) {
        DocPage page = requireManageable(pageId, userId);
        boolean restricted = Boolean.TRUE.equals(request.getRestricted());
        Map<Long, DocAclRole> grants = new LinkedHashMap<>();
        if (restricted && request.getItems() != null) {
            for (DocPageAclUpdateRequest.Item item : request.getItems()) {
                if (item.getUserId() == null || item.getUserId() <= 0) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "授权用户ID非法");
                }
                DocAclRole role = DocAclRole.parse(item.getAclRole());
                if (role == null) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "页面角色只能是 READ 或 EDIT");
                }
                grants.put(item.getUserId(), role);
            }
        }
        if (grants.size() > MAX_GRANTS) {
            throw new BizException(ErrorCode.PARAM_ERROR, "授权用户不能超过 " + MAX_GRANTS + " 个");
        }
        long tenantId = TenantContext.requireId();
        aclMapper.deleteByPage(tenantId, pageId);
        for (Map.Entry<Long, DocAclRole> entry : grants.entrySet()) {
            DocPageAcl acl = new DocPageAcl();
            acl.setPageId(pageId);
            acl.setUserId(entry.getKey());
            acl.setAclRole(entry.getValue().name());
            aclMapper.insert(acl);
        }
        pageMapper.update(null, Wrappers.<DocPage>lambdaUpdate()
                .eq(DocPage::getId, pageId)
                .set(DocPage::getIsRestricted, restricted ? 1 : 0)
                .set(DocPage::getUpdateTime, LocalDateTime.now()));
        activityService.record(pageId, "ACL", userId,
                restricted ? "启用页面级权限，授权 " + grants.size() + " 人" : "关闭页面级权限");
        log.info("页面级权限已更新: pageId={}, restricted={}, grants={}, operator={}",
                pageId, restricted, grants.size(), userId);
        return get(pageId, userId);
    }

    /**
     * 校验页面存在且当前用户可管理空间。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @return 页面
     */
    private DocPage requireManageable(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        if (!accessGuard.canManage(page, userId)) {
            throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
        }
        return page;
    }
}
