package com.bluewhale.pdm.document.support;

/**
 * ACL 有效权限判定结果
 *
 * <p>{@code ALLOW} 显式放行，{@code DENY} 显式拒绝且优先，
 * {@code ABSTAIN} 表示无命中规则，由调用方回落既有规则。</p>
 *
 * @author BlueWhale
 */
public enum PdmDocAclDecision {

    /** 显式允许 */
    ALLOW,

    /** 显式拒绝（优先于允许） */
    DENY,

    /** 无命中规则 */
    ABSTAIN
}
