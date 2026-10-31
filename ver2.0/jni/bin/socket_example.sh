set -x
pushd ..
#java -Djava.library.path=. -cp "java:lib/*" FQueueTest
java -Djava.library.path=. -cp "java:lib/*" FileQueueSocketExample 172.30.1.31 7777 /sendq1/enmq KT_SM_ONL
popd
