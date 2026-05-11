package com.example.mylearning.network;

import java.util.List;

public class GeminiResponse {

    public List<Candidate> candidates;

    // Safely extracts the text from the nested response structure
    public String getResponseText() {
        if (candidates != null && !candidates.isEmpty()) {
            Candidate c = candidates.get(0);
            if (c.content != null && c.content.parts != null && !c.content.parts.isEmpty()) {
                return c.content.parts.get(0).text;
            }
        }
        return null; // caller handles null as a failure state
    }

    public static class Candidate {
        public Content content;
    }

    public static class Content {
        public List<Part> parts;
    }

    public static class Part {
        public String text;
    }
}