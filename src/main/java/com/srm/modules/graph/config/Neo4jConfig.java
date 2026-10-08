package com.srm.modules.graph.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j 图数据库连接配置。
 * 创建 Driver Bean 并做一次连通性校验，打印 info 级连接成功日志。
 */
@Slf4j
@Configuration
public class Neo4jConfig {

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(@Value("${srm.neo4j.uri}") String uri,
                              @Value("${srm.neo4j.username}") String username,
                              @Value("${srm.neo4j.password}") String password) {
        Driver driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
        try {
            driver.verifyConnectivity();
            log.info("Neo4j 连接成功: {}", uri);
        } catch (Exception e) {
            log.warn("Neo4j 连接失败（不影响启动，后续查询会自动重试）: {}，原因: {}", uri, e.getMessage());
        }
        return driver;
    }
}
