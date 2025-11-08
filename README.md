# Remote Classroom Polling System

A real-time quiz/poll application with **Chat/Discussion Feature** using Java Network Programming.

## 🏗️ Architecture

- **TCP Server** (Port 8088) - Multi-threaded server handling student connections
- **HTTP Server** (Port 8090) - RESTful APIs and web interface
- **Student Client** - Console-based TCP client
- **Web UI** - Browser-based interface for students and instructor
- **Chat System** - Real-time discussion feature for students

## ✨ New Feature: Chat/Discussion Room 💬

The chat feature allows students to discuss poll questions and results in real-time:

### Features:

- **Real-time messaging** - Instant broadcast to all connected students
- **Instructor moderation** - Enable/disable chat at any time
- **Message history** - Store up to 100 recent messages
- **Thread-safe operations** - Concurrent message handling using ConcurrentLinkedQueue
- **Web & TCP support** - Chat accessible from web UI and TCP clients
- **System notifications** - Automatic messages when chat is enabled/disabled

### How to Use Chat:

#### For Instructors:

1. Open Instructor Panel: `http://localhost:8090/instructor.html`
2. Use chat controls:
   - `enablechat` - Enable chat for discussion (CLI)
   - `disablechat` - Disable chat (CLI)
   - `clearchat` - Clear all messages (CLI)
   - Or use buttons in the web panel
3. Click "Open Chat Room" to monitor discussions

#### For Students (Web):

1. Open Chat Room: `http://localhost:8090/chat.html`
2. Enter your name to join
3. Send messages when chat is enabled
4. View real-time messages from other students

#### For Students (TCP Client):

- Chat messages appear automatically in the console
- Messages prefixed with 💬 username
- System messages shown in brackets

## 👥 Team Member Responsibilities

### Member 1: TCP Server (MainServer.java)

- **Concept:** TCP ServerSocket, Thread Pool
- **Files:** `src/server/MainServer.java`
- Create ServerSocket on port 8088
- Use ExecutorService with 16 threads
- Implement instructor CLI commands
- **New:** Chat management commands (enablechat, disablechat, clearchat)

### Member 2: Client Handler (ClientHandler.java, JsonUtil.java)

- **Concept:** Socket I/O, Protocol Design
- **Files:** `src/server/ClientHandler.java`, `src/server/JsonUtil.java`
- Handle individual client connections
- Parse and send JSON messages
- Implement communication protocol
- **New:** Implement ChatListener interface for message broadcasting

### Member 3: HTTP Server & Web UI (HttpDashboard.java, web/\*)

- **Concept:** HTTP Protocol, RESTful APIs
- **Files:** `src/server/HttpDashboard.java`, `web/*`
- Create HTTP server on port 8090
- Implement REST endpoints
- Build web interface (HTML, CSS, JS)
- **New:** Chat HTTP endpoints (/chat/messages, /chat/send, /chat/enable, etc.)

### Member 4: Student Client (StudentClient.java)

- **Concept:** TCP Client Socket, Async I/O
- **Files:** `src/client/StudentClient.java`
- Connect to server
- Send/receive messages
- Display polls and results
- **New:** Handle incoming chat messages and display in console

### Member 5: Data Management (PollManager.java, Models.java, ChatManager.java)

- **Concept:** Thread Safety, Concurrency
- **Files:** `src/server/PollManager.java`, `src/server/Models.java`, `src/server/ChatManager.java`
- Thread-safe poll management
- Atomic operations
- Concurrent data structures
- **New:** ChatManager with ConcurrentLinkedQueue for thread-safe messaging

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
http://localhost:8090/chat.html
```

## 📝 Message Protocol (JSON)

### Client to Server:

```json
{"type":"JOIN","name":"Alice"}
{"type":"ANSWER","answer":"B"}
{"type":"CHAT","message":"Great question!"}
{"type":"PING"}
```

### Server to Client:

```json
{"type":"POLL","id":1,"question":"What is 2+2?","options":["3","4","5","6"]}
{"type":"RESULT","id":1,"counts":[1,15,2,0],"correct":1}
{"type":"ACK","message":"Answer recorded"}
{"type":"CHAT","id":5,"username":"Bob","message":"I think it's B","timestamp":1699564800000}
{"type":"chatCleared","message":"Chat history has been cleared"}
{"type":"PONG"}
```

## 🎯 Network Programming Concepts

- TCP Socket Programming (ServerSocket, Socket)
- Multithreading (ExecutorService, Thread Pools)
- Thread Synchronization (Atomic classes, ConcurrentHashMap, ConcurrentLinkedQueue)
- HTTP Protocol (HttpServer, RESTful APIs)
- Client-Server Architecture
- JSON Protocol Design
- Asynchronous I/O
- **Message Broadcasting** - One-to-many communication pattern
- **Observer Pattern** - ChatListener interface for event notification
- **Thread-Safe Collections** - ConcurrentLinkedQueue for chat messages

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
8. **Enable chat:** `enablechat`
9. **Open chat room:** http://localhost:8090/chat.html
10. **Send messages** and see real-time updates
11. Check web UI at http://localhost:8090

## 🆕 Chat Feature Testing

### Test Scenario 1: Basic Chat

1. Start server
2. Open chat.html in multiple browser tabs
3. Join with different names
4. Enable chat from instructor CLI: `enablechat`
5. Send messages from different tabs
6. Verify messages appear in real-time on all tabs

### Test Scenario 2: Moderation

1. With chat enabled and messages flowing
2. From instructor CLI, run: `disablechat`
3. Verify students cannot send messages
4. Run: `enablechat` to re-enable
5. Verify chat is active again

### Test Scenario 3: Clear History

1. Send several messages
2. From instructor CLI, run: `clearchat`
3. Verify all messages are cleared for all users
4. System message appears notifying users

### Test Scenario 4: TCP Client Chat

1. Start server, enable chat
2. Connect TCP client: `run-client.bat`
3. Join with a name
4. Send messages from web chat
5. Verify TCP client receives and displays chat messages in console

---

**Course:** Network Programming (5th Semester)  
**Project Type:** Multi-threaded Client-Server Application with Real-time Chat  
**New Feature:** Chat/Discussion System with Thread-Safe Broadcasting
