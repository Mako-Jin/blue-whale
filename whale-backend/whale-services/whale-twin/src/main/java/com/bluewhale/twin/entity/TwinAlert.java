package com.bluewhale.twin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 孪生阈值告警实体，对应表 {@code twin_alert}。
 *
 * <p>运行时级别变化时落库：非 normal 级别插入新行（resolved=0），恢复到
 * normal 时回填未恢复行的 {@code resolved} 与 {@code resolve_time}（恢复点
 * 体现为恢复时间，不额外插行）。同绑定同状态键的未恢复行至多一条。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("twin_alert")
public class TwinAlert extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（行级隔离） */
    private Long tenantId;

    /** 所属场景ID（twin_scene.id） */
    private Long sceneId;

    /** 孪生实体ID（twin_entity.id，可空） */
    private Long entityId;

    /** 实体编码（冗余，实体删除后仍可读） */
    private String entityCode;

    /** 绑定ID（twin_binding.id） */
    private Long bindingId;

    /** 实体状态键 */
    private String stateKey;

    /** 触发取值（字符串化） */
    private String alertValue;

    /** 上一级别（首评为空） */
    private String previousLevel;

    /** 当前级别：normal / warn / critical */
    private String level;

    /** 参与判定的阈值（JSON 数组字符串） */
    private String thresholdsJson;

    /** 是否已恢复：0 未恢复 / 1 已恢复 */
    private Integer resolved;

    /** 恢复时间（恢复到 normal 时回填） */
    private LocalDateTime resolveTime;
}
