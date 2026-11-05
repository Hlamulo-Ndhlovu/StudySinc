**Study Sinc Applications** *This was Created by Prokreatives Team*

*An all-in-one Android student workspace and virtual study collaboration platform built with Kotlin, Jetpack Compose, and Firebase.*

**> Table of Contents**
1. About StudySinc
2. Application Screenshot
3. Core Features
4.  Architecture & Tech Stack
5. Repository Structure
6. Getting Started
7. Prerequisites
8. Firebase & Environment Setup
9. Building & Running
10. CI/CD Pipeline 
11. Testing & Quality Assurance
12. Team & License

>**About StudySinc**
StudySinc is a centralized Android workspace designed to unify how students connect, communicate, share academic resources, and study together. Instead of switching between multiple disconnected applications for scheduling, chat, document storage, and video calling, StudySinc provides a single platform built tailored for peer-to-peer study collaboration.

Developed by **Team Prokreative**, StudySinc combines real-time group interaction, low-latency virtual study rooms, secure document management, and peer presence.
<img width="1080" height="2340" alt="study" src="https://github.com/user-attachments/assets/aae31821-fa31-4078-8f90-ef0ca401adbc" />
Figure 1.1 Login/Register UI

<img width="1080" height="2340" alt="Study1" src="https://github.com/user-attachments/assets/dc570481-4f33-4aaa-9d03-c9c311497fbe" />
Figure 1.2 How the Dashboard look like and the Menu


<img width="1080" height="2340" alt="Study2" src="https://github.com/user-attachments/assets/4b146cf0-b4c1-43fd-b72c-c81eea5513c4" />
Figure 1.3 Class UI were users can join meetings or Go Live




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

**Building & Running*
Open the project in Android Studio.
Sync Gradle files (File > Sync Project with Gradle Files).
Select an emulator or physical Android device.
Click Run to build and launch the application.

**Team & License*
Developer & Maintainer: Team Prokreative
Application Package ID: com.prokreatives.studysync
License: Distributed under the MIT License. This was created for Educational Purposes and part of the Rosebank International Assessment See LICENSE for details.
