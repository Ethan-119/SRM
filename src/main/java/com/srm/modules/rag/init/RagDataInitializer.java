package com.srm.modules.rag.init;

import com.srm.modules.rag.service.RagDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG 文档一次性灌库器。
 *
 * <p>仅在 {@code srm.rag.ingest-on-startup=true} 时启动执行，把演示文档
 * （供应商资料 / 物料价格）写入：主数据表 {@code rag_document} + 向量索引 {@code vector_store}。
 * 写入幂等（{@link RagDocumentService#add} 内部对主数据和向量均做 upsert）。</p>
 *
 * <p>运行方式：{@code --srm.rag.ingest-on-startup=true}。</p>
 *
 * <p>文档 ID 与 {@code eval/golden-dataset.yml} 的 {@code expectedDocIds} 对齐；
 * 正文中的供应商名称必须与 Neo4j 图谱 {@code Supplier.name} 完全一致，
 * 供 {@code HybridRagService} 结构化过滤使用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "srm.rag.ingest-on-startup", havingValue = "true")
public class RagDataInitializer implements CommandLineRunner {

    private final RagDocumentService ragDocumentService;

    @Override
    public void run(String... args) {
        log.info("开始灌入 RAG 向量演示文档，共 {} 条", DOCS.size());
        for (Doc doc : DOCS) {
            ragDocumentService.add(doc.id(), null, doc.content(), null, doc.metadata());
            log.info("已写入文档: {}", doc.id());
        }
        log.info("RAG 向量灌库完成");
    }

    private record Doc(String id, String content, Map<String, Object> metadata) {
    }

    private static Doc doc(String id, String content) {
        return new Doc(id, content, null);
    }

    private static final List<Doc> DOCS = List.of(
            // ---- 冷轧钢板 ----
            doc("supplier-2-冷轧钢板",
                    "上海宝钢精密钢材有限公司位于华东，主营钢材，供应冷轧钢板，单价8500元/吨，最小起订量50吨，交货期15天，资质等级高级，ISO9001资质有效。"),
            doc("supplier-15-冷轧钢板",
                    "济南钢铁集团销售有限公司位于华北，主营钢材，供应冷轧钢板，单价8300元/吨，最小起订量100吨，交货期20天，资质待审核。"),
            doc("supplier-23-冷轧钢板",
                    "大连船舶重工配套有限公司位于东北，主营钢材，供应冷轧钢板，单价8600元/吨，最小起订量80吨，交货期25天。"),
            // ---- 不锈钢板 ----
            doc("supplier-2-不锈钢板",
                    "上海宝钢精密钢材有限公司供应不锈钢板304，单价18500元/吨，最小起订量25吨，交货期15天。"),
            doc("supplier-39-不锈钢板",
                    "太原钢铁集团有限公司位于华北，主营钢材，供应不锈钢板，单价18000元/吨，最小起订量50吨，交货期30天，资质待审核。"),
            // ---- STM32 单片机 ----
            doc("supplier-1-STM32",
                    "深圳华强电子科技有限公司位于华南，主营电子元器件，供应STM32F407VET6单片机，单价32元/个，最小起订量500个，交货期8天。"),
            doc("supplier-7-STM32",
                    "武汉光谷光电科技有限公司位于华中，主营电子元器件，供应STM32单片机，单价35元/个，最小起订量300个，交货期10天。"),
            doc("supplier-24-STM32",
                    "无锡华润微电子有限公司位于华东，主营电子元器件，供应STM32单片机，单价30元/个，最小起订量1000个，交货期12天。"),
            // ---- 关联企业 ----
            doc("supplier-2",
                    "上海宝钢精密钢材有限公司主营钢材，与太原钢铁集团有限公司存在股权关联（太原钢铁为其控股子公司），与济南钢铁集团销售有限公司存在法人关联。"),
            doc("supplier-15",
                    "济南钢铁集团销售有限公司主营钢材，与上海宝钢精密钢材有限公司存在董事关联。"),
            doc("supplier-39",
                    "太原钢铁集团有限公司主营钢材（不锈钢），是上海宝钢精密钢材有限公司的控股子公司，同属一个集团。"),
            // ---- 历史均价 ----
            new Doc("order-不锈钢板-avg",
                    "不锈钢板历史成交均价18500元/吨，成交供应商为上海宝钢精密钢材有限公司。",
                    null)
    );
}
