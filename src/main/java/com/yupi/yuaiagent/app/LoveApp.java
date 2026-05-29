package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.advisor.SecurityCheckAdvisor;
import com.yupi.yuaiagent.advisor.SensitiveWordLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class LoveApp {

    private final ChatClient chatClient;
    private final SensitiveWordLoader sensitiveWordLoader;

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    /** 结构化报告专用 prompt：不要求问候，只要求 JSON 输出 */
    private static final String REPORT_SYSTEM_PROMPT =
            "你是恋爱分析报告生成器。根据用户输入生成恋爱报告。" +
            "title 为「用户名」的恋爱报告，suggestions 为字符串数组形式的建议列表。" +
            "严禁输出问候语、解释说明或 markdown 代码块，只能输出符合 schema 的 JSON。";

    public LoveApp(ChatModel dashscopeChatModel, SensitiveWordLoader sensitiveWordLoader, JdbcChatMemoryRepository chatMemoryRepository) throws IOException {
        this.sensitiveWordLoader = sensitiveWordLoader;

        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        //储存到文件中
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir, 10);
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();

        SecurityCheckAdvisor securityCheckAdvisor =
                new SecurityCheckAdvisor(sensitiveWordLoader.getSensitiveWords());

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(
                        securityCheckAdvisor,
                        new MyLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .advisors(spec -> spec.param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size", 10))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    record LoveReport(String title, List<String> suggestions) {}

    public LoveReport doChatWithReport(String message, String chatId) {
        if (sensitiveWordLoader.containsSensitiveWord(message)) {
            LoveReport blockedReport = new LoveReport(
                    "安全提醒", List.of(SecurityCheckAdvisor.BLOCKED_MESSAGE));
            log.info("LoveReport blocked: {}", blockedReport);
            return blockedReport;
        }
        LoveReport loveReport = chatClient
                .prompt()
                .system(REPORT_SYSTEM_PROMPT)
                .user(message)
                .advisors(spec -> spec.param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size", 10))
                .call()
                .entity(LoveReport.class);
        log.info("LoveReport: {}", loveReport);
        return loveReport;
    }
}
