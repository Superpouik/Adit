plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.pouik.audit"
    compileSdk = 35
    // Le SDK de la machine fournit 34.0.0, 36.0.0 et 37.0.0, jamais le 35.0.0
    // qu'AGP réclamerait par défaut.
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "fr.pouik.audit"
        // 29 suffirait pour écrire dans MediaStore sans permission, 30 aligne
        // ce projet sur les autres et donne java.time natif.
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"
    }

    // Clé de signature locale, la même que sur les autres projets maison :
    // sans elle, impossible de mettre l'app à jour sans la désinstaller.
    val cle = rootProject.file("cle-maison.jks")
    signingConfigs {
        if (cle.exists()) {
            create("maison") {
                storeFile = cle
                storePassword = "pointeuse"
                keyAlias = "maison"
                keyPassword = "pointeuse"
            }
        }
    }

    buildTypes {
        val maison = signingConfigs.findByName("maison")
        release {
            isMinifyEnabled = false
            signingConfig = maison
            resValue("string", "app_name", "Audit")
        }
        debug {
            if (maison != null) signingConfig = maison
            // Une build de test s'installe À CÔTÉ de l'application réelle :
            // identifiant distinct, données séparées, et aucun moyen d'effacer
            // les photos d'un chantier en se trompant d'APK.
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            resValue("string", "app_name", "Audit (test)")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.tooling)

    // Appareil photo intégré : un aller-retour vers l'app photo du téléphone
    // par cliché coûterait deux secondes à chaque fois, et on en prend trente
    // par audit.
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Vignettes : décoder trente JPEG de 4000 px à la main dans un LazyGrid,
    // c'est réécrire un cache d'images. Coil le fait mieux.
    implementation(libs.coil.compose)

    // Une photo prise en paysage est écrite droite dans le fichier avec une
    // consigne de rotation à côté : sans la lire, l'éditeur annoterait une image
    // couchée.
    implementation(libs.exifinterface)

    // Le catalogue des audits est un JSON dans filesDir : quelques dizaines
    // de fiches, Room n'apporterait qu'un processeur d'annotations.
    implementation(libs.serialization.json)

    // Les calculs qui comptent (noms de dossier, numérotation, récapitulatif)
    // tournent sur la JVM, donc testables sans émulateur.
    testImplementation(libs.junit)
    // Le dépôt est suspendu de bout en bout : sans runTest, aucun de ses
    // comportements n'est vérifiable hors émulateur.
    testImplementation(libs.coroutines.test)
}
