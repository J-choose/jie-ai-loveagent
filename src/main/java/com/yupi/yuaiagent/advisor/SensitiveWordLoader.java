package com.yupi.yuaiagent.advisor;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 从 classpath 下的 sensitive-words.txt 加载敏感词，一行一个词。
 */
@Component
public class SensitiveWordLoader {

    private static final String SENSITIVE_WORDS_FILE = "sensitive-words.txt";

    private final List<String> sensitiveWords;

    public SensitiveWordLoader() throws IOException {
        this.sensitiveWords = loadFromFile();
    }

    public List<String> getSensitiveWords() {
        return sensitiveWords;
    }

    public boolean containsSensitiveWord(String text) {
        //判断txt文件中是否含有空字符串以及空格
        if (!StringUtils.hasText(text)) {
            return false;
        }
        //通过流的方式判断用户对话中是否包含敏感词
        return sensitiveWords.stream().anyMatch(text::contains);
    }
    //读取文件
    private List<String> loadFromFile() throws IOException {
        ClassPathResource resource = new ClassPathResource(SENSITIVE_WORDS_FILE);
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        }
    }
}
