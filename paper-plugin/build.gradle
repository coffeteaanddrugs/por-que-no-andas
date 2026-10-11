plugins {
	id 'java'
}

group = 'com.smartphone'
version = '1.0.0'

repositories {
	mavenCentral()
	maven { url = "https://repo.papermc.io/repository/maven-public/" }
	maven { url = "https://repo.plasmoverse.com/releases" }
	maven { url = "https://repo.plasmoverse.com/snapshots" }
}

dependencies {
	compileOnly "io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT"
	// Plasmo Voice (solo la API; el plugin real debe estar instalado en el servidor)
	compileOnly "su.plo.voice.api:server:2.1.17"
}

java {
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType(JavaCompile).configureEach {
	options.release = 21
	options.encoding = "UTF-8"
}

processResources {
	inputs.property "version", project.version
	filesMatching("plugin.yml") {
		expand "version": project.version
	}
}

jar {
	archiveBaseName = 'SmartphonePlugin'
}
