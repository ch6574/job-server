# Synopsis
Demo of simple Java/Netty4 client-server architecture 

# Example
```bash
git clone https://github.com/ch6574/job-server.git
cd job-server
mvn clean compile
mvn exec:java -Dexec.mainClass="hillc.JobServer"

/src/main/script/jobserver-client.sh /tmp/foo

touch /tmp/foo
```

# License
GPL v3.
