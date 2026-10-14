package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.tenant.TenantEntity;
import com.bluewhale.sts.auth.tenant.TenantMapper;
import com.bluewhale.sts.auth.usergroup.UserGroupEntity;
import com.bluewhale.sts.auth.usergroup.UserGroupMapper;
import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.model.R;
import com.bluewhale.sts.common.web.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 唯一约束冲突的端到端映射测试。
 *
 * <p><b>它验证两件通常被想当然的事，其中第一件此前没人验证过：</b>
 * <ol>
 *   <li>直接撞唯一索引时，抛出来的**确实是 Spring 的 {@link DuplicateKeyException}**。
 *       这依赖 MyBatis-Spring 的异常翻译链（{@code SQLException} → {@code DataAccessException}），
 *       而它是**按数据库方言匹配错误码**的（H2 的 23505 / MySQL 的 1062 各自在
 *       spring-tx 的错误码表里）。如果换一套数据库、或换了连接池、或翻译链被改动，
 *       抛出来的可能是 {@code PersistenceException} 一类的裸异常 ——
 *       那时 {@code GlobalExceptionHandler} 里那个处理器**永远匹配不上**，
 *       而症状与修之前一模一样（50000），没有任何提示。这条断言就是防这个的；</li>
 *   <li>那个处理器把冲突翻译成 <b>409</b>（{@link ResultCode#CONFLICT}）而不是兜底的 50000。</li>
 * </ol>
 *
 * <p><b>为什么直接调 Mapper 而不是走 HTTP：</b>本仓所有"先查后判重"的创建路径都
 * 已经把撞键的可能性挡住了，也就是说**没有任何一个 HTTP 请求能稳定地触发这条异常**
 * （并发下的那一次除外）。这不是缺陷，而是设计 —— 服务层的判重是第一道防线，
 * 本处理器是并发下的第二道。
 *
 * <p><b>上面这句话在它被写下的那一天并不成立。</b>租户编码与用户登录名这两列
 * 当时判重看不见软删行，"删掉一个租户、再用同一个编码建一个"这样的普通请求
 * 就能稳定地把兜底码撞出来（ADR-0056）。它们补上之后这句话才成为事实，
 * 并由 {@code UniqueCodeReuseApiTest} 看着 —— 那里钉的不是"本处理器可达"，
 * 恰恰是"它不该可达"。
 *
 * <p>正因为它（在正常路径上）不可达，才更需要单独把它测出来，否则它就是一段没人跑过的代码。
 *
 * <p>用用户组的编码做试验对象，是因为它与全仓其余五个唯一编码列的形状完全相同
 * （{@code (tenant_id, ...code)} 且不含 {@code deleted}）—— 这个形状是六处判重
 * 各自必须显式回答一次的成因，清单见 {@code server/README.md} 的
 * 「唯一编码列与它们的软删语义」，理由见 {@code RoleService.create} 的长注释。
 */
@SpringBootTest
class DuplicateKeyAdviceTest {

    @Autowired
    private UserGroupMapper userGroupMapper;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private AuthProperties properties;

    @Test
    @DisplayName("撞唯一键抛的是 DuplicateKeyException，并被翻译成 409 而不是 50000")
    void shouldTranslateDuplicateKeyToConflict() {
        TenantEntity tenant = tenantMapper.selectOne(Wrappers.<TenantEntity>lambdaQuery()
                .eq(TenantEntity::getTenantCode, properties.getSeedAdmin().getTenantCode()));
        assertNotNull(tenant, "种子租户应当存在（见 DefaultAdminSeeder）");

        String code = "E2EDUP" + Long.toString(System.nanoTime(), 36).toUpperCase();
        UserGroupEntity first = group(tenant.getId(), code);
        userGroupMapper.insert(first);

        DuplicateKeyException conflict = assertThrows(DuplicateKeyException.class,
                () -> userGroupMapper.insert(group(tenant.getId(), code)),
                "唯一键冲突应当被翻译成 Spring 的 DuplicateKeyException —— "
                        + "换成别的类型，全局异常处理器就永远匹配不上，症状是 50000");

        R<Void> response = new GlobalExceptionHandler()
                .handleDuplicateKeyException(conflict, new MockHttpServletRequest());

        assertEquals(ResultCode.CONFLICT.getCode(), response.getCode(),
                "唯一约束冲突必须归到 409 段：它说的是「这条记录已经有人用了」，责任方是调用方，"
                        + "落到 50000 会让人去查服务端日志、让监控把它算进可用性");
        assertNotNull(response.getMessage(), "必须带一句可展示的提示，而不是空消息");

        // 收尾：这条记录是测试自己造的，删掉它，避免别的用例按关键字搜到时多出一行
        userGroupMapper.deleteById(first.getId());
    }

    private static UserGroupEntity group(Long tenantId, String code) {
        UserGroupEntity entity = new UserGroupEntity();
        entity.setTenantId(tenantId);
        entity.setGroupCode(code);
        entity.setGroupName("唯一键测试组");
        return entity;
    }

}
