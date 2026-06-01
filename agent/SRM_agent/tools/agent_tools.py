import os
import requests
from langchain.tools import tool
from rag.rag_service import RagSummarizeService
from rag.rag_eval_service import rag_eval
from utils.logger_handler import logger
import threading

# Java 后端 API 地址
JAVA_API_BASE = os.getenv("JAVA_API_BASE", "http://localhost:8083")

# 内部 API Key，用于调用 Java 后端受保护的接口
AGENT_API_KEY = os.getenv("AGENT_API_KEY", "srm-agent-internal-key-2026")

# 初始化 RAG 服务
rag = RagSummarizeService()

# 线程本地存储：用于在工具调用时传递 session_id
_thread_local = threading.local()

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
    # 先检索（用于日志记录）
    docs = rag.retriever_docs(query)
    # 生成 RAG 回答
    response = rag.rag_summarize(query)
    # 记录到评估日志
    session_id = getattr(_thread_local, 'session_id', 'unknown')
    rag_eval.log_query(session_id, query, docs, k=3, response=response)
    logger.info(f"[RAG Eval] logged query: '{query[:50]}...' -> {len(docs)} docs")
    return response


@tool
def search_suppliers(keyword: str) -> str:
    """
    根据关键词搜索匹配的供应商信息，支持地区与品类的组合筛选。
    入参 keyword: 搜索关键词，可以是地区（华南/华东/华北/华中/西南）、
         品类关键词（电子/元器件/机械/材料/精密/金属等）、
         地区+品类组合（如"华东电子"、"华南机械"），或供应商名称的一部分。
    出参: 匹配的供应商列表信息（JSON格式字符串），包含供应商编码、名称、地区、主营品类、资质等级、合作状态。
    使用场景: 当需要查找符合特定条件的供应商时调用。如需同时筛选地区和品类，请传入组合关键词。
    """
    try:
        resp = requests.get(f"{JAVA_API_BASE}/api/supplier", timeout=10,
                             headers={"X-Api-Key": AGENT_API_KEY})
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

    # 1. 解析关键词：尝试提取地区和品类
    target_region = None
    target_category = None
    remaining = keyword_lower

    for region_name in REGION_MAP:
        if region_name in keyword:
            target_region = REGION_MAP[region_name]
            remaining = remaining.replace(region_name, "")
            break

    for cat_name in CATEGORY_KEYWORDS:
        if cat_name in remaining:
            target_category = CATEGORY_KEYWORDS[cat_name]
            break

    # 2. AND 匹配：同时满足地区和品类要求
    matched = []
    for s in suppliers:
        region = s.get("region") or ""
        category = s.get("mainCategory") or ""
        name = s.get("supplierName") or ""
        code = s.get("supplierCode") or ""

        # 如果指定了地区和品类，两者都必须匹配
        if target_region and target_category:
            if target_region == region and target_category in category:
                matched.append(s)
        elif target_region:
            if target_region == region:
                matched.append(s)
        elif target_category:
            if target_category in category:
                matched.append(s)
        else:
            # 纯文本匹配（按名称/编码/地区/品类模糊匹配）
            if (keyword in name or keyword.upper() == code.upper()
                    or keyword_lower in region.lower()):
                matched.append(s)

    if not matched:
        hint_parts = []
        if target_region and target_category:
            hint_parts.append(f"在'{target_region}'地区且主营品类含'{target_category}'")
        elif target_region:
            hint_parts.append(f"在'{target_region}'地区")
        elif target_category:
            hint_parts.append(f"主营品类含'{target_category}'")
        else:
            hint_parts.append(f"与'{keyword}'相关")
        hint = "、".join(hint_parts)
        return f"未找到{hint}的供应商信息。可尝试使用地区名（华南/华东/华北/华中/西南/西北）、品类关键词（电子/机械/材料等）或供应商名称进行搜索。"

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
        resp = requests.get(f"{JAVA_API_BASE}/api/order", timeout=10,
                             headers={"X-Api-Key": AGENT_API_KEY})
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


@tool
def compare_supplier_quotes(material_name: str, quantity: int) -> str:
    """
    询比价工具：对指定物料进行供应商报价综合比较，输出排序后的比价结果。
    封装了完整的询比价业务逻辑：供应商筛选 → 价格查询 → 阶梯折扣计算 → 综合排名。

    入参 material_name: 物料名称，如"冷轧钢板"、"STM32单片机"、"瓦楞纸箱"等。
    入参 quantity: 采购数量（整数）。

    出参: 按总成本升序排列的供应商报价比较表，包含供应商名称、单价、总成本、资质、合作状态、推荐建议。

    使用场景: 当用户需要对某项物料进行询比价、选择最优供应商时调用。
    注意：此工具仅比较已有历史订单记录的供应商，首次采购的物料需走正式询价流程。
    """
    if quantity <= 0:
        return "比价失败：采购数量必须大于0。"

    # 1. 获取所有订单
    try:
        resp = requests.get(f"{JAVA_API_BASE}/api/order", timeout=10,
                             headers={"X-Api-Key": AGENT_API_KEY})
        resp.raise_for_status()
        orders = resp.json().get("data", [])
    except Exception as e:
        logger.error(f"[比价] 获取订单失败: {e}")
        return "比价失败：无法获取订单数据，请稍后重试。"

    if not orders:
        return f"当前系统中暂无订单数据，无法对'{material_name}'进行比价。"

    # 2. 获取所有供应商（用于补充供应商信息）
    try:
        resp = requests.get(f"{JAVA_API_BASE}/api/supplier", timeout=10,
                             headers={"X-Api-Key": AGENT_API_KEY})
        resp.raise_for_status()
        suppliers = resp.json().get("data", [])
    except Exception as e:
        logger.warning(f"[比价] 获取供应商失败: {e}，将仅使用订单数据")
        suppliers = []

    # 构建供应商映射：supplier_id → supplier_info
    supplier_map = {s["id"]: s for s in suppliers}

    # 3. 筛选物料匹配的订单
    matched = [o for o in orders if material_name in (o.get("materialName") or "")]
    if not matched:
        all_materials = list(set(o.get("materialName", "") for o in orders if o.get("materialName")))
        similar = [m for m in all_materials if material_name in m or m in material_name]
        if similar:
            return (
                f"未找到'{material_name}'的精确匹配，近似物料有：\n" +
                "\n".join(f"  - {m}" for m in similar[:10]) +
                f"\n请指定具体物料名称后重新比价。"
            )
        return f"未找到物料'{material_name}'的历史订单记录。该物料可能为首次采购，建议走正式询价流程。"

    # 4. 按供应商分组，取最优单价
    supplier_prices = {}
    for o in matched:
        sid = o.get("supplierId")
        price = float(o.get("unitPrice", 0))
        if sid not in supplier_prices or price < supplier_prices[sid]["unit_price"]:
            supplier_prices[sid] = {
                "unit_price": price,
                "supplier_id": sid,
                "material_name": o.get("materialName", ""),
                "order_count": 0,
            }
        supplier_prices[sid]["order_count"] += 1

    # 5. 计算总成本（含阶梯折扣）
    def calc_discount(qty):
        if qty >= 10000:
            return 0.10, "10%"
        elif qty >= 5000:
            return 0.06, "6%"
        elif qty >= 1000:
            return 0.03, "3%"
        return 0.0, "无"

    results = []
    for sid, info in supplier_prices.items():
        unit_price = info["unit_price"]
        total = unit_price * quantity
        discount_rate, discount_label = calc_discount(quantity)
        discounted_total = total * (1 - discount_rate)

        sup = supplier_map.get(sid, {})
        status_code = sup.get("status")
        qual_code = sup.get("qualificationLevel")

        results.append({
            "supplier_name": sup.get("supplierName", f"供应商{sid}"),
            "supplier_code": sup.get("supplierCode", ""),
            "region": sup.get("region", "未知"),
            "unit_price": unit_price,
            "total_raw": total,
            "discount_label": discount_label,
            "discount_amount": total * discount_rate,
            "total_cost": discounted_total,
            "status": STATUS_MAP.get(status_code, "未知"),
            "status_code": status_code,
            "qualification": QUAL_MAP.get(qual_code, "未知"),
            "qual_code": qual_code,
            "order_count": info["order_count"],
        })

    # 6. 按总成本升序排列
    results.sort(key=lambda x: x["total_cost"])

    # 7. 生成比价报告
    lines = [
        f"物料「{material_name}」询比价报告",
        f"采购数量: {quantity:,} | 报价供应商数: {len(results)}",
        "=" * 60,
    ]

    for i, r in enumerate(results):
        # 推荐标记
        flags = []
        if i == 0 and r["status_code"] in (2, 3):
            flags.append("★ 最低价")
        if r["status_code"] == 3:
            flags.append("合作中")
        elif r["status_code"] == 2:
            flags.append("已准入")
        elif r["status_code"] in (4, 5):
            flags.append("⚠ 风险")

        flag_str = f" [{', '.join(flags)}]" if flags else ""

        lines.append(
            f"\n#{i + 1} {r['supplier_name']} ({r['supplier_code']}){flag_str}\n"
            f"  地区: {r['region']} | 资质: {r['qualification']} | 状态: {r['status']}\n"
            f"  单价: ¥{r['unit_price']:,.2f} × {quantity:,} = ¥{r['total_raw']:,.2f}\n"
            f"  阶梯折扣: {r['discount_label']} (-¥{r['discount_amount']:,.2f})\n"
            f"  总成本: ¥{r['total_cost']:,.2f}\n"
            f"  历史成交: {r['order_count']}笔"
        )

    # 8. 综合建议
    lines.append("\n" + "=" * 60)
    lines.append("综合建议:")

    eligible = [r for r in results if r["status_code"] in (2, 3)]
    if eligible:
        best = eligible[0]
        lines.append(
            f"推荐选择「{best['supplier_name']}」："
            f"总成本最低（¥{best['total_cost']:,.2f}），"
            f"资质{best['qualification']}，状态{best['status']}，"
            f"有{best['order_count']}笔历史成交记录。"
        )
        if len(eligible) > 1:
            backup = eligible[1]
            lines.append(
                f"备选「{backup['supplier_name']}」："
                f"总成本 ¥{backup['total_cost']:,.2f}，"
                f"可作为议价参考。"
            )
    else:
        lines.append("当前无可用供应商（合作中/已准入状态）。")

    risk_suppliers = [r for r in results if r["status_code"] in (4, 5)]
    if risk_suppliers:
        names = "、".join(r["supplier_name"] for r in risk_suppliers)
        lines.append(f"⚠ 注意：{names} 存在风险状态，不建议选择。")

    return "\n".join(lines)
