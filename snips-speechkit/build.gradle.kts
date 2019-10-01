repositories {
    maven("https://nexus-repository.snips.ai/repository/snips-maven-releases/")
}

dependencies {
    implementation("com.justai.aimybox:core:${Versions.aimybox}")

    implementation(Libraries.Kotlin.stdLib)
    implementation(Libraries.Android.appCompat)
    implementation(Libraries.Kotlin.coroutines)

    implementation("ai.snips:snips-platform-android:0.63.3@aar") {
        isTransitive = true
    }
}
