package com.bluewhale.scene.application.view;

import lombok.Data;

/**
 * 导航入口视图。
 *
 * @author BlueWhale
 */
@Data
public class NavigationEntryView {

    /** 入口类型 */
    private String entryType;

    /** 目标地址 */
    private String targetPath;
}
