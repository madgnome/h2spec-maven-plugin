package com.github.madgnome.maven.h2spec;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;


@Mojo(name = "h2spec", defaultPhase = LifecyclePhase.INTEGRATION_TEST,
        requiresDependencyResolution = ResolutionScope.TEST)
public class Http2SpecMojo extends AbstractMojo {

    /**
     * The port on which the Server will listen.
     */
    @Parameter(defaultValue = "-1", property="port", required = true)
    private int port;

    /**
     * Timeout in seconds
     */
    @Parameter(defaultValue = "2", property="timeout")
    private int timeout;

    /**
     * Maximum length of HTTP headers
     */
    @Parameter(defaultValue = "4000", property="maxHeaderLength")
    private int maxHeaderLength;

    /**
     * A list of cases to exclude during the test. Default is to exclude none.
     */
    @Parameter(property = "excludeSpecs")
    private List<String> excludeSpecs;

    /**
     * The class which is used to startup the Server. It will pass the port in as argument to the main(...) method.
     */
    @Parameter(property = "mainClass", required = true)
    private String mainClass;

    /**
     * The number of milliseconds to max wait for the server to startup. Default is 10000 ms
     */
    @Parameter(property = "waitTime")
    private long waitTime;

    /**
     * Allow to skip execution of plugin
     */
    @Parameter(property = "skip", defaultValue = "false")
    private boolean skip;

    @Component
    private MavenProject project;

    @SuppressWarnings("unchecked")
    private ClassLoader getClassLoader() throws MojoExecutionException {
        try {
            List<String> classpathElements = project.getTestClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory() );
            classpathElements.add(project.getBuild().getTestOutputDirectory() );
            URL urls[] = new URL[classpathElements.size()];

            for ( int i = 0; i < classpathElements.size(); i++) {
                urls[i] = new File(classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, getClass().getClassLoader());
        } catch (Exception e) {
            throw new MojoExecutionException("Couldn't create a classloader", e);
        }
    }

    public void execute()
            throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skip execution of h2spec-maven-plugin");
            return;
        }

        final AtomicReference<Exception> error = new AtomicReference<Exception>();
        Thread runner = null;
        try {
            String host;
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                getLog().debug("Unable to detect localhost address, using 127.0.0.1 as fallback");
                host = "127.0.0.1";
            }
            if (port == -1) {
                // Get some random free port
                port = findRandomOpenPortOnAllLocalInterfaces();
            }
            runner = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.currentThread().setContextClassLoader(getClassLoader());
                        Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(mainClass);
                        Method main = clazz.getMethod("main", String[].class);
                        main.invoke(null, (Object) new String[] { String.valueOf(port) });
                    } catch (Exception e) {
                        error.set(e);
                    }
                }
            });
            runner.setDaemon(true);
            runner.start();
            try {
                // wait for 50 milliseconds to give the server some time to startup
                Thread.sleep(500);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
            if (waitTime <= 0) {
                // use 10 seconds as default
                waitTime = 10000;
            }

            // Wait until the server accepts connections
            long sleepTime = waitTime / 10;
            for (int i = 0; i < 10; i++) {
                Throwable cause = error.get();
                if (cause != null) {
                    throw new MojoExecutionException("Unable to start server", cause);
                }
                Socket socket = new Socket();
                try {
                    socket.connect( new InetSocketAddress(host, port));
                    break;
                } catch (IOException e) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ignore) {
                        // restore interrupt state
                        Thread.currentThread().interrupt();
                    }
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
                if (i == 9) {
                    throw new MojoExecutionException("Unable to connect to server in " + waitTime, error.get());
                }
            }

            if (excludeSpecs == null) {
                excludeSpecs = Collections.emptyList();
            }

            try {
                getLog().info("!!! Exclude specs");
                for (String excludeSpec : excludeSpecs) {
                    getLog().info(excludeSpec);
                }

                File outputDirectory = new File(project.getBuild().getTestOutputDirectory());
                List<Failure> allFailures = H2SpecTestSuite.runH2Spec(getLog(), outputDirectory, port, timeout, maxHeaderLength,
                        new HashSet<String>(excludeSpecs));
                List<Failure> nonIgnoredFailures = new ArrayList<Failure>();
                List<Failure> ignoredFailures = new ArrayList<Failure>();


                for (Failure failure : allFailures) {
                    if (failure.isIgnored()) {
                        ignoredFailures.add(failure);
                    } else {
                        nonIgnoredFailures.add(failure);
                    }
                }

                if (nonIgnoredFailures.size() > 0) {
                    StringBuilder sb = new StringBuilder("\nFailed test cases:\n");
                    for (Failure failure : nonIgnoredFailures) {
                        sb.append("\t");
                        sb.append(failure.toString());
                        sb.append("\n\n");
                    }
                    throw new MojoFailureException(sb.toString());
                } else {
                    getLog().info("All test cases passed. " + ignoredFailures.size() + " test cases ignored.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }


        } finally {
            if (runner != null) {
                runner.interrupt();
            }
        }
    }

    private Integer findRandomOpenPortOnAllLocalInterfaces() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Can't find an open socket", e);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Can't close server socket.");
                }
            }
        }
    }
}
