"""
YAML 配置加载器
"""

import yaml
from utils.path_tool import get_abs_path


def load_yaml_config(config_name: str, encoding: str = "utf-8"):
    """
    通用的 YAML 加载函数
    :param config_name: 配置文件名 (例如 "config/rag.yml")
    :param encoding: 编码格式
    """
    config_path = get_abs_path(config_name)
    with open(config_path, "r", encoding=encoding) as f:
        return yaml.load(f, Loader=yaml.FullLoader)


# 加载配置
rag_conf = load_yaml_config("config/rag.yml")
chroma_conf = load_yaml_config("config/chroma.yml")
agent_conf = load_yaml_config("config/agent.yml")
prompts_conf = load_yaml_config("config/prompts.yml")
