package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocNotificationService;
import com.bluewhale.doc.application.service.DocSpaceService;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.domain.model.DocVisibility;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpaceMember;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMemberMapper;
import com.bluewhale.doc.interfaces.dto.DocMemberAddRequest;
import com.bluewhale.doc.interfaces.dto.DocMemberRoleRequest;
import com.bluewhale.doc.interfaces.dto.DocSpaceQueryRequest;
import com.bluewhale.doc.interfaces.dto.DocSpaceSaveRequest;
import com.bluewhale.doc.interfaces.vo.DocMemberVO;
import com.bluewhale.doc.interfaces.vo.DocSpaceVO;
import com.bluewhale.doc.support.DocAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文档空间与成员应用服务实现。
 *
 * <p>空间编码在租户内唯一（逻辑删除行仍占用）；成员移除为逻辑删除，重新邀请复用原行；
 * OWNER 转让在同一事务内更新空间所有者与两条成员记录。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocSpaceServiceImpl implements DocSpaceService {

    /** 空间启用状态 */
    private static final int STATUS_ACTIVE = 1;

    /** 单页最大条数，防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 默认可见性 */
    private static final String DEFAULT_VISIBILITY = "MEMBER";

    /** 我参与范围标识 */
    private static final String SCOPE_MINE = "mine";

    /** 自动生成空间编码的前缀 */
    private static final String KEY_PREFIX = "doc-";

    /** 自动生成空间编码的尝试次数 */
    private static final int KEY_RETRY = 5;

    /** 空间编码随机段长度 */
    private static final int KEY_RANDOM_LENGTH = 10;

    private final DocSpaceMapper spaceMapper;

    private final DocSpaceMemberMapper memberMapper;

    private final DocAccessGuard accessGuard;

    private final DocNotificationService notificationService;

    @Override
    public PageResult<DocSpaceVO> page(DocSpaceQueryRequest request, Long userId) {
        List<Long> memberSpaceIds = memberSpaceIds(userId);
        LambdaQueryWrapper<DocSpace> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(DocSpace::getStatus, STATUS_ACTIVE);
        applyScope(wrapper, request.getScope(), memberSpaceIds);
        applyFilters(wrapper, request, memberSpaceIds);
        wrapper.orderByAsc(DocSpace::getSortNo).orderByDesc(DocSpace::getUpdateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<DocSpace> page = spaceMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        Map<Long, String> roles = rolesOf(userId, page.getRecords());
        List<DocSpaceVO> records = page.getRecords().stream()
                .map(space -> toVO(space, roles.get(space.getId()), null))
                .toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<DocSpaceVO> my(Long userId) {
        List<Long> memberSpaceIds = memberSpaceIds(userId);
        if (memberSpaceIds.isEmpty()) {
            return List.of();
        }
        List<DocSpace> spaces = spaceMapper.selectList(Wrappers.<DocSpace>lambdaQuery()
                .eq(DocSpace::getStatus, STATUS_ACTIVE)
                .in(DocSpace::getId, memberSpaceIds)
                .orderByAsc(DocSpace::getSortNo)
                .orderByDesc(DocSpace::getUpdateTime));
        Map<Long, String> roles = rolesOf(userId, spaces);
        return spaces.stream().map(space -> toVO(space, roles.get(space.getId()), null)).toList();
    }

    @Override
    public DocSpaceVO detail(Long spaceId, Long userId) {
        DocSpace space = accessGuard.requireSpaceRead(spaceId, userId);
        return toVO(space, roleName(accessGuard.roleOf(spaceId, userId)), countMembers(spaceId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocSpaceVO create(DocSpaceSaveRequest request, Long userId) {
        String key = resolveKey(request.getSpaceKey());
        DocSpace space = new DocSpace();
        space.setSpaceKey(key);
        space.setName(request.getName().trim());
        space.setDescription(request.getDescription());
        space.setIcon(request.getIcon());
        space.setVisibility(normalizeVisibility(request.getVisibility()));
        space.setOwnerId(userId);
        space.setStatus(STATUS_ACTIVE);
        space.setSortNo(0);
        spaceMapper.insert(space);
        addOwnerMember(space.getId(), userId);
        log.info("文档空间已创建: id={}, key={}, owner={}", space.getId(), key, userId);
        return toVO(space, DocMemberRole.OWNER.name(), 1L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocSpaceVO update(Long spaceId, DocSpaceSaveRequest request, Long userId) {
        accessGuard.requireMember(spaceId, userId, DocMemberRole.ADMIN);
        DocSpace space = accessGuard.requireSpace(spaceId);
        space.setName(request.getName().trim());
        space.setDescription(request.getDescription());
        space.setIcon(request.getIcon());
        space.setVisibility(normalizeVisibility(request.getVisibility()));
        spaceMapper.updateById(space);
        log.info("文档空间已更新: id={}, operator={}", spaceId, userId);
        return toVO(space, roleName(accessGuard.roleOf(spaceId, userId)), countMembers(spaceId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long spaceId, Long userId) {
        accessGuard.requireMember(spaceId, userId, DocMemberRole.OWNER);
        DocSpace space = accessGuard.requireSpace(spaceId);
        space.setStatus(0);
        spaceMapper.updateById(space);
        log.info("文档空间已归档: id={}, operator={}", spaceId, userId);
    }

    @Override
    public List<DocMemberVO> members(Long spaceId, Long userId) {
        accessGuard.requireSpaceRead(spaceId, userId);
        List<DocMemberVO> members = new ArrayList<>(activeMembers(spaceId));
        members.sort(Comparator.comparingInt((DocMemberVO member) -> -roleRank(member.getMemberRole())));
        return members;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DocMemberVO> addMembers(Long spaceId, DocMemberAddRequest request, Long userId) {
        accessGuard.requireMember(spaceId, userId, DocMemberRole.ADMIN);
        if (request.getRole() == DocMemberRole.OWNER) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.space.owner-grant-unsupported", "授予所有者请使用成员角色转让");
        }
        Long tenantId = TenantContext.requireId();
        LinkedHashSet<Long> targets = new LinkedHashSet<>(request.getUserIds());
        for (Long targetUserId : targets) {
            upsertMember(tenantId, spaceId, targetUserId, request.getRole());
        }
        notifyMembers(spaceId, userId, new ArrayList<>(targets), "ADD");
        log.info("文档空间成员已添加: spaceId={}, count={}, operator={}", spaceId, targets.size(), userId);
        List<DocMemberVO> result = new ArrayList<>();
        for (Long targetUserId : targets) {
            DocMemberVO member = findMember(spaceId, targetUserId);
            if (member != null) {
                result.add(member);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocMemberVO updateMember(Long spaceId, Long memberId, DocMemberRoleRequest request, Long userId) {
        DocMemberRole actorRole = accessGuard.requireMember(spaceId, userId, DocMemberRole.ADMIN);
        DocSpaceMember member = requireMemberRow(spaceId, memberId);
        DocMemberRole currentRole = parseRole(member.getMemberRole());
        if (request.getRole() == DocMemberRole.OWNER) {
            transferOwnership(spaceId, actorRole, member);
        } else {
            changeRole(actorRole, member, currentRole, request.getRole(), userId);
        }
        memberMapper.updateById(member);
        notifyMembers(spaceId, userId, List.of(member.getUserId()), "UPDATE");
        return toMemberVO(member);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long spaceId, Long memberId, Long userId) {
        DocMemberRole actorRole = accessGuard.requireMember(spaceId, userId, DocMemberRole.ADMIN);
        DocSpaceMember member = requireMemberRow(spaceId, memberId);
        DocMemberRole currentRole = parseRole(member.getMemberRole());
        if (currentRole == DocMemberRole.OWNER) {
            throw DocErrorCode.exception(DocErrorCode.LAST_OWNER_REQUIRED);
        }
        if (currentRole == DocMemberRole.ADMIN && actorRole != DocMemberRole.OWNER
                && !member.getUserId().equals(userId)) {
            throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
        }
        memberMapper.deleteById(memberId);
        notifyMembers(spaceId, userId, List.of(member.getUserId()), "REMOVE");
        log.info("文档空间成员已移除: spaceId={}, memberId={}, operator={}", spaceId, memberId, userId);
    }

    /**
     * 发送空间成员变更通知（开关关闭或外部失败不影响主流程）。
     *
     * @param spaceId    空间ID
     * @param operatorId 操作人ID
     * @param memberIds  受影响成员ID
     * @param action     变更动作
     */
    private void notifyMembers(Long spaceId, Long operatorId, List<Long> memberIds, String action) {
        DocSpace space = accessGuard.requireSpace(spaceId);
        notificationService.memberChanged(TenantContext.requireId(), spaceId, space.getName(),
                operatorId, memberIds, action);
    }

    /**
     * 按范围追加空间过滤条件。
     *
     * @param wrapper        查询条件
     * @param scope          范围
     * @param memberSpaceIds 我加入的空间ID
     */
    private void applyScope(LambdaQueryWrapper<DocSpace> wrapper, String scope, List<Long> memberSpaceIds) {
        if (SCOPE_MINE.equalsIgnoreCase(scope)) {
            if (memberSpaceIds.isEmpty()) {
                wrapper.eq(DocSpace::getId, 0L);
            } else {
                wrapper.in(DocSpace::getId, memberSpaceIds);
            }
            return;
        }
        applyVisibleScope(wrapper, memberSpaceIds);
    }

    /**
     * 追加「公开空间或我加入的空间」过滤。
     *
     * @param wrapper        查询条件
     * @param memberSpaceIds 我加入的空间ID
     */
    private void applyVisibleScope(LambdaQueryWrapper<DocSpace> wrapper, List<Long> memberSpaceIds) {
        if (memberSpaceIds.isEmpty()) {
            wrapper.eq(DocSpace::getVisibility, DocVisibility.PUBLIC.name());
            return;
        }
        wrapper.and(condition -> condition.eq(DocSpace::getVisibility, DocVisibility.PUBLIC.name())
                .or()
                .in(DocSpace::getId, memberSpaceIds));
    }

    /**
     * 追加关键字与可见性过滤。
     *
     * @param wrapper        查询条件
     * @param request        查询参数
     * @param memberSpaceIds 我加入的空间ID
     */
    private void applyFilters(LambdaQueryWrapper<DocSpace> wrapper, DocSpaceQueryRequest request,
                              List<Long> memberSpaceIds) {
        String keyword = request.getKeyword();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(condition -> condition.like(DocSpace::getName, trimmed)
                    .or()
                    .like(DocSpace::getDescription, trimmed));
        }
        if (StringUtils.hasText(request.getVisibility())) {
            String visibility = normalizeVisibility(request.getVisibility());
            if (!DocVisibility.PUBLIC.name().equals(visibility) || !memberSpaceIds.isEmpty()) {
                wrapper.eq(DocSpace::getVisibility, visibility);
            }
        }
    }

    /**
     * 查询我加入的空间ID。
     *
     * @param userId 用户ID
     * @return 空间ID列表
     */
    private List<Long> memberSpaceIds(Long userId) {
        return memberMapper.selectList(Wrappers.<DocSpaceMember>lambdaQuery()
                        .eq(DocSpaceMember::getUserId, userId))
                .stream()
                .map(DocSpaceMember::getSpaceId)
                .distinct()
                .toList();
    }

    /**
     * 批量查询当前用户在空间中的角色。
     *
     * @param userId 用户ID
     * @param spaces 空间列表
     * @return 空间ID到角色文本的映射
     */
    private Map<Long, String> rolesOf(Long userId, List<DocSpace> spaces) {
        Map<Long, String> roles = new HashMap<>();
        if (spaces.isEmpty()) {
            return roles;
        }
        List<Long> spaceIds = spaces.stream().map(DocSpace::getId).toList();
        memberMapper.selectList(Wrappers.<DocSpaceMember>lambdaQuery()
                        .eq(DocSpaceMember::getUserId, userId)
                        .in(DocSpaceMember::getSpaceId, spaceIds))
                .forEach(member -> roles.put(member.getSpaceId(), member.getMemberRole()));
        return roles;
    }

    /**
     * 统计空间启用成员数量。
     *
     * @param spaceId 空间ID
     * @return 成员数量
     */
    private long countMembers(Long spaceId) {
        return memberMapper.selectCount(Wrappers.<DocSpaceMember>lambdaQuery()
                .eq(DocSpaceMember::getSpaceId, spaceId));
    }

    /**
     * 查询空间启用成员列表。
     *
     * @param spaceId 空间ID
     * @return 成员视图列表
     */
    private List<DocMemberVO> activeMembers(Long spaceId) {
        return memberMapper.selectList(Wrappers.<DocSpaceMember>lambdaQuery()
                        .eq(DocSpaceMember::getSpaceId, spaceId)
                        .orderByAsc(DocSpaceMember::getCreateTime))
                .stream()
                .map(this::toMemberVO)
                .toList();
    }

    /**
     * 按空间与用户查询启用成员。
     *
     * @param spaceId 空间ID
     * @param userId  用户ID
     * @return 成员视图；不存在返回 {@code null}
     */
    private DocMemberVO findMember(Long spaceId, Long userId) {
        DocSpaceMember member = memberMapper.selectOne(Wrappers.<DocSpaceMember>lambdaQuery()
                .eq(DocSpaceMember::getSpaceId, spaceId)
                .eq(DocSpaceMember::getUserId, userId));
        return member == null ? null : toMemberVO(member);
    }

    /**
     * 添加或复用成员行。
     *
     * @param tenantId 租户ID
     * @param spaceId  空间ID
     * @param userId   目标用户ID
     * @param role     目标角色
     */
    private void upsertMember(Long tenantId, Long spaceId, Long userId, DocMemberRole role) {
        DocSpaceMember existing = memberMapper.selectAny(tenantId, spaceId, userId);
        if (existing == null) {
            DocSpaceMember member = new DocSpaceMember();
            member.setSpaceId(spaceId);
            member.setUserId(userId);
            member.setMemberRole(role.name());
            memberMapper.insert(member);
            return;
        }
        if (DocMemberRole.OWNER == parseRole(existing.getMemberRole())) {
            return;
        }
        memberMapper.reactivate(tenantId, existing.getId(), role.name());
    }

    /**
     * 写入创建者的 OWNER 成员记录。
     *
     * @param spaceId 空间ID
     * @param userId  用户ID
     */
    private void addOwnerMember(Long spaceId, Long userId) {
        DocSpaceMember member = new DocSpaceMember();
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setMemberRole(DocMemberRole.OWNER.name());
        memberMapper.insert(member);
    }

    /**
     * 转让空间所有权：更新空间 owner_id 与原 OWNER 角色。
     *
     * @param spaceId   空间ID
     * @param actorRole 操作者角色
     * @param member    新所有者成员行
     */
    private void transferOwnership(Long spaceId, DocMemberRole actorRole, DocSpaceMember member) {
        if (actorRole != DocMemberRole.OWNER) {
            throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
        }
        DocSpace space = accessGuard.requireSpace(spaceId);
        if (!space.getOwnerId().equals(member.getUserId())) {
            DocSpaceMember previous = memberMapper.selectOne(Wrappers.<DocSpaceMember>lambdaQuery()
                    .eq(DocSpaceMember::getSpaceId, spaceId)
                    .eq(DocSpaceMember::getUserId, space.getOwnerId()));
            if (previous != null) {
                previous.setMemberRole(DocMemberRole.ADMIN.name());
                memberMapper.updateById(previous);
            }
            space.setOwnerId(member.getUserId());
            spaceMapper.updateById(space);
        }
        member.setMemberRole(DocMemberRole.OWNER.name());
    }

    /**
     * 校验并设置普通角色变更。
     *
     * @param actorRole 操作者角色
     * @param member    目标成员
     * @param current   当前角色
     * @param target    目标角色
     * @param userId    操作者用户ID
     */
    private void changeRole(DocMemberRole actorRole, DocSpaceMember member, DocMemberRole current,
                            DocMemberRole target, Long userId) {
        if (current == DocMemberRole.OWNER) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.space.owner-transfer-unsupported", "所有者转让请通过授予 OWNER 完成");
        }
        if (current == DocMemberRole.ADMIN && actorRole != DocMemberRole.OWNER
                && !member.getUserId().equals(userId)) {
            throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
        }
        member.setMemberRole(target.name());
    }

    /**
     * 读取并校验成员行属于目标空间。
     *
     * @param spaceId  空间ID
     * @param memberId 成员记录ID
     * @return 成员行
     */
    private DocSpaceMember requireMemberRow(Long spaceId, Long memberId) {
        DocSpaceMember member = memberMapper.selectById(memberId);
        if (member == null || !spaceId.equals(member.getSpaceId())) {
            throw DocErrorCode.exception(DocErrorCode.SPACE_ACCESS_DENIED);
        }
        return member;
    }

    /**
     * 解析空间编码；留空时生成随机编码。
     *
     * @param raw 原始编码
     * @return 租户内未占用的编码
     */
    private String resolveKey(String raw) {
        if (StringUtils.hasText(raw)) {
            String key = raw.trim().toLowerCase(Locale.ROOT);
            Long tenantId = TenantContext.requireId();
            if (spaceMapper.countKeyIncludeDeleted(tenantId, key) > 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "doc.space.key-duplicated", "空间编码已存在: " + key, key);
            }
            return key;
        }
        Long tenantId = TenantContext.requireId();
        for (int i = 0; i < KEY_RETRY; i++) {
            String candidate = KEY_PREFIX + UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, KEY_RANDOM_LENGTH);
            if (spaceMapper.countKeyIncludeDeleted(tenantId, candidate) == 0) {
                return candidate;
            }
        }
        return KEY_PREFIX + IdWorker.getId();
    }

    /**
     * 规范化可见性，非法值回落 MEMBER。
     *
     * @param raw 原始值
     * @return 可见性
     */
    private String normalizeVisibility(String raw) {
        if (StringUtils.hasText(raw) && DocVisibility.PUBLIC.name().equalsIgnoreCase(raw.trim())) {
            return DocVisibility.PUBLIC.name();
        }
        return DEFAULT_VISIBILITY;
    }

    /**
     * 解析角色文本。
     *
     * @param raw 角色文本
     * @return 角色；非法值返回 {@code null}
     */
    private DocMemberRole parseRole(String raw) {
        try {
            return DocMemberRole.valueOf(raw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 查询角色层级，用于成员列表排序。
     *
     * @param role 角色文本
     * @return 层级数值
     */
    private int roleRank(String role) {
        DocMemberRole parsed = parseRole(role);
        if (parsed == null) {
            return 0;
        }
        switch (parsed) {
            case OWNER:
                return 4;
            case ADMIN:
                return 3;
            case EDITOR:
                return 2;
            default:
                return 1;
        }
    }

    /**
     * 角色转文本。
     *
     * @param role 角色
     * @return 角色文本；null 返回 {@code null}
     */
    private String roleName(DocMemberRole role) {
        return role == null ? null : role.name();
    }

    /**
     * 实体转空间视图。
     *
     * @param space       空间
     * @param memberRole  当前用户角色
     * @param memberCount 成员数量
     * @return 空间视图
     */
    private DocSpaceVO toVO(DocSpace space, String memberRole, Long memberCount) {
        DocSpaceVO vo = new DocSpaceVO();
        vo.setId(space.getId());
        vo.setSpaceKey(space.getSpaceKey());
        vo.setName(space.getName());
        vo.setDescription(space.getDescription());
        vo.setIcon(space.getIcon());
        vo.setVisibility(space.getVisibility());
        vo.setOwnerId(space.getOwnerId());
        vo.setStatus(space.getStatus());
        vo.setMemberRole(memberRole);
        vo.setMemberCount(memberCount);
        vo.setCreateTime(space.getCreateTime());
        vo.setUpdateTime(space.getUpdateTime());
        return vo;
    }

    /**
     * 实体转成员视图。
     *
     * @param member 成员
     * @return 成员视图
     */
    private DocMemberVO toMemberVO(DocSpaceMember member) {
        DocMemberVO vo = new DocMemberVO();
        vo.setId(member.getId());
        vo.setSpaceId(member.getSpaceId());
        vo.setUserId(member.getUserId());
        vo.setMemberRole(member.getMemberRole());
        vo.setCreateTime(member.getCreateTime());
        return vo;
    }
}
