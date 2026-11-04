package com.bluewhale.scene.domain.model;

import lombok.Data;

import java.util.Set;

/**
 * 场景 / 节点可见性规则。
 *
 * <p>{@code nodeId = 0} 表示场景级规则；{@code DENY} 优先于 {@code ALLOW}。
 * 角色、用户主体由 HTTP 身份头解析；部门主体首期不参与判定（决策 D3）。</p>
 *
 * @author BlueWhale
 */
@Data
public class SceneVisibility {

    /** 场景级规则使用的节点 ID 占位值 */
    public static final long SCENE_LEVEL_NODE_ID = 0L;

    /** 主键 */
    private Long id;

    /** 所属场景 ID */
    private Long sceneId;

    /** 节点 ID；0 表示场景级规则 */
    private Long nodeId;

    /** 主体类型：ALL / ROLE / DEPT / USER */
    private String subjectType;

    /** 主体标识：角色码 / 部门 ID / 用户 ID；ALL 时为空 */
    private String subjectId;

    /** 效果：ALLOW / DENY */
    private String effect;

    /**
     * 判断规则是否命中当前访问者。
     *
     * @param roles 当前用户角色码集合
     * @param userId 当前用户 ID（未登录可为空）
     * @param deptId 当前用户部门 ID（首期为空）
     * @return 命中返回 {@code true}
     */
    public boolean matches(Set<String> roles, Long userId, String deptId) {
        if (VisibilitySubjectType.ALL.equals(subjectType)) {
            return true;
        }
        if (VisibilitySubjectType.ROLE.equals(subjectType)) {
            return subjectId != null && roles.contains(subjectId);
        }
        if (VisibilitySubjectType.USER.equals(subjectType)) {
            return userId != null && subjectId != null && subjectId.equals(String.valueOf(userId));
        }
        if (VisibilitySubjectType.DEPT.equals(subjectType)) {
            return deptId != null && subjectId != null && subjectId.equals(deptId);
        }
        return false;
    }

    /**
     * 判断是否为场景级规则。
     *
     * @return 场景级返回 {@code true}
     */
    public boolean isSceneLevel() {
        return nodeId == null || SCENE_LEVEL_NODE_ID == nodeId;
    }
}
