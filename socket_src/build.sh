set -x
#javac -classpath ./../lib/json-20210307.jar:.:./../lib/log4j-1.2.17.jar -d . FileQueueSocket.java
javac -classpath ./../lib/json-20210307.jar -d . FileQueueSocket.java
mv FileQueueSocket.class ./classes/
jar cvf FileQueueSocket.jar -C classes .
cp -f FileQueueSocket.jar ./../lib
