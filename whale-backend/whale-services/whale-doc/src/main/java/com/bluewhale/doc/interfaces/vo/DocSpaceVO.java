package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文档空间视图。
 *
 * @author BlueWhale
 */
@Data
public class DocSpaceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 空间ID */
    private Long id;

    /** 空间编码 */
    private String spaceKey;

    /** 空间名称 */
    private String name;

    /** 空间描述 */
    private String description;

    /** 图标标识 */
    private String icon;

    /** 可见性 */
    private String visibility;

    /** 所有者ID */
    private Long ownerId;

    /** 状态：1 正常 / 0 归档 */
    private Integer status;

    /** 当前用户角色；非成员为 null */
    private String memberRole;

    /** 成员数量（详情接口返回） */
    private Long memberCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
