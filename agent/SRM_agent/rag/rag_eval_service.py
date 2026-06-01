"""
RAG 召回率评估服务
记录每次 RAG 查询的检索结果，支持人工标注相关性，计算召回率指标。
"""

import sqlite3
import json
import os
from datetime import datetime
from utils.logger_handler import logger


class RagEvalService:
    """RAG 评估：日志记录 + 召回率统计"""

    def __init__(self, db_path: str = "rag_eval/rag_eval.db"):
        db_dir = os.path.dirname(db_path)
        if db_dir and not os.path.exists(db_dir):
            os.makedirs(db_dir, exist_ok=True)

        self.db_path = db_path
        self._init_tables()

    def _get_conn(self):
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_tables(self):
        with self._get_conn() as conn:
            conn.executescript("""
                CREATE TABLE IF NOT EXISTS rag_eval_log (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id  TEXT    NOT NULL,
                    query       TEXT    NOT NULL,
                    retrieved   TEXT    NOT NULL,  -- JSON: [{rank, content_preview, source}]
                    k           INTEGER NOT NULL,
                    response    TEXT,
                    relevant    TEXT    DEFAULT NULL,  -- JSON: [0,1,0] 标注每条的命中情况, NULL=未标注
                    score       REAL    DEFAULT NULL,  -- 自动评估分数 (可选)
                    created_at  TEXT    DEFAULT (datetime('now','localtime'))
                );

                CREATE TABLE IF NOT EXISTS rag_recall_stats (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    total_queries   INTEGER DEFAULT 0,
                    labeled_queries INTEGER DEFAULT 0,
                    total_retrieved INTEGER DEFAULT 0,
                    total_relevant  INTEGER DEFAULT 0,
                    recall_avg      REAL    DEFAULT 0.0,
                    updated_at      TEXT    DEFAULT (datetime('now','localtime'))
                );
            """)
            conn.commit()
        logger.info(f"[RagEval] 评估数据库初始化完成: {self.db_path}")

    def log_query(self, session_id: str, query: str, retrieved_docs: list,
                  k: int, response: str = None, score: float = None) -> int:
        """记录一次 RAG 查询，返回记录 ID"""
        docs_json = []
        for i, doc in enumerate(retrieved_docs):
            docs_json.append({
                "rank": i + 1,
                "content": (doc.page_content[:200] + "...") if len(doc.page_content) > 200 else doc.page_content,
                "source": doc.metadata.get("source", ""),
            })

        with self._get_conn() as conn:
            cur = conn.execute(
                """INSERT INTO rag_eval_log (session_id, query, retrieved, k, response, score)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (session_id, query, json.dumps(docs_json, ensure_ascii=False), k, response, score)
            )
            conn.commit()
            return cur.lastrowid

    def label_relevance(self, log_id: int, relevant_list: list[int]):
        """人工标注：relevant_list 如 [1, 0, 1] 表示第1条相关、第2条不相关、第3条相关"""
        with self._get_conn() as conn:
            conn.execute(
                "UPDATE rag_eval_log SET relevant = ? WHERE id = ?",
                (json.dumps(relevant_list), log_id)
            )
            conn.commit()
        self._recalc_stats()

    def _recalc_stats(self):
        """重新计算召回率统计"""
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT k, relevant FROM rag_eval_log WHERE relevant IS NOT NULL"
            ).fetchall()

            total_queries = len(rows)
            total_retrieved = sum(r["k"] for r in rows)
            total_relevant = 0

            for r in rows:
                rel = json.loads(r["relevant"])
                total_relevant += sum(1 for v in rel if v == 1)

            recall_avg = (total_relevant / total_retrieved) if total_retrieved > 0 else 0.0

            existing = conn.execute("SELECT COUNT(*) as cnt FROM rag_recall_stats").fetchone()
            if existing["cnt"] == 0:
                conn.execute(
                    """INSERT INTO rag_recall_stats
                       (total_queries, labeled_queries, total_retrieved, total_relevant, recall_avg)
                       VALUES (?, ?, ?, ?, ?)""",
                    (total_queries, total_queries, total_retrieved, total_relevant, round(recall_avg, 4))
                )
            else:
                conn.execute(
                    """UPDATE rag_recall_stats SET
                       total_queries = ?, labeled_queries = ?, total_retrieved = ?,
                       total_relevant = ?, recall_avg = ?, updated_at = datetime('now','localtime')""",
                    (total_queries, total_queries, total_retrieved, total_relevant, round(recall_avg, 4))
                )
            conn.commit()

    def get_stats(self) -> dict:
        """获取当前召回率统计"""
        with self._get_conn() as conn:
            row = conn.execute("SELECT * FROM rag_recall_stats ORDER BY id DESC LIMIT 1").fetchone()
            if not row:
                return {"total_queries": 0, "labeled_queries": 0, "total_retrieved": 0,
                        "total_relevant": 0, "recall_avg": 0}
            return dict(row)

    def get_unlabeled(self, limit: int = 20) -> list[dict]:
        """获取未标注的查询记录"""
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT * FROM rag_eval_log WHERE relevant IS NULL ORDER BY created_at DESC LIMIT ?",
                (limit,)
            ).fetchall()
            return [dict(r) for r in rows]

    def get_all_logs(self, limit: int = 50) -> list[dict]:
        """获取所有查询记录"""
        with self._get_conn() as conn:
            rows = conn.execute(
                "SELECT * FROM rag_eval_log ORDER BY created_at DESC LIMIT ?",
                (limit,)
            ).fetchall()
            return [dict(r) for r in rows]


# 全局单例
rag_eval = RagEvalService()
