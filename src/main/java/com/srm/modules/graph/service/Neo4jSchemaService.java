package com.srm.modules.graph.service;

import com.srm.modules.graph.repository.GraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Neo4j 索引/约束初始化服务。
 *
 * <p>解决“服务器上 Neo4j 查询慢、本地快”的典型根因：评分链路大量使用
 * {@code MATCH (s:Supplier {id: ...})} 等点查，一旦缺少 {@code Supplier(id)}
 * 的唯一约束索引，每条查询都会退化为全图扫描，跨容器部署时被网络 RTT 放大。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jSchemaService {

    private final GraphRepository graphRepository;

    /** 幂等创建约束/索引，返回执行的语句数；失败不抛出，仅告警。 */
    public int ensureIndexes() {
        try {
            String script = new String(
                    new ClassPathResource("graph/schema-indexes.cypher")
                            .getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            int count = graphRepository.runSchemaScript(script);
            log.info("[Neo4jSchema] 约束/索引初始化完成，共 {} 条", count);
            return count;
        } catch (Exception e) {
            log.warn("[Neo4jSchema] 索引初始化失败（不影响启动，但 Neo4j 查询可能因缺少索引而变慢）：{}", e.getMessage());
            return 0;
        }
    }
}
