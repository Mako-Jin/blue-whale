package com.bluewhale.auth.infrastructure.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.application.port.GroupMemberEvaluator;
import com.bluewhale.auth.domain.aggregate.UserPermissionAttributeAggregate;
import com.bluewhale.auth.domain.model.vo.GroupFilters;
import com.bluewhale.auth.domain.repository.UserPermissionRepository;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态组成员求值适配器：实现应用层 {@link GroupMemberEvaluator} 端口（权限中心扩展 M3）。
 *
 * <p>按租户加载 {@code sys_user} 用户基础字段与 ABAC 属性（{@code sys_user_permission}），
 * 将 {@link GroupFilters} 条件在内存中过滤（租户内用户量级可接受）。字段口径：
 * {@code id/username/nickname/phone/email/deptId/status} 取用户 PO 基础字段，
 * 其余字段名去用户属性 map 查找；操作符 {@code eq/ne/in/like} 统一按字符串比较
 * （属性值落库为字符串，JSON 条件值可能为数字，字符串化后比对口径一致）。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class GroupMemberEvaluatorImpl implements GroupMemberEvaluator {

    /** 用户基础字段集合（命中时直接取 sys_user PO getter，不再查属性表） */
    private static final Set<String> BASE_FIELDS = Set.of(
            "id", "username", "nickname", "phone", "email", "deptId", "status");

    private final SysUserMapper userMapper;
    private final UserPermissionRepository userPermissionRepository;

    /**
     * 求值：返回规则命中的用户 ID 集合（去重）
     *
     * @param tenantId 租户 ID
     * @param filters 过滤规则
     * @return 命中用户 ID 列表；无命中时返回空列表
     */
    @Override
    public List<Long> evaluate(Long tenantId, GroupFilters filters) {
        if (tenantId == null || filters == null || filters.conditions().isEmpty()) {
            return List.of();
        }
        List<SysUserPo> users = userMapper.selectList(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getTenantId, tenantId));
        if (users.isEmpty()) {
            return List.of();
        }
        // 仅当存在非基础字段条件时才逐用户加载 ABAC 属性，避免无谓的 N 次查询
        Set<String> attributeFields = filters.conditions().stream()
                .map(GroupFilters.Condition::field)
                .filter(field -> !BASE_FIELDS.contains(field))
                .collect(Collectors.toSet());
        Map<Long, Map<String, String>> attributeIndex = attributeFields.isEmpty()
                ? Map.of() : loadAttributes(users);
        return users.stream()
                .filter(user -> matchesAll(user,
                        attributeIndex.getOrDefault(user.getId(), Map.of()), filters.conditions()))
                .map(SysUserPo::getId)
                .distinct()
                .toList();
    }

    /**
     * 批量加载用户 ABAC 属性索引：{@code userId → (attrKey → attrValue)}
     *
     * @param users 租户内用户列表
     * @return 属性索引（用户无属性时对应空 map）
     */
    private Map<Long, Map<String, String>> loadAttributes(List<SysUserPo> users) {
        Map<Long, Map<String, String>> index = new HashMap<>();
        for (SysUserPo user : users) {
            Map<String, String> attrs = new HashMap<>();
            for (UserPermissionAttributeAggregate attribute : userPermissionRepository.listByUserId(user.getId())) {
                attrs.put(attribute.getAttrKey(), attribute.getAttrValue());
            }
            index.put(user.getId(), attrs);
        }
        return index;
    }

    /**
     * 判断用户是否满足全部条件
     *
     * @param user 用户 PO
     * @param attributes 用户 ABAC 属性（attrKey → attrValue）
     * @param conditions 过滤条件列表
     * @return 全部满足返回 true
     */
    private boolean matchesAll(SysUserPo user, Map<String, String> attributes,
                               List<GroupFilters.Condition> conditions) {
        for (GroupFilters.Condition condition : conditions) {
            if (!matchesOne(user, attributes, condition)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断用户是否满足单条条件（字段先取基础字段，缺失时取属性 map）
     *
     * @param user 用户 PO
     * @param attributes 用户 ABAC 属性
     * @param condition 过滤条件
     * @return 满足返回 true
     */
    private boolean matchesOne(SysUserPo user, Map<String, String> attributes, GroupFilters.Condition condition) {
        Object actual = resolveField(user, attributes, condition.field());
        String op = condition.op() == null ? "" : condition.op().toLowerCase(Locale.ROOT);
        return switch (op) {
            case "eq" -> actual != null && Objects.equals(String.valueOf(actual), stringOf(condition.value()));
            case "ne" -> actual != null && !Objects.equals(String.valueOf(actual), stringOf(condition.value()));
            case "in" -> actual != null && matchesIn(actual, condition.value());
            case "like" -> actual != null && String.valueOf(actual).contains(stringOf(condition.value()));
            default -> false;
        };
    }

    /**
     * 解析字段取值：基础字段取 PO getter，否则取属性 map（缺失返回 {@code null}）
     *
     * @param user 用户 PO
     * @param attributes 用户 ABAC 属性
     * @param field 字段名
     * @return 字段取值（未取到返回 {@code null}）
     */
    private Object resolveField(SysUserPo user, Map<String, String> attributes, String field) {
        if (field == null) {
            return null;
        }
        return switch (field) {
            case "id" -> user.getId();
            case "username" -> user.getUsername();
            case "nickname" -> user.getNickname();
            case "phone" -> user.getPhone();
            case "email" -> user.getEmail();
            case "deptId" -> user.getDeptId();
            case "status" -> user.getStatus();
            default -> attributes.get(field);
        };
    }

    /**
     * in 操作：条件值为集合时按包含判断，否则退化为相等判断
     *
     * @param actual 用户字段取值
     * @param expected 条件比较值
     * @return 命中返回 true
     */
    private boolean matchesIn(Object actual, Object expected) {
        if (expected instanceof Collection<?> candidates) {
            String actualText = String.valueOf(actual);
            return candidates.stream().anyMatch(candidate -> stringOf(candidate).equals(actualText));
        }
        return Objects.equals(String.valueOf(actual), stringOf(expected));
    }

    /**
     * 比较值统一字符串化（null 视为空串，保持比对口径确定）
     *
     * @param value 条件比较值
     * @return 字符串形式
     */
    private String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
