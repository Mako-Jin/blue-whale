package com.bluewhale.crm.service.application.view;

import com.bluewhale.crm.service.domain.model.KbRef;
import lombok.Data;

/**
 * 知识引用视图。
 *
 * @author BlueWhale
 */
@Data
public class KbRefView {

    /** 主键 */
    private Long id;

    /** FAQ 标题 */
    private String title;

    /** 分类 */
    private String category;

    /** 产品标签 */
    private String productTags;

    /** 故障标签 */
    private String faultTags;

    /** 可见范围 */
    private String visibleScope;

    /** whale-doc 文档 ID */
    private Long docId;

    /** 文档版本 */
    private Integer docVersion;

    /** 文档是否可用 */
    private Boolean docAvailable;

    /** 命中次数 */
    private Integer hitCount;

    /** 状态 */
    private String status;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /**
     * 领域对象转视图。
     *
     * @param ref 知识引用
     * @return 视图
     */
    public static KbRefView from(KbRef ref) {
        KbRefView view = new KbRefView();
        view.setId(ref.getId());
        view.setTitle(ref.getTitle());
        view.setCategory(ref.getCategory());
        view.setProductTags(ref.getProductTags());
        view.setFaultTags(ref.getFaultTags());
        view.setVisibleScope(ref.getVisibleScope());
        view.setDocId(ref.getDocId());
        view.setDocVersion(ref.getDocVersion());
        view.setDocAvailable(Boolean.TRUE.equals(ref.getDocAvailable()));
        view.setHitCount(ref.getHitCount() == null ? 0 : ref.getHitCount());
        view.setStatus(ref.getStatus());
        view.setOwnerId(ref.getOwnerId());
        view.setDeptId(ref.getDeptId());
        return view;
    }
}
