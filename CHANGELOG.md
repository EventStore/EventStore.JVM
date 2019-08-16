#Changelog


This change log is ordered chronologically, so each release contains all changes described below
it. Changes start from v6.0.0 and only relevant or interesting changes are mentioned. 

## v7.0.2 (2019-08-22)

### Dependencies

- Upgrades
  * akka-*: 2.5.23 -> 2.5.25
  * akka-http-*: 10.1.8 -> 10.1.9
  * specs2-core: 4.5.1 -> 4.7.0

## v7.0.1 (2019-06-17)

### Changes
* Only change in this release is ensuring compile target for Java 8.

## ~~v7.0.0 (2019-06-14)~~

Please disregard this version and use v7.0.1 instead as this version was mistakenly published with jdk11 class file format (55) instead of jdk8 (52).

### Breaking changes

* [#126](https://github.com/EventStore/EventStore.JVM/pull/126): Drop Scala 2.11 support.
* [#128](https://github.com/EventStore/EventStore.JVM/pull/128): Replace usage of joda libraries with java.time.
* [#136](https://github.com/EventStore/EventStore.JVM/pull/136): Change Java API with respect to types moved from `eventstore.*` into `eventstore.akka.*` and `eventstore.core.*`. For instance, `eventstore.[Settings, SubscriptionObserver]` are now in `eventstore.akka` and `eventstore.[EventData, ExpectedVersion]` are in `eventstore.core`. Most of this is not an issue for the Scala API as type aliases are temporarily added until v7.1.0.

### Enhancements

* [#131](https://github.com/EventStore/EventStore.JVM/pull/131): Add missing Java API's that deprecations point to.
* [#134](https://github.com/EventStore/EventStore.JVM/pull/134): Scala 2.13.0-M5 support.
* [#129](https://github.com/EventStore/EventStore.JVM/pull/129): Change Java examples, `ReadEventExample` and `WriteEventExample` to use `AbstractActor` instead of deprecated `UntypedActor`.
* [#143](https://github.com/EventStore/EventStore.JVM/pull/129): Scala 2.13.0 support.

### Internals
* [#125](https://github.com/EventStore/EventStore.JVM/pull/125): Decouple actor client from operation.
* [#126](https://github.com/EventStore/EventStore.JVM/pull/126): Switch to use scodec `ByteVector` instead of Akka `ByteString`.
* [#127](https://github.com/EventStore/EventStore.JVM/pull/127): Remove Apache Commons Codec 1.8 dependency.
* [#129](https://github.com/EventStore/EventStore.JVM/pull/129): Split project into three parts:
    - __core__: Basic types, protobuf, tcp package code, and code that can be reused for alternative implementations.
    - __client__: Akka related code and java API.
    - __examples__: Example code for Scala & Java.
* [#131](https://github.com/EventStore/EventStore.JVM/pull/131): Change various implementations in __client__ to `private[eventstore]` in order to minimize the public surface area.
* [#132](https://github.com/EventStore/EventStore.JVM/pull/132): Remove dependency on mockito.

### Dependencies

- Upgrades
  * protobuf-java: 3.0.0 -> 3.7.1
  * akka-*: 2.5.21 -> 2.5.23
  * akka-http-*: 10.1.7 -> 10.1.8
  * config: 1.3.3 -> 1.3.4
  * specs2-core: 3.8.6 -> 4.5.1

- Removals
  * org.apache.commons.codec
  * joda-time
  * joda-convert
  * mockito-all
  * specs2-mock
