package com.bluewhale.twin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 接收人选择器用的部门树节点（Wave 6.12）：来自 auth 内网 {@code GET /dept/internal/tree}。
 *
 * <p>只保留选人需要的三个字段（ID / 名称 / 子节点）：部门负责人、排序、创建时间等
 * 与「按部门筛人」无关的字段一律不转发，避免孪生侧被动扩散认证域的字段口径。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwinNotifyDeptVO {

    /** 部门ID（字符串化，避免前端长整型精度丢失） */
    private String id;

    /** 部门名称 */
    private String name;

    /** 子部门（无子节点时为 null） */
    private List<TwinNotifyDeptVO> children;
}
