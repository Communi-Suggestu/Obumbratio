package com.communi.suggestu.obumbratio.model;

import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.neoforged.gradle.dsl.common.runs.run.Run;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;

public record RunConfiguration(String name, Provider<Directory> workDirectory) {
    public RunConfiguration(Run run) {
        this(run.getName(), run.getWorkingDirectory());
    }

    public RunConfiguration(RunConfigSettings runConfigSettings) {
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
