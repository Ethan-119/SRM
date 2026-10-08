package com.srm.modules.graph.init;

import com.srm.modules.graph.service.Neo4jSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动时自动同步业务库到 Neo4j（可选）。
 * 仅在 {@code srm.neo4j.sync-on-startup=true} 时执行；同步失败不阻断启动，
 * 可稍后手动调用 {@code POST /api/graph/sync}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "srm.neo4j.sync-on-startup", havingValue = "true")
public class Neo4jSyncInitializer implements CommandLineRunner {

    private final Neo4jSyncService neo4jSyncService;

    @Override
    public void run(String... args) {
        try {
            neo4jSyncService.syncAll();
        } catch (Exception e) {
            log.error("[Neo4jSync] 启动同步失败（不影响启动），可稍后调用 /api/graph/sync 重试", e);
        }
    }
}
