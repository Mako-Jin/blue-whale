package com.bluewhale.scene.application.view;

import lombok.Data;

/**
 * 布局快照视图（管理端回滚比对用）。
 *
 * @author BlueWhale
 */
@Data
public class LayoutSnapshotView {

    /** 版本号 */
    private Integer version;

    /** SHA-256 校验和 */
    private String checksum;

    /** 布局 JSON */
    private String layoutJson;
}
