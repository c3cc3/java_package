set -x
#java -cp /ums/fq/lib/FileQueueJNI.jar:.:/ums/fq/lib/json-20210307.jar VirtualCoAgent VirtualCoAgent_conf.xml
java -cp ./../../lib/FileQueueJNI.jar:.:./../../lib/json-20210307.jar TestDeQXA /home/ums/java_package/enmq LG_SM_ONL 0 10000000
