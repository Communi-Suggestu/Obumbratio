package com.communi.suggestu.obumbratio.model;

import com.communi.suggestu.obumbratio.extensions.ShadersExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Provider;

import java.util.function.Function;

public enum Implementation {
    SODIUM(p -> true),
    EMBEDDIUM(Platform::isNeoForge);

    private final Function<Platform, Boolean> supportedPlatforms;

    Implementation(Function<Platform, Boolean> supportedPlatforms) {
        this.supportedPlatforms = supportedPlatforms;
    }

    public boolean isSupported(Platform platform) {
        return supportedPlatforms.apply(platform);
    }

    public boolean requiresDownloadedIris() {
        return this == EMBEDDIUM;
    }

    public void registerFabricDependencies(Project project, ShadersExtension.Versions versions, ConfigurationSetup configurations) {
        if (this == SODIUM) {
            registerSodiumFabricDependencies(project, versions, configurations);
        }
    }

    public void registerNeoForgeDependencies(Project project, ShadersExtension.Versions versions, ConfigurationSetup configurations) {
        if (this == SODIUM) {
            registerSodiumNeoForgeDependencies(project, versions, configurations);
        } else if (this == EMBEDDIUM) {
            registerEmbeddiumNeoForgeDependencies(project, versions, configurations);
        }
    }

    public static void registerSodiumFabricDependencies(Project project, ShadersExtension.Versions versions, ConfigurationSetup configurations) {
        final Provider<Dependency> sodium =
                versions.getMinecraft().zip(
                        versions.getSodium().getVersion(),
                        "maven.modrinth:sodium:mc%s-%s-fabric"::formatted
                ).map(project.getDependencies()::create);

        configurations.localCompileOnly().getDependencies().addLater(sodium);
        configurations.localRuntimeOnly().getDependencies().addLater(sodium);
    }

    public static void registerSodiumNeoForgeDependencies(Project project, ShadersExtension.Versions versions, ConfigurationSetup configurations) {
        final Provider<Dependency> sodium =
                versions.getMinecraft().zip(
                        versions.getSodium().getVersion(),
                        "maven.modrinth:sodium:mc%s-%s-neoforge"::formatted
                ).map(project.getDependencies()::create);
        final Provider<Dependency> fabricApi =
                versions.getSodium().getFabricApi()
                        .map("org.sinytra.forgified-fabric-api:fabric-api-base:%s"::formatted)
                        .map(project.getDependencies()::create);
        final Provider<Dependency> fabricRenderer =
                versions.getSodium().getFabricRenderer()
                        .map("org.sinytra.forgified-fabric-api:fabric-renderer-api-v1:%s"::formatted)
                        .map(project.getDependencies()::create);

        configurations.localCompileOnly().getDependencies().addLater(sodium);
        configurations.localRuntimeOnly().getDependencies().addLater(sodium);

        configurations.localCompileOnly().getDependencies().addLater(fabricApi);
        configurations.localCompileOnly().getDependencies().addLater(fabricRenderer);
    }

    public static void registerEmbeddiumNeoForgeDependencies(Project project, ShadersExtension.Versions versions, ConfigurationSetup configurations) {
        final Provider<Dependency> embeddium =
                versions.getEmbeddium()
                        .zip(
                                versions.getMinecraft(),
                                "maven.modrinth:embeddium:%s+mc%s"::formatted
                        ).map(project.getDependencies()::create);
        final Provider<Dependency> monocle =
                versions.getMonocle()
                        .map("maven.modrinth:monocle-iris:%s"::formatted)
                        .map(project.getDependencies()::create);

        configurations.localCompileOnly().getDependencies().addLater(embeddium);
        configurations.localRuntimeOnly().getDependencies().addLater(embeddium);

        //Monocle has a special transformer which means it needs to be added to the downloading configuration
        configurations.modDownloads().getDependencies().addLater(monocle);
    }
}
