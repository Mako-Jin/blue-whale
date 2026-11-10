package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 孪生实体视图对象（含子节点，构成场景树）。
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityVO {

    /** 实体ID（字符串化） */
    private String id;

    /** 所属场景ID（字符串化） */
    private String sceneId;

    /** 实体编码 */
    private String entityCode;

    /** 实体名称 */
    private String name;

    /** 父节点ID（字符串化，根节点为空） */
    private String parentId;

    /** 模型资产文件ID（字符串化，可空） */
    private String modelFileId;

    /** 变换 JSON 字符串（原样回显，可空） */
    private String transformJson;

    /** 扩展配置 JSON 字符串（原样回显，可空） */
    private String extraConfig;

    /** 同级排序号 */
    private Integer sortOrder;

    /** 子节点（按 sortOrder 升序；平铺查询时为空列表） */
    @Builder.Default
    private List<EntityVO> children = new ArrayList<>();
}
