package com.communi.suggestu.obumbratio.model;

import com.communi.suggestu.obumbratio.extensions.ShadersExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
        project.getRepositories().maven(m -> {
            m.setName("CaffeineMC");
            m.setUrl("https://maven.caffeinemc.net/releases");
        });

        final Provider<Dependency> sodium =
                versions.getSodium().getVersion().zip(
                        versions.getMinecraft(),
                        "net.caffeinemc:sodium-neoforge:%s+mc%s"::formatted
                ).map(project.getDependencies()::create);

        configurations.localCompileOnly().attributes(attributes -> {
            attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar-jar-extracted");
        });

        project.getDependencies().registerTransform(ExtractTransform.class, spec -> {
            spec.getFrom().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE);
            spec.getTo().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar-jar-extracted");
        });

        configurations.localCompileOnly().getDependencies().addLater(sodium);
        configurations.localRuntimeOnly().getDependencies().addLater(sodium);
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

    abstract static class ExtractTransform implements TransformAction<TransformParameters.None> {
        @InputArtifact
        abstract Provider<FileSystemLocation> getInputArtifact();

        @Override
        public void transform(@NonNull TransformOutputs outputs) {
            final File input = getInputArtifact().get().getAsFile();
            final File output = outputs.file(input.getName());

            System.out.println("Extracting from: " + input.getAbsolutePath() + " to: " + output.getAbsolutePath());

            try(FileInputStream stream = new FileInputStream(input);
                FileOutputStream outputStream = new FileOutputStream(output)) {
                stream.transferTo(outputStream);
            }
            catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
    }
}
