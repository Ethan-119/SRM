package com.srm.modules.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.srm.modules.rag.entity.RagEvalResultEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 评测明细表 Mapper（srm_rag_eval_result）。
 */
@Mapper
public interface RagEvalResultMapper extends BaseMapper<RagEvalResultEntity> {
}
