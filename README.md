# TCP Session-Based Chat Server

A Java TCP server that implements ephemeral, password-protected chat sessions directly on the Java socket APIs. The project focuses less on chat features as a product and more on the server-side engineering concerns underneath them: socket lifecycle management, session ownership, concurrent client handling, liveness detection, and the tradeoffs of a blocking, thread-per-connection architecture.

This repository is interesting from a systems perspective because it makes the application protocol, connection state, and thread coordination explicit instead of hiding them behind a framework. The code demonstrates how a simple TCP server manages multiple clients, tracks session membership, cleans up resources, and monitors client health without relying on external libraries or higher-level server frameworks.

## Architecture

The system is structured around a small number of responsibilities:

Client
→ TCP Socket
→ ServerSocket
→ ClientConnection
→ Router
→ SessionManager / Session
→ other ClientConnections

The actual flow is straightforward:

- `TcpServer` accepts incoming TCP connections and starts a new thread for each client.
- Each accepted socket is wrapped in a `ClientConnection`, which encapsulates the socket, input/output streams, and connection state.
- `Router` drives the application-level protocol for each connected client. It reads commands such as `CREATE` and `JOIN`, validates them, and decides whether the client should be attached to a session.
- `SessionManager` creates and joins sessions, while `Session` owns the participant list for a room.
- `ClientConnection` provides the boundary between network I/O and session logic.
- When a client is active in a session, messages are forwarded to other participants in that same session.
- A separate heartbeat mechanism runs on a scheduled executor to monitor whether a peer is still alive.

This separation of responsibilities is deliberate. Networking concerns remain near the socket boundary, session rules are isolated in the manager/session layer, and message broadcasting is handled by the router rather than being embedded in the socket or client logic.

## Networking Model

The application currently uses:

- TCP as the transport protocol
- blocking Java sockets
- line-oriented communication via `BufferedReader` and `PrintWriter`
- one server-side thread per active client connection

This is a simple and explicit networking model. The server accepts a client, wraps it in a `ClientConnection`, and then reads from that socket in a dedicated thread. Because each connection is handled by its own thread, a blocking read on one client does not necessarily prevent the server from servicing other clients.

That design is easy to reason about and aligns well with the educational and systems-oriented goals of the project. It also makes the architecture’s limitation visible: each active connection is associated with a thread, so the server’s resource footprint grows with the number of clients. This creates a clear motivation for a future NIO-based or non-blocking design.

This repository does not implement NIO, selectors, asynchronous channels, or an event loop. The implementation is intentionally blocking and thread-based.

## Features

The repository demonstrates the following server behaviors:

- Create an ephemeral chat session with a password
- Join an existing session using the correct password
- Broadcast messages to other participants in the same session
- Track active sessions in memory
- Remove participants when a connection terminates
- Remove the session from the global registry when the last participant leaves
- Detect a dead or unresponsive peer with a heartbeat mechanism
- Keep networking, routing, and session logic separated across classes

The chat behavior is the application-level demonstration; the underlying engineering value is in the server design.

## Application Protocol

The project uses a lightweight, line-oriented application protocol built directly on top of the socket streams. Messages are exchanged in plain text, one line at a time.

### Session creation

`CREATE,<session>,<password>`

### Session join

`JOIN,<session>,<password>`

### Heartbeat

`PING`
`PONG`

### Session exit

`./Exit`

These commands are intentionally simple. There is no formal protocol versioning, no binary framing, and no encryption layer. The protocol is a minimal application-level convention between the client and server.

## Session Lifecycle

The session model is simple but useful to examine from an engineering perspective.

1. A client connects to the TCP server.
2. The client identifies itself by sending a name.
3. The client sends either `CREATE` or `JOIN` with a session identifier and password.
4. `Router` delegates the request to `SessionManager`.
5. `SessionManager` either creates a new session or validates access to an existing one.
6. Once the session is active, messages are broadcast to the other participants.
7. When the client disconnects or sends `./Exit`, it is removed from the session.
8. If the session becomes empty, it is removed from the global session registry.

This lifecycle is meaningful because it demonstrates real server concerns: session ownership, participant tracking, cleanup, and state transitions over time. The project is not just “chat rooms”; it is an in-memory session manager built on top of sockets.

## Concurrency and Connection Management

The concurrency model is one of the strongest parts of the repository and worth emphasizing.

### Global session registry

The server stores sessions in a `ConcurrentHashMap`:

- `GlobalMapHolder.SHARED_MAP`

This ensures the map can be accessed by multiple connection-handling threads without requiring a single-threaded bottleneck. Session lookup by name is expected to be average-case `O(1)`.

### Session participants

Each `Session` owns a `CopyOnWriteArrayList<ClientConnection>` for its participants.

This structure supports concurrent iteration while updates occur. The repository frequently iterates through the participant list to broadcast messages, while participant modifications are comparatively less frequent. That makes `CopyOnWriteArrayList` a reasonable fit for this access pattern, even if it is not necessarily the optimal choice for all higher-throughput workloads.

### Thread model

The server creates a dedicated thread for each accepted socket:

- `TcpServer` accepts a client
- a new `Thread` is created
- `GlobalRouter.SHARED_ROUTER.start(connection)` runs on that thread

This architecture is simple and explicit, and it allows each client to have independent blocking reads and writes without central serialization. The cost is that each active connection consumes a thread, which is a natural limitation of the current design.

### Synchronization

Synchronization is used in specific areas where shared state requires coordination:

- `Session.addParticipant` is synchronized
- `Session.remove` is synchronized
- `ClientConnection.send` is synchronized
- some methods coordinate access to the socket and connection data

This is not a claim that the entire system is globally thread-safe in every scenario. The code demonstrates targeted synchronization for state transitions and socket writes, but the architectural simplicity of the design remains clear: the system is a multi-threaded server with shared state that is intentionally constrained and managed.

## Heartbeat and Liveness Detection

The repository includes a heartbeat implementation in `ScheduledHeartBeatChecker`.

### Actual mechanism

A scheduled task runs every 5 seconds:

- if the connection is still alive, it sends `PING`
- if the connection has not responded, it closes the socket

The liveness state is tracked by an `AtomicBoolean` in `ClientConnection`:

- when a `PONG` arrives, `setIsAlive()` is called
- when a ping is sent, `consumeIsAlive()` is called
- if the connection does not respond, the heartbeat task removes the monitor and closes the socket

This is a simple application-level liveness check. It does not try to pretend it is a full distributed health system or a sophisticated protocol. The point is practical: TCP does not always provide immediate application-level certainty that a peer has disappeared in all failure modes, so the server performs an explicit check.

This is a useful systems engineering pattern because it makes connection failure behavior visible and allows cleanup to occur before stale sockets remain in a session.

## Session and Message Flow

A typical connection lifecycle is:

1. Client connects.
2. Client sends a display name.
3. Client sends `CREATE,<session>,<password>` or `JOIN,<session>,<password>`.
4. `Router` validates the request.
5. If accepted, the session is created or the client joins.
6. The client sends messages.
7. Messages are broadcast to all other participants.
8. Client sends `./Exit` or disconnects.
9. Participant is removed.
10. If no participants remain, the session is removed from `GlobalMapHolder.SHARED_MAP`.

This is the real engineering value of the project: it is a small but concrete example of application-layer session orchestration built over raw sockets.

## Example Interaction

A simple example of the system’s intended behavior:

Client A
- connects
- sends `CREATE,engineering,secret123`
- receives `OK, Session successfully created`

Client B
- connects
- sends `JOIN,engineering,secret123`
- receives `OK, Successfully joined session`

Client A sends:
- `hello from A`

Client B receives:
- `A : hello from A`

This demonstrates the core application behavior: sessions are room-like abstractions, and messages are routed to members of the same session instead of broadcast to all clients globally.

## Design Decisions and Tradeoffs

### Blocking sockets

The implementation favors blocking Java sockets because they keep the code explicit and easy to follow. A client’s read operation blocks until data arrives, but because each client is assigned its own thread, the server can serve multiple clients concurrently without a more complex event-driven scheduler.

This is easy to teach and easy to reason about, but it also introduces a clear scaling issue: one active connection requires one thread.

### Thread-per-connection model

The thread-per-client design is simple and effective for a small number of simultaneous clients. It also makes the lifetime of a connection and the ownership of socket resources very visible. The tradeoff is that it does not scale as gracefully as a non-blocking or event-driven architecture.

### `ConcurrentHashMap`

The session registry is shared across multiple client threads, so `ConcurrentHashMap` is the appropriate choice for the global session index. It prevents the application from relying on a single-threaded session registry.

### `CopyOnWriteArrayList`

Participants are stored in a `CopyOnWriteArrayList`, which is compatible with frequent iteration during message broadcasts and relatively infrequent mutation. This matches the repository’s access pattern more closely than a simple array list would, and it permits safe iteration without explicit locking in many cases.

### Heartbeat mechanism

The project does not depend solely on TCP to signal client death. Instead, it introduces a heartbeat mechanism that sends `PING` and expects `PONG`. This is a practical application-layer liveness check designed to catch stale or broken connections that may otherwise remain in the session state.

## Project Structure

```text
TCP-session-based-chat-server
├── .gitignore
└── src
    ├── Builder
    │   └── ConnectionHandler.java
    ├── Client
    │   └── TcpClient.java
    ├── Clients
    │   └── ClientConnection.java
    ├── Connection
    │   └── ScheduledHeartBeatChecker.java
    ├── GlobalHolders
    │   ├── GlobalMapHolder.java
    │   ├── GlobalRouter.java
    │   └── GlobalSystemReader.java
    ├── Manager
    │   ├── Session.java
    │   └── SessionManager.java
    ├── Router
    │   └── Router.java
    ├── Runnables
    │   ├── ConsoleWriter.java
    │   └── SocketReader.java
    ├── Server
    │   └── TcpServer.java
    └── ...
```

The structure separates responsibilities clearly:

- `Server` starts the listener
- `Clients` wraps individual socket connections
- `Router` handles session logic
- `Manager` owns session state
- `GlobalHolders` contains shared runtime state
- `Connection` contains liveness monitoring
- `Builder` and `Runnables` are client-side or terminal-oriented helpers

## Running the Project

The repository currently does not include Maven, Gradle, or a project build file. The project is compiled directly with `javac`.

### Compile

```bash
javac -d out $(find src -name "*.java")
```

### Start the server

```bash
java -cp out Server.TcpServer
```

### Start a client

```bash
java -cp out Client.TcpClient
```

The default server port is `8080`, and the default host is `localhost`.

## Limitations

This project is intentionally small and demonstrates a clear server architecture, but there are meaningful architectural constraints:

- blocking I/O, with one thread per active client
- in-memory session state only
- no persistence between process restarts
- no TLS or transport-layer encryption
- minimal input validation
- a simple, text-based application protocol without formal message schema
- no automated test suite
- no build automation or packaging configuration

These are not criticisms of the implementation; they are the design boundaries of a first version.

## Next Version: Java NIO / Non-Blocking Networking

This repository represents a first implementation built around direct socket programming and a thread-per-client model. A natural next step would be a v2 that explores Java NIO:

- `ServerSocketChannel`
- `SocketChannel`
- `Selector`
- non-blocking I/O
- explicit connection state tracking
- event-driven message handling
- reduced coupling to one thread per client

That next version would address the architectural cost of blocking sockets while preserving the same basic session semantics and server responsibilities.

## License

No explicit license file is present in the repository at the time of inspection, so no license declaration has been established for this project.

## Engineering Summary

This project is best understood as a Java TCP server implementing ephemeral, password-protected chat sessions using direct socket APIs and explicit concurrency primitives. The engineering interest is not in the chat feature itself, but in the architecture beneath it:

- connection lifecycle management
- blocking I/O
- thread-per-client concurrency
- room/session state
- shared mutable state across threads
- liveness detection
- cleanup and resource management
- separation of responsibilities across networking, routing, and session layers

That is the technical story this repository tells.
