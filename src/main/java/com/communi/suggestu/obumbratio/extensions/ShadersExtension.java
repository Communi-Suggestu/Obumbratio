package com.communi.suggestu.obumbratio.extensions;

import com.communi.suggestu.obumbratio.model.Implementation;
import com.communi.suggestu.obumbratio.model.Platform;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import javax.inject.Inject;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class ShadersExtension {

    private final BiConsumer<ShadersExtension, Implementation> configured;
    private boolean isEnabled = false;
    private Platform platform;
    private EnumSet<Implementation> implementations = EnumSet.noneOf(Implementation.class);
    private Versions versions;

    @Inject
    public ShadersExtension(final Project project, BiConsumer<ShadersExtension, Implementation> configured) {
        this.configured = configured;
        this.versions = project.getObjects().newInstance(Versions.class, project);
    }

    public boolean getIsEnabled() {
        return isEnabled;
    }

    public void setIsEnabled(boolean enabled) {
        isEnabled = enabled;
        configure();
    }

    public void enabled(final boolean enabled) {
        this.isEnabled = enabled;
    }

    public void enable() {
        this.isEnabled = true;
        configure();
    }

    private void configure() {
        if (this.isEnabled && this.platform != null && !this.implementations.isEmpty()) {
            this.implementations.forEach(implementation -> configured.accept(this, implementation));
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

    public void implementation(Implementation implementation) {
        this.implementations.add(implementation);
        configure();
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

    public Set<Implementation> getImplementations() {
        return this.implementations;
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
    }
}
