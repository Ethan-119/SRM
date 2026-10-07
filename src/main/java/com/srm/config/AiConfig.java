package com.srm.config;

import com.srm.modules.agent.tools.GraphAnalysisTools;
import com.srm.modules.agent.tools.PriceTools;
import com.srm.modules.agent.tools.StatusFlowTools;
import com.srm.modules.agent.tools.SupplierTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置：注册 Agent 工具与 ChatClient。
 */
@Configuration
public class AiConfig {

    /**
     * 直接传入工具对象（Spring AI 会自动扫描 @Tool 注解方法）。
     * 注意：不能把 ToolCallbackProvider 传给 defaultTools（会走 Object... 重载导致扫描不到注解）。
     */
    @Bean
    public ChatClient agentChatClient(ChatModel chatModel,
                                      SupplierTools supplierTools,
                                      PriceTools priceTools,
                                      StatusFlowTools statusFlowTools,
                                      GraphAnalysisTools graphAnalysisTools) {
        return ChatClient.builder(chatModel)
                .defaultTools(supplierTools, priceTools, statusFlowTools, graphAnalysisTools)
                .build();
    }
}
