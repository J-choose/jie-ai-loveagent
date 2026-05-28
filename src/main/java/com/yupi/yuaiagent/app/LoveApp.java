package com.yupi.yuaiagent.app;

import com.yupi.yuaiagent.FileBasedChatMemory;
import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import com.yupi.yuaiagent.advisor.ReReadingAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

@Component
@Slf4j
/**
 *
 */
public class LoveApp {
    private final ChatClient chatClient;
    //默认系统prompt
    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
            "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
            "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
            "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";

    public LoveApp(ChatModel dashscopeChatModel) {
        //基于文件储存是对话记忆长久保存

        String FileDir = System.getProperty("user.dir") +"/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(FileDir,10);
        //基于内存的对话记忆
//            ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
            chatClient = ChatClient.builder(dashscopeChatModel)
                    .defaultSystem(SYSTEM_PROMPT)
                    .defaultAdvisors(
                            //自定义日志拦截器
                            new MyLoggerAdvisor(),
                            //重复读取拦截器
                            //new ReReadingAdvisor(),
                            MessageChatMemoryAdvisor.builder(chatMemory).build()

                    )
                    .build();
    }

    /**
     * ai基础对话，实现多轮对话
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)// 把你当前输入的聊天内容（message）塞进去
                .advisors(spec -> spec.param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size", 10))// ... 带着上面指定的 chatId 变量
                .call()// 啪，正式向阿里云通义千问发起网络请求
                .chatResponse();// 拿到完整的、结构化的云端响应对象
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }



    /**
     * 结构化输出
     * @param
     * @param
     * @return
     */
    record LoveReport(String title, List<String> suggestions) {}
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)// 把你当前输入的聊天内容（message）塞进去
                .advisors(spec -> spec.param("chat_memory_conversation_id", chatId)
                        .param("chat_memory_retrieve_size", 10))// ... 带着上面指定的 chatId 变量
                .call()// 啪，正式向阿里云通义千问发起网络请求
                .entity(LoveReport.class);// 拿到完整的、结构化的云端响应对象
        log.info("LoveReport: {}",loveReport);
        return loveReport;
    }

}
