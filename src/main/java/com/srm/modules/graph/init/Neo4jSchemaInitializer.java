package com.srm.modules.graph.init;

import com.srm.modules.graph.service.Neo4jSchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时自动初始化 Neo4j 约束/索引（幂等，失败不阻断启动）。
 * 与 seed/sync 不同，这里默认无条件执行，保证任何部署环境下点查都能命中索引。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class Neo4jSchemaInitializer implements CommandLineRunner {

    private final Neo4jSchemaService neo4jSchemaService;

    @Override
    public void run(String... args) {
        neo4jSchemaService.ensureIndexes();
    }
}
