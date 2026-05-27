"""
聊天历史管理服务
基于 SQLite 实现会话级别的聊天记录持久化
"""

from langchain_community.chat_message_histories import SQLChatMessageHistory
from typing import List
import os


class MemoryService:
    def __init__(self, db_path: str = "chat_history/chat_history.db"):
        """
        初始化历史记忆服务

        Args:
            db_path: SQLite 数据库路径
        """
        # 确保数据库目录存在
        db_dir = os.path.dirname(db_path)
        if db_dir and not os.path.exists(db_dir):
            os.makedirs(db_dir, exist_ok=True)

        self.db_path = f"sqlite:///{db_path}"
        self._sessions = {}

    def _get_message_history(self, session_id: str) -> SQLChatMessageHistory:
        """
        获取或创建会话的消息历史对象

        Args:
            session_id: 会话ID

        Returns:
            SQLChatMessageHistory 对象
        """
        if session_id not in self._sessions:
            self._sessions[session_id] = SQLChatMessageHistory(
                session_id=session_id,
                connection=self.db_path
            )
        return self._sessions[session_id]

    def get_messages(self, session_id: str) -> List:
        """
        获取指定会话的所有历史消息

        Args:
            session_id: 会话ID

        Returns:
            消息列表
        """
        message_history = self._get_message_history(session_id)
        return message_history.messages

    def add_message(self, session_id: str, role: str, content: str):
        """
        添加单条消息到历史记录

        Args:
            session_id: 会话ID
            role: 消息角色（"user" 或 "assistant"）
            content: 消息内容
        """
        message_history = self._get_message_history(session_id)

        if role == "user":
            message_history.add_user_message(content)
        elif role == "assistant":
            message_history.add_ai_message(content)
        else:
            raise ValueError(f"不支持的消息角色: {role}")

    def add_messages(self, session_id: str, user_message: str, ai_message: str):
        """
        批量添加一轮对话（用户消息 + AI回复）

        Args:
            session_id: 会话ID
            user_message: 用户消息
            ai_message: AI 回复消息
        """
        message_history = self._get_message_history(session_id)
        message_history.add_user_message(user_message)
        message_history.add_ai_message(ai_message)

    def clear(self, session_id: str):
        """
        清空指定会话的历史记录

        Args:
            session_id: 会话ID
        """
        message_history = self._get_message_history(session_id)
        message_history.clear()
        if session_id in self._sessions:
            del self._sessions[session_id]

    def get_message_count(self, session_id: str) -> int:
        """
        获取指定会话的消息数量

        Args:
            session_id: 会话ID

        Returns:
            消息总数
        """
        messages = self.get_messages(session_id)
        return len(messages)

    def get_conversation_count(self, session_id: str) -> int:
        """
        获取对话轮数（一轮 = 用户消息 + AI回复）

        Args:
            session_id: 会话ID

        Returns:
            对话轮数
        """
        return self.get_message_count(session_id) // 2
