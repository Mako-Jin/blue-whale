package com.bluewhale.auth.interfaces;

/**
 * 接口层：对外契约（{@code api/*Api} 只声明路径、版本与映射注解；{@code dto} 承载请求 / 响应），
 * 不含任何业务实现与领域规则（DDD 四层，AUTH-14）。
 *
 * <p>实现类位于 starter 层的 {@code starter/controller}，本层不得依赖 application / domain /
 * infrastructure / starter（ArchUnit 守护）。</p>
 *
 * @author BlueWhale
 */
