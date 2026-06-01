"""
SRM 供应商管理系统 - 智能采购 Agent API
提供 FastAPI 接口，供 Java 后端调用
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from typing import Optional
import uvicorn
import json

from langchain.agents import create_agent
from langchain_core.messages import HumanMessage, AIMessage, AIMessageChunk
from model.factory import chat_model
from utils.prompt_loader import load_system_prompts
from tools.agent_tools import (
    search_supplier_knowledge,
    search_suppliers,
    get_price_reference,
    calculate_total_cost,
    compare_supplier_quotes,
    get_supplier_status_flow,
    get_order_status_flow,
    _thread_local,
)
from tools.middleware import monitor_tool, log_before_model
from memory.memory_service import MemoryService
from utils.logger_handler import logger

# --- FastAPI 应用初始化 ---
app = FastAPI(
    title="SRM 智能采购 Agent API",
    description="供应商关系管理系统的 AI Agent 接口，提供供应商筛选、询比价、订单咨询等智能服务",
    version="1.0.0",
)

# 跨域配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# --- 请求/响应模型 ---
class ChatRequest(BaseModel):
    query: str = Field(..., description="用户问题")
    session_id: str = Field(default="default", description="会话ID，用于区分不同用户/会话")


class ChatResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: Optional[str] = None
    timestamp: str = ""


class HistoryResponse(BaseModel):
    code: int = 200
    message: str = "success"
    data: list = []


# --- Agent 初始化 ---
class SRMAgent:
    """SRM 智能采购 Agent 封装"""

    def __init__(self):
        logger.info("[SRMAgent]正在初始化 Agent...")
        self.agent = create_agent(
            model=chat_model,
            system_prompt=load_system_prompts(),
            tools=[
                search_supplier_knowledge,
                search_suppliers,
                get_price_reference,
                calculate_total_cost,
                compare_supplier_quotes,
                get_supplier_status_flow,
                get_order_status_flow,
            ],
            middleware=[monitor_tool, log_before_model],
        )
        self.memory_service = MemoryService()
        logger.info("[SRMAgent]Agent 初始化完成")

    def execute(self, query: str, session_id: str) -> str:
        """
        执行 Agent 推理（非流式），返回完整回复

        Args:
            query: 用户当前问题
            session_id: 会话ID

        Returns:
            Agent 的完整回复文本
        """
        # 设置当前 session_id 到线程本地存储，供 RAG 评估日志使用
        _thread_local.session_id = session_id

        # 1. 加载历史消息
        history_messages = self.memory_service.get_messages(session_id)

        # 2. 构建输入
        input_dict = {
            "messages": history_messages + [HumanMessage(content=query)]
        }

        # 3. 收集完整回复（只取 AIMessage，排除工具结果）
        full_response = ""

        # 4. 流式执行并收集结果
        for chunk in self.agent.stream(input_dict, stream_mode="values"):
            latest_message = chunk["messages"][-1]
            if isinstance(latest_message, AIMessage) and latest_message.content:
                full_response = latest_message.content.strip()

        # 5. 保存对话历史
        if full_response:
            self.memory_service.add_messages(session_id, query, full_response)
        else:
            logger.warning(f"[Agent] 未生成回复，session={session_id} query={query[:50]}...")

        return full_response

    def execute_stream(self, query: str, session_id: str):
        """
        执行 Agent 推理（流式），返回 SSE 事件流，逐 token 输出。
        stream_mode="messages" 配合模型 streaming=True，实现 token 级增量推送。
        """
        _thread_local.session_id = session_id

        history_messages = self.memory_service.get_messages(session_id)

        input_dict = {
            "messages": history_messages + [HumanMessage(content=query)]
        }

        full_response = ""

        try:
            logger.info(f"[Agent] 开始执行 stream（token级），session={session_id}")
            for chunk in self.agent.stream(input_dict, stream_mode="messages"):
                # messages 模式产出 (message_chunk, metadata) 元组
                if isinstance(chunk, tuple) and len(chunk) == 2:
                    msg_chunk = chunk[0]
                else:
                    msg_chunk = chunk

                if isinstance(msg_chunk, AIMessageChunk) and msg_chunk.content:
                    delta = msg_chunk.content
                    if isinstance(delta, list):
                        delta = "".join(
                            b.get("text", "") if isinstance(b, dict) else str(b)
                            for b in delta
                        )
                    if delta:
                        full_response += delta
                        yield f"data: {json.dumps({'content': delta}, ensure_ascii=False)}\n\n"

            logger.info(f"[Agent] stream 完成，session={session_id} response_len={len(full_response)}")
        except Exception as e:
            logger.error(f"[Agent] stream 执行异常: {type(e).__name__}: {e}", exc_info=True)
            yield f"data: {json.dumps({'content': f'[系统错误] Agent 执行异常: {e}'})}\n\n"
        finally:
            self.memory_service.add_messages(session_id, query, full_response)
            if not full_response:
                logger.warning(f"[Agent] 未生成回复，session={session_id} query={query[:80]}...")

        yield f"data: {json.dumps({'done': True})}\n\n"

    def get_history(self, session_id: str):
        """获取会话历史"""
        return self.memory_service.get_messages(session_id)

    def clear_history(self, session_id: str):
        """清空会话历史"""
        self.memory_service.clear(session_id)


# 全局单例
srm_agent = SRMAgent()


# --- API 路由 ---

@app.get("/")
def root():
    """根路径，返回 API 信息"""
    return {
        "service": "SRM 智能采购 Agent API",
        "version": "1.0.0",
        "docs": "/docs",
    }


@app.get("/api/agent/health")
def health_check():
    """健康检查接口"""
    return {"status": "ok", "service": "SRM Agent"}


@app.post("/api/agent/chat", response_model=ChatResponse)
def chat(request: ChatRequest):
    """
    智能采购对话接口（非流式）

    Java 后端调用示例：
    POST /api/agent/chat
    {
        "query": "帮我推荐华东地区做电子元器件的供应商",
        "session_id": "user_001"
    }
    """
    try:
        logger.info(f"[API]收到请求 session={request.session_id} query={request.query[:50]}...")
        response_text = srm_agent.execute(request.query, request.session_id)
        return ChatResponse(
            data=response_text,
            timestamp=str(__import__("datetime").datetime.now()),
        )
    except Exception as e:
        logger.error(f"[API]处理请求失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Agent 执行失败: {str(e)}")


@app.post("/api/agent/chat/stream")
def chat_stream(request: ChatRequest):
    """
    智能采购对话接口（SSE 流式）

    Java 后端调用示例：
    POST /api/agent/chat/stream
    {
        "query": "帮我比较一下螺丝刀的供应商报价",
        "session_id": "user_001"
    }
    """
    try:
        logger.info(f"[API]收到流式请求 session={request.session_id} query={request.query[:50]}...")
        return StreamingResponse(
            srm_agent.execute_stream(request.query, request.session_id),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )
    except Exception as e:
        logger.error(f"[API]流式请求失败: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Agent 流式执行失败: {str(e)}")


@app.get("/api/agent/history/{session_id}")
def get_history(session_id: str):
    """获取指定会话的对话历史"""
    try:
        messages = srm_agent.get_history(session_id)
        history_data = []
        for msg in messages:
            msg_type = type(msg).__name__
            history_data.append({
                "role": "user" if "Human" in msg_type else "assistant",
                "content": msg.content,
                "type": msg_type,
            })
        return {
            "code": 200,
            "message": "success",
            "data": history_data,
        }
    except Exception as e:
        logger.error(f"[API]获取历史失败: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


@app.delete("/api/agent/history/{session_id}")
def clear_history(session_id: str):
    """清空指定会话的对话历史"""
    try:
        srm_agent.clear_history(session_id)
        return {"code": 200, "message": "历史记录已清空"}
    except Exception as e:
        logger.error(f"[API]清空历史失败: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))


# --- 启动入口 ---
if __name__ == "__main__":
    logger.info("SRM Agent API 服务启动中...")
    uvicorn.run(
        "app:app",
        host="0.0.0.0",
        port=8000,
        reload=False,
        log_level="info",
    )
