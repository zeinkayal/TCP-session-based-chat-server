# TCP Session Chat Server

A Java server that hosts ephemeral, password-protected chat rooms over raw TCP sockets, with no networking framework. Sessions live only in memory and disappear when the last participant leaves. The project is centered on the server-side problems underneath a chat application: connection lifecycle, shared state across many connection-handling threads, liveness detection, and deterministic cleanup.

Stack: Java 25, Maven, `java.net` sockets, virtual threads, `java.util.concurrent`, JUnit 5, Awaitility.

---

## What this project demonstrates

- A hand-built application protocol over line-oriented TCP
- Per-connection concurrency on virtual threads
- Shared mutable state with explicit synchronization choices
- Failure detection at the application layer
- Session lifecycle management and room cleanup
- Integration tests over real loopback sockets

No benchmarks have been run, so this README does not claim performance numbers or scalability guarantees.

---

## Architecture

```text
TcpServer.accept()
   └─ virtual thread per connection ──► Router.start(connection)
        ├─ InitializeSession      name → CREATE/JOIN loop → SessionManager
        ├─ startHeartBeatMonitor  (only after a successful join)
        └─ streamAndCleanUp       broadcast loop
              finally: cancel heartbeat, leave session, close socket

Component responsibilities

    Server.TcpServer
        opens a ServerSocket
        accepts incoming TCP connections
        wraps each socket in a ClientConnection
        submits work to GlobalExecutor.executor

    GlobalHolders.GlobalExecutor
        manages the process-wide virtual-thread executor
        Executors.newVirtualThreadPerTaskExecutor()

    Clients.ClientConnection
        owns the socket, reader, writer, display name, liveness flag, and heartbeat task
        serializes outbound writes with synchronized send(...)

    Router.Router
        drives the protocol flow
        runs the create/join handshake
        broadcasts messages to the session
        performs cleanup in a finally block

    Manager.SessionManager
        creates and joins sessions
        validates passwords
        returns OK or ERROR responses

    Manager.Session
        owns the participant list
        manages membership add/remove logic
        removes itself from the global registry when empty

    Connection.ScheduledHeartBeatChecker
        runs a scheduled task per connection
        sends PING to detect stale clients
        closes the socket when the previous PONG was not received

    GlobalHolders.*
        contains process-wide state such as the session registry and shared router

    Client, Builder, and Runnables
        client-side terminal logic, handshake prompts, and read/write loops

The handshake and streaming phases are deliberately separated (InitializeSession and streamAndCleanUp). That separation is what makes the lifecycle and cleanup logic testable without needing to simulate a full terminal UI.
Protocol

All messages are single lines of UTF-8 text.
Handshake
Text

client → server   <display name>
client → server   CREATE,<session>,<password>
client → server   JOIN,<session>,<password>
server → client   OK, <message>
server → client   ERROR, <message>

After an ERROR, the client may retry on the same connection. The server keeps looping in the handshake until a create or join succeeds or the client disconnects.
Streaming
Text

client → server   any other text
server → client   PING
client → server   PONG
client → server   ./Exit

Behavior:

    a normal chat message is delivered to every other participant as <name> : <text>
    PING is sent by the server to detect stale peers
    PONG resets the liveness flag
    ./Exit ends the session loop and triggers cleanup

Wire-level example:
Text

A → server   alice
A → server   CREATE,engineering,secret123
server → A   OK, Session successfully created

B → server   bob
B → server   JOIN,engineering,wrongpw
server → B   ERROR, Wrong password! Please enter the password again

B → server   JOIN,engineering,secret123
server → B   OK, Successfully joined session

A → server   hello
server → B   alice : hello

server → A   PING
A → server   PONG

PONG and ./Exit are treated as control traffic. A message consisting of exactly those strings is not forwarded as normal chat content.
Concurrency model
Session registry

The server stores sessions in a ConcurrentHashMap:

    GlobalMapHolder.SHARED_MAP

This allows multiple connection-handling threads to create, join, and remove sessions without depending on a single global lock.
Session membership

Each session keeps its participants in a CopyOnWriteArrayList<ClientConnection>.

This is a good fit for the server’s usage pattern:

    the broadcast loop iterates the participant list frequently
    membership changes are less frequent
    the list is updated by join/leave operations

Synchronization

Synchronization is used in targeted places:

    Session.addParticipant(...) is synchronized
    Session.remove(...) is synchronized
    ClientConnection.send(...) is synchronized

This is not a claim that the entire server is magically perfect or universally lock-free; it is a practical way to serialize state transitions and outbound writes that could otherwise interleave.
Thread model

Each accepted connection is serviced by its own virtual thread. This keeps the model straightforward:

    the server can accept many connections without a one-thread-per-connection pattern in the old blocking sense
    each client maintains its own read loop
    individual connections remain independent

The tradeoff remains visible: the server still has a relationship between active clients and execution resources, even though the underlying threading model is virtual-thread-based rather than classic thread-per-task.
Connection lifecycle and failure handling

Every exit path converges on the finally block in streamAndCleanUp, which:

    cancels the heartbeat task
    removes the participant from the session
    removes the session if it is empty
    closes the socket

Lifecycle events

    client sends ./Exit
        broadcast loop ends
        cleanup runs

    client disconnects or the read fails
        readLine() returns null or throws
        cleanup runs

    peer goes silent
        the next heartbeat tick sees that the liveness flag was not reset
        the socket is closed
        the same cleanup path runs

    last participant leaves
        session is removed from the registry
        the session name can be reused

The heartbeat starts only after a successful join, so PING never overlaps with the handshake itself. A silent peer is evicted after the next missed liveness check; with the current 5-second heartbeat interval, that means roughly 5–10 seconds after the last valid PONG.
Testing

The project includes automated tests under src/test/java using JUnit 5 and Awaitility. The tests validate real socket behavior rather than only isolated unit logic.
Current coverage

    connection lifecycle behavior
    session cleanup when a client closes
    multi-client interaction and message broadcasting

Relevant classes:

    Tests.TestForConnectionLifeCycleAndSessionCleanUp
    Tests.TestingMultipleClientInteraction

These tests start a server on a loopback socket and assert asynchronous outcomes using Awaitility instead of fixed sleeps.

Not yet covered:

    handshake error paths
    duplicate session names
    wrong password handling
    malformed input
    concurrent create/join/leave race cases on a single session name
    silent-peer eviction under the heartbeat

The heartbeat test waits in real time because the timer is fixed at 5 seconds.
Build and run

The project uses Maven.
Build and test
bash

./mvnw clean test

Compile only
bash

./mvnw clean compile

Run the server
bash

./mvnw exec:java -Dexec.mainClass=Server.TcpServer

If the exec plugin is not configured in your local setup, you can run the compiled classes directly:
bash

./mvnw clean compile
java -cp target/classes Server.TcpServer

Run a client
bash

java -cp target/classes Client.TcpClient

The default server port is 8080 and the default host is localhost.

At the client prompt, enter a display name and then type:
Text

Create <session> <password>

or
Text

Join <session> <password>

Type ./Exit to leave the room.
Known limitations

    No transport security; traffic and passwords are sent in plaintext
    No persistence; sessions exist only in memory
    Minimal validation of malformed input
    Informal application protocol with comma-delimited fields
    No formal protocol versioning
    No rate limiting on repeated join attempts
    No external observability or logging system
    No build pipeline or CI configuration yet
    Session state is process-local and not distributed

These are not flaws in the sense of “broken”; they are the practical boundaries of the repository as it currently exists.
Roadmap

The repository is clearly moving toward a more structured systems project:

    richer server commands such as MEMBERS, KICK, and FLUSH
    explicit admin/owner authority within a session
    TLS/SSL for encrypted client-server communication
    a Java NIO version using Selector and non-blocking I/O
    stronger validation and malformed-input handling
    improved configuration and cleaner build/test automation

This is a natural next-phase evolution for the existing architecture.
Project structure
Text

src
├── Server           TcpServer
├── Clients          ClientConnection
├── Router           Router
├── Manager          Session, SessionManager
├── Connection       ScheduledHeartBeatChecker
├── GlobalHolders    GlobalMapHolder, GlobalRouter, GlobalExecutor, GlobalSystemReader
├── Client           TcpClient
├── Builder          ConnectionHandler
├── Runnables        ConsoleWriter, SocketReader
├── Tests            JUnit/Awaitility integration tests
├── TestDataFactory  mock server/client helpers
└── main/java        all production source files

License

No explicit license file is present in the repository at the time of inspection, so no license declaration has been established for the project.
Engineering summary

This repository is best described as a Java TCP session server built directly on socket primitives, with explicit handling of:

    connection lifecycle
    room/session state
    thread-per-client concurrency
    state cleanup
    liveness checks
    separation of responsibilities across networking, routing, and session logic
