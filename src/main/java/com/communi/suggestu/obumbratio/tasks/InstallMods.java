package com.communi.suggestu.obumbratio.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.*;

import javax.inject.Inject;

public abstract class InstallMods extends DefaultTask {

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
