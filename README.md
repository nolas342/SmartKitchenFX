# 🍳 SmartKitchenFX — Distributed Restaurant System (JavaFX + Lamport + Agrawala)

![JavaFX](https://img.shields.io/badge/JavaFX-UI_Framework-blue)
![Gradle](https://img.shields.io/badge/Gradle-Build_System-green)
![Status](https://img.shields.io/badge/Status-Active-success)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## 📘 Overview

**SmartKitchenFX** is a distributed system simulation that demonstrates **Lamport’s Logical Clock** and **Agrawala’s Mutual Exclusion Algorithm** in a restaurant-style setting.

Each **client node** acts as a cash register placing orders concurrently, while a **central server** manages the global ordering of requests and ensures fairness using **logical timestamps**.

The project’s **ModernFX UI edition** introduces a sleek JavaFX interface, featuring:
- Interactive dashboards
- Real-time Lamport queue visualization
- Enhanced logging & clock synchronization
- Modular structure for easy extension to real network sockets

---

## 📄 Read These First!

Before running the project, please read these two documents inside the repo:

1. 📘 **SmartKitchenFX Documentation**  
   → Explains architecture, requirements, features, and how to run the simulation.  

2. 🧭 **SmartKitchenFX — Next Steps**  
   → Guides you through transforming the simulation into a real distributed network (socket-based).

---

## 🧱 Project Structure

```

SmartKitchenFX/
│
├── app/
│   ├── src/main/java/smk/
│   │   ├── client/                # Client logic & UI (order terminals)
│   │   ├── server/                # Server logic & dashboard
│   │   └── shared/                # Shared logic (LamportClock, OrderRow)
│   │
│   └── resources/
│       ├── css/                   # UI styling
│       ├── fxml/                  # Layouts for Client & Server
│       └── img/                   # Icons & assets
│
├── build.gradle
├── settings.gradle
├── .gitignore
└── README.md

````

---

## 🧩 Requirements

| Component | Version / Description |
|------------|----------------------|
| **Java JDK** | 17+ (recommended 21) |
| **Gradle** | Included wrapper (`./gradlew`) |
| **JavaFX SDK** | Managed automatically via Gradle |
| **IDE** | IntelliJ IDEA / VS Code / Eclipse |
| **OS** | Windows, macOS, or Linux |

---

## ⚙️ How to Run

### 1️⃣ Build the project
```bash
./gradlew build
````

### 2️⃣ Run the Client UI

```bash
./gradlew :app:run -PmainClass="smk.client.ui.SmartKitchenClientModernApp"
```

### 3️⃣ Run the Server UI

```bash
./gradlew :app:run -PmainClass="smk.server.ui.SmartKitchenServerModernApp"
```

> 💡 Run both in separate terminals or IDE windows.

---

## 🧠 Core Concepts

* **Lamport Logical Clock:**
  Maintains causality across distributed nodes.

* **Agrawala Mutual Exclusion:**
  Ensures ordered access to the shared critical section (kitchen queue).

* **Client–Server Architecture:**
  Clients issue timestamped requests → server orders them logically.

---

## 🧮 Example Output

```
[RECV] C1 Pizza ts=1 → L=3
[START] C1 Pizza (Lq=3) S(L)=4
[END]   C1 Pizza DONE. S(L)=5
```

---

---


## 📜 License

This project is open-source under the [MIT License](LICENSE).
