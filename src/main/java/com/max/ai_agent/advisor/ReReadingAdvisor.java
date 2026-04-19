package com.max.ai_agent.advisor;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

public class ReReadingAdvisor implements BaseAdvisor {

    public static final String DEFAULT_RE2_TEMPLATE =
            "Read the question again: {re2_input_query}\n\n***";

    private final String re2AdviseTemplate;
    private final int order;

    public ReReadingAdvisor() {
        this(DEFAULT_RE2_TEMPLATE, 0);
    }

    public ReReadingAdvisor(String re2AdviseTemplate) {
        this(re2AdviseTemplate, 0);
    }

    public ReReadingAdvisor(String re2AdviseTemplate, int order) {
        this.re2AdviseTemplate = re2AdviseTemplate;
        this.order = order;
    }

    @Override
    public AdvisedRequest before(AdvisedRequest request) {
        String userText = request.userText();

        // 空值防御
        if (userText == null || userText.isBlank()) {
            return request;
        }

        // render 时传入变量，语义更清晰
        PromptTemplate promptTemplate = new PromptTemplate(this.re2AdviseTemplate);
        String augmentedUserText = promptTemplate.render(
                Map.of("re2_input_query", userText)
        );

        return AdvisedRequest.from(request)
                .userText(augmentedUserText)
                .build();
    }

    @Override
    public AdvisedResponse after(AdvisedResponse advisedResponse) {
        return advisedResponse;
    }

    @Override
    public int getOrder() {
        return this.order;
    }
}