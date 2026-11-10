package com.bluewhale.doc.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocAclRole;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.domain.model.DocPageStatus;
import com.bluewhale.doc.domain.model.DocVisibility;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageAcl;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpaceMember;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAclMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 文档数据权限守卫。
 *
 * <p>平台权限码控制功能入口；本组件按空间成员角色（OWNER &gt; ADMIN &gt; EDITOR &gt; READER）
 * 做数据权限二次校验。公开（PUBLIC）空间仅对租户成员开放已发布页面的只读访问。</p>
 *
 * <p>M2-4 页面级权限：页面 {@code is_restricted=1} 时，仅空间 ADMIN/OWNER 与
 * {@code doc_page_acl} 授权用户可见；ACL 角色 READ/EDIT 叠加在空间角色之上。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class DocAccessGuard {

    private final DocSpaceMapper spaceMapper;
    private final DocSpaceMemberMapper memberMapper;
    private final DocPageMapper pageMapper;
    private final DocPageAclMapper aclMapper;

    /**
     * 查询用户在空间中的角色。
     *
     * @param spaceId 空间ID
     * @param userId  用户ID
     * @return 角色；非成员返回 {@code null}
     */
    public DocMemberRole roleOf(Long spaceId, Long userId) {
        DocSpaceMember member = memberMapper.selectOne(Wrappers.<DocSpaceMember>lambdaQuery()
                .eq(DocSpaceMember::getSpaceId, spaceId)
                .eq(DocSpaceMember::getUserId, userId));
        return member == null ? null : parseRole(member.getMemberRole());
    }

    /**
     * 读取空间并校验存在性。
     *
     * @param spaceId 空间ID
     * @return 空间
     */
    public DocSpace requireSpace(Long spaceId) {
        DocSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
        }
        return space;
    }

    /**
     * 校验空间读取权限：成员始终可读；非成员仅可读启用中的公开空间。
     *
     * @param spaceId 空间ID
     * @param userId  用户ID
     * @return 空间
     */
    public DocSpace requireSpaceRead(Long spaceId, Long userId) {
        DocSpace space = requireSpace(spaceId);
        if (roleOf(spaceId, userId) != null) {
            return space;
        }
        if (isPublicAndActive(space)) {
            return space;
        }
        throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
    }

    /**
     * 校验最小空间角色。
     *
     * @param spaceId 空间ID
     * @param userId  用户ID
     * @param required 最小角色
     * @return 实际角色
     */
    public DocMemberRole requireMember(Long spaceId, Long userId, DocMemberRole required) {
        DocMemberRole role = roleOf(spaceId, userId);
        if (role == null || !role.atLeast(required)) {
            throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
        }
        return role;
    }

    /**
     * 读取页面并校验存在性。
     *
     * @param pageId 页面ID
     * @return 页面
     */
    public DocPage requirePage(Long pageId) {
        DocPage page = pageMapper.selectById(pageId);
        if (page == null) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        return page;
    }

    /**
     * 校验页面读取权限。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @return 页面
     */
    public DocPage requirePageRead(Long pageId, Long userId) {
        DocPage page = requirePage(pageId);
        if (canRead(page, userId)) {
            return page;
        }
        throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
    }

    /**
     * 校验页面编辑权限：受限页面按 ACL EDIT 判定，其余按空间 EDITOR 及以上。
     *
     * @param page 页面
     * @param userId 用户ID
     */
    public void requirePageEdit(DocPage page, Long userId) {
        if (!canEdit(page, userId)) {
            throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
        }
    }

    /**
     * 判断用户是否可读页面。
     *
     * @param page 页面
     * @param userId 用户ID
     * @return 可读返回 {@code true}
     */
    public boolean canRead(DocPage page, Long userId) {
        DocMemberRole role = roleOf(page.getSpaceId(), userId);
        boolean restricted = isRestricted(page);
        if (role != null) {
            if (!restricted) {
                return true;
            }
            return role.atLeast(DocMemberRole.ADMIN) || aclRoleOf(page.getId(), userId) != null;
        }
        if (restricted) {
            DocSpace space = spaceMapper.selectById(page.getSpaceId());
            return space != null && space.getStatus() != null && space.getStatus() == 1
                    && aclRoleOf(page.getId(), userId) != null;
        }
        DocSpace space = spaceMapper.selectById(page.getSpaceId());
        return space != null && isPublicAndActive(space)
                && DocPageStatus.PUBLISHED.name().equals(page.getStatus());
    }

    /**
     * 判断用户是否可编辑页面。
     *
     * @param page 页面
     * @param userId 用户ID
     * @return 可编辑返回 {@code true}
     */
    public boolean canEdit(DocPage page, Long userId) {
        DocMemberRole role = roleOf(page.getSpaceId(), userId);
        if (isRestricted(page)) {
            if (role != null && role.atLeast(DocMemberRole.ADMIN)) {
                return true;
            }
            DocAclRole aclRole = aclRoleOf(page.getId(), userId);
            return aclRole != null && aclRole.atLeast(DocAclRole.EDIT);
        }
        if (role != null) {
            return role.atLeast(DocMemberRole.EDITOR);
        }
        DocAclRole aclRole = aclRoleOf(page.getId(), userId);
        return aclRole != null && aclRole.atLeast(DocAclRole.EDIT);
    }

    /**
     * 判断用户是否可评论页面：需可读，且为空间成员或页面 ACL 授权用户。
     *
     * @param page 页面
     * @param userId 用户ID
     * @return 可评论返回 {@code true}
     */
    public boolean canComment(DocPage page, Long userId) {
        return canRead(page, userId)
                && (roleOf(page.getSpaceId(), userId) != null || aclRoleOf(page.getId(), userId) != null);
    }

    /**
     * 查询用户在页面上的 ACL 角色。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @return ACL 角色；未授权或非法值返回 {@code null}
     */
    public DocAclRole aclRoleOf(Long pageId, Long userId) {
        DocPageAcl acl = aclMapper.selectOne(Wrappers.<DocPageAcl>lambdaQuery()
                .eq(DocPageAcl::getPageId, pageId)
                .eq(DocPageAcl::getUserId, userId));
        return acl == null ? null : DocAclRole.parse(acl.getAclRole());
    }

    /**
     * 判断页面是否启用页面级权限。
     *
     * @param page 页面
     * @return 启用页面级权限返回 {@code true}
     */
    public boolean isRestricted(DocPage page) {
        return page.getIsRestricted() != null && page.getIsRestricted() == 1;
    }

    /**
     * 批量过滤当前用户不可见的页面（用于页面树、搜索补充过滤等）。
     *
     * @param pages  同一空间的页面
     * @param userId 用户ID
     * @return 可见页面列表
     */
    public List<DocPage> filterReadable(List<DocPage> pages, Long userId) {
        if (pages == null || pages.isEmpty()) {
            return pages;
        }
        Long spaceId = pages.get(0).getSpaceId();
        DocMemberRole role = roleOf(spaceId, userId);
        boolean admin = role != null && role.atLeast(DocMemberRole.ADMIN);
        Set<Long> granted = new HashSet<>();
        if (!admin) {
            List<Long> restrictedIds = pages.stream()
                    .filter(this::isRestricted)
                    .map(DocPage::getId)
                    .toList();
            if (!restrictedIds.isEmpty()) {
                aclMapper.selectList(Wrappers.<DocPageAcl>lambdaQuery()
                                .eq(DocPageAcl::getUserId, userId)
                                .in(DocPageAcl::getPageId, restrictedIds))
                        .forEach(acl -> granted.add(acl.getPageId()));
            }
        }
        return pages.stream()
                .filter(page -> {
                    if (!isRestricted(page)) {
                        return role != null
                                || DocPageStatus.PUBLISHED.name().equals(page.getStatus());
                    }
                    return admin || granted.contains(page.getId());
                })
                .toList();
    }

    /**
     * 判断用户是否可管理空间（ADMIN 及以上）。
     *
     * @param page 页面
     * @param userId 用户ID
     * @return 可管理返回 {@code true}
     */
    public boolean canManage(DocPage page, Long userId) {
        DocMemberRole role = roleOf(page.getSpaceId(), userId);
        return role != null && role.atLeast(DocMemberRole.ADMIN);
    }

    /**
     * 判断空间是否公开且启用。
     *
     * @param space 空间
     * @return 公开且启用返回 {@code true}
     */
    public boolean isPublicAndActive(DocSpace space) {
        return space.getStatus() != null && space.getStatus() == 1
                && DocVisibility.PUBLIC.name().equals(space.getVisibility());
    }

    /**
     * 解析角色文本，非法值按非成员处理。
     *
     * @param value 角色文本
     * @return 角色；非法值返回 {@code null}
     */
    private DocMemberRole parseRole(String value) {
        try {
            return DocMemberRole.valueOf(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
