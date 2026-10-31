# FileQueue Socket Java Client (modernized)

Original protocol preserved: 4-byte big-endian frame length + JSON, `LINK`, `ENQU`, `DEQU`, `FQP_VERSION=10`, `ACK_MODE=Y`, session ID.

## Build

Requires Java 17+ and the `org.json` JSON-java JAR (not included).

```bash
mkdir -p out
javac -cp 'lib/*' -d out FileQueueSocket.java FileQueueSocketExample.java
java -cp 'out:lib/*' FileQueueSocketExample 127.0.0.1 9000 /sendq1/enmq KT_SM_ONL
```

Place a compatible `org.json` JAR in `lib/` before compiling. The package is `com.clang.socket`.

## API

- `open()` — connects and performs LINK; throws on failure.
- `write(String)` — one ENQU; returns `Result` with `OK`, `FULL`, or `ERROR`.
- `read()` — one DEQU; returns `OK` (with message), `EMPTY`, or `ERROR`.
- `writeRetryOnFull(String, 1000)` — repeats only on explicit `FULL` responses.
- `close()` — closes socket and clears session. Use try-with-resources.

## Important compatibility / reliability notes

1. `MSG_LENGTH` uses UTF-8 byte length. Original code used Java `String.length()`; confirm the server expects bytes. Change this one calculation if server expects characters.
2. Response frame is limited to 1 MiB by default; adjust constructor configuration to match server.
3. A socket failure after sending ENQU leaves outcome **uncertain**; this client does not automatically retry such failures. Implement application-level deduplication or server status inquiry if needed.
4. Socket protocol `DEQU` has no XA commit/cancel in the supplied Java client. Verify server semantics before using it for critical delivery.
5. One client instance is designed for one worker; do not share across unrelated concurrent consumers.
6. `writeRetryOnFull()` is intentionally unbounded. Interrupt the waiting thread to stop; consider bounded retry policies in production.
7. The example enqueues then dequeues from the same queue; use separate queue objects for distinct input/output queues in real applications.
8. Server-side behavior and `RESULT` error vocabulary are not verified without the C server source.
