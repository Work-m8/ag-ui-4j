package io.workm8.agui4j.core.message;

public enum Role {
    assistant("assistant"),
    developer("developer"),
    system("system"),
    tool("tool"),
    user("user")
    ;

    private String name;

    Role(final String name) {
        this.name = name;
    }

}
