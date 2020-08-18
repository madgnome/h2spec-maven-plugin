package com.github.madgnome.maven.h2spec;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.SurefireReportParser;
import org.apache.maven.reporting.MavenReportException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class H2SpecTestSuite
{

    public static final String DEFAULT_VERSION = "2.6.0";

    public static String getSpecIdentifier(String specId, String name)
    {
        return specId + " - " + name;
    }

    public static List<Failure> parseReports(final Log logger, final File reportsDirectory, final Set<String> excludeSpecs)
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
            logger.warn( e.getMessage(), e );
        }

        return failures;
    }
}
