from utils.config_handler import chroma_conf
from langchain_chroma import Chroma
from model.factory import embed_model
from langchain_text_splitters import RecursiveCharacterTextSplitter
from utils.path_tool import get_abs_path
import os
from utils.file_handler import txt_loader, pdf_loader, listdir_with_allowed_type, get_file_md5_hex
from utils.logger_handler import logger
from langchain_core.documents import Document


class VectorStoreService:
    def __init__(self):
        # 1. 初始化向量数据库连接
        self.vector_store = Chroma(
            collection_name=chroma_conf["collection_name"],
            embedding_function=embed_model,
            persist_directory=chroma_conf["persist_directory"]
        )
        # 2. 初始化文本分割器
        self.spliter = RecursiveCharacterTextSplitter(
            chunk_size=chroma_conf["chunk_size"],
            chunk_overlap=chroma_conf["chunk_overlap"],
            separators=chroma_conf["separators"],
            length_function=len,
        )

    def get_retriever(self):
        """获取检索器"""
        return self.vector_store.as_retriever(search_kwargs={"k": chroma_conf["k"]})

    def load_document(self):
        """
        加载文档并写入向量库的主流程
        """
        # 1. 获取指定目录下所有允许类型的文件路径列表
        allowed_files_path: list[str] = listdir_with_allowed_type(
            chroma_conf["data_path"],
            tuple(chroma_conf["allow_knowledge_file_type"]),
        )

        # 2. 遍历每一个找到的文件进行处理
        for path in allowed_files_path:
            # 步骤 A: 获取文件指纹 (MD5)
            md5_hex = get_file_md5_hex(path)

            if not md5_hex:
                continue

            # 步骤 B: 检查是否已处理过 (去重)
            if check_md5_hex(md5_hex):
                logger.info(f"[加载知识库]{path}内容已经存在知识库内，跳过")
                continue

            try:
                # 步骤 C: 加载文档内容
                documents: list[Document] = get_file_documents(path)

                if not documents:
                    logger.warning(f"[加载知识库]{path}内没有有效文本内容，跳过")
                    continue

                # 步骤 D: 文本分块
                split_documents: list[Document] = self.spliter.split_documents(documents)

                if not split_documents:
                    logger.warning(f"[加载知识库]{path}分片后没有有效文本内容，跳过")
                    continue

                # 步骤 E: 写入向量数据库
                logger.info(f"[加载知识库]正在写入 {path} 的分片数据...")
                self.vector_store.add_documents(split_documents)

                # 步骤 F: 保存 MD5 记录
                save_md5_hex(md5_hex)
                logger.info(f"[加载知识库]{path} 处理完成并已保存。")

            except Exception as e:
                logger.error(f"[加载知识库]处理文件 {path} 时发生错误: {e}")


# --- 辅助函数 ---

def check_md5_hex(md5_for_check: str):
    md5_file_path = get_abs_path(chroma_conf["md5_hex_store"])
    if not os.path.exists(md5_file_path):
        open(md5_file_path, "w", encoding="utf-8").close()
        return False

    with open(md5_file_path, "r", encoding="utf-8") as f:
        for line in f.readlines():
            if line.strip() == md5_for_check:
                return True
    return False


def save_md5_hex(md5_for_check: str):
    md5_file_path = get_abs_path(chroma_conf["md5_hex_store"])
    with open(md5_file_path, "a", encoding="utf-8") as f:
        f.write(md5_for_check + "\n")


def get_file_documents(read_path: str):
    if read_path.endswith("txt"):
        return txt_loader(read_path)
    elif read_path.endswith("pdf"):
        return pdf_loader(read_path)
    return []
