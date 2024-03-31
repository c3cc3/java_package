include ./Make.defines

SRCS = $(shell ls *.c)
OBJS = $(SRCS:.c=.o) #(patsubst %c,%o$(SRCS))

TARGET=monitor

all: dep $(OBJS)
	@echo ""
	@echo ""
	@echo "==========<start : Linking all objects and libraries >==========="
	$(CC) $(CFLAGS) -o $(TARGET) $(OBJS) -I$(FQ_INC) -I./../ncurses -L$(FQ_LIB) $(LIBS) -lncurses
	@echo "==========<end   : Linking all objects and libraries >==========="
	
clean:
	rm -f *.o *.so *.swp core $(TARGET)

install:
	cp -f $(TARGET) ./../../bin/

#make dependencies
dep:$(SRCS)
	$(CC) -MM $(SRCS) -I $(FQ_INC) -I /home/ums/java_package/include/ncurses > .depend

#ruls
%.o:%.c
	$(CC) -c $< -I $(FQ_INC) -I /home/ums/java_package/include/ncurses
%.o:%.cpp
	$(CPP) -c $< -I $(FQ_INC)

#dependencies
-include .depend
