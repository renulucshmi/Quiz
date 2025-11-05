# Remote Classroom Polling System

A real-time quiz/poll application using Java Network Programming.

## 🏗️ Architecture

- **TCP Server** (Port 8088) - Multi-threaded server handling student connections
- **HTTP Server** (Port 8090) - RESTful APIs and web interface
- **Student Client** - Console-based TCP client
- **Web UI** - Browser-based interface for students and instructor

## 👥 Team Member Responsibilities

### Member 1: TCP Server (MainServer.java)

- **Concept:** TCP ServerSocket, Thread Pool
- **Files:** `src/server/MainServer.java`
- Create ServerSocket on port 8088
- Use ExecutorService with 16 threads
- Implement instructor CLI commands

### Member 2: Client Handler (ClientHandler.java, JsonUtil.java)

- **Concept:** Socket I/O, Protocol Design
- **Files:** `src/server/ClientHandler.java`, `src/server/JsonUtil.java`
- Handle individual client connections
- Parse and send JSON messages
- Implement communication protocol

### Member 3: HTTP Server & Web UI (HttpDashboard.java, web/\*)

- **Concept:** HTTP Protocol, RESTful APIs
- **Files:** `src/server/HttpDashboard.java`, `web/*`
- Create HTTP server on port 8090
- Implement REST endpoints
- Build web interface (HTML, CSS, JS)

### Member 4: Student Client (StudentClient.java)

- **Concept:** TCP Client Socket, Async I/O
- **Files:** `src/client/StudentClient.java`
- Connect to server
- Send/receive messages
- Display polls and results

### Member 5: Data Management (PollManager.java, Models.java)

- **Concept:** Thread Safety, Concurrency
- **Files:** `src/server/PollManager.java`, `src/server/Models.java`
- Thread-safe poll management
- Atomic operations
- Concurrent data structures

## 🚀 How to Build and Run

### Compile:

```batch
compile-all.bat
```

### Run Server:

```batch
run-server.bat
```

### Run Client:

```batch
run-client.bat
```

### Open Web UI:

```
http://localhost:8090/index.html
http://localhost:8090/student.html
http://localhost:8090/instructor.html
```

## 📝 Message Protocol (JSON)

### Client to Server:

```json
{"type":"JOIN","name":"Alice"}
{"type":"ANSWER","answer":"B"}
{"type":"PING"}
```

### Server to Client:

```json
{"type":"POLL","id":1,"question":"What is 2+2?","options":["3","4","5","6"]}
{"type":"RESULT","id":1,"counts":[1,15,2,0],"correct":1}
{"type":"ACK","message":"Answer recorded"}
{"type":"PONG"}
```

## 🎯 Network Programming Concepts

- TCP Socket Programming (ServerSocket, Socket)
- Multithreading (ExecutorService, Thread Pools)
- Thread Synchronization (Atomic classes, ConcurrentHashMap)
- HTTP Protocol (HttpServer, RESTful APIs)
- Client-Server Architecture
- JSON Protocol Design
- Asynchronous I/O

## 📚 Technologies

- Java SE 11+
- TCP Sockets
- HTTP Server (com.sun.net.httpserver)
- HTML5, CSS3, JavaScript
- JSON

## ✅ Testing

1. Start server
2. Connect multiple clients
3. Create poll via CLI: `newpoll Test? | A;B;C;D | B`
4. Start poll: `startpoll`
5. Submit answers from clients
6. End poll: `endpoll`
7. Reveal answer: `reveal`
8. Check web UI at http://localhost:8090

---

**Course:** Network Programming (5th Semester)  
**Project Type:** Multi-threaded Client-Server Application
