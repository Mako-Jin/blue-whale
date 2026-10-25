package com.bluewhale.pm.application.assembler;

import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmProjectMember;
import com.bluewhale.pm.infrastructure.entity.PmStatus;
import com.bluewhale.pm.infrastructure.entity.PmTransition;
import com.bluewhale.pm.interfaces.vo.ProjectMemberVO;
import com.bluewhale.pm.interfaces.vo.ProjectVO;
import com.bluewhale.pm.interfaces.vo.StatusVO;
import com.bluewhale.pm.interfaces.vo.TransitionVO;

/**
 * PM 实体与视图对象转换器（ID 一律转为字符串）
 *
 * @author BlueWhale
 */
public final class PmAssembler {

    /** 布尔标记：是 */
    private static final int FLAG_TRUE = 1;

    /**
     * 工具类不允许实例化
     */
    private PmAssembler() {
    }

    /**
     * 项目实体转视图对象
     *
     * @param project 项目实体
     * @return 项目视图对象
     */
    public static ProjectVO toProjectVo(PmProject project) {
        ProjectVO vo = new ProjectVO();
        vo.setId(asString(project.getId()));
        vo.setKey(project.getProjectKey());
        vo.setName(project.getName());
        vo.setProjectType(project.getProjectType());
        vo.setVisibility(project.getVisibility());
        vo.setLeadUserId(asString(project.getLeadUserId()));
        vo.setDescription(project.getDescription());
        vo.setStatus(project.getStatus());
        vo.setCreateTime(project.getCreateTime());
        vo.setUpdateTime(project.getUpdateTime());
        return vo;
    }

    /**
     * 成员实体转视图对象
     *
     * @param member 成员实体
     * @return 成员视图对象
     */
    public static ProjectMemberVO toMemberVo(PmProjectMember member) {
        ProjectMemberVO vo = new ProjectMemberVO();
        vo.setId(asString(member.getId()));
        vo.setProjectId(asString(member.getProjectId()));
        vo.setUserId(asString(member.getUserId()));
        vo.setProjectRole(member.getProjectRole());
        vo.setJoinedTime(member.getJoinedTime());
        vo.setCreateTime(member.getCreateTime());
        return vo;
    }

    /**
     * 状态实体转视图对象
     *
     * @param status 状态实体
     * @return 状态视图对象
     */
    public static StatusVO toStatusVo(PmStatus status) {
        StatusVO vo = new StatusVO();
        vo.setId(asString(status.getId()));
        vo.setProjectId(asString(status.getProjectId()));
        vo.setCode(status.getCode());
        vo.setName(status.getName());
        vo.setCategory(status.getCategory());
        vo.setSortNo(status.getSortNo());
        vo.setIsDefault(Integer.valueOf(FLAG_TRUE).equals(status.getIsDefault()));
        vo.setCreateTime(status.getCreateTime());
        return vo;
    }

    /**
     * 流转实体转视图对象
     *
     * @param transition 流转实体
     * @return 流转视图对象
     */
    public static TransitionVO toTransitionVo(PmTransition transition) {
        TransitionVO vo = new TransitionVO();
        vo.setId(asString(transition.getId()));
        vo.setProjectId(asString(transition.getProjectId()));
        vo.setFromStatusId(asString(transition.getFromStatusId()));
        vo.setToStatusId(asString(transition.getToStatusId()));
        vo.setName(transition.getName());
        vo.setRequireComment(Integer.valueOf(FLAG_TRUE).equals(transition.getRequireComment()));
        vo.setRequirePermission(transition.getRequirePermission());
        vo.setCreateTime(transition.getCreateTime());
        return vo;
    }

    /**
     * 长整型转字符串，空值返回 {@code null}
     *
     * @param value 长整型值
     * @return 字符串；入参为空时返回 {@code null}
     */
    private static String asString(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
