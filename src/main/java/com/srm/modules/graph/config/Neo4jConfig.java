package com.srm.modules.graph.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Neo4j 图数据库连接配置。
 * 仅负责创建 Driver Bean，具体 Cypher 执行见 {@code GraphRepository}。
 */
@Configuration
public class Neo4jConfig {

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(@Value("${srm.neo4j.uri}") String uri,
                              @Value("${srm.neo4j.username}") String username,
                              @Value("${srm.neo4j.password}") String password) {
        return GraphDatabase.driver(uri, AuthTokens.basic(username, password));
    }
}
