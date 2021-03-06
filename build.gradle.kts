import com.mkobit.jenkins.pipelines.http.AnonymousAuthentication
import org.gradle.kotlin.dsl.version
import java.io.ByteArrayOutputStream

plugins {
    id("com.gradle.build-scan") version "2.3"
    id("com.mkobit.jenkins.pipelines.shared-library") version "0.10.1"
    id("com.github.ben-manes.versions") version "0.21.0"
    // The below needs to be here in order to fix
    // https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin/issues/65
    id("org.jenkins-ci.jpi") version "0.38.0" apply false
}

sourceSets {
    integrationTest {
        resources {
            srcDirs("test/resources")
        }
    }
}


val commitSha: String by lazy {
    ByteArrayOutputStream().use {
        project.exec {
            commandLine("git", "rev-parse", "HEAD")
            standardOutput = it
        }
        it.toString(Charsets.UTF_8.name()).trim()
    }
}


buildScan {
    setTermsOfServiceAgree("yes")
    setTermsOfServiceUrl("https://gradle.com/terms-of-service")
    link("Git", "https://git.floop.org.uk/git/ONS/pmd-jenkins-library.git")
    value("Revision", commitSha)
}

tasks {
    wrapper {
        gradleVersion = "5.5.1"
    }

    // The following two magic build steps need to be included to deal with a bug/incompatibility described here:
    // https://github.com/mkobit/jenkins-pipeline-shared-libraries-gradle-plugin/issues/65
    register<org.jenkinsci.gradle.plugins.jpi.TestDependenciesTask>("resolveIntegrationTestDependencies") {
        into {
            val javaConvention = project.convention.getPlugin<JavaPluginConvention>()
            File("${javaConvention.sourceSets.integrationTest.get().output.resourcesDir}/test-dependencies")
        }
        configuration = configurations.integrationTestRuntimeClasspath.get()
    }
    processIntegrationTestResources {
        dependsOn("resolveIntegrationTestDependencies")
    }

    integrationTest {
        environment("PMD_DRAFTSET_URI_BASE", "https://staging.gss-data.org.uk/admin/drafts/")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    val spock = "org.spockframework:spock-core:1.2-groovy-2.4"
    testImplementation(spock)
    testImplementation("org.assertj:assertj-core:3.12.2")
    integrationTestImplementation(spock)
    integrationTestImplementation("com.github.tomakehurst:wiremock:2.25.1")
    integrationTestImplementation("jakarta.xml.bind:jakarta.xml.bind-api:2.3.2")
    integrationTestImplementation("org.glassfish.jaxb:jaxb-runtime:2.3.2")
    integrationTestImplementation("org.apache.jena:jena-arq:3.15.0")
}

jenkinsIntegration {
    baseUrl.set(uri("http://localhost:5050").toURL())
    authentication.set(providers.provider { AnonymousAuthentication })
    downloadDirectory.set(layout.projectDirectory.dir("jenkinsResources"))
}

sharedLibrary {

    // TODO: this will need to be altered when auto-mapping functionality is complete
    coreVersion.set(jenkinsIntegration.downloadDirectory.file("core-version.txt").map { it.asFile.readText().trim() })
    // TODO: retrieve downloaded plugin resource
    pluginDependencies {
        dependency("org.jenkins-ci.plugins", "pipeline-build-step", "2.13")
        dependency("org.6wind.jenkins", "lockable-resources", "2.10")
        val declarativePluginsVersion = "1.7.2"
        dependency("org.jenkinsci.plugins", "pipeline-model-api", declarativePluginsVersion)
        dependency("org.jenkinsci.plugins", "pipeline-model-declarative-agent", "1.1.1")
        dependency("org.jenkinsci.plugins", "pipeline-model-definition", declarativePluginsVersion)
        dependency("org.jenkinsci.plugins", "pipeline-model-extensions", declarativePluginsVersion)
        dependency("org.jenkins-ci.plugins", "config-file-provider", "3.7.0")
        dependency("org.jenkins-ci.plugins", "http_request", "1.8.27")
        dependency("org.jenkins-ci.plugins", "pipeline-utility-steps", "2.6.1")
        dependency("org.jenkins-ci.plugins", "credentials-binding", "1.24")
        dependency("org.jenkins-ci.plugins", "unique-id", "2.2.0")
        dependency("org.jenkins-ci.plugins", "docker-workflow", "1.24")
        dependency("org.jenkins-ci.plugins", "htmlpublisher", "1.23")
        dependency("org.jenkins-ci.plugins", "durable-task", "1.15")
        dependency("org.jenkins-ci.plugins", "ansicolor", "0.5.2")
        dependency("org.jenkins-ci.plugins", "mask-passwords", "2.13")

    }
}
