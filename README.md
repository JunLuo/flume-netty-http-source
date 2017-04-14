Flume Netty Http Source
===============

Flume-ng Netty Http Source.
This is another Flume Http Source write by netty, it receives request with HTTP protocol. 

Source Configuration
===============
In flume-conf.properties:
```java

# Netty Http Source
###############################
agent.sources.mysource.type = jun.flume.netty.http.HTTPSource
agent.sources.mysource.port = 10086
agent.sources.mysource.bind = 0.0.0.0
agent.sources.mysource.handler = jun.flume.netty.http.JSONHandler
agent.sources.mysource.charset = utf-8


```

Installation
===============

```java
git clone https://github.com/JunLuo/flume-netty-http-source.git
cd flume-netty-http-source
mvn clean install assembly:assembly
cp target/flume.netty-{version}.jar {flume_home}/lib/flume.netty-{version}.jar
```

####Maven Dependency Tree
```java
------------------------------------------------------------------------
Building flume-http-sink 0.0.1-SNAPSHOT
------------------------------------------------------------------------



--- maven-dependency-plugin:2.5:tree (default-cli) @ flume-netty-http-source ---
jun:flume.netty:jar:0.0.1-SNAPSHOT
+- io.netty:netty-all:jar:4.1.9.Final:compile
+- log4j:log4j:jar:1.2.17:compile
+- org.slf4j:slf4j-api:jar:1.6.1:compile
+- com.google.code.gson:gson:jar:2.2.2:compile
\- org.apache.flume:flume-ng-core:jar:1.6.0:compile
   +- org.apache.flume:flume-ng-sdk:jar:1.6.0:compile
   +- org.apache.flume:flume-ng-configuration:jar:1.6.0:compile
   +- org.apache.flume:flume-ng-auth:jar:1.6.0:compile
   +- com.google.guava:guava:jar:11.0.2:compile
   |  \- com.google.code.findbugs:jsr305:jar:1.3.9:compile
   +- commons-io:commons-io:jar:2.1:compile
   +- commons-codec:commons-codec:jar:1.8:compile
   +- org.slf4j:slf4j-log4j12:jar:1.6.1:compile
   +- commons-cli:commons-cli:jar:1.2:compile
   +- commons-lang:commons-lang:jar:2.5:compile
   +- org.apache.avro:avro:jar:1.7.4:compile
   |  +- org.codehaus.jackson:jackson-core-asl:jar:1.8.8:compile
   |  +- org.codehaus.jackson:jackson-mapper-asl:jar:1.8.8:compile
   |  +- com.thoughtworks.paranamer:paranamer:jar:2.3:compile
   |  +- org.xerial.snappy:snappy-java:jar:1.0.4.1:compile
   |  \- org.apache.commons:commons-compress:jar:1.4.1:compile
   |     \- org.tukaani:xz:jar:1.0:compile
   +- org.apache.avro:avro-ipc:jar:1.7.4:compile
   |  \- org.apache.velocity:velocity:jar:1.7:compile
   |     \- commons-collections:commons-collections:jar:3.2.1:compile
   +- io.netty:netty:jar:3.5.12.Final:compile
   +- joda-time:joda-time:jar:2.1:compile
   +- org.mortbay.jetty:servlet-api:jar:2.5-20110124:compile
   +- org.mortbay.jetty:jetty-util:jar:6.1.26:compile
   +- org.mortbay.jetty:jetty:jar:6.1.26:compile
   +- org.apache.thrift:libthrift:jar:0.9.0:compile
   |  +- org.apache.httpcomponents:httpclient:jar:4.1.3:compile
   |  |  \- commons-logging:commons-logging:jar:1.1.1:compile
   |  \- org.apache.httpcomponents:httpcore:jar:4.1.3:compile
   \- org.apache.mina:mina-core:jar:2.0.4:compile
```



License
=======
Apache License, Version 2.0
http://www.apache.org/licenses/LICENSE-2.0
