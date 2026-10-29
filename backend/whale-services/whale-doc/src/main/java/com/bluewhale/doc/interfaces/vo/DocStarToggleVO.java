package com.bluewhale.doc.interfaces.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 收藏切换结果。
 *
 * @author BlueWhale
 */
@Data
public class DocStarToggleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 切换后是否已收藏 */
    private boolean starred;
}
