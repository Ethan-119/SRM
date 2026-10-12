package com.srm.modules.graph.service;

import com.srm.modules.graph.repository.GraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 图谱种子数据加载服务。
 *
 * <p>HHI 集中度分析等能力依赖 {@code SUPPLIES}（价格/最小起订量/交期）关系，
 * 该关系在业务库（PostgreSQL）中无直接字段，只存在于 classpath 的
 * {@code graph/seed.cypher}。本服务把种子数据幂等（MERGE）灌入 Neo4j。</p>
 *
 * <p>仅加载 seed 数据、不加载 schema.cypher 的唯一约束：业务库订单的物料只有
 * {@code materialName} 无物料编码，{@code Neo4jSyncService} 按 name 建 Material 节点，
 * 与 schema 中 {@code material_id} 唯一约束冲突，故约束/索引仍保持手动执行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jSeedService {

    private final GraphRepository graphRepository;

    /** 加载种子数据，返回执行的语句数。 */
    public Map<String, Integer> loadSeed() {
        int seed = runClasspath("graph/seed.cypher");
        log.info("[Neo4jSeed] 图谱种子数据加载完成 seed={}", seed);
        return Map.of("seed", seed);
    }

    private int runClasspath(String location) {
        try {
            String script = new String(
                    new ClassPathResource(location).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            return graphRepository.runScript(script);
        } catch (Exception e) {
            throw new IllegalStateException("加载图谱脚本失败: " + location, e);
        }
    }
}
