#!/bin/bash
set -e
set -x

make
javac -cp "java:lib/*" -d java java/FQueue.java java/FQueueTest.java java/Agent.java java/DurableStore.java java/BundleReader.java java/BundleAgent.java java/AgentConfig.java java/FileQueueSocket.java java/FileQueueSocketExample.java java/FileQueueSocketExample_loop.java java/Agent_socket.java java/JsonGenerator.java java/FieldMap.java java/JsonAudit.java
