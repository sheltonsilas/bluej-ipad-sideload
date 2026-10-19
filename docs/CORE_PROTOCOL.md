# Core protocol — Java runtime ↔ native shell

The iPad shell never touches interpreter internals. It sends one JSON object and receives
one JSON object per call through `org.bluej.ipad.core.CoreProtocol`. The bridge on the iOS
side only has to move two strings in each direction (for example a C entry point exported by
the GraalVM image that calls `CoreProtocol.handle`).

Implementation and tests: `spike-java/src/main/java/org/bluej/ipad/core/` and
`spike-java/src/test/java/org/bluej/ipad/core/`. Evidence:
[docs/evidence/core-session-protocol.md](evidence/core-session-protocol.md).

## Requests

| Request | Response |
| --- | --- |
| `{"op":"classes"}` | `{"classes":[{"name":…,"fields":[{"name","type","static"}],"methods":[{"name","parameters","returns","static"}]}]}` |
| `{"op":"new","class":"Counter","args":[…]}` | `{"handle":1,"class":"Counter","fields":{…}}` |
| `{"op":"invoke","handle":1,"method":"increment","args":[{"int":5}]}` | `{"value":{"int":9}}` |
| `{"op":"invokeStatic","class":"Counter","method":"sumTo","args":[{"int":6}]}` | `{"value":{"int":30}}` |
| `{"op":"inspect","handle":1}` | `{"handle":1,"class":"Counter","fields":{…}}` |
| `{"op":"bench"}` | `{"bench":[{"handle","class","fields"}, …]}` |
| `{"op":"output"}` | `{"output":"…"}` |
| `{"op":"clearOutput"}` | `{"cleared":true}` |

Handles are session-scoped, start at 1 and are never reused. Fields are a snapshot taken at
the moment of the call; `inspect`/`bench` re-read them after later mutations.

## Values

| Java value | JSON | Back as an argument |
| --- | --- | --- |
| `int` / `boolean` / `char` | `{"int":42}` | `{"int":42}` |
| `double` | `{"double":2.5}` | `{"double":2.5}` |
| `String` | `{"string":"hi"}` | `{"string":"hi"}` |
| `null` | `{"null":true}` | `{"null":true}` or `null` |
| bench object | `{"object":"Counter","fields":{…}}` | `{"ref":1}` |
| array | `{"array":{"component":"int","items":[…]}}` | same shape |
| boolean argument | — | `{"bool":true}` |

Strings are escaped as JSON requires. Empty arguments may be omitted or `[]`.

## Errors

Failures never cross the bridge as exceptions:

```json
{"error":{"class":"java.lang.ArithmeticException","message":"/ by zero","unsupported":false}}
```

`unsupported:true` marks a construct the interpreter deliberately rejects (for example
`long` bytecode), which the UI should present as "not supported on iPad yet" rather than as
a student bug. `class` is `java.lang.IllegalArgumentException` for malformed requests.

## Swift integration plan

1. Export one C entry point from the GraalVM image: `const char *bluej_handle(const char *request)`.
   The implementation constructs a `CoreSession` once, keeps a `CoreProtocol` and returns a
   UTF-8 JSON string that Swift copies into a `String`.
2. In Swift, encode requests with `JSONSerialization`/`Codable` and decode the fixed response
   shapes above into `enum CoreValue` and `struct BenchObject`.
3. Keep the calls on a serial queue: `CoreSession` is synchronized, but the protocol assumes
   one outstanding request at a time, matching a single UI thread.
4. The native image needs no reflection for this path; the protocol uses only concrete
   classes and the already-registered interpreter.

The current iOS shell already runs three protocol calls inside the native build's host
self-test, so the JSON contract is exercised by the same code that ships in the `.ipa`.
