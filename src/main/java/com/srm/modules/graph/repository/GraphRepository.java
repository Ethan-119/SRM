package com.srm.modules.graph.repository;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cypher 查询执行器。所有图谱查询统一经过这里，避免在各 Service 中散落 Session 管理代码。
 */
@Slf4j
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
        log.info("[Neo4j] 查询 Cypher: {}\n[Neo4j] 参数: {}", cypher.strip(), params);
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
        log.info("[Neo4j] 写入 Cypher: {}\n[Neo4j] 参数: {}", cypher.strip(), params);
        try (Session session = session()) {
            session.executeWrite(tx -> {
                tx.run(cypher, params).consume();
                return null;
            });
        }
    }

    /**
     * 执行一段由分号分隔、含 {@code //} 行注释的 Cypher 脚本（幂等脚本，用于灌入 schema/seed）。
     * 所有语句在同一事务内执行，保证脚本内跨语句的变量（如 {@code (p)}/{@code (c)}）能正确延续；
     * 任一条失败则整体回滚，可安全重跑。
     */
    public int runScript(String cypherScript) {
        List<String> statements = splitStatements(cypherScript);
        try (Session session = session()) {
            session.executeWrite(tx -> {
                for (String stmt : statements) {
                    log.info("[Neo4j] 执行脚本语句: {}", stmt);
                    tx.run(stmt).consume();
                }
                return null;
            });
        }
        return statements.size();
    }

    /** 去掉 {@code //} 注释行与空行，再按分号拆分为单条语句。 */
    private List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : script.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("//")) {
                continue;
            }
            current.append(line).append('\n');
            if (line.endsWith(";")) {
                String stmt = current.toString().strip();
                if (stmt.endsWith(";")) {
                    stmt = stmt.substring(0, stmt.length() - 1);
                }
                if (!stmt.isBlank()) {
                    statements.add(stmt);
                }
                current.setLength(0);
            }
        }
        String rest = current.toString().strip();
        if (!rest.isBlank()) {
            if (rest.endsWith(";")) {
                rest = rest.substring(0, rest.length() - 1);
            }
            statements.add(rest);
        }
        return statements;
    }

    private Session session() {
        return (database == null || database.isBlank())
                ? driver.session()
                : driver.session(SessionConfig.forDatabase(database));
    }
}
