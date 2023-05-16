# EventStoreDb JVM TCP Client Test - Plain Old Java 

## What You Need

[Java 1.8 ](https://openjdk.org/projects/jdk8/)

[Gradle 7.5+](https://gradle.org/install/) or [Maven 3.5+](https://maven.apache.org/download.cgi)


## Build and run

Run EventStore locally in Docker with: 
```
docker run --name esdb-node -it -p 2113:2113 -p 1113:1113 eventstore/eventstore:latest --insecure --enable-external-tcp
```

Verify that the database is up and running by pointing your web browser to the EventStore dashboard at: http://localhost:2113/


For Maven, build the JAR file with `./mvn clean package` and then run the JAR file, as follows:
```
java -jar target/jvm-tcp-demo-0.0.1-allinone.jar
```


For Gradle, build the JAR file with `./gradlew build` and then run the JAR file, as follows:
```
java -jar build/libs/jvm-tcp-demo-0.0.1-allinone.jar
```

