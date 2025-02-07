package com.communi.suggestu.obumbratio.utils;

import com.communi.suggestu.obumbratio.model.ConfigurationSetup;
import com.communi.suggestu.obumbratio.model.Implementation;
import com.communi.suggestu.obumbratio.model.Platform;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;

import java.util.List;
import java.util.Locale;

public final class SourceSetUtils {

    private SourceSetUtils() {
        throw new IllegalStateException("Tried to instantiate: 'SourceSetUtils', but this is a utility class.");
    }

    public static SourceSet getOrCreateShaderSourceSetIn(final Project project, final Platform platform, final Implementation implementation) {
        final SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        final SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final TaskProvider<Jar> mainJarTask = project.getTasks().named(main.getJarTaskName(), Jar.class);

        final String name = StringUtils.capitalize(implementation.name().toLowerCase(Locale.ROOT));
        final String sourceSetName = name.toLowerCase(Locale.ROOT);
        if (sourceSets.findByName(sourceSetName) != null) {
            return sourceSets.getByName(sourceSetName);
        }

        final SourceSet sourceSet = sourceSets.create(sourceSetName);

        sourceSet.getJava().setSrcDirs(
                List.of(project.file("src/shaders/%s/java".formatted(implementation.name().toLowerCase(Locale.ROOT))))
        );
        sourceSet.getResources().setSrcDirs(
                List.of(project.file("src/shaders/%s/resources".formatted(implementation.name().toLowerCase(Locale.ROOT))))
        );

        final JavaPluginExtension javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);
        javaPlugin.registerFeature(
                implementation.name().toLowerCase(Locale.ROOT),
                feature -> {
                    feature.usingSourceSet(sourceSet);
                    feature.withSourcesJar();
                    feature.withJavadocJar();
                }
        );

        mainJarTask.configure(task -> task.from(sourceSet.getOutput()));
        sourceSet.setCompileClasspath(
                sourceSet.getCompileClasspath().plus(main.getCompileClasspath())
                        .plus(main.getOutput())
        );
        sourceSet.setRuntimeClasspath(
                sourceSet.getRuntimeClasspath().plus(main.getRuntimeClasspath())
                        .plus(main.getOutput())
        );

        project.getConfigurations().create(
                "%sModDownloads".formatted(StringUtils.uncapitalize(name)),
                config -> {
                    config.setCanBeResolved(true);
                    config.setCanBeConsumed(false);
                });

        if (platform.isFabric()) {
            final LoomGradleExtensionAPI api = project.getExtensions().getByType(LoomGradleExtensionAPI.class);

            if (!SourceSet.isMain(sourceSet)) {
                final String modLocalRuntime = "mod%sLocalRuntime".formatted(name);
                final String modCompileOnly = "mod%sCompileOnly".formatted(name);

                final Configuration sourceSetLocalRuntime =
                        project.getConfigurations().create(
                                "%sLocalRuntime".formatted(name.toLowerCase(Locale.ROOT)),
                                config -> {
                                    config.setCanBeResolved(true);
                                    config.setCanBeConsumed(false);
                                });

                project.getConfigurations().getByName(
                        sourceSet.getRuntimeClasspathConfigurationName()
                ).extendsFrom(sourceSetLocalRuntime);

                api.addRemapConfiguration(
                        modCompileOnly,
                        config -> {
                            config.getSourceSet().set(sourceSet);
                            config.getTargetConfigurationName().set(sourceSet.getCompileClasspathConfigurationName());
                            config.getOnCompileClasspath().set(false);
                            config.getOnRuntimeClasspath().set(false);
                        }
                );

                api.addRemapConfiguration(
                        modLocalRuntime,
                        config -> {
                            config.getSourceSet().set(sourceSet);
                            config.getTargetConfigurationName().set(sourceSetLocalRuntime.getName());
                            config.getOnCompileClasspath().set(false);
                            config.getOnRuntimeClasspath().set(false);
                        }
                );
            }
        }

        return sourceSet;
    }

    public static ConfigurationSetup getConfigurationFor(Project project, final Platform platform, final Implementation implementation) {
        if (platform.isFabric()) {
            final String name = StringUtils.capitalize(implementation.name().toLowerCase(Locale.ROOT));
            final String modLocalRuntime = "mod%sLocalRuntime".formatted(name);
            final String modCompileOnly = "mod%sCompileOnly".formatted(name);
            final String modDownloads = "%sModDownloads".formatted(StringUtils.uncapitalize(name));

            return new ConfigurationSetup(
                    project.getConfigurations().getByName(modLocalRuntime),
                    project.getConfigurations().getByName(modCompileOnly),
                    project.getConfigurations().getByName(modDownloads)
            );
        }

        if (platform.isNeoForge()) {
            final String name = StringUtils.uncapitalize(implementation.name().toLowerCase(Locale.ROOT));
            final String localRuntime = "%sLocalRuntime".formatted(name.toLowerCase(Locale.ROOT));
            final String compileOnly = "%sCompileOnly".formatted(name.toLowerCase(Locale.ROOT));
            final String modDownloads = "%sModDownloads".formatted(name.toLowerCase(Locale.ROOT));

            return new ConfigurationSetup(
                    project.getConfigurations().getByName(localRuntime),
                    project.getConfigurations().getByName(compileOnly),
                    project.getConfigurations().getByName(modDownloads)
            );
        }

        throw new IllegalStateException("Unknown platform: %s".formatted(platform));
    }
}
