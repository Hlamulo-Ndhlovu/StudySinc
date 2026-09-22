**Study Sinc Applications** *This was Created by Prokreatives Team*

*An all-in-one Android student workspace and virtual study collaboration platform built with Kotlin, Jetpack Compose, and Firebase.*

**> Table of Contents**
1. About StudySinc
2. Core Features
3.  Architecture & Tech Stack
4. Repository Structure
5. Getting Started
6. Prerequisites
7. Firebase & Environment Setup
8. Building & Running
9. ~~CI/CD Pipeline~~~  *We sketched this for now*
10. Testing & Quality Assurance
11. Team & License

>**About StudySinc**
StudySinc is a centralized Android workspace designed to unify how students connect, communicate, share academic resources, and study together. Instead of switching between multiple disconnected applications for scheduling, chat, document storage, and video calling, StudySinc provides a single platform built tailored for peer-to-peer study collaboration.

Developed by **Team Prokreative**, StudySinc combines real-time group interaction, low-latency virtual study rooms, secure document management, and peer presence.


**Core Features**
Schedule online classes, publish event topics, set timelines, and attach meeting URLs.

Dedicated group chat channels for course discussions, and announcements.

Upload, download, and organize study guides, lecture slides, and PDF attachments via Firebase Storage.

Multi-party virtual study sessions powered by low-latency Agora RTC channels.

**Architecture & Tech Stack*
Android Client Application
Language: Kotlin
UI Framework: Jetpack Compose (Declarative UI).
Asynchronous Execution: Kotlin Coroutines & StateFlow / SharedFlow.
Networking & WebRTC: Retrofit2, OkHttp3, Agora RTC Android SDK.

**Backend & Cloud Infrastructure*
Authentication: Firebase Auth (JWT / ID Tokens).
Push Notifications: Firebase Cloud Messaging (FCM).
DevOps: GitHub Actions CI/CD Pipeline.



**Prerequisites*
Android Studio: Jellyfish (2024.1.1) or higher not sure I forgot which Android Studio did I choose.
JDK: Java Development Kit 17
Firebase CLI: Installed via npm install -g firebase-tools
Target Device: Android 8.0 (API level 26) or higher


