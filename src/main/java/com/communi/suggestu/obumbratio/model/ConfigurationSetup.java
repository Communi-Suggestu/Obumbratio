package com.communi.suggestu.obumbratio.model;

import org.gradle.api.artifacts.Configuration;

public record ConfigurationSetup(Configuration localRuntimeOnly, Configuration localCompileOnly, Configuration modDownloads) {
}
