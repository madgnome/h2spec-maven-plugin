package com.github.madgnome.maven.h2spec;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class H2SpecTestSuite
{

    public static final String H2SPEC_VERSION = "2.4.0";

    public static void main(String[] args) throws IOException
    {
        Config config = new Config();
        config.log = new SystemStreamLog();
        config.outputDirectory = new File(args[0]);
        config.port = Integer.parseInt(args[1]);
        config.timeout = 2;
        config.excludeSpecs = Collections.emptySet();
        runH2Spec(config);
    }

    public static String getSpecIdentifier(String specId, String name)
    {
        return specId + " - " + name;
    }

    public static List<Failure> runH2Spec(Config config) throws IOException
    {
        File reportsDirectory = new File(config.outputDirectory, "surefire-reports");
        if (!Files.exists(reportsDirectory.toPath()))
        {
            config.log.debug("Reports directory " + reportsDirectory.getAbsolutePath() + " does not exist, try creating it...");
            if (reportsDirectory.mkdirs())
            {
                config.log.debug("Reports directory " + reportsDirectory.getAbsolutePath() + " created.");
            }
            else
            {
                config.log.debug("Failed to create report directory");
            }
        }

        File junitFile = new File(reportsDirectory, config.junitFileName);
        File h2spec = getH2SpecFile(config.outputDirectory);

        Executor exec = new DefaultExecutor();
        PumpStreamHandler psh = new PumpStreamHandler(System.out, System.err, System.in);
        exec.setStreamHandler(psh);
        exec.setExitValues(new int[]{0, 1});

        psh.start();
        if (exec.execute(buildCommandLine(h2spec, junitFile, config)) != 0)
        {
            return parseReports(config.log, reportsDirectory, config.excludeSpecs);
        }
        psh.stop();

        return Collections.emptyList();
    }

    private static List<Failure> parseReports(final Log logger, final File reportsDirectory, final Set<String> excludeSpecs)
    {
        logger.debug("Parsing h2spec reports");
        SurefireReportParser parser = new SurefireReportParser(Collections.singletonList(reportsDirectory), Locale.getDefault());

        String currentPackageName = "";
        List<Failure> failures = new ArrayList<>();
        try
        {
            List<ReportTestSuite> parsedReports = parser.parseXMLReportFiles();
            logger.debug(parsedReports.size() + " h2spec reports parsed.");
            for (ReportTestSuite parsedReport : parsedReports)
            {
                String packageName = parsedReport.getPackageName();
                if (packageName.length() > 0)
                {
                    currentPackageName = packageName;
                }

                if (parsedReport.getNumberOfErrors() > 0)
                {
                    for (ReportTestCase reportTestCase : parsedReport.getTestCases())
                    {
                        String name = parsedReport.getFullClassName();
                        String failureDetail = reportTestCase.getFailureDetail();
                        if (failureDetail != null)
                        {
                            String[] failureTokens = failureDetail.split("\n");
                            final String specIdentifier = getSpecIdentifier(currentPackageName, name);
                            boolean ignored = excludeSpecs.contains(specIdentifier);

                            String expected = failureTokens.length > 0 ? failureTokens[0] : "";
                            String actual = failureTokens.length > 1 ? failureTokens[1] : "";
                            failures.add(new Failure(name, currentPackageName, actual, expected, ignored));
                        }
                    }
                }
            }

        }
        catch (MavenReportException e)
        {
            e.printStackTrace();
        }

        return failures;
    }

    private static CommandLine buildCommandLine(File h2spec, File junitFile, Config config)
    {
        String command = String.format("%s %s -p %d -j %s -o %d --max-header-length %d",
                h2spec.getAbsolutePath(), " ", config.port, junitFile.getAbsolutePath(),
                                             config.timeout, config.maxHeaderLength);
        config.log.info("h2spec command: " + command);
        if (config.verbose)
        {
            command += " -v";
        }
        return CommandLine.parse(command);
    }

    private static File getH2SpecFile(final File targetDirectory) throws IOException
    {
        URL h2SpecArchiveInJar = H2SpecTestSuite.class.getResource(getH2SpecArchivePathForOs());

        File h2SpecArchive = new File(targetDirectory, new File(h2SpecArchiveInJar.getPath()).getName());
        FileUtils.copyURLToFile(h2SpecArchiveInJar, h2SpecArchive);

        File h2Spec = new File(targetDirectory, "h2spec");
        UnArchiver unArchiver;
        if (h2SpecArchive.getName().endsWith(".tar.gz"))
        {
            unArchiver = new TarGZipUnArchiver();
        }
        else
        {
            unArchiver = new ZipUnArchiver();
        }
        ((AbstractLogEnabled) unArchiver).enableLogging(new ConsoleLogger(Logger.LEVEL_DEBUG, "console"));

        unArchiver.setSourceFile(h2SpecArchive);
        unArchiver.setDestDirectory(targetDirectory);
        unArchiver.extract();

        if (!h2Spec.setExecutable(true))
        {
            throw new RuntimeException("Can't set h2spec as executable");
        }
        return h2Spec;
    }

    private static String getH2SpecArchivePathForOs()
    {
        String os = System.getProperty("os.name").toLowerCase();
        String fileName;
        if (os.contains("win"))
        {
            fileName = "h2spec_windows_amd64.zip";
        }
        else if (os.contains("nix") || os.contains("nux") || os.contains("aix"))
        {
            fileName = "h2spec_linux_amd64.tar.gz";
        }
        else if (os.contains("mac"))
        {
            fileName = "h2spec_darwin_amd64.tar.gz";
        }
        else
        {
            throw new IllegalStateException("This OS is not supported.");
        }

        return String.format("/h2spec/%s/%s", H2SPEC_VERSION, fileName);
    }


    static class Config
    {
        Log log;
        File outputDirectory;
        int port;
        int timeout;
        int maxHeaderLength;
        Set excludeSpecs;
        String junitFileName = "TEST-h2spec.xml";
        boolean verbose;
    }

}
