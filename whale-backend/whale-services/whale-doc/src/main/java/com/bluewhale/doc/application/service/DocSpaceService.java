package com.bluewhale.doc.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.doc.interfaces.dto.DocMemberAddRequest;
import com.bluewhale.doc.interfaces.dto.DocMemberRoleRequest;
import com.bluewhale.doc.interfaces.dto.DocSpaceQueryRequest;
import com.bluewhale.doc.interfaces.dto.DocSpaceSaveRequest;
import com.bluewhale.doc.interfaces.vo.DocMemberVO;
import com.bluewhale.doc.interfaces.vo.DocSpaceVO;

import java.util.List;

/**
 * 文档空间与成员应用服务。
 *
 * @author BlueWhale
 */
public interface DocSpaceService {

    /**
     * 分页查询我可访问的空间（公开空间 + 我加入的空间）。
     *
     * @param request 查询条件
     * @param userId  当前用户ID
     * @return 空间分页
     */
    PageResult<DocSpaceVO> page(DocSpaceQueryRequest request, Long userId);

    /**
     * 查询我加入或拥有的空间。
     *
     * @param userId 当前用户ID
     * @return 空间列表
     */
    List<DocSpaceVO> my(Long userId);

    /**
     * 查询空间详情（含我的角色与成员数量）。
     *
     * @param spaceId 空间ID
     * @param userId  当前用户ID
     * @return 空间详情
     */
    DocSpaceVO detail(Long spaceId, Long userId);

    /**
     * 创建空间，创建者自动成为 OWNER。
     *
     * @param request 空间信息
     * @param userId  当前用户ID
     * @return 新建空间
     */
    DocSpaceVO create(DocSpaceSaveRequest request, Long userId);

    /**
     * 更新空间名称、描述、图标与可见性。
     *
     * @param spaceId 空间ID
     * @param request 空间信息
     * @param userId  当前用户ID
     * @return 更新后的空间
     */
    DocSpaceVO update(Long spaceId, DocSpaceSaveRequest request, Long userId);

    /**
     * 归档空间（仅 OWNER）。
     *
     * @param spaceId 空间ID
     * @param userId  当前用户ID
     */
    void archive(Long spaceId, Long userId);

    /**
     * 查询空间成员。
     *
     * @param spaceId 空间ID
     * @param userId  当前用户ID
     * @return 成员列表
     */
    List<DocMemberVO> members(Long spaceId, Long userId);

    /**
     * 批量添加成员（已存在的成员按新角色更新，已移除成员复用原行）。
     *
     * @param spaceId 空间ID
     * @param request 成员与角色
     * @param userId  当前用户ID
     * @return 受影响的成员
     */
    List<DocMemberVO> addMembers(Long spaceId, DocMemberAddRequest request, Long userId);

    /**
     * 调整成员角色；授予 OWNER 视为所有权转让。
     *
     * @param spaceId  空间ID
     * @param memberId 成员记录ID
     * @param request  新角色
     * @param userId   当前用户ID
     * @return 更新后的成员
     */
    DocMemberVO updateMember(Long spaceId, Long memberId, DocMemberRoleRequest request, Long userId);

    /**
     * 移除成员（不可移除最后一名 OWNER）。
     *
     * @param spaceId  空间ID
     * @param memberId 成员记录ID
     * @param userId   当前用户ID
     */
    void removeMember(Long spaceId, Long memberId, Long userId);
}
