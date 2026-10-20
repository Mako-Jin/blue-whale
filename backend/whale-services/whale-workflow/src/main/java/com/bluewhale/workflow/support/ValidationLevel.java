package com.bluewhale.workflow.support;

/**
 * 校验问题级别。
 *
 * @author BlueWhale
 */
public enum ValidationLevel {

    /** 错误：阻断（{@code valid=false}） */
    ERROR,

    /** 警告：不阻断（{@code valid=true}），如本轮「条件不参与选路」 */
    WARNING
}
