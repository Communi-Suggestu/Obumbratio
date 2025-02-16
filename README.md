# Obumbratio
Gradle plugin to handle development of minecraft mods that are compatible with shaders.

## Features:
- Supports running with Embeddium and Sodium on NeoForge
- Supports running with Sodium on Fabric
- Supports running with NeoGradle on NeoForge
  - Support for MDG will be added later
- Supports running with Loom on Fabric
- Configures publishing for all variations

## Installation:
To install Obumbratio you will need to perform a couple of steps:
1) Register a maven repository so your project can access Obumbratio
2) Apply the Obumbratio plugin to your project
3) Configure the plugin so that it registers everything properly.

### Repository
Register the Communi-Suggestu repository in the `settings.gradle`:
```groovy
pluginManagement {
    repositories {
        maven {
            url 'https://ldtteam.jfrog.io/artifactory/communi-suggestu-maven'
            name 'Communi-Suggestu Maven'
        }
    }
}
```

### Apply the plugin
Once you have the repository added you can add the plugin to your project:
```groovy
plugins {
    //Look up the version below from the tags in the repository, or from the LDTTeam maven.
    id 'com.communi-suggestu.obumbratio' version '0.0.4'
}
```

### Configure the plugin:
#### NeoGradle
When you want the plugin to configure running with shaders on NeoGradle you can use the following DSL to configure it:
```groovy
shaders {
    enable() //Activates the plugins processing
    sodium() //Enables the sodium module of Obumbratio, registering a sourceset for it and configuring publishing and running
    embeddium() //Enables the embeddium module of Obumbratio, registering a sourceset, configuring publishing and running, as well as downloading Monocle and Iris.
    neoforge() //Sets this project as being a neoforge project.
}
```

### Loom
When you want the plugin to configure running with shaders on Loom you can use the following DSL to configure it:
```groovy
shaders {
    enable() //Activates the plugins processing
    sodium() //Enables the sodium module of Obumbratio, registering a sourceset for it and configuring publishing and running
    fabric() //Sets this project as being a fabric project.
}
```

### MDG
> [!IMPORTANT]  
> Support for MDG will be added in the future.

### Sodium and Embeddium versions
To be able to configure the versions of Sodium and Embeddium you want to run with you need to either configure them
through the DSL or via gradle properties.

To set the versions through the DSL use the following schema:
```groovy
shaders {
    versions {
        sodium {
            version = "1.2.3"
            fabricApi = "4.5.6"
            fabricRenderer = "7.8.9"
        }
        iris {
            version = "1.2.3"
            antlr4Runtime = "4.5.6"
            glslTransformer = "7.8.9"
            JCpp = "10.11.12"
        }
        embeddium = "1.2.3"
        monocle = "4.5.6"
    }
}
```

Alternatively, you can configure them using the convention gradle properties, which you can put in your `gradle.properties`-file:
```properties
compat.shaders.versions.sodium.version=0.6.5
compat.shaders.versions.sodium.fabric.api=0.4.42+d1308ded19
compat.shaders.versions.sodium.fabric.renderer=3.4.0+acb05a3919
compat.shaders.versions.iris.version=1.8.1
compat.shaders.versions.iris.antlr4.runtime=4.13.1
compat.shaders.versions.iris.glsl.transformer=2.0.1
compat.shaders.versions.iris.jcpp=1.4.14
compat.shaders.versions.embeddium=1.0.11
compat.shaders.versions.monocle=0.1.8
```

> [!NOTE]  
> The DSL overwrites the gradle properties, as they are just a convention.

> [!TIP]  
> Not all versions need to be set, if you are not running on a multi-platform architecture.