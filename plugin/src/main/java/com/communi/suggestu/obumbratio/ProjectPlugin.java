package com.communi.suggestu.obumbratio;

import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.neoforged.gradle.dsl.common.runs.run.Run;
import net.neoforged.gradle.dsl.common.runs.run.RunManager;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.*;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.*;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

@SuppressWarnings("UnstableApiUsage")
public abstract class ProjectPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull final Project project) {

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

        final PlatformConfigurations configurations = getConfigurations(project);
        final Configuration downloadingConfiguration = project.getConfigurations().detachedConfiguration();

        final ShadersExtension extension = project.getExtensions().create("shaders", ShadersExtension.class, project, (Consumer<ShadersExtension>) extension1 -> {
            final ConfigurationSetup setup = extension1.getPlatform().isNeoForge() ? configurations.neoForge() : configurations.fabric();
            configureDependencies(project, extension1, setup, downloadingConfiguration);
        });

        configureConventions(project, extension);

        project.afterEvaluate(p -> {
            if (!extension.getIsEnabled()) {
                //Short circuit if the extension is not enabled
                return;
            }

            if (extension.getPlatform() == null) {
                throw getProblems().getReporter().throwing(spec -> {
                    spec.id("obumbratio.shaders.platform.missing", "Platform is missing");
                    spec.details("Platform is required to be set");
                    spec.solution("Set the platform using the `platform` method");
                    spec.withException(new InvalidUserDataException("Platform is required to be set"));
                });
            }

            if (extension.getImplementation() == null) {
                throw getProblems().getReporter().throwing(spec -> {
                    spec.id("obumbratio.shaders.implementation.missing", "Implementation is missing");
                    spec.details("Implementation is required to be set");
                    spec.solution("Set the implementation using the `implementation` method");
                    spec.withException(new InvalidUserDataException("Implementation is required to be set"));
                });
            }

            final Platform platform = extension.getPlatform();
            final Implementation implementation = extension.getImplementation();
            if (!implementation.isSupported(platform)) {
                throw getProblems().getReporter().throwing(spec -> {
                    spec.id("obumbratio.shaders.implementation.unsupported", "Implementation is unsupported");
                    spec.details("Implementation is not supported for the platform");
                    spec.solution("Set a supported implementation using the `implementation` method");
                    spec.withException(new InvalidUserDataException("Implementation is not supported for the platform"));
                });
            }

            validateRequiredVersions(extension);

            if (!downloadingConfiguration.getDependencies().isEmpty()) {
                final Set<RunConfiguration> runs = getRunConfigurations(p, extension);
                if (runs.isEmpty()) {
                    throw getProblems().getReporter().throwing(spec -> {
                        spec.id("obumbratio.shaders.runs.missing", "Runs are missing");
                        spec.details("Runs are required to be set");
                        spec.solution("Add runs using the `run` method");
                        spec.withException(new InvalidUserDataException("Runs are required to be set"));
                    });
                }

                final TaskProvider<?> processResources = p.getTasks().named("processResources");

                runs.forEach(run -> {
                    final String taskName = "installMods%s".formatted(StringUtils.capitalize(run.name));
                    final TaskProvider<InstallMods> installMods = p.getTasks().register(taskName, InstallMods.class, task -> {
                        task.getModsDirectory().set(run.workDirectory().map(directory -> directory.dir("mods")));
                        task.getModFiles().from(downloadingConfiguration);
                    });

                    processResources.configure(task -> {
                        task.dependsOn(installMods);
                    });
                });
            }

            final SourceSetContainer sourceSets = p.getExtensions().getByType(SourceSetContainer.class);
            final SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            main.getJava().srcDir("src/shaders/java");
            main.getResources().srcDir("src/shaders/resources");
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

        extension.getRuns().convention(
                project.getProviders().gradleProperty("compat.shaders.runs")
                        .map(value -> List.of(value.split(",")))
                        .orElse(List.of("client"))
        );
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

    private void configureDependencies(Project project, ShadersExtension extension, ConfigurationSetup configurations, Configuration downloadingConfiguration) {
        if (extension.getPlatform().isNeoForge()) {
            if (!extension.getImplementation().isSupported(Platform.NEOFORGE)) {
                return;
            }

            final Implementation implementation = extension.getImplementation();
            implementation.registerNeoForgeDependencies(project, extension.getVersions(), configurations, downloadingConfiguration);
        } else if (extension.getPlatform().isFabric()) {
            if (!extension.getImplementation().isSupported(Platform.FARBIC)) {
                return;
            }

            final Implementation implementation = extension.getImplementation();
            implementation.registerFabricDependencies(project, extension.getVersions(), configurations, downloadingConfiguration);
        }

        extension.getPlatform().configureIrisDependencies(project, extension, configurations, downloadingConfiguration);
    }

    private record PlatformConfigurations(ConfigurationSetup neoForge, ConfigurationSetup fabric) {}

    private record ConfigurationSetup(Configuration localRuntimeOnly, Configuration localCompileOnly) {}

    private PlatformConfigurations getConfigurations(Project project) {
        return new PlatformConfigurations(
                new ConfigurationSetup(
                        project.getConfigurations().getByName("compileOnly"),
                        project.getConfigurations().maybeCreate("localRuntime")
                ),
                new ConfigurationSetup(
                        project.getConfigurations().maybeCreate("modCompileOnly"),
                        project.getConfigurations().maybeCreate("modLocalRuntime")
                )
        );
    }

    private record RunConfiguration(String name, Provider<Directory> workDirectory) {
        private RunConfiguration(Run run) {
            this(run.getName(), run.getWorkingDirectory());
        }

        private RunConfiguration(RunConfigSettings runConfigSettings) {
            this(
                    runConfigSettings.getName(),
                    runConfigSettings.getProject().provider(
                            () -> runConfigSettings.getProject().getLayout().getProjectDirectory().dir(
                                    runConfigSettings.getRunDir()
                            )
                    )
            );
        }
    }

    private Set<RunConfiguration> getRunConfigurations(Project project, ShadersExtension extension) {
        final Platform platform = extension.getPlatform();

        if (!extension.getIsEnabled()) {
            return new HashSet<>();
        }

        if (platform.isNeoForge()) {
            return getNeoForgeRunConfigurations(project, extension.getRuns());
        } else if (platform.isFabric()) {
            return getFabricRunConfigurations(project, extension.getRuns());
        }

        return new HashSet<>();
    }

    private Set<RunConfiguration> getNeoForgeRunConfigurations(Project project, final SetProperty<String> runNames) {
        final RunManager runs = project.getExtensions().getByType(RunManager.class);
        final Set<RunConfiguration> result = new HashSet<>();

        runNames.get().forEach(name -> {
            final Run run = runs.getByName(name);
            result.add(new RunConfiguration(run));
        });

        return result;
    }

    private Set<RunConfiguration> getFabricRunConfigurations(Project project, final SetProperty<String> runNames) {
        final LoomGradleExtensionAPI loom = project.getExtensions().getByType(LoomGradleExtensionAPI.class);
        final Set<RunConfiguration> result = new HashSet<>();

        runNames.get().forEach(name -> {
            final RunConfigSettings runConfig = loom.getRunConfigs().getByName(name);
            result.add(new RunConfiguration(runConfig));
        });

        return result;
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

        final Implementation implementation = extension.getImplementation();
        if (implementation == Implementation.SODIUM) {
            validateRequiredSodiumNeoForgeVersions(extension);
        } else if (implementation == Implementation.EMBEDDIUM) {
            validateRequiredEmbeddiumNeoForgeVersions(extension);
        }
    }

    private void validateRequiredFabricVersions(ShadersExtension extension) {
        validateRequiredIrisFabricVersions(extension);

        final Implementation implementation = extension.getImplementation();
        if (implementation == Implementation.SODIUM) {
            validateRequiredSodiumFabricVersions(extension);
        }
    }

    private void validateRequiredSodiumNeoForgeVersions(ShadersExtension extension) {
        final Property<String> sodiumVersion = extension.getVersions().getSodium().getVersion();
        final Property<String> sodiumFabricApi = extension.getVersions().getSodium().getFabricApi();
        final Property<String> sodiumFabricRenderer = extension.getVersions().getSodium().getFabricRenderer();

        if (sodiumVersion.isPresent() && sodiumFabricApi.isPresent() && sodiumFabricRenderer.isPresent()) {
            return;
        }

        if (!sodiumVersion.isPresent()) {
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.sodium.version.missing", "Sodium version is missing");
                spec.details("Sodium version is required to be set");
                spec.solution("Set the sodium version using the `sodium` method");
                spec.withException(new InvalidUserDataException("Sodium version is required to be set"));
            });
        }

        if (!sodiumFabricApi.isPresent()) {
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.sodium.fabricApi.missing", "Sodium Fabric API version is missing");
                spec.details("Sodium Fabric API version is required to be set");
                spec.solution("Set the sodium Fabric API version using the `sodium` method");
                spec.withException(new InvalidUserDataException("Sodium Fabric API version is required to be set"));
            });
        }

        if (!sodiumFabricRenderer.isPresent()) {
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.sodium.fabricRenderer.missing", "Sodium Fabric Renderer version is missing");
                spec.details("Sodium Fabric Renderer version is required to be set");
                spec.solution("Set the sodium Fabric Renderer version using the `sodium` method");
                spec.withException(new InvalidUserDataException("Sodium Fabric Renderer version is required to be set"));
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
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.embeddium.version.missing", "Embeddium version is missing");
                spec.details("Embeddium version is required to be set");
                spec.solution("Set the embeddium version using the `embeddium` method");
                spec.withException(new InvalidUserDataException("Embeddium version is required to be set"));
            });
        }

        if (!monocleVersion.isPresent()) {
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.monocle.version.missing", "Monocle version is missing");
                spec.details("Monocle version is required to be set");
                spec.solution("Set the monocle version using the `monocle` method");
                spec.withException(new InvalidUserDataException("Monocle version is required to be set"));
            });
        }
    }

    private void validateRequiredSodiumFabricVersions(ShadersExtension extension) {
        final Property<String> sodiumVersion = extension.getVersions().getSodium().getVersion();

        if (sodiumVersion.isPresent()) {
            return;
        }

        if (!sodiumVersion.isPresent()) {
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.sodium.version.missing", "Sodium version is missing");
                spec.details("Sodium version is required to be set");
                spec.solution("Set the sodium version using the `sodium` method");
                spec.withException(new InvalidUserDataException("Sodium version is required to be set"));
            });
        }
    }

    private void validateRequiredIrisNeoForgeVersions(ShadersExtension extension) {
        final Property<String> irisVersion = extension.getVersions().getIris().getVersion();

        if (irisVersion.isPresent()) {
            return;
        }

        throw getProblems().getReporter().throwing(spec -> {
            spec.id("obumbratio.shaders.versions.iris.version.missing", "Iris version is missing");
            spec.details("Iris version is required to be set");
            spec.solution("Set the iris version using the `iris` method");
            spec.withException(new InvalidUserDataException("Iris version is required to be set"));
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
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.iris.version.missing", "Iris version is missing");
                spec.details("Iris version is required to be set");
                spec.solution("Set the iris version using the `iris` method");
                spec.withException(new InvalidUserDataException("Iris version is required to be set"));
            });
        }

        if (!antlr4Runtime.isPresent()) {
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.sodium.antlr4Runtime.missing", "Sodium Antlr4 Runtime version is missing");
                spec.details("Sodium Antlr4 Runtime version is required to be set");
                spec.solution("Set the sodium Antlr4 Runtime version using the `sodium` method");
                spec.withException(new InvalidUserDataException("Sodium Antlr4 Runtime version is required to be set"));
            });
        }

        if (!glslTransformer.isPresent()) {
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.sodium.glslTransformer.missing", "Sodium GLSL Transformer version is missing");
                spec.details("Sodium GLSL Transformer version is required to be set");
                spec.solution("Set the sodium GLSL Transformer version using the `sodium` method");
                spec.withException(new InvalidUserDataException("Sodium GLSL Transformer version is required to be set"));
            });
        }

        if (!jCpp.isPresent()) {
            throw getProblems().getReporter().throwing(spec -> {
                spec.id("obumbratio.shaders.versions.sodium.jCpp.missing", "Sodium JCpp version is missing");
                spec.details("Sodium JCpp version is required to be set");
                spec.solution("Set the sodium JCpp version using the `sodium` method");
                spec.withException(new InvalidUserDataException("Sodium JCpp version is required to be set"));
            });
        }
    }

    private void validateMinecraftVersion(ShadersExtension extension) {
        final Property<String> minecraftVersion = extension.getVersions().getMinecraft();

        if (minecraftVersion.isPresent()) {
            return;
        }

        throw getProblems().getReporter().throwing(spec -> {
            spec.id("obumbratio.shaders.versions.minecraft.version.missing", "Minecraft version is missing");
            spec.details("Minecraft version is required to be set");
            spec.solution("Set the minecraft version using the `minecraft` method");
            spec.withException(new InvalidUserDataException("Minecraft version is required to be set"));
        });
    }

    public abstract static class ShadersExtension {

        private final Consumer<ShadersExtension> configured;
        private boolean isEnabled = false;
        private Platform platform;
        private Implementation implementation;
        private Versions versions;

        @Inject
        public ShadersExtension(final Project project, Consumer<ShadersExtension> configured) {
            this.configured = configured;
            this.versions = project.getObjects().newInstance(Versions.class, project);
        }

        public boolean getIsEnabled() {
            return isEnabled;
        }

        public void setIsEnabled(boolean enabled) {
            isEnabled = enabled;
            if (this.isEnabled && this.platform != null && this.implementation != null) {
                configured.accept(this);
            }
        }

        public void enabled(final boolean enabled) {
            this.isEnabled = enabled;
        }

        public void enable() {
            this.isEnabled = true;
            configure();
        }

        private void configure() {
            if (this.isEnabled && this.platform != null && this.implementation != null) {
                configured.accept(this);
            }
        }

        public void disable() {
            this.isEnabled = false;
        }

        public Platform getPlatform() {
            return platform;
        }

        public void setPlatform(Platform platform) {
            this.platform = platform;
            configure();
        }

        public void platform(Platform platform) {
            setPlatform(platform);
        }

        public void neoforge() {
            platform(Platform.NEOFORGE);
        }

        public void fabric() {
            platform(Platform.FARBIC);
        }

        public Implementation getImplementation() {
            return implementation;
        }

        public void setImplementation(Implementation implementation) {
            this.implementation = implementation;
            configure();
        }

        public void implementation(Implementation implementation) {
            setImplementation(implementation);
        }

        public void sodium() {
            implementation(Implementation.SODIUM);
        }

        public void embeddium() {
            implementation(Implementation.EMBEDDIUM);
        }

        public Versions getVersions() {
            return versions;
        }

        public void versions(final Action<Versions> configure) {
            configure.execute(getVersions());
        }

        public abstract SetProperty<String> getRuns();

        public void run(final String name) {
            getRuns().add(name);
        }

        public void run(final Named named) {
            run(named.getName());
        }
    }

    public enum Platform {
        FARBIC,
        NEOFORGE;

        public boolean isFabric() {
            return this == FARBIC;
        }

        public boolean isNeoForge() {
            return this == NEOFORGE;
        }

        private void configureIrisDependencies(Project project, ShadersExtension shadersExtension, ConfigurationSetup configurations, Configuration downloadingConfiguration) {
            if (this == FARBIC) {
                configureIrisFabricDependencies(project, shadersExtension, configurations, downloadingConfiguration);
            } else if (this == NEOFORGE) {
                configureIrisNeoForgeDependencies(project, shadersExtension, configurations, downloadingConfiguration);
            }
        }

        private void configureIrisFabricDependencies(Project project, ShadersExtension shadersExtension, ConfigurationSetup configurations, Configuration downloadingConfiguration) {
            final Versions versions = shadersExtension.getVersions();
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

        private void configureIrisNeoForgeDependencies(Project project, ShadersExtension shadersExtension, ConfigurationSetup configurations, Configuration downloadingConfiguration) {
            final Versions versions = shadersExtension.getVersions();
            final Provider<Dependency> iris =
                    versions.getIris().getVersion().zip(
                            versions.getMinecraft(),
                            "maven.modrinth:iris:%s+%s-neoforge"::formatted
                    ).map(project.getDependencies()::create);

            configurations.localCompileOnly().getDependencies().addLater(iris);

            final Implementation implementation = shadersExtension.getImplementation();
            if (implementation.requiresDownloadedIris()) {
                downloadingConfiguration.getDependencies().addLater(iris);
            } else {
                configurations.localRuntimeOnly().getDependencies().addLater(iris);
            }
        }
    }

    public enum Implementation {
        SODIUM(p -> true),
        EMBEDDIUM(Platform::isNeoForge);

        private final Function<Platform, Boolean> supportedPlatforms;

        Implementation(Function<Platform, Boolean> supportedPlatforms) {
            this.supportedPlatforms = supportedPlatforms;
        }

        private boolean isSupported(Platform platform) {
            return supportedPlatforms.apply(platform);
        }

        private boolean requiresDownloadedIris() {
            return this == EMBEDDIUM;
        }

        private void registerFabricDependencies(Project project, Versions versions, ConfigurationSetup configurations, Configuration downloadingConfiguration) {
            if (this == SODIUM) {
                registerSodiumFabricDependencies(project, versions, configurations, downloadingConfiguration);
            }
        }

        private void registerNeoForgeDependencies(Project project, Versions versions, ConfigurationSetup configurations, Configuration downloadingConfiguration) {
            if (this == SODIUM) {
                registerSodiumNeoForgeDependencies(project, versions, configurations, downloadingConfiguration);
            } else if (this == EMBEDDIUM) {
                registerEmbeddiumNeoForgeDependencies(project, versions, configurations, downloadingConfiguration);
            }
        }

        private static void registerSodiumFabricDependencies(Project project, Versions versions, ConfigurationSetup configurations, Configuration downloadingConfiguration) {
            final Provider<Dependency> sodium =
                    versions.getMinecraft().zip(
                            versions.getSodium().getVersion(),
                            "maven.modrinth:sodium:mc%s-%s-fabric"::formatted
                    ).map(project.getDependencies()::create);

            configurations.localCompileOnly().getDependencies().addLater(sodium);
            configurations.localRuntimeOnly().getDependencies().addLater(sodium);
        }

        private static void registerSodiumNeoForgeDependencies(Project project, Versions versions, ConfigurationSetup configurations, Configuration downloadingConfiguration) {
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

        private static void registerEmbeddiumNeoForgeDependencies(Project project, Versions versions, ConfigurationSetup configurations, Configuration downloadingConfiguration) {
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
            configurations.localCompileOnly().getDependencies().addLater(monocle);
            downloadingConfiguration.getDependencies().addLater(monocle);
        }
    }

    public abstract static class Versions {

        private final SodiumVersions sodium;
        private final IrisVersions iris;

        @Inject
        public Versions(final Project project) {
            this.sodium = project.getObjects().newInstance(SodiumVersions.class);
            this.iris = project.getObjects().newInstance(IrisVersions.class);
        }

        public abstract Property<String> getMinecraft();

        public SodiumVersions getSodium() {
            return sodium;
        }

        public void sodium(final Action<SodiumVersions> configure) {
            configure.execute(getSodium());
        }

        public abstract Property<String> getEmbeddium();

        public IrisVersions getIris() {
            return iris;
        }

        public void iris(final Action<IrisVersions> configure) {
            configure.execute(getIris());
        }

        public abstract Property<String> getMonocle();
    }

    public abstract static class SodiumVersions {

        public abstract Property<String> getVersion();

        public abstract Property<String> getFabricApi();

        public abstract Property<String> getFabricRenderer();
    }

    public abstract static class IrisVersions {

        public abstract Property<String> getVersion();

        public abstract Property<String> getAntlr4Runtime();

        public abstract Property<String> getGlslTransformer();

        public abstract Property<String> getJCpp();
    }

    public abstract static class InstallMods extends DefaultTask {

        @Inject
        public InstallMods() {
            setGroup("obumbratio");
            setDescription("Installs mods for the project");
        }

        @Inject
        public abstract FileSystemOperations getFileSystemOperations();

        @TaskAction
        public void install() {
            getModFiles().getFiles().forEach(file -> {
                getFileSystemOperations().copy(spec -> {
                    spec.from(file);
                    spec.into(getModsDirectory());
                });
            });
        }

        @OutputDirectory
        public abstract DirectoryProperty getModsDirectory();

        @InputFiles
        @PathSensitive(PathSensitivity.NAME_ONLY)
        public abstract ConfigurableFileCollection getModFiles();
    }
}
