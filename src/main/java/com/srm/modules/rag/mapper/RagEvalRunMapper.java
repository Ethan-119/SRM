package com.srm.modules.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.srm.modules.rag.entity.RagEvalRun;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 评测批次表 Mapper（srm_rag_eval_run）。
 */
@Mapper
public interface RagEvalRunMapper extends BaseMapper<RagEvalRun> {
}
