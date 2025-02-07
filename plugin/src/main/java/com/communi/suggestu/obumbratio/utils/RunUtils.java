package com.communi.suggestu.obumbratio.utils;

import com.communi.suggestu.obumbratio.model.Implementation;
import com.communi.suggestu.obumbratio.model.Platform;
import com.communi.suggestu.obumbratio.model.RunConfiguration;
import net.fabricmc.loom.api.LoomGradleExtensionAPI;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.neoforged.gradle.dsl.common.runs.ide.extensions.IdeaRunExtension;
import net.neoforged.gradle.dsl.common.runs.run.Run;
import net.neoforged.gradle.dsl.common.runs.run.RunManager;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;

public final class RunUtils {

    private RunUtils() {
        throw new IllegalStateException("Tried to instantiate: 'RunUtils', but this is a utility class.");
    }

    public static RunConfiguration getOrCreateRunFor(final Project project, final Platform platform, final Implementation implementation, final SourceSet implementationSourceSet) {
        if (platform.isFabric())
            return getOrCreateLoomRunFor(project, implementation, implementationSourceSet);
        else
            return getOrCreateNeoGradleRunFor(project, implementation, implementationSourceSet);
    }

    private static RunConfiguration getOrCreateLoomRunFor(final Project project, final Implementation implementation, final SourceSet implementationSourceSet) {
        final LoomGradleExtensionAPI loomApi = project.getExtensions().getByType(LoomGradleExtensionAPI.class);
        final String name = "clientWith%sShaders".formatted(StringUtils.capitalize(implementation.name().toLowerCase()));

        if (loomApi.getRuns().findByName(name) != null)
            return new RunConfiguration(loomApi.getRuns().getByName(name));

        final RunConfigSettings clientRun = loomApi.getRuns().getByName("client");

        return new RunConfiguration(loomApi.getRuns().create(name, run -> {
            run.inherit(clientRun);
            run.source(implementationSourceSet);
            run.client();
            run.ideConfigGenerated(true);
        }));
    }

    private static RunConfiguration getOrCreateNeoGradleRunFor(final Project project, final Implementation implementation, final SourceSet implementationSourceSet) {
        final RunManager runs = project.getExtensions().getByType(RunManager.class);
        final String name = "clientWith%sShaders".formatted(StringUtils.capitalize(implementation.name().toLowerCase()));

        if (runs.findByName(name) != null)
            return new RunConfiguration(runs.getByName(name));

        final Run clientRun = runs.getByName("client");
        clientRun.workingDirectory(
                project.file("runs/client/no-shader")
        );

        return new RunConfiguration(runs.create(name, run -> {
            run.configure(clientRun);
            run.getModSources().add(implementationSourceSet);
            run.configureFromTypeWithName(false);
            run.getIDERunName().set(clientRun.getIDERunName().map(
                    ideName -> ideName + " with %s shaders".formatted(StringUtils.capitalize(implementation.name().toLowerCase()))
            ));
            run.getExtensions().getByType(IdeaRunExtension.class).getPrimarySourceSet().set(implementationSourceSet);
            run.getWorkingDirectory().set(
                    project.file("runs/client/%s-shader".formatted(implementation.name().toLowerCase()))
            );
        }));
    }
}
