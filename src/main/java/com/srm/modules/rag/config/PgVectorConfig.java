package com.srm.modules.rag.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * pgvector 独立数据源配置。
 *
 * 注意：这里刻意不注册 {@code DataSource} Bean，而是在本方法内部手工构建
 * {@link HikariDataSource} 并包装成 {@link JdbcTemplate}。这样既避免了与
 * MySQL 主数据源（spring.datasource 自动装配）冲突，也规避了「手动定义
 * DataSource Bean 会导致主数据源自动装配失效」的坑。
 */
@Configuration
public class PgVectorConfig {

    @Bean
    public JdbcTemplate pgVectorJdbcTemplate(@Value("${srm.pgvector.url}") String url,
                                             @Value("${srm.pgvector.username}") String username,
                                             @Value("${srm.pgvector.password}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setPoolName("PgVectorPool");
        config.setConnectionTimeout(10_000);
        config.setMaxLifetime(600_000);
        return new JdbcTemplate(new HikariDataSource(config));
    }
}
