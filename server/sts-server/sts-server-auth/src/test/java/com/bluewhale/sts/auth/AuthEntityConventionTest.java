package com.bluewhale.sts.auth;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实体与表的约定测试 —— 纯反射，不启动 Spring、不连数据库。
 *
 * <p><b>为什么需要它：</b>参照实现 {@code SystemTerminator} 里
 * {@code TenantInfoPo} 标着 {@code auth_tbl_tenant_info}，而建表脚本建的是
 * {@code auth_tbl_tenant} —— 两处对不上，所有租户查询直接 SQL 报错。
 * 这种"类名、注解、脚本三者不一致"的问题，只有在真实发起查询时才暴露，
 * 而单元测试通常不碰数据库。
 *
 * <p>这里把三条约定钉死成可执行的断言，让它们在**编译后的第一次测试**就被发现：
 * <ol>
 *   <li>每张表名都以 {@code bw_auth_} 开头，且**全模块唯一**；</li>
 *   <li>不出现 {@code @TableField("改过的列名")} —— 本仓的硬规则是属性名与列名同构，
 *       一旦用注解改名，MyBatis-Plus 会自动补 {@code 列名 AS 属性名} 别名，
 *       别名若撞上 MySQL 保留字就会只在 MySQL 上炸（见 {@code docs/decisions/0009} 第 3 条）；</li>
 *   <li>逻辑删除字段必须由 {@link TableLogic} 声明，语义统一为 0 正常 / 1 已删除。
 *       参照实现在这一列上自相矛盾（不同表 0/1 含义相反），是需要修掉的缺陷。</li>
 * </ol>
 */
class AuthEntityConventionTest {

    private static final String BASE_PACKAGE = "com.bluewhale.sts.auth";
    private static final String TABLE_PREFIX = "bw_auth_";

    @Test
    @DisplayName("所有实体表名以 bw_auth_ 开头且唯一")
    void tableNamesShouldBePrefixedAndUnique() {
        List<Class<?>> entities = entitiesWithTableName();
        assertFalse(entities.isEmpty(), "未扫描到任何实体，包路径或扫描器可能有问题");

        Set<String> seen = new LinkedHashSet<>();
        for (Class<?> entity : entities) {
            String tableName = entity.getAnnotation(TableName.class).value();
            assertTrue(tableName.startsWith(TABLE_PREFIX),
                    entity.getSimpleName() + " 的表名 " + tableName + " 未以 " + TABLE_PREFIX + " 开头");
            assertTrue(seen.add(tableName),
                    "表名重复：" + tableName + "（" + entity.getSimpleName() + "）—— "
                            + "两个实体映射到同一张表，至少有一个是错的");
        }
    }

    @Test
    @DisplayName("不出现用 @TableField 改列名的写法")
    void shouldNotRenameColumnsByAnnotation() {
        for (Class<?> entity : entitiesWithTableName()) {
            for (Field field : entity.getDeclaredFields()) {
                TableField annotation = field.getAnnotation(TableField.class);
                if (annotation == null) {
                    continue;
                }
                assertTrue(annotation.value().isBlank(),
                        entity.getSimpleName() + "." + field.getName()
                                + " 用 @TableField(\"" + annotation.value() + "\") 改了列名。"
                                + "本仓约定属性名与列名同构，改名会触发 MyBatis-Plus 的自动别名，"
                                + "别名撞保留字时只在 MySQL 上暴露（docs/decisions/0009 第 3 条）");
            }
        }
    }

    @Test
    @DisplayName("逻辑删除字段语义统一：@TableLogic + 列名 deleted")
    void logicalDeleteShouldBeUniform() {
        List<String> violations = new ArrayList<>();
        for (Class<?> entity : entitiesWithTableName()) {
            for (Field field : entity.getDeclaredFields()) {
                boolean isDeletedField = field.getName().equals("deleted")
                        || field.getName().equalsIgnoreCase("isDeleted");
                if (!isDeletedField) {
                    continue;
                }
                if (field.getAnnotation(TableLogic.class) == null) {
                    violations.add(entity.getSimpleName() + "." + field.getName() + " 缺少 @TableLogic");
                }
                if (!field.getName().equals("deleted")) {
                    violations.add(entity.getSimpleName() + "." + field.getName()
                            + " 字段名应为 deleted（全仓统一），而不是 " + field.getName());
                }
            }
        }
        // 反向断言：逻辑删除的列名不应出现 is_deleted/isDeleted 这类分叉写法
        assertEquals(List.of(), violations,
                "逻辑删除字段约定被破坏：" + violations);
    }

    private static List<Class<?>> entitiesWithTableName() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));

        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                result.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("无法加载 " + definition.getBeanClassName(), ex);
            }
        }
        return result;
    }

}
