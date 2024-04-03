set -x
javac -classpath ./../lib/FileQueueJNI.jar:.:./../lib/json-20210307.jar TestDeQXA.java
javac -classpath ./../lib/FileQueueJNI.jar:.:./../lib/json-20210307.jar TestEnQ_loop.java
