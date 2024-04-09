set -x
javac -classpath ./../lib/FileQueueSocket.jar:.:./../lib/json-20210307.jar:./../lib/log4j-1.2.17.jar TestRead.java
javac -classpath ./../lib/FileQueueSocket.jar:.:./../lib/json-20210307.jar TestWrite.java
