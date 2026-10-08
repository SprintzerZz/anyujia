package com.anyujia.anyujiachatservice.service.moderation;

import com.anyujia.anyujiacommondomain.domain.ResultCode;
import com.anyujia.anyujiacommondomain.exception.ServiceException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultChatContentModerationService implements ChatContentModerationService {
    private final ChatContentFilterProperties properties;

    @Override
    public String moderate(String content) {
        if (!properties.isEnabled() || content == null || content.isEmpty()) {
            return content;
        }
        List<String> words = properties.getSensitiveWords().stream()
                .filter(word -> word != null && !word.isBlank())
                .map(String::trim)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        if (words.isEmpty()) {
            return content;
        }
        String result = content;
        boolean matched = false;
        for (String word : words) {
            if (!containsIgnoreCase(result, word)) {
                continue;
            }
            matched = true;
            if (properties.getMode() == ChatContentFilterProperties.Mode.REJECT) {
                throw new ServiceException("Message contains sensitive content",
                        ResultCode.INVALID_PARA.getCode());
            }
            result = replaceIgnoreCase(result, word, properties.getReplacement());
        }
        return matched ? result : content;
    }

    private boolean containsIgnoreCase(String content, String word) {
        return content.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT));
    }

    private String replaceIgnoreCase(String content, String word, String replacement) {
        StringBuilder result = new StringBuilder(content.length());
        String lowerContent = content.toLowerCase(Locale.ROOT);
        String lowerWord = word.toLowerCase(Locale.ROOT);
        int from = 0;
        int index;
        while ((index = lowerContent.indexOf(lowerWord, from)) >= 0) {
            result.append(content, from, index).append(replacement);
            from = index + word.length();
        }
        return result.append(content, from, content.length()).toString();
    }
}
