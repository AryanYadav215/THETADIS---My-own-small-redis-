# MiniRedis: A Multithreaded In-Memory Key-Value Database in Java

Course project for **Object Oriented Programming (CSC2104)**, B.Tech CSE, Manipal University Jaipur.

## What it is
A small database that stores `key = value` pairs in memory. Several client threads send text commands at the same time; worker threads execute them safely on shared data. Keys can expire automatically and data is saved to a file so it survives restarts.

## How to run (JDK 17+)
```
mkdir out
javac -d out src/*.java

java -cp out Server               # scripted demo: 2 clients, 3 workers, expiry, save
java -cp out Server interactive   # type your own commands, EXIT to quit
java -cp out RaceDemo             # race-condition proof (unsafe vs synchronized)
```

## Commands
| Command | Meaning |
|---|---|
| `SET key value` | store a value |
| `SET key value EX n` | store with expiry of n seconds |
| `GET key` | read a value |
| `DEL key` | delete (1 = deleted, 0 = not found) |
| `EXISTS key` | 1 or 0 |
| `INCR key` | add 1 to an integer value (creates it at 1) |
| `TTL key` | seconds left (-1 = never expires) |
| `KEYS` | list live keys |
| `STATS` | number of keys and commands run |
| `SAVE` | write the database to `data.db` |

## Architecture
```
ClientThread(s)            producers
      | put()  synchronized + notifyAll()
      v
 CommandQueue  <-- wait() while empty
      | take()
      v
 Worker thread(s)          consumers
      |  Parser.parse(line)  -> Command object   (Factory)
      |  cmd.execute(db)                         (runtime polymorphism)
      v
 Database  (private HashMap, all methods synchronized)
      ^                         ^
 Cleaner (inner class,    FileStore implements Storable
 daemon thread)           (save: temp file + rename)
```

## Class guide
| Class | Role | OOP concept |
|---|---|---|
| `Command` (abstract) | `execute(Database)` | abstraction |
| `SetCommand`, `GetCommand`, `DelCommand`, `ExistsCommand`, `IncrCommand`, `TtlCommand`, `KeysCommand`, `StatsCommand`, `SaveCommand` | one class per command | inheritance, overriding, polymorphism |
| `Record` | value + expiry time | `final` class, immutability, constructor overloading |
| `Database` | shared store | encapsulation, `synchronized` |
| `Database.Cleaner` | removes expired keys | inner class, daemon thread, `volatile` |
| `Storable` / `FileStore` | save and load | interface, file I/O, try-with-resources |
| `Parser` | text to Command | factory pattern |
| `CommandQueue` | shared buffer | `wait()`, `notifyAll()`, producer-consumer |
| `Worker`, `ClientThread` | threads | `Runnable`, multithreading |
| `MiniRedisException` -> `KeyNotFoundException`, `InvalidCommandException` | errors | custom checked exceptions |
| `Server` | wires everything, starts and joins threads | thread lifecycle, `join()` |
| `RaceDemo` | thread-safety proof | race condition |

## Mapping to course outcomes
- **CO1:** classes, objects, constructors, methods, arrays and collections
- **CO2:** encapsulation (private map), abstraction (Command), overloading, access control, `final`
- **CO3:** inheritance, overriding, polymorphism, interface, abstract class, composition
- **CO4:** exceptions (custom, try/catch/finally, throws), multithreading, synchronization, wait/notify, garbage collection

## Demo script for the viva
1. `java -cp out Server`: point out interleaved output from different workers, an error line for `GET xyz` and `FLY away`, `TTL otp`, then `otp` expiring.
2. Run it again: first line says `Loaded 4 key(s)`, so data survived the restart.
3. `java -cp out RaceDemo`: roughly 1500 without `synchronized`, exactly 5000 with it.
4. `java -cp out Server interactive` for live questions.

## Limitations (say these honestly)
- Clients are threads in one process, not real network connections.
- Only string values and integer counters.
- One global lock, so operations are serialized. A `ReadWriteLock` or `ConcurrentHashMap` would scale better.
- Values cannot contain spaces.

## Possible future work
Socket-based server, lists and hashes, `ReadWriteLock`, JUnit tests, GUI.
