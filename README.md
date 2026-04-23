# 🏁 Networked Checkers Game

A full-stack, **multiplayer desktop application** that allows users to play Checkers over a network. This project demonstrates the implementation of complex game logic, multi-threaded networking, and a responsive real-time user interface.

---

## 🚀 Key Features
* **Real-Time Multiplayer:** Full client-server architecture allowing two players to connect and play from different machines.
* **Integrated Chat System:** A concurrent chat window that allows players to communicate without interrupting game flow.
* **Move Validation:** Robust server-side logic for legal moves, including king-piece mechanics and forced jumps.
* **Recursive Multi-Jumps:** Engineered a recursive algorithm to detect and enforce multiple jump sequences in a single turn.
* **Sync State Management:** Uses Object Streams to keep the board state perfectly synchronized between both clients.

---

## 🛠️ Tech Stack
* **Language:** Java
* **Framework:** JavaFX (GUI & Animations)
* **Networking:** Java Sockets (TCP/IP)
* **Data Transfer:** Object Streams for serialized game states
* **Architecture:** Client-Server Model

---

## 📈 Development Journey
This project focused on the intersection of **UI design** and **network synchronization**. Our process involved:
1.  **Protocol Design:** Defining how game moves and chat messages would be serialized and sent over sockets.
2.  **Concurrency:** Implementing multi-threading so the UI stays responsive while the client listens for incoming network data.
3.  **Validation Logic:** Building the "Referee" logic to ensure all Checkers rules are strictly followed.

---

## 🧠 Technical Challenges
* **The Multi-Jump Logic:** Designing the recursive logic for multi-jumps was the most complex hurdle—ensuring the game accurately identifies when a turn *must* continue.
* **Network Latency & Sync:** Handling the "Race Condition" where a player might try to move before the server has updated the state. We solved this by implementing a strict turn-based locking system.
* **Thread Safety:** Ensuring the JavaFX UI thread correctly handled updates coming in from the background networking threads.

---

## 👥 Collaboration
This was a collaborative effort between two partners. I focused on server end, while also contributing to the core move-validation algorithms and debugging the final socket connections.
