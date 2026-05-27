"""
文件处理工具：MD5 计算、文件类型筛选、PDF/TXT 加载
"""

import os
import hashlib
from utils.logger_handler import logger
from langchain_core.documents import Document
from langchain_community.document_loaders import PyPDFLoader, TextLoader


def get_file_md5_hex(filepath: str):
    """
    计算指定文件的 MD5 值（十六进制）。
    采用分块读取的方式，防止大文件占用过多内存。
    """
    if not os.path.exists(filepath):
        logger.error(f"[md5计算]文件{filepath}不存在")
        return

    if not os.path.isfile(filepath):
        logger.error(f"[md5计算]路径{filepath}不是文件")
        return

    md5_obj = hashlib.md5()
    chunk_size = 4096

    try:
        with open(filepath, "rb") as f:
            while chunk := f.read(chunk_size):
                md5_obj.update(chunk)

            return md5_obj.hexdigest()

    except Exception as e:
        logger.error(f"计算文件{filepath}md5失败，{str(e)}")
        return None


def listdir_with_allowed_type(path: str, allowed_types: tuple[str]):
    """
    列出指定目录下所有符合允许后缀名的文件。
    :param path: 目录路径
    :param allowed_types: 允许的后缀元组，例如 ('.txt', '.pdf')
    :return: 包含绝对路径的文件列表
    """
    files = []
    if not os.path.isdir(path):
        logger.error(f"[listdir_with_allowed_type]{path}不是文件夹")
        return files

    for f in os.listdir(path):
        if f.endswith(allowed_types):
            files.append(os.path.join(path, f))

    return tuple(files)


def pdf_loader(filepath: str, passwd=None) -> list[Document]:
    """
    加载 PDF 文件并转换为 LangChain Document 对象列表。
    """
    return PyPDFLoader(filepath, password=passwd).load()


def txt_loader(filepath: str) -> list[Document]:
    """
    加载 TXT 文件并转换为 LangChain Document 对象列表。
    """
    return TextLoader(filepath, encoding='utf-8').load()
