package com.bluewhale.sts.auth.resource;

import com.bluewhale.sts.auth.authz.RequiresPermission;
import com.bluewhale.sts.auth.common.AuthAuditActions;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.auth.common.AuthResources;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.basic.model.R;
import com.bluewhale.sts.components.audit.AuditContext;
import com.bluewhale.sts.components.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源接口 —— 权限资源（菜单 / 页面 / 按钮 / 接口 / 数据）目录的增删改查。
 *
 * <p><b>它保护的是"权限本身"。</b>这个目录里的每一行都是一个权限码，
 * 被 {@code @RequiresPermission} 与角色授权关系引用着 —— 于是本接口的写操作
 * 能改变**其它接口**的准入判定。这是本仓唯一一个有这种间接影响力的管理接口，
 * 因此三个写方法全部标了 {@link Audited}，让"谁改过权限配置"可查。
 *
 * <p><b>为什么需要它（在它之前权限只能靠改库配置）：</b>往 {@code bw_auth_resource}
 * 加一条以前只能走种子数据或直接写 SQL，而资源标识是**长期契约**、有严格的形状约束
 * （{@code 域:资源:动作} 三段小写）。靠人肉记住这个格式并手写 SQL，正是
 * {@code AuthTaxonomyTest} 想拦住的那类错误 —— 只不过测试只看得见代码里的常量，
 * 看不见库里被手写进去的行。有了这个接口，格式约束由 {@code ResourceSaveRequest}
 * 上的正则承担，并且它会进契约、前端能在提交前就拦下一半。
 *
 * <p><b>它是"菜单权限"的前置。</b>要让基座按权限过滤导航，{@code bw_auth_resource}
 * 里必须先真的有 {@code MENU} / {@code PAGE} 类型的行（现在种子数据把它们一律建成了
 * {@code API}）。登记这些行的入口就是本接口。
 */
@RestController
@RequestMapping("/api/auth/resources")
@Tag(name = "资源", description = "权限资源（菜单 / 页面 / 按钮 / 接口 / 数据）目录的增删改查")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    @RequiresPermission(AuthPermissions.RESOURCE_READ)
    @Operation(summary = "分页查询资源", description = "平铺列表。要层级用 /tree")
    public R<PageResult<ResourceVO>> page(@ParameterObject ResourceQuery query) {
        return R.ok(resourceService.page(query));
    }

    /**
     * 路径放在 {@code /{id}} 之前没有技术上的必要（Spring 的映射按"字面量优先于变量"匹配），
     * 但读代码时"静态路径在前"能省掉一次"这俩会不会冲突"的停顿。
     */
    @GetMapping("/tree")
    @RequiresPermission(AuthPermissions.RESOURCE_READ)
    @Operation(summary = "查询资源树",
            description = "按 parentCode 组装，全量返回。孤儿节点与成环节点按根返回，不丢弃")
    public R<List<ResourceTreeNode>> tree() {
        return R.ok(resourceService.tree());
    }

    @GetMapping("/{id}")
    @RequiresPermission(AuthPermissions.RESOURCE_READ)
    @Operation(summary = "查询资源详情")
    public R<ResourceVO> detail(@PathVariable @Parameter(description = "资源主键") Long id) {
        return R.ok(resourceService.detail(id));
    }

    @PostMapping
    @RequiresPermission(AuthPermissions.RESOURCE_CREATE)
    @Audited(action = AuthAuditActions.RESOURCE_CREATE, resource = AuthResources.RESOURCE)
    @Operation(summary = "新增资源",
            description = "资源标识须为「域:资源:动作」三段小写；若该标识曾被删除，则恢复原记录而非新建")
    public R<ResourceVO> create(@Valid @RequestBody ResourceSaveRequest request) {
        ResourceVO created = resourceService.create(request);
        AuditContext.target(created.getId());
        return R.ok(created);
    }

    @PutMapping("/{id}")
    @RequiresPermission(AuthPermissions.RESOURCE_UPDATE)
    @Audited(action = AuthAuditActions.RESOURCE_UPDATE, resource = AuthResources.RESOURCE)
    @Operation(summary = "修改资源", description = "**资源标识不可修改**；父资源不能是自身或自身的下级")
    public R<ResourceVO> update(@PathVariable @Parameter(description = "资源主键") Long id,
                                @Valid @RequestBody ResourceSaveRequest request) {
        ResourceVO updated = resourceService.update(id, request);
        AuditContext.target(id);
        return R.ok(updated);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(AuthPermissions.RESOURCE_DELETE)
    @Audited(action = AuthAuditActions.RESOURCE_DELETE, resource = AuthResources.RESOURCE)
    @Operation(summary = "删除资源",
            description = "逻辑删除。有子资源（40913）或已被角色引用（40915）时拒绝")
    public R<Void> delete(@PathVariable @Parameter(description = "资源主键") Long id) {
        resourceService.delete(id);
        AuditContext.target(id);
        return R.ok();
    }

}
