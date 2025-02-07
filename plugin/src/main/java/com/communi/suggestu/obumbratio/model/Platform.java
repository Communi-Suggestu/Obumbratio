package com.communi.suggestu.obumbratio.model;

import com.communi.suggestu.obumbratio.extensions.ShadersExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.provider.Provider;

public enum Platform {
    FARBIC,
    NEOFORGE;

    public boolean isFabric() {
        return this == FARBIC;
    }

    public boolean isNeoForge() {
        return this == NEOFORGE;
    }

    public void configureIrisDependencies(Project project, ShadersExtension shadersExtension, Implementation implementation, ConfigurationSetup configurations) {
        if (this == FARBIC) {
            configureIrisFabricDependencies(project, shadersExtension, implementation, configurations);
        } else if (this == NEOFORGE) {
            configureIrisNeoForgeDependencies(project, shadersExtension, implementation, configurations);
        }
    }

    private void configureIrisFabricDependencies(Project project, ShadersExtension shadersExtension, Implementation implementation, ConfigurationSetup configurations) {
        final ShadersExtension.Versions versions = shadersExtension.getVersions();
        final Provider<Dependency> iris =
                versions.getIris().getVersion().zip(
                        versions.getMinecraft(),
                        "maven.modrinth:iris:%s+%s-fabric"::formatted
                ).map(project.getDependencies()::create);
        final Provider<Dependency> antlr4Runtime =
                versions.getIris().getAntlr4Runtime()
                        .map("org.antlr:antlr4-runtime:%s"::formatted)
                        .map(project.getDependencies()::create);
        final Provider<Dependency> glslTransformer =
                versions.getIris().getGlslTransformer()
                        .map("io.github.douira:glsl-transformer:%s"::formatted)
                        .map(project.getDependencies()::create);
        final Provider<Dependency> jCpp =
                versions.getIris().getJCpp()
                        .map("org.anarres:jcpp:%s"::formatted)
                        .map(project.getDependencies()::create);

        configurations.localCompileOnly().getDependencies().addLater(iris);
        configurations.localCompileOnly().getDependencies().addLater(antlr4Runtime);
        configurations.localCompileOnly().getDependencies().addLater(glslTransformer);
        configurations.localCompileOnly().getDependencies().addLater(jCpp);

        configurations.localRuntimeOnly().getDependencies().addLater(iris);
        configurations.localRuntimeOnly().getDependencies().addLater(antlr4Runtime);
        configurations.localRuntimeOnly().getDependencies().addLater(glslTransformer);
        configurations.localRuntimeOnly().getDependencies().addLater(jCpp);
    }

    private void configureIrisNeoForgeDependencies(Project project, ShadersExtension shadersExtension, Implementation implementation, ConfigurationSetup configurations) {
        final ShadersExtension.Versions versions = shadersExtension.getVersions();
        final Provider<Dependency> iris =
                versions.getIris().getVersion().zip(
                        versions.getMinecraft(),
                        "maven.modrinth:iris:%s+%s-neoforge"::formatted
                ).map(project.getDependencies()::create);

        configurations.localCompileOnly().getDependencies().addLater(iris);

        if (implementation.requiresDownloadedIris()) {
            configurations.modDownloads().getDependencies().addLater(iris);
        } else {
            configurations.localRuntimeOnly().getDependencies().addLater(iris);
        }
    }
}
