# Adjust FQ_HOME if FileQueue is installed elsewhere.
FQ_HOME ?= /ums24/wiseu/fq
JAVA_HOME ?= $(shell dirname $$(dirname $$(readlink -f $$(command -v javac))))

CC      ?= gcc
CFLAGS  ?= -O2 -fPIC -Wall -Wextra
CPPFLAGS += -I$(JAVA_HOME)/include -I$(JAVA_HOME)/include/linux \
            -I$(FQ_HOME)/include
LDFLAGS += -shared -pthread
LDLIBS  += -L$(FQ_HOME)/lib -Wl,-rpath,$(FQ_HOME)/lib -l:libfq.so.14

TARGET = ./lib/libfqueue_jni.so
SRC = native/fqueue_jni.c

.PHONY: all clean
all: $(TARGET)

$(TARGET): $(SRC) native/FQueue.h
	$(CC) $(CPPFLAGS) $(CFLAGS) $(LDFLAGS) -o $@ $(SRC) $(LDLIBS)

clean:
	rm -f $(TARGET) native/*.o
