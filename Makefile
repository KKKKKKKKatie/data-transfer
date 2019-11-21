.PHONY: all

all:
	javac sender.java
	javac receiver.java
	javac packet.java

clean:
	rm *.class *.log

