package com.yupi.yuaiagent.memorystation;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于本地文件系统和 Kryo 序列化的持久化聊天记忆组件
 * 能够保证项目重启后多轮对话上下文记忆不丢失
 */
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;
    private static final Kryo kryo = new Kryo();
    private final int maxMessages;

    static {
        // 关闭显式注册要求，允许动态序列化任意未注册的类（非常适合 Spring AI 复杂的 Message 实现类）
        kryo.setRegistrationRequired(false);
        // 使用 Objenesis 实例化策略，防止部分没有无参构造函数的类反序列化失败
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    /**
     * 构造函数
     * @param dir 指定持久化文件存放的本地文件夹路径
     */
    public FileBasedChatMemory(String dir,int maxMessages) {
        Assert.isTrue(maxMessages > 0, "maxMessages must be greater than 0");
        this.maxMessages = maxMessages;
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs(); // 如果目录不存在，自动层级创建
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        Assert.notNull(messages, "messages cannot be null");
        // 1. 先读取当前会话现有的历史记录
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        // 2. 把本次新增的消息追加进去
        conversationMessages.addAll(messages);
        // 3. 重新持久化写入本地文件
        saveConversation(conversationId, conversationMessages);
    }

    @Override
    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");

        List<Message> allMessages = getOrCreateConversation(conversationId);
        // 在内部利用你设定的 maxMessages 进行安全截取
        return allMessages.stream()
                .skip(Math.max(0, allMessages.size() - this.maxMessages))
                .toList();
    }



    @Override
    public void clear(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        // 清除记忆：直接物理删除对应的本地序列化文件
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 读取本地文件获取历史消息，若文件不存在则初始化空列表
     */
    @SuppressWarnings("unchecked")
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                // 使用 Kryo 反序列化回 ArrayList 对象
                messages = kryo.readObject(input, ArrayList.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return messages;
    }

    /**
     * 将消息列表通过 Kryo 二进制流安全地写入本地磁盘
     */
    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, messages);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 拼接标准的文件存储路径
     */
    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}
