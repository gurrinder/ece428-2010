all:
	javac -1.6 p3server.java
	javac -1.6 p3client.java
	
run1:
	java p3server $(F) $(N)

runClient:
	/home/tripunit/p3client –s $(P) –f $(F) –n $(N)

runClient2:
	java p3client

clean:
	rm -rf *.class
