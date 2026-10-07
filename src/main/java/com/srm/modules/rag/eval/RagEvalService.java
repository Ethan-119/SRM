package com.srm.modules.rag.eval;

import com.srm.modules.rag.eval.model.EvalCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 黄金标准数据集加载服务。
 * 支持 classpath: 与 file: 前缀，默认从 classpath:eval/golden-dataset.yml 读取。
 */
@Service
public class RagEvalService {

    private final List<EvalCase> goldenCases;

    public RagEvalService(@Value("${srm.rag-eval.dataset:classpath:eval/golden-dataset.yml}") String location) {
        this.goldenCases = load(location);
    }

    public List<EvalCase> loadGoldenDataset() {
        return goldenCases;
    }

    @SuppressWarnings("unchecked")
    private List<EvalCase> load(String location) {
        try (InputStream is = open(location)) {
            Yaml yaml = new Yaml();
            Object obj = yaml.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            if (obj == null) {
                return List.of();
            }
            if (!(obj instanceof List<?> rawList)) {
                throw new IllegalStateException("黄金标准数据集必须是 YAML 列表: " + location);
            }
            List<EvalCase> cases = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> map) {
                    cases.add(toCase(map));
                }
            }
            return cases;
        } catch (IOException e) {
            throw new IllegalStateException("加载黄金标准数据集失败: " + location, e);
        }
    }

    private InputStream open(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            return new ClassPathResource(location.substring("classpath:".length())).getInputStream();
        }
        return new FileSystemResource(location).getInputStream();
    }

    private EvalCase toCase(Map<?, ?> map) {
        EvalCase c = new EvalCase();
        c.setId(str(map.get("id")));
        c.setQuestion(str(map.get("question")));
        c.setExpectedDocIds(strList(map.get("expectedDocIds")));
        c.setExpectedAnswerContains(strList(map.get("expectedAnswerContains")));
        c.setTags(strList(map.get("tags")));
        c.setDifficulty(str(map.get("difficulty")));
        return c;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private List<String> strList(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(String.valueOf(item));
        }
        return result;
    }
}
