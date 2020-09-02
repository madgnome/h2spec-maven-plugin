package com.github.madgnome.maven.h2spec;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.junit.Test;

import java.io.Reader;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class Http2SpecMojoTest
{

    @Test
    public void failedTestReallyMarkedAsSkipped() throws Exception
    {
        Http2SpecMojo mojo = new Http2SpecMojo();
        mojo.setExcludeSpecs( Arrays.asList( new String[]{ "3.5 - Sends invalid connection preface"} ));
        Path junitOrigin = Paths.get( "src/test/resources/TEST-h2spec.xml" );
        Path junit = Paths.get( "target/TEST-h2spec.xml" );
        Files.copy( junitOrigin, junit, StandardCopyOption.REPLACE_EXISTING );
        mojo.markedFailedTestAsSkipped( junit );

        try(Reader reader = Files.newBufferedReader( junit ))
        {
            Xpp3Dom dom = Xpp3DomBuilder.build( reader);
            assertEquals(1, Arrays.stream( dom.getChildren()).
                filter( testsuite ->  testsuite.getAttribute( "id" ).equals( "3.5" ) &&
                                    testsuite.getAttribute( "skipped" ).equals( "1" )).
                count());
            assertEquals(2, Arrays.stream( dom.getChildren()).
                filter( testsuite ->  testsuite.getAttribute( "id" ).equals( "3.5" ) &&
                    testsuite.getAttribute( "errors" ).equals( "0" )).
                count());

            assertEquals(1, Arrays.stream( dom.getChildren()).
                filter( testsuite ->  testsuite.getAttribute( "skipped" ).equals( "1" )).
                count());

            assertEquals(0, Arrays.stream( dom.getChildren()).
                filter( testsuite ->  !testsuite.getAttribute( "errors" ).equals( "0" )).
                count());

        }


    }

}
