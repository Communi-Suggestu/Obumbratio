package com.communi.suggestu.obumbratio.utils;

import org.gradle.api.Project;

public final class RepositoryUtils {

    private RepositoryUtils() {
        throw new IllegalStateException("Tried to instantiate: 'RepositoryUtils', but this is a utility class.");
    }

    public static void configureRepositories(final Project project) {
        project.getRepositories().maven(repo -> {
            repo.setUrl("https://ldtteam.jfrog.io/ldtteam/modding/");
            repo.setName("LDTTeam - Modding");
        });
        project.getRepositories().exclusiveContent(content -> {
            content.forRepository(() -> project.getRepositories().maven(repo -> {
                repo.setUrl("https://api.modrinth.com/maven");
                repo.setName("Modrinth");
            }));
            content.filter(filter -> {
                filter.includeGroup("maven.modrinth");
            });
        });
        project.getRepositories().maven(repo -> {
            repo.setUrl("https://maven.su5ed.dev/releases");
            repo.setName("Su5ed");
        });
    }
}
