# 为整个工程提供统一的绝对路径
import os


def get_project_root() -> str:
    """
    获取工程所在的根目录
    """
    # 获取当前文件的绝对路径
    current_file = os.path.abspath(__file__)
    # 获取文件的文件夹的绝对路径
    current_dir = os.path.dirname(current_file)
    # 获取工程根目录
    project_root = os.path.dirname(current_dir)

    return project_root


def get_abs_path(relative_path: str) -> str:
    """
    给相对路径返回绝对路径
    """
    project_root = get_project_root()

    return os.path.join(project_root, relative_path)
