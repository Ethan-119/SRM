"""RAG 召回率评估 CLI：查看日志、标注相关性、计算召回率"""
import sys
import json
import os

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from rag.rag_eval_service import RagEvalService

eval_svc = RagEvalService()


def show_stats():
    s = eval_svc.get_stats()
    print("=" * 50)
    print("RAG 召回率统计")
    print("=" * 50)
    print(f"  已标注查询数: {s.get('labeled_queries', 0)}")
    print(f"  检索文档总数: {s.get('total_retrieved', 0)}")
    print(f"  相关文档数:   {s.get('total_relevant', 0)}")
    print(f"  平均召回率:   {s['recall_avg']:.2%}" if s['recall_avg'] else "  平均召回率:   N/A")
    print()


def show_unlabeled():
    rows = eval_svc.get_unlabeled(limit=30)
    if not rows:
        print("暂无待标注的查询记录。")
        return
    print(f"待标注记录 ({len(rows)} 条):")
    print("-" * 60)
    for r in rows:
        docs = json.loads(r["retrieved"])
        print(f"[ID:{r['id']}] {r['created_at']}")
        print(f"  查询: {r['query'][:80]}")
        for d in docs:
            print(f"    #{d['rank']} [{d.get('source','?')}] {d['content'][:60]}")
        print()


def label(log_id: int, relevant_str: str):
    """标注：1,0,1 表示 3 条结果中第1、3条相关"""
    try:
        rel = [int(x.strip()) for x in relevant_str.split(",")]
    except ValueError:
        print("格式错误。示例: 1,0,1 (表示第1和第3条相关)")
        return
    eval_svc.label_relevance(log_id, rel)
    print(f"已标注 ID={log_id}，相关性: {rel}")
    show_stats()


def show_all(limit: int = 20):
    rows = eval_svc.get_all_logs(limit=limit)
    for r in rows:
        rel = json.loads(r["relevant"]) if r["relevant"] else "未标注"
        print(f"[ID:{r['id']}] {r['created_at']} query='{r['query'][:50]}' relevant={rel}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法:")
        print("  python rag_eval_cli.py stats        — 查看召回率统计")
        print("  python rag_eval_cli.py unlabeled    — 查看待标注的查询")
        print("  python rag_eval_cli.py label <ID> <1,0,1>  — 标注相关性")
        print("  python rag_eval_cli.py all [N]      — 查看最近 N 条记录")
        sys.exit(0)

    cmd = sys.argv[1]
    if cmd == "stats":
        show_stats()
    elif cmd == "unlabeled":
        show_unlabeled()
    elif cmd == "label":
        if len(sys.argv) < 4:
            print("用法: python rag_eval_cli.py label <ID> <1,0,1>")
        else:
            label(int(sys.argv[2]), sys.argv[3])
    elif cmd == "all":
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 20
        show_all(n)
    else:
        print(f"未知命令: {cmd}")
