/*
 *  Copyright 2026 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.JavaVersion

pluginManagement {
     repositories {
         maven { url = uri("https://teavm.org/maven/repository") }
         mavenCentral()
         mavenLocal()
         gradlePluginPortal()
     }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://teavm.org/maven/repository") }
        mavenCentral()
        mavenLocal()
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
    }
}


gradle.allprojects {
    tasks.withType(JavaCompile::class.java).configureEach {
        options.encoding = "UTF-8"
    }
}

gradle.afterProject {
    val java = extensions.findByType(JavaPluginExtension::class.java)
    if (java != null) {
        apply(mapOf("plugin" to CheckstylePlugin::class.java))
        extensions.configure(CheckstyleExtension::class.java) {
            val v = extensions.getByType(VersionCatalogsExtension::class.java).named("libs").findVersion("checkstyle").orElse(null)
            if (v != null) {
                toolVersion = v.requiredVersion
            }
        }
        java.sourceCompatibility = JavaVersion.VERSION_25
        java.targetCompatibility = JavaVersion.VERSION_25
    }
}

include("javac")
include("compiler")
include("protocol")
include("ui")
