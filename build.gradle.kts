/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.3.2"
    id("xyz.jpenilla.run-paper") version "1.0.5" // Adds runServer and runMojangMappedServer tasks for testing
    //id("net.minecrell.plugin-yml.bukkit") version "0.5.0"
    id ("com.github.johnrengelman.shadow") version "7.1.0"
    id ("io.freefair.lombok") version "5.2.1"

}


group = "com.volmit.iris"
version = "1.9.3"
var apiVersion = "1.18.1"
var name = getRootProject().getName() // Defined in settings.gradle
var main = "com.volmit.iris.Iris"

// ADD YOURSELF AS A NEW LINE IF YOU WANT YOUR OWN BUILD TASK GENERATED
// ==============================================================
//registerCustomOutputTask("Cyberpwn", "C://Users/cyberpwn/Documents/development/server/plugins", name)
//registerCustomOutputTask("Psycho", "D://Dan/MinecraftDevelopment/server/plugins", name)
//registerCustomOutputTask("ArcaneArts", "C://Users/arcane/Documents/development/server/plugins", name)
//registerCustomOutputTask("Coco", "C://Users/sjoer/Desktop/MCSM/plugins", name)
//registerCustomOutputTask("Strange", "D://Servers/1.17 Test Server/plugins", name)
//registerCustomOutputTask("AGI", "D://Cloud//Server//TestServer 1.18//plugins", name)
// ==============================================================

/*fun registerCustomOutputTask(name: String, path: String, plugin: String) {
    tasks.register("build" + name) {
        //group("development")
        outputs.upToDateWhen { false }
        dependsOn(reobfJar)
        //dependsOn ':shadowJar'
        from(file('build/libs/' + plugin + '-' + version + '-all.jar'))
        into(file(path))
        /*rename { String fileName ->
            fileName.replace(plugin + '-' + version + '-all.jar', plugin + ".jar")
        }*/
    }
}*/
tasks {
    // Run reobfJar on build
    build {
        dependsOn(reobfJar)
    }


    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }
}

/**
 * Gradle is weird sometimes, we need to delete the plugin yml from the build folder to actually filter properly.
 */
//file(jar.archiveFile.get().getAsFile().getParentFile().getParentFile().getParentFile().getAbsolutePath() + '/build/resources/main/plugin.yml').delete()

/**
 * Expand properties into plugin yml
 */
/*processResources {
    filesMatching('**//*plugin.yml') {
        expand(
                'name' = name.toString(),
                'version' = version.toString(),
                'main' = main.toString(),
                'apiversion' = apiVersion.toString()
        )
    }
}*/

/**
 * Unified repo
 */
repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://dl.cloudsmith.io/public/arcane/archive/maven/")
    mavenCentral()
}

/**
 * We need parameter meta for the decree command system

compileJava {
    options.compilerArgs << '-parameters'
}*/

/**
 * Configure Iris for shading
 */
tasks.withType<ShadowJar> {
    minimize()
    append("plugin.yml")
    relocate("com.dfsek.paralithic", "com.volmit.iris.util.paralithic")
    relocate("io.papermc.lib", "com.volmit.iris.util.paper")
    relocate("net.kyori", "com.volmit.iris.util.kyori")
    dependencies {
        include(dependency("io.papermc:paperlib"))
        include(dependency("com.dfsek:Paralithic"))
        include(dependency("net.kyori:"))
    }
}

/*configurations.all {
    resolutionStrategy.cacheChangingModulesFor 60, 'minutes'
    resolutionStrategy.cacheDynamicVersionsFor 60, 'minutes'
}*/

/**
 * Dependencies.
 *
 * Provided or classpath dependencies are not shaded and are available on the runtime classpath
 *
 * Shaded dependencies are not available at runtime, nor are they available on mvn central so they
 * need to be shaded into the jar (increasing binary size)
 *
 * Dynamically loaded dependencies are defined in the plugin.yml (updating these must be updated in the
 * plugin.yml also, otherwise they wont be available). These do not increase binary size). Only declare
 * these dependencies if they are available on mvn central.
 */
dependencies {
    paperweightDevelopmentBundle("io.papermc.paper:dev-bundle:1.18.1-R0.1-SNAPSHOT")

    // Provided or Classpath
    compileOnly("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")
    //implementation 'org.bukkit.craftbukkit:1.18:1.18'
    //implementation 'org.spigotmc:spigot-api:1.18-R0.1-SNAPSHOT'
    implementation("me.clip:placeholderapi:2.10.10")
    implementation("io.th0rgal:oraxen:1.94.0")

    // Shaded
    implementation("com.dfsek:Paralithic:0.4.0")
    implementation("io.papermc:paperlib:1.0.7")
    implementation("net.kyori:adventure-api:4.9.3")
    implementation("net.kyori:adventure-text-minimessage:4.10.0-SNAPSHOT")
    implementation("net.kyori:adventure-platform-bukkit:4.0.1")

    // Dynamically Loaded
    implementation("io.timeandspace:smoothie-map:2.0.2")
    implementation("it.unimi.dsi:fastutil:8.5.6")
    implementation("com.googlecode.concurrentlinkedhashmap:concurrentlinkedhashmap-lru:1.4.2")
    implementation("org.zeroturnaround:zt-zip:1.14")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("org.ow2.asm:asm:9.2")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("bsf:bsf:2.4.0")
    implementation("rhino:js:1.7R2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.0.5")
    implementation("org.apache.commons:commons-lang3:3.12.0")

    //implementation 'io.netty:netty-all:4.1.52.Final'

}
