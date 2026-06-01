"""Chroma 知识库入库脚本 — 将 data/knowledge/ 下所有文档分块存入向量库"""
import sys
import os

# 确保当前目录在 path 中
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from rag.vector_store import VectorStoreService
from utils.logger_handler import logger

if __name__ == "__main__":
    logger.info("========== 开始加载知识库文档到 Chroma ==========")
    vss = VectorStoreService()
    vss.load_document()
    logger.info("========== 知识库加载完成 ==========")
    print("Done — all knowledge documents ingested into Chroma.")
