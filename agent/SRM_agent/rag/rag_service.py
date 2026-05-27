"""
RAG 总结服务类：用户提问 -> 搜索参考资料 -> 将提问和参考资料提交给模型 -> 模型总结回复
"""

from langchain_core.documents import Document
from langchain_core.output_parsers import StrOutputParser
from rag.vector_store import VectorStoreService
from utils.prompt_loader import load_rag_prompts
from langchain_core.prompts import PromptTemplate
from model.factory import chat_model


class RagSummarizeService(object):
    def __init__(self):
        # 1. 初始化向量数据库服务
        self.vector_store = VectorStoreService()

        # 2. 获取检索器 (Retriever)
        self.retriever = self.vector_store.get_retriever()

        # 3. 加载提示词文本
        self.prompt_text = load_rag_prompts()

        # 4. 创建提示词模板对象
        self.prompt_template = PromptTemplate.from_template(self.prompt_text)

        # 5. 获取大语言模型
        self.model = chat_model

        # 6. 初始化处理链
        self.chain = self._init_chain()

    def _init_chain(self):
        """
        构建 RAG 链：提示词 -> 模型 -> 输出解析
        """
        chain = self.prompt_template | self.model | StrOutputParser()
        return chain

    def retriever_docs(self, query: str) -> list[Document]:
        """
        根据用户查询语句，从向量数据库中检索相关的文档片段。
        """
        return self.retriever.invoke(query)

    def rag_summarize(self, query: str) -> str:
        """
        RAG 核心逻辑：检索 + 生成
        1. 检索相关文档
        2. 拼接上下文
        3. 调用大模型生成回答
        """
        # 步骤 1: 获取相关文档
        context_docs = self.retriever_docs(query)

        # 步骤 2: 构建上下文字符串
        context = ""
        counter = 0
        for doc in context_docs:
            counter += 1
            context += f"【参考资料{counter}】: 参考资料: {doc.page_content} | 参考元数据: {doc.metadata}\n"

        # 步骤 3: 调用链路生成回答
        return self.chain.invoke(
            {
                "input": query,
                "context": context,
            }
        )
