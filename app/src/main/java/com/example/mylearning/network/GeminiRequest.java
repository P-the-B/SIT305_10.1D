package com.example.mylearning.network;

import java.util.Collections;
import java.util.List;

public class GeminiRequest {

    public List<Content> contents;

    public GeminiRequest(String prompt) {
        Part part = new Part(prompt);
        Content content = new Content(Collections.singletonList(part));
        this.contents = Collections.singletonList(content);
    }

    public static class Content {
        public List<Part> parts;
        public Content(List<Part> parts) { this.parts = parts; }
    }

    public static class Part {
        public String text;
        public Part(String text) { this.text = text; }
    }
}