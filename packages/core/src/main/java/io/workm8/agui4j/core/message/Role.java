package io.workm8.agui4j.core.message;

public enum Role {
    Assistant("assistant"),
    Developer("developer"),
    System("system"),
    Tool("tool"),
    User("user")
    ;

    private String name;

    Role(final String name) {
        this.name = name;
    }
}
