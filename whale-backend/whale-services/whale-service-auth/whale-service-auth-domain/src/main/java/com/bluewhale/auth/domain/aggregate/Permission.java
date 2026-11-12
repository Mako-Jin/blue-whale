package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.PermCode;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Permission 聚合根（对应 {@code sys_permission}，REQ-ARCH-001 / T-DDD-6）。
 *
 * <p><b>不变量</b>：① {@code permCode} 非空且格式合法（由 {@link PermCode} 值对象承载，
 * 其全局唯一性由仓储跨聚合校验）；② {@code permName} 非空；③ {@code parentId} 非空（{@code 0} 表示顶级）；
 * ④ {@code permType ∈ {SYSTEM, MENU, API, BUTTON, DATA, ATTR}}；⑤ {@code status ∈ {1 启用, 0 停用}}。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。写路径接入留待 AUTH-17。</p>
 *
 * @author BlueWhale
 */
public class Permission implements Entity<Long> {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;
    /** 顶级权限点的父 ID */
    public static final long ROOT_PARENT_ID = 0L;

    /** 合法权限类型集合 */
    private static final Set<String> PERM_TYPES = Set.of("SYSTEM", "MENU", "API", "BUTTON", "DATA", "ATTR");

    private final Long id;
    private Long parentId;
    private PermCode permCode;
    private String permName;
    private String permType;
    private String path;
    private String method;
    private int sortNo;
    private int status;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 权限点 ID（新建时为 {@code null}）
     * @param parentId 父权限点 ID（0 表示顶级）
     * @param permCode 权限码值对象
     * @param permName 权限名称
     * @param permType 权限类型（SYSTEM / MENU / API / BUTTON / DATA / ATTR）
     * @param path 接口路径
     * @param method 请求方法
     * @param sortNo 排序号
     * @param status 状态（1 启用 / 0 停用）
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private Permission(Long id, Long parentId, PermCode permCode, String permName, String permType,
                       String path, String method, int sortNo, int status,
                       LocalDateTime createTime, LocalDateTime updateTime) {
        if (parentId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "父权限点 ID 不能为空（0 表示顶级）");
        }
        if (permCode == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "权限码不能为空");
        }
        if (permName == null || permName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "权限名称不能为空");
        }
        if (permType == null || !PERM_TYPES.contains(permType)) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "权限类型非法，仅支持 SYSTEM / MENU / API / BUTTON / DATA / ATTR");
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.id = id;
        this.parentId = parentId;
        this.permCode = permCode;
        this.permName = permName;
        this.permType = permType;
        this.path = path;
        this.method = method;
        this.sortNo = sortNo;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 工厂方法：新建权限点（无 ID），{@code parentId}/{@code sortNo}/{@code status} 为空时取默认（0 / 0 / 启用）
     *
     * @param parentId 父权限点 ID，为空时按 0（顶级）处理
     * @param permCode 权限码值对象
     * @param permName 权限名称
     * @param permType 权限类型（SYSTEM / MENU / API / BUTTON / DATA / ATTR）
     * @param path 接口路径
     * @param method 请求方法
     * @param sortNo 排序号，为空时按 0 处理
     * @param status 状态，为空时按启用处理
     * @return 新建的权限点聚合根
     */
    public static Permission create(Long parentId, PermCode permCode, String permName, String permType,
                                    String path, String method, Integer sortNo, Integer status) {
        LocalDateTime now = LocalDateTime.now();
        return new Permission(null, parentId == null ? ROOT_PARENT_ID : parentId, permCode, permName, permType,
                path, method, sortNo == null ? 0 : sortNo, status == null ? STATUS_ENABLED : status, now, now);
    }

    /**
     * 重建方法：由持久化数据还原权限点聚合根
     *
     * @param id 权限点 ID
     * @param parentId 父权限点 ID，为空时按 0（顶级）处理
     * @param permCode 权限码值对象
     * @param permName 权限名称
     * @param permType 权限类型（SYSTEM / MENU / API / BUTTON / DATA / ATTR）
     * @param path 接口路径
     * @param method 请求方法
     * @param sortNo 排序号，为空时按 0 处理
     * @param status 状态，为空时按启用处理
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 还原的权限点聚合根
     */
    public static Permission reconstitute(Long id, Long parentId, PermCode permCode, String permName, String permType,
                                          String path, String method, Integer sortNo, Integer status,
                                          LocalDateTime createTime, LocalDateTime updateTime) {
        return new Permission(id, parentId == null ? ROOT_PARENT_ID : parentId, permCode, permName, permType,
                path, method, sortNo == null ? 0 : sortNo, status == null ? STATUS_ENABLED : status,
                createTime, updateTime);
    }

    /**
     * 行为：移动到新的父权限点（对应更新接口的 {@code parentId} 赋值）
     *
     * @param parentId 新父权限点 ID（0 表示顶级）
     */
    public void moveTo(Long parentId) {
        if (parentId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "父权限点 ID 不能为空（0 表示顶级）");
        }
        this.parentId = parentId;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更权限码（对应更新接口的 {@code permCode} 赋值）
     *
     * @param permCode 新权限码值对象
     */
    public void changeCode(PermCode permCode) {
        if (permCode == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "权限码不能为空");
        }
        this.permCode = permCode;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：重命名权限点（对应更新接口的 {@code permName} 赋值）
     *
     * @param permName 新权限名称
     */
    public void rename(String permName) {
        if (permName == null || permName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "权限名称不能为空");
        }
        this.permName = permName;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更权限类型（对应更新接口的 {@code permType} 赋值）
     *
     * @param permType 新权限类型（SYSTEM / MENU / API / BUTTON / DATA / ATTR）
     */
    public void changeType(String permType) {
        if (permType == null || !PERM_TYPES.contains(permType)) {
            throw new DomainException(ErrorCode.PARAM_ERROR,
                    "权限类型非法，仅支持 SYSTEM / MENU / API / BUTTON / DATA / ATTR");
        }
        this.permType = permType;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更接口路径与方法（对应更新接口的 {@code path}/{@code method} 赋值）
     *
     * @param path 接口路径
     * @param method 请求方法
     */
    public void changeApi(String path, String method) {
        this.path = path;
        this.method = method;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：修改排序号（对应更新接口的 {@code sortNo} 赋值）
     *
     * @param sortNo 新排序号
     */
    public void changeSort(int sortNo) {
        this.sortNo = sortNo;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：变更状态（对应更新接口的 {@code status} 赋值）
     *
     * @param status 新状态（1 启用 / 0 停用）
     */
    public void changeStatus(int status) {
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
        this.status = status;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：一次性应用更新接口的全部字段（PATCH 契约）
     *
     * <p><b>PATCH 契约</b>：所有参数统一 {@code null = 不修改}（保持原值），不做任何隐式默认值
     * 降级（如不再把空 {@code parentId} 降级为顶级、空 {@code sortNo} 降级为 0）。</p>
     *
     * @param parentId 新父权限点 ID（{@code null} 表示不修改）
     * @param permCode 新权限码值对象（{@code null} 表示不修改）
     * @param permName 新权限名称（{@code null} 表示不修改）
     * @param permType 新权限类型（{@code null} 表示不修改）
     * @param path 接口路径（{@code null} 表示不修改）
     * @param method 请求方法（{@code null} 表示不修改）
     * @param sortNo 新排序号（{@code null} 表示不修改）
     * @param status 新状态（{@code null} 表示不修改）
     */
    public void applyUpdate(Long parentId, PermCode permCode, String permName, String permType,
                            String path, String method, Integer sortNo, Integer status) {
        if (parentId != null) {
            moveTo(parentId);
        }
        if (permCode != null) {
            changeCode(permCode);
        }
        if (permName != null) {
            rename(permName);
        }
        if (permType != null) {
            changeType(permType);
        }
        if (path != null || method != null) {
            changeApi(path, method);
        }
        if (sortNo != null) {
            changeSort(sortNo);
        }
        if (status != null) {
            changeStatus(status);
        }
    }

    /**
     * 判定当前权限点是否启用
     *
     * @return 启用返回 true，停用返回 false
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 获取权限点 ID
     *
     * @return 权限点 ID（新建且未落库时为 {@code null}）
     */
    public Long getId() {
        return id;
    }

    /**
     * 获取父权限点 ID
     *
     * @return 父权限点 ID（0 表示顶级）
     */
    public Long getParentId() {
        return parentId;
    }

    /**
     * 获取权限码值对象
     *
     * @return 权限码值对象
     */
    public PermCode getPermCode() {
        return permCode;
    }

    /**
     * 获取权限名称
     *
     * @return 权限名称
     */
    public String getPermName() {
        return permName;
    }

    /**
     * 获取权限类型
     *
     * @return 权限类型
     */
    public String getPermType() {
        return permType;
    }

    /**
     * 获取接口路径
     *
     * @return 接口路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 获取请求方法
     *
     * @return 请求方法
     */
    public String getMethod() {
        return method;
    }

    /**
     * 获取排序号
     *
     * @return 排序号
     */
    public int getSortNo() {
        return sortNo;
    }

    /**
     * 获取状态
     *
     * @return 状态（1 启用 / 0 停用）
     */
    public int getStatus() {
        return status;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
