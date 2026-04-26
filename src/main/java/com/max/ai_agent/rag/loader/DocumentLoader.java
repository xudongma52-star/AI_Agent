package com.max.ai_agent.rag.loader;

import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentLoader {

    List<Document> load();
}