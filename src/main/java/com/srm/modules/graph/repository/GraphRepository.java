package com.srm.modules.graph.repository;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Cypher 查询执行器。所有图谱查询统一经过这里，避免在各 Service 中散落 Session 管理代码。
 */
@Repository
public class GraphRepository {

    private final Driver driver;
    private final String database;

    public GraphRepository(Driver driver,
                           @Value("${srm.neo4j.database:neo4j}") String database) {
        this.driver = driver;
        this.database = database;
    }

    /**
     * 执行只读 Cypher 查询，返回记录列表。
     */
    public List<Record> query(String cypher, Map<String, Object> params) {
        try (Session session = session()) {
            return session.executeRead(tx -> tx.run(cypher, params).list());
        }
    }

    /**
     * 执行只读 Cypher 查询（无参数）。
     */
    public List<Record> query(String cypher) {
        return query(cypher, Map.of());
    }

    /**
     * 执行写 Cypher（CREATE/MERGE/SET/DELETE），不返回结果。
     */
    public void write(String cypher, Map<String, Object> params) {
        try (Session session = session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, params).consume();
                return null;
            });
        }
    }

    private Session session() {
        return (database == null || database.isBlank())
                ? driver.session()
                : driver.session(SessionConfig.forDatabase(database));
    }
}
