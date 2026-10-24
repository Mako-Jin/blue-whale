package com.bluewhale.agent.service;

import com.bluewhale.agent.config.AgentProperties;
import com.bluewhale.agent.support.dataquery.DataMasker;
import com.bluewhale.agent.support.dataquery.DataQueryGuard;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读数据查询：白名单视图 + SQL 守卫 + 行数上限 + 脱敏。
 *
 * <p>使用独立只读账号连接（不加入主数据源与 MyBatis 事务），默认关闭；
 * 未配置 {@code whale.agent.data.url} 时直接拒绝。返回体附带实际执行 SQL，
 * 但不回显连接串与账号。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class DataQueryService {

    /** 只读数据源配置 */
    private final AgentProperties properties;

    /** 只读查询模板（懒初始化，未启用时不建立连接） */
    private JdbcTemplate jdbcTemplate;

    /**
     * 生产构造器：按配置懒创建只读连接。
     *
     * @param properties Agent 配置
     */
    public DataQueryService(AgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 测试构造器：注入查询模板，不建立真实连接。
     *
     * @param properties   Agent 配置
     * @param jdbcTemplate 查询模板
     */
    DataQueryService(AgentProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 执行只读查询。
     *
     * @param sql 用户/模型提交的 SQL
     * @return 执行 SQL、视图、行数、耗时与脱敏后的结果
     */
    public Map<String, Object> query(String sql) {
        AgentProperties.DataQuery data = properties.getData();
        if (!data.isEnabled() || !StringUtils.hasText(data.getUrl())) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(), "数据查询通道未启用");
        }
        DataQueryGuard.GuardedQuery guarded = DataQueryGuard.check(sql, data.getAllowedViews(), data.getMaxRows());
        long started = System.currentTimeMillis();
        List<Map<String, Object>> rows;
        try {
            rows = jdbc().queryForList(guarded.sql());
        } catch (DataAccessException e) {
            log.warn("只读数据查询失败：{}", e.getMessage());
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "查询失败，请检查 SQL 与白名单视图授权");
        }
        int maxRows = Math.max(1, data.getMaxRows());
        boolean truncated = rows.size() > maxRows;
        List<Map<String, Object>> limited = truncated ? new ArrayList<>(rows.subList(0, maxRows)) : rows;
        List<Map<String, Object>> masked = DataMasker.maskRows(limited);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", guarded.sql());
        result.put("views", guarded.views());
        result.put("count", masked.size());
        result.put("truncated", truncated);
        result.put("elapsedMillis", System.currentTimeMillis() - started);
        result.put("rows", masked);
        return result;
    }

    /**
     * 返回只读查询模板；首次调用时按配置创建独立连接。
     *
     * @return 查询模板
     */
    private JdbcTemplate jdbc() {
        if (jdbcTemplate != null) {
            return jdbcTemplate;
        }
        AgentProperties.DataQuery data = properties.getData();
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(data.getUrl(), data.getUsername(), data.getPassword());
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(Math.max(1, data.getTimeoutSeconds()));
        jdbcTemplate = template;
        return template;
    }
}
