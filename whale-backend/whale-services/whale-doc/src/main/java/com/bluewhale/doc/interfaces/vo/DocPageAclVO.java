package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 页面级权限视图。
 *
 * @author BlueWhale
 */
@Data
public class DocPageAclVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页面ID */
    private Long pageId;

    /** 是否启用页面级权限 */
    private boolean restricted;

    /** 授权列表 */
    private List<DocAclItemVO> items = new ArrayList<>();
}
