import os
import requests
from langchain.tools import tool
from rag.rag_service import RagSummarizeService
from utils.logger_handler import logger

# Java 后端 API 地址
JAVA_API_BASE = os.getenv("JAVA_API_BASE", "http://localhost:8080")

# 初始化 RAG 服务
rag = RagSummarizeService()

# 状态码映射
STATUS_MAP = {0: "注册", 1: "待审核", 2: "已准入", 3: "合作中", 4: "冻结", 5: "黑名单"}
QUAL_MAP = {1: "一级", 2: "二级", 3: "三级"}

# 地区关键词映射
REGION_MAP = {
    "华南": "华南", "华东": "华东", "华北": "华北",
    "华中": "华中", "西南": "西南", "西北": "西北",
    "深圳": "华南", "广州": "华南", "东莞": "华南",
    "杭州": "华东", "上海": "华东", "合肥": "华东", "南京": "华东", "苏州": "华东",
    "北京": "华北", "天津": "华北",
    "武汉": "华中",
    "成都": "西南", "重庆": "西南",
}

# 品类关键词映射
CATEGORY_KEYWORDS = {
    "精密": "精密", "电子": "电子", "材料": "材料",
    "仪器": "仪器", "包装": "包装", "金属": "金属",
    "自动化": "自动", "软件": "软件", "五金": "五金",
    "物流": "物流", "机械": "机械", "元器件": "电子",
}


# --- 定义工具函数 ---

@tool
def search_supplier_knowledge(query: str) -> str:
    """
    从供应商管理知识库中检索相关专业知识。
    入参 query: 检索关键词，如"供应商资质等级标准"、"供应商评估方法"、"询比价流程"等。
    出参: 知识库中匹配的专业内容字符串。
    使用场景: 当需要补充供应商管理、采购流程、评估标准等专业知识时调用。
    """
    return rag.rag_summarize(query)


@tool
def search_suppliers(keyword: str) -> str:
    """
    根据关键词搜索匹配的供应商信息。
    入参 keyword: 搜索关键词，可以是地区（华南/华东/华北/华中/西南）、
         品类（精密机械加工/电子元器件/新材料等）、或供应商名称的一部分。
    出参: 匹配的供应商列表信息（JSON格式字符串），包含供应商编码、名称、地区、主营品类、资质等级、合作状态。
    使用场景: 当需要查找符合特定条件的供应商时调用。
    """
    try:
        resp = requests.get(f"{JAVA_API_BASE}/api/supplier", timeout=10)
        resp.raise_for_status()
        result = resp.json()
        suppliers = result.get("data", [])
    except requests.RequestException as e:
        logger.error(f"查询供应商失败: {e}")
        return f"查询供应商数据时出错，请稍后重试。"
    except Exception as e:
        logger.error(f"解析供应商数据失败: {e}")
        return f"解析供应商数据时出错，请稍后重试。"

    if not suppliers:
        return "当前系统中暂无供应商数据。"

    keyword_lower = keyword.strip().lower()
    matched = []

    for s in suppliers:
        region = s.get("region") or ""
        category = s.get("mainCategory") or ""
        name = s.get("supplierName") or ""
        code = s.get("supplierCode") or ""

        # 按地区匹配
        target_region = REGION_MAP.get(keyword)
        if target_region and region == target_region:
            matched.append(s)
            continue

        # 按品类关键词匹配
        matched_cat = False
        for kw, cat in CATEGORY_KEYWORDS.items():
            if kw in keyword_lower and cat in category:
                matched.append(s)
                matched_cat = True
                break
        if matched_cat:
            continue

        # 按名称模糊匹配
        if keyword in name:
            matched.append(s)
            continue

        # 按编码匹配
        if keyword.upper() == code.upper():
            matched.append(s)
            continue

    if not matched:
        return f"未找到与'{keyword}'匹配的供应商信息。可尝试使用地区名（华南/华东/华北/华中/西南/西北）、品类关键词（电子/机械/材料等）或供应商名称进行搜索。"

    result_parts = []
    for s in matched:
        status_label = STATUS_MAP.get(s.get("status"), str(s.get("status")))
        qual_label = QUAL_MAP.get(s.get("qualificationLevel"), str(s.get("qualificationLevel")))
        result_parts.append(
            f"供应商编码: {s.get('supplierCode', '')}, "
            f"名称: {s.get('supplierName', '')}, "
            f"地区: {s.get('region', '')}, "
            f"主营品类: {s.get('mainCategory', '')}, "
            f"资质等级: {qual_label}, "
            f"合作状态: {status_label}"
        )

    return "匹配到的供应商信息如下：\n" + "\n".join(result_parts)


@tool
def get_price_reference(material_name: str) -> str:
    """
    获取指定物料的采购价格参考（历史成交价）。
    入参 material_name: 物料名称，如"螺丝刀"、"电子芯片"、"不锈钢板"等。
    出参: 包含平均价、最低价、最高价的字符串。
    使用场景: 当需要对物料进行价格比较或预算评估时调用。
    """
    try:
        resp = requests.get(f"{JAVA_API_BASE}/api/order", timeout=10)
        resp.raise_for_status()
        result = resp.json()
        orders = result.get("data", [])
    except requests.RequestException as e:
        logger.error(f"查询订单失败: {e}")
        return f"查询订单数据时出错，请稍后重试。"
    except Exception as e:
        logger.error(f"解析订单数据失败: {e}")
        return f"解析订单数据时出错，请稍后重试。"

    if not orders:
        return f"当前系统中暂无订单数据，无法提供'{material_name}'的价格参考。"

    # 按物料名称模糊匹配
    matched = [o for o in orders if material_name in (o.get("materialName") or "")]
    prices = [float(o.get("unitPrice", 0)) for o in matched if o.get("unitPrice") is not None]

    if not prices:
        # 尝试反方向模糊匹配
        all_materials = list(set(o.get("materialName", "") for o in orders if o.get("materialName")))
        similar = [m for m in all_materials if material_name in m or m in material_name]
        if similar:
            matched = [o for o in orders if o.get("materialName") == similar[0]]
            prices = [float(o.get("unitPrice", 0)) for o in matched if o.get("unitPrice") is not None]
            if prices:
                avg_price = sum(prices) / len(prices)
                return (
                    f"未找到'{material_name}'的精确数据，近似物料'{similar[0]}'的价格参考：\n"
                    f"  历史平均价: {avg_price:.2f}元\n"
                    f"  历史最低价: {min(prices):.2f}元\n"
                    f"  历史最高价: {max(prices):.2f}元\n"
                    f"  成交记录数: {len(prices)}笔"
                )
        return f"未找到物料'{material_name}'的历史价格参考数据。可尝试使用其他物料名称查询。"

    avg_price = sum(prices) / len(prices)
    return (
        f"物料: {material_name}\n"
        f"  历史平均价: {avg_price:.2f}元\n"
        f"  历史最低价: {min(prices):.2f}元\n"
        f"  历史最高价: {max(prices):.2f}元\n"
        f"  成交记录数: {len(prices)}笔"
    )


@tool
def calculate_total_cost(unit_price: float, quantity: int) -> str:
    """
    计算采购总成本。
    入参 unit_price: 单价（元），quantity: 数量。
    出参: 总价计算结果字符串。
    使用场景: 当需要计算采购总金额时调用。
    """
    if unit_price <= 0 or quantity <= 0:
        return "计算失败：单价和数量必须大于0。"

    total = unit_price * quantity

    # 阶梯折扣
    discount = 0.0
    discount_note = "无折扣"
    if quantity >= 10000:
        discount = 0.10
        discount_note = "10%（批量>=10000）"
    elif quantity >= 5000:
        discount = 0.06
        discount_note = "6%（批量>=5000）"
    elif quantity >= 1000:
        discount = 0.03
        discount_note = "3%（批量>=1000）"

    discounted_total = total * (1 - discount)

    return (
        f"采购成本明细：\n"
        f"  单价: {unit_price}元 × 数量: {quantity} = 小计: {total:.2f}元\n"
        f"  阶梯折扣: {discount_note}, 折扣金额: {total * discount:.2f}元\n"
        f"  合计总价: {discounted_total:.2f}元"
    )


@tool
def get_supplier_status_flow() -> str:
    """
    获取供应商全生命周期状态流转规则。
    无入参。
    出参: 供应商状态机流转规则的文字说明。
    使用场景: 当需要了解供应商状态管理规则、判断状态变更是否合规时调用。
    """
    return (
        "供应商状态流转规则：\n"
        "0-注册 → 1-待审核（新供应商注册后进入待审核状态）\n"
        "1-待审核 → 2-已准入（审核通过，进入准入状态）\n"
        "1-待审核 → 5-黑名单（审核不通过，可拉黑）\n"
        "2-已准入 → 3-合作中（开始正式合作）\n"
        "3-合作中 → 4-冻结（合作出现问题，暂时冻结）\n"
        "4-冻结 → 3-合作中（问题解决，恢复合作）\n"
        "4-冻结 → 5-黑名单（严重违规，拉入黑名单）\n"
        "注意：禁止越级变更，如从'注册'直接变为'合作中'或'黑名单'（除待审核不通过外）。"
    )


@tool
def get_order_status_flow() -> str:
    """
    获取采购订单状态流转规则。
    无入参。
    出参: 订单状态机流转规则的文字说明。
    使用场景: 当需要了解订单状态管理规则、判断订单状态变更是否合规时调用。
    """
    return (
        "采购订单状态流转规则：\n"
        "待确认 → 生产中（供应商确认接单后进入生产）\n"
        "生产中 → 已发货（生产完成并发货）\n"
        "已发货 → 已签收（采购方确认收货）\n"
        "注意：\n"
        "1. 严格禁止越级变更状态（如从'待确认'直接变为'已发货'）。\n"
        "2. 每个状态变更都需要对应的业务操作和凭证。\n"
        "3. '已签收'为终态，不可再变更。"
    )
