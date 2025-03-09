package com.communi.suggestu.obumbratio;

import com.communi.suggestu.obumbratio.extensions.ShadersExtension;
import com.communi.suggestu.obumbratio.model.ConfigurationSetup;
import com.communi.suggestu.obumbratio.model.Implementation;
import com.communi.suggestu.obumbratio.model.Platform;
import com.communi.suggestu.obumbratio.model.RunConfiguration;
import com.communi.suggestu.obumbratio.tasks.InstallMods;
import com.communi.suggestu.obumbratio.utils.RepositoryUtils;
import com.communi.suggestu.obumbratio.utils.RunUtils;
import com.communi.suggestu.obumbratio.utils.SourceSetUtils;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.neoforged.gradle.dsl.common.runs.run.Run;
import net.neoforged.gradle.dsl.common.runs.run.RunManager;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

@SuppressWarnings("UnstableApiUsage")
public abstract class ProjectPlugin implements Plugin<Project> {

    private static ProblemGroup PROBLEM_GROUP = ProblemGroup.create("obumbratio", "Obumbratio");

    private static ProblemId createProblemId(String problemId, String message) {
        return ProblemId.create(problemId, message, PROBLEM_GROUP);
    }

    @Override
    public void apply(@NotNull final Project project) {

        RepositoryUtils.configureRepositories(project);

        final ShadersExtension extension = project.getExtensions().create("shaders", ShadersExtension.class, project, (BiConsumer<ShadersExtension, Implementation>) (shaders, implementation) -> {
            SourceSetUtils.getOrCreateShaderSourceSetIn(project, shaders.getPlatform(), implementation);
            getOrCreateRunConfigurations(project, shaders, implementation);
            configureDependencies(project, shaders, implementation, SourceSetUtils.getConfigurationFor(project, shaders.getPlatform(), implementation));
        });

        configureConventions(project, extension);

        project.afterEvaluate(p -> {
            if (!extension.getIsEnabled()) {
                //Short circuit if the extension is not enabled
                return;
            }

            if (extension.getPlatform() == null) {
                throw getProblems().getReporter().throwing(
                        new InvalidUserDataException("Platform is required to be set"),
                        createProblemId("obumbratio.shaders.platform.missing", "Platform is missing"),
                        spec -> {
                            spec.details("Platform is required to be set");
                            spec.solution("Set the platform using the `platform` method");
                        });
            }

            if (extension.getImplementations().isEmpty()) {
                throw getProblems().getReporter().throwing(
                        new InvalidUserDataException("Implementation is required to be set"),
                        createProblemId("obumbratio.shaders.implementation.missing", "Implementation is missing"),
                        spec -> {
                            spec.details("Implementation is required to be set");
                            spec.solution("Set at least one implementation using the `implementation` method");
                        });
            }

            extension.getImplementations().forEach(implementation -> {
                final ConfigurationSetup configurationSetup = SourceSetUtils.getConfigurationFor(p, extension.getPlatform(), implementation);

                final Platform platform = extension.getPlatform();
                if (!implementation.isSupported(platform)) {
                    throw getProblems().getReporter().throwing(
                            new InvalidUserDataException("Implementation is not supported for the platform"),
                            createProblemId("obumbratio.shaders.implementation.unsupported", "Implementation is unsupported"),
                            spec -> {
                                spec.details("Implementation is not supported for the platform");
                                spec.solution("Set a supported implementation using the `implementation` method");
                            });
                }

                validateRequiredVersions(extension);

                final Set<RunConfiguration> runs = getOrCreateRunConfigurations(p, extension, implementation);
                if (!configurationSetup.modDownloads().getDependencies().isEmpty()) {
                    if (runs.isEmpty()) {
                        throw getProblems().getReporter().throwing(
                                new InvalidUserDataException("Runs are required to be set"),
                                createProblemId("obumbratio.shaders.runs.missing", "Runs are missing"),
                                spec -> {
                                    spec.details("Runs are required to be set");
                                    spec.solution("Add runs using the `run` method");
                                });
                    }

                    final TaskProvider<?> processResources = p.getTasks().named("processResources");

                    runs.forEach(run -> {
                        final String taskName = "installMods%s".formatted(StringUtils.capitalize(run.name()));
                        final TaskProvider<InstallMods> installMods = p.getTasks().register(taskName, InstallMods.class, task -> {
                            task.getModsDirectory().set(run.workDirectory().map(directory -> directory.dir("mods")));
                            task.getModFiles().from(configurationSetup.modDownloads());
                        });

                        processResources.configure(task -> {
                            task.dependsOn(installMods);
                        });
                    });
                }
            });
        });
    }

    private void configureConventions(Project project, ShadersExtension extension) {
        extension.setIsEnabled(parseProperty(project, "compat.shaders.enabled"));

        extension.getVersions().getMinecraft().convention(project.getProviders().gradleProperty("minecraft.version"));

        extension.getVersions().getEmbeddium().convention(project.getProviders().gradleProperty("compat.shaders.versions.embeddium"));
        extension.getVersions().getMonocle().convention(project.getProviders().gradleProperty("compat.shaders.versions.monocle"));

        extension.getVersions().getSodium().getVersion().convention(project.getProviders().gradleProperty("compat.shaders.versions.sodium.version"));
        extension.getVersions().getSodium().getFabricApi().convention(project.getProviders().gradleProperty("compat.shaders.versions.sodium.fabric.api"));
        extension.getVersions().getSodium().getFabricRenderer().convention(project.getProviders().gradleProperty("compat.shaders.versions.sodium.fabric.renderer"));

        extension.getVersions().getIris().getVersion().convention(project.getProviders().gradleProperty("compat.shaders.versions.iris.version"));
        extension.getVersions().getIris().getAntlr4Runtime().convention(project.getProviders().gradleProperty("compat.shaders.versions.iris.antlr4.runtime"));
        extension.getVersions().getIris().getGlslTransformer().convention(project.getProviders().gradleProperty("compat.shaders.versions.iris.glsl.transformer"));
        extension.getVersions().getIris().getJCpp().convention(project.getProviders().gradleProperty("compat.shaders.versions.iris.jcpp"));
    }

    private boolean parseProperty(Project project, String key) {
        if (!project.hasProperty(key)) {
            return false;
        }

        return Boolean.parseBoolean(Objects.requireNonNull(project.property(key)).toString());
    }

    @SuppressWarnings("UnstableApiUsage")
    @Inject
    public abstract Problems getProblems();

    private void configureDependencies(Project project, ShadersExtension extension, Implementation implementation, ConfigurationSetup configurations) {
        if (extension.getPlatform().isNeoForge()) {
            if (!implementation.isSupported(Platform.NEOFORGE)) {
                return;
            }

            implementation.registerNeoForgeDependencies(project, extension.getVersions(), configurations);
        } else if (extension.getPlatform().isFabric()) {
            if (!implementation.isSupported(Platform.FARBIC)) {
                return;
            }

            implementation.registerFabricDependencies(project, extension.getVersions(), configurations);
        }

        extension.getPlatform().configureIrisDependencies(project, extension, implementation, configurations);
    }


    private Set<RunConfiguration> getOrCreateRunConfigurations(Project project, ShadersExtension extension, Implementation implementation) {
        if (!extension.getIsEnabled()) {
            return new HashSet<>();
        }

        return Set.of(
                RunUtils.getOrCreateRunFor(
                        project,
                        extension.getPlatform(),
                        implementation,
                        SourceSetUtils.getOrCreateShaderSourceSetIn(project, extension.getPlatform(), implementation)
                )
        );
    }

    private void validateRequiredVersions(ShadersExtension extension) {
        validateMinecraftVersion(extension);

        final Platform platform = extension.getPlatform();
        if (platform.isNeoForge()) {
            validateRequiredNeoForgeVersions(extension);
        } else if (platform.isFabric()) {
            validateRequiredFabricVersions(extension);
        }
    }

    private void validateRequiredNeoForgeVersions(ShadersExtension extension) {
        validateRequiredIrisNeoForgeVersions(extension);

        extension.getImplementations().forEach(implementation -> {
            if (implementation == Implementation.SODIUM) {
                validateRequiredSodiumNeoForgeVersions(extension);
            } else if (implementation == Implementation.EMBEDDIUM) {
                validateRequiredEmbeddiumNeoForgeVersions(extension);
            }
        });
    }

    private void validateRequiredFabricVersions(ShadersExtension extension) {
        validateRequiredIrisFabricVersions(extension);


        extension.getImplementations().forEach(implementation -> {
            if (implementation == Implementation.SODIUM) {
                validateRequiredSodiumFabricVersions(extension);
            }
        });
    }

    private void validateRequiredSodiumNeoForgeVersions(ShadersExtension extension) {
        final Property<String> sodiumVersion = extension.getVersions().getSodium().getVersion();
        final Property<String> sodiumFabricApi = extension.getVersions().getSodium().getFabricApi();
        final Property<String> sodiumFabricRenderer = extension.getVersions().getSodium().getFabricRenderer();

        if (sodiumVersion.isPresent() && sodiumFabricApi.isPresent() && sodiumFabricRenderer.isPresent()) {
            return;
        }

        if (!sodiumVersion.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Sodium version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.sodium.version.missing", "Sodium version is missing"),
                    spec -> {
                        spec.details("Sodium version is required to be set");
                        spec.solution("Set the sodium version using the `sodium` method");
                    });
        }

        if (!sodiumFabricApi.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Sodium Fabric API version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.sodium.fabricApi.missing", "Sodium Fabric API version is missing"),
                    spec -> {
                        spec.details("Sodium Fabric API version is required to be set");
                        spec.solution("Set the sodium Fabric API version using the `sodium` method");
                    });
        }

        if (!sodiumFabricRenderer.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Sodium Fabric Renderer version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.sodium.fabricRenderer.missing", "Sodium Fabric Renderer version is missing"),
                    spec -> {
                        spec.details("Sodium Fabric Renderer version is required to be set");
                        spec.solution("Set the sodium Fabric Renderer version using the `sodium` method");
                    });
        }
    }

    private void validateRequiredEmbeddiumNeoForgeVersions(ShadersExtension extension) {
        final Property<String> embeddiumVersion = extension.getVersions().getEmbeddium();
        final Property<String> monocleVersion = extension.getVersions().getMonocle();

        if (embeddiumVersion.isPresent() && monocleVersion.isPresent()) {
            return;
        }

        if (!embeddiumVersion.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Embeddium version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.embeddium.version.missing", "Embeddium version is missing"),
                    spec -> {
                        spec.details("Embeddium version is required to be set");
                        spec.solution("Set the embeddium version using the `embeddium` method");
                    });
        }

        if (!monocleVersion.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Monocle version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.monocle.version.missing", "Monocle version is missing"),
                    spec -> {
                        spec.details("Monocle version is required to be set");
                        spec.solution("Set the monocle version using the `monocle` method");
                    });
        }
    }

    private void validateRequiredSodiumFabricVersions(ShadersExtension extension) {
        final Property<String> sodiumVersion = extension.getVersions().getSodium().getVersion();

        if (sodiumVersion.isPresent()) {
            return;
        }

        if (!sodiumVersion.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Sodium version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.sodium.version.missing", "Sodium version is missing"),
                    spec -> {
                        spec.details("Sodium version is required to be set");
                        spec.solution("Set the sodium version using the `sodium` method");
                    });
        }
    }

    private void validateRequiredIrisNeoForgeVersions(ShadersExtension extension) {
        final Property<String> irisVersion = extension.getVersions().getIris().getVersion();

        if (irisVersion.isPresent()) {
            return;
        }

        throw getProblems().getReporter().throwing(
                new InvalidUserDataException("Iris version is required to be set"),
                createProblemId("obumbratio.shaders.versions.iris.version.missing", "Iris version is missing"),
                spec -> {
                    spec.details("Iris version is required to be set");
                    spec.solution("Set the iris version using the `iris` method");
                });
    }

    private void validateRequiredIrisFabricVersions(ShadersExtension extension) {
        final Property<String> irisVersion = extension.getVersions().getIris().getVersion();
        final Property<String> antlr4Runtime = extension.getVersions().getIris().getAntlr4Runtime();
        final Property<String> glslTransformer = extension.getVersions().getIris().getGlslTransformer();
        final Property<String> jCpp = extension.getVersions().getIris().getJCpp();

        if (irisVersion.isPresent() && antlr4Runtime.isPresent() && glslTransformer.isPresent() && jCpp.isPresent()) {
            return;
        }

        if (!irisVersion.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Iris version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.iris.version.missing", "Iris version is missing"),
                    spec -> {
                        spec.details("Iris version is required to be set");
                        spec.solution("Set the iris version using the `iris` method");
                    });
        }

        if (!antlr4Runtime.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Sodium Antlr4 Runtime version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.sodium.antlr4Runtime.missing", "Sodium Antlr4 Runtime version is missing"),
                    spec -> {
                        spec.details("Sodium Antlr4 Runtime version is required to be set");
                        spec.solution("Set the sodium Antlr4 Runtime version using the `sodium` method");
                    });
        }

        if (!glslTransformer.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Sodium GLSL Transformer version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.sodium.glslTransformer.missing", "Sodium GLSL Transformer version is missing"),
                    spec -> {
                        spec.details("Sodium GLSL Transformer version is required to be set");
                        spec.solution("Set the sodium GLSL Transformer version using the `sodium` method");
                    });
        }

        if (!jCpp.isPresent()) {
            throw getProblems().getReporter().throwing(
                    new InvalidUserDataException("Sodium JCpp version is required to be set"),
                    createProblemId("obumbratio.shaders.versions.sodium.jCpp.missing", "Sodium JCpp version is missing"),
                    spec -> {
                        spec.details("Sodium JCpp version is required to be set");
                        spec.solution("Set the sodium JCpp version using the `sodium` method");
                    });
        }
    }

    private void validateMinecraftVersion(ShadersExtension extension) {
        final Property<String> minecraftVersion = extension.getVersions().getMinecraft();

        if (minecraftVersion.isPresent()) {
            return;
        }

        throw getProblems().getReporter().throwing(
                new InvalidUserDataException("Minecraft version is required to be set"),
                createProblemId("obumbratio.shaders.versions.minecraft.version.missing", "Minecraft version is missing"),
                spec -> {
                    spec.details("Minecraft version is required to be set");
                    spec.solution("Set the minecraft version using the `minecraft` method");
                });
    }

}
