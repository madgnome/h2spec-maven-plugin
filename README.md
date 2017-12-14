Maven h2spec test suite Plugin
==============================

Maven plugin which allows to run the [h2spec](https://github.com/summerwind/h2spec) test suite as part of your maven build.

# Requirements
If you want to have the Test suite executed as part of your build you need to provide a Main class which will
startup a HTTP/2 server.


# Adding it to your build
Adding the test suite and make it part of your build is as easy as adding it to the pom.xml file of your
maven project:
    
    <build>
        <plugins>
          <plugin>
            <groupId>com.github.madgnome</groupId>
            <artifactId>h2spec-maven-plugin</artifactId>
            <version>0.1-SNAPSHOT</version>
            <configuration>
              <mainClass>io.netty.testsuite.http2.Http2Server</mainClass>
              
              <!-- Optional configuration -->
              <!-- ---------------------- -->
              <!-- The port to bind the server on. Default is to choose a random free port. -->
              <port>-1</port>
              
              <!-- The number of milliseconds to wait for the server to startup. Default is 10000 ms. -->
              <waitTime>10000</waitTime>
              
              <!-- A list of cases to exclude. Default is none. -->
              <excludeSpecs>
                <excludeSpec>3.8 - Sends a GOAWAY frame</excludeSpec>
              </excludeSpecs>
              
              <skip>${skipHttp2Testsuite}</skip>
            </configuration>
            <executions>
              <execution>
                <phase>test</phase>
                <goals>
                  <goal>h2spec</goal>
                </goals>
              </execution>
            </executions>
          </plugin>
        </plugins>
      </build>


This will execute the h2spec tests as part of the test phase and fail the phase if one of the test cases
fails.

After the run was complete you will find test-reports in the `target/test-classes/reports/TEST-h2spec.xml`, which contains all
the details about every test case.


