package com.yupi.yuaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 安全风控拦截器：在请求发往模型前检查用户输入，命中敏感词则短路返回警告，不调用通义千问。
 */
@Slf4j
public class SecurityCheckAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String BLOCKED_MESSAGE =
            "您的提问包含敏感内容，已被系统安全拦截。";

    private final List<String> sensitiveWords;
    private final String warningResponse;
    private final int order;
    //第一个构造函数是便捷通道：只要传敏感词列表，默认就把拦截提示词设为“您的提问包含敏感内容...”，且优先级拉满
    public SecurityCheckAdvisor(List<String> sensitiveWords) {
        this(sensitiveWords, BLOCKED_MESSAGE, Ordered.HIGHEST_PRECEDENCE);
    }
    //第二个构造函数是完全体：允许外部自定义警告词、自定义拦截器排序。
    public SecurityCheckAdvisor(List<String> sensitiveWords, String warningResponse, int order) {
        this.sensitiveWords = List.copyOf(sensitiveWords);
        this.warningResponse = warningResponse;
        this.order = order;
    }
    //普通单次对话
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest,
                                         CallAdvisorChain callAdvisorChain) {
        if (containsSensitiveWord(chatClientRequest)) {
            log.warn("SecurityCheckAdvisor blocked request: sensitive content detected");
            return createWarningResponse(chatClientRequest);// 👈 拦截！返回伪造的警告响应
        }
        return callAdvisorChain.nextCall(chatClientRequest);// 👈 放行！进入下一个拦截器或发给大模型
    }
    //流式对话
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                                 StreamAdvisorChain streamAdvisorChain) {
        if (containsSensitiveWord(chatClientRequest)) {
            log.warn("SecurityCheckAdvisor blocked stream request: sensitive content detected");
            return Flux.just(createWarningResponse(chatClientRequest));// 👈 拦截！返回伪造的警告响应
        }
        return streamAdvisorChain.nextStream(chatClientRequest);// 👈 放行！进入下一个拦截器或发给大模型
    }
    //判断对话中是否含有敏感词
    private boolean containsSensitiveWord(ChatClientRequest chatClientRequest) {
        String userText = chatClientRequest.prompt().getUserMessage().getText();
        if (!StringUtils.hasText(userText)) {
            return false;
        }
        return sensitiveWords.stream().anyMatch(userText::contains);
    }
    //直接返回触发敏感词的警告
    private ChatClientResponse createWarningResponse(ChatClientRequest chatClientRequest) {
        ChatResponse chatResponse = ChatResponse.builder()
                .generations(List.of(new Generation(new AssistantMessage(warningResponse))))
                .build();
        return ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .context(Map.copyOf(chatClientRequest.context()))
                .build();
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    public static boolean isBlocked(String content) {
        return BLOCKED_MESSAGE.equals(content);
    }
}
