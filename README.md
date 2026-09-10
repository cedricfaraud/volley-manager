# Volley Manager

Application Android native pour gérer un collectif de volley, ses séances, ses matchs, les invités et le suivi des présences.

## Choix techniques

- Kotlin et Jetpack Compose pour une interface Android moderne.
- Room pour une base locale utilisable hors connexion.
- Architecture simple en couches : données Room, `ViewModel`, écrans Compose.

## Fonctionnalités du MVP

- Création et suppression de joueurs (nom, prénom, âge, poste).
- Création de séances d'entraînement, matchs et séances exceptionnelles.
- Récurrence hebdomadaire paramétrable et annulation d'une séance.
- Ajout d'invités à un événement.
- Présent, absent ou excusé, avec absences sur une période.
- Tableau de bord avec taux d'absence.

Ouvrir `volley-manager` dans Android Studio Ladybug ou plus récent, puis lancer `app`.

## Émulateur inclus

Le projet déclare un émulateur géré par Gradle nommé `pixel2Api35` (Pixel 2, Android API 35).
Après installation de l'image système Android API 35 dans Android Studio, il est disponible
dans la liste des appareils virtuels. Il peut aussi être démarré depuis le terminal :

```bash
./gradlew :app:pixel2Api35Debug
```

La tâche télécharge automatiquement l'image système si le SDK Android est correctement
configuré (`ANDROID_HOME` ou `ANDROID_SDK_ROOT`).
