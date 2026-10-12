package com.srm.modules.graph.init;

import com.srm.modules.graph.service.Neo4jSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 启动时自动灌入图谱种子数据（可选，一次性、幂等）。
 * 仅在 {@code srm.neo4j.seed-on-startup=true} 时执行；失败不阻断启动，
 * 可稍后手动调用 {@code POST /api/graph/seed}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "srm.neo4j.seed-on-startup", havingValue = "true")
public class Neo4jSeedInitializer implements CommandLineRunner {

    private final Neo4jSeedService neo4jSeedService;

    @Override
    public void run(String... args) {
        try {
            neo4jSeedService.loadSeed();
        } catch (Exception e) {
            log.error("[Neo4jSeed] 启动灌入种子数据失败（不影响启动），可稍后调用 /api/graph/seed 重试", e);
        }
    }
}
