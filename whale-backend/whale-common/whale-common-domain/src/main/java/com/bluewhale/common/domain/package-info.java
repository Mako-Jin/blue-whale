/**
 * 通用领域基座：DDD 内核抽象。
 *
 * <p>仅包含与框架无关的领域建模要素：实体与聚合、值对象与标识、领域事件、仓储端口与领域异常。
 * 领域层可依赖本模块，本模块不反向依赖任何业务模块或 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
package com.bluewhale.common.domain;
