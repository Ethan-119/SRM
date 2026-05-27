if __name__ == "__main__":
    logger.info("SRM Agent API 服务启动中...")
    uvicorn.run(
        "app:app",
        host="0.0.0.0",
        port=8000,
        reload=False,
        log_level="info",
    )