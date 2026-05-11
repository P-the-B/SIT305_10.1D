package com.example.mylearning.util;

import com.example.mylearning.data.entity.Topic;

import java.util.Arrays;
import java.util.List;

public class TopicSeeder {

    public static List<Topic> getTopics() {
        return Arrays.asList(
                // Law
                topic("Criminal Law",         "Law"),
                topic("Contract Law",         "Law"),
                topic("Tort Law",             "Law"),
                topic("Constitutional Law",   "Law"),
                topic("International Law",    "Law"),

                // Medicine
                topic("Anatomy",              "Medicine"),
                topic("Physiology",           "Medicine"),
                topic("Pathology",            "Medicine"),
                topic("Pharmacology",         "Medicine"),
                topic("Neuroscience",         "Medicine"),

                // Engineering
                topic("Mechanics",            "Engineering"),
                topic("Thermodynamics",       "Engineering"),
                topic("Circuits",             "Engineering"),
                topic("Fluid Dynamics",       "Engineering"),
                topic("Materials Science",    "Engineering"),

                // Mathematics
                topic("Algebra",              "Mathematics"),
                topic("Calculus",             "Mathematics"),
                topic("Statistics",           "Mathematics"),
                topic("Linear Algebra",       "Mathematics"),
                topic("Discrete Mathematics", "Mathematics"),

                // Computer Science
                topic("Algorithms",           "Computer Science"),
                topic("Data Structures",      "Computer Science"),
                topic("Operating Systems",    "Computer Science"),
                topic("Networking",           "Computer Science"),
                topic("Cybersecurity",        "Computer Science"),

                // Business
                topic("Accounting",           "Business"),
                topic("Marketing",            "Business"),
                topic("Economics",            "Business"),
                topic("Finance",              "Business"),
                topic("Management",           "Business"),

                // Science
                topic("Physics",              "Science"),
                topic("Chemistry",            "Science"),
                topic("Biology",              "Science"),
                topic("Astronomy",            "Science"),
                topic("Environmental Science","Science"),

                // Fun Facts — random trivia, no sub-choice needed
                topic("Fun Facts",            "Fun Facts")
        );
    }

    private static Topic topic(String name, String category) {
        Topic t = new Topic();
        t.name = name;
        t.category = category;
        return t;
    }
}