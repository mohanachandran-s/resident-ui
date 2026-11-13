package io.mosip.testrig.residentui.runner;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import java.io.PrintWriter;

// Glue and plugin are configured solely via junit-platform.properties — a @ConfigurationParameter
// here would take precedence over that file for the same key and silently override it.
//
// main() is this jar's packaged entry point (`java -jar`, see entrypoint.sh) — it drives this
// same @Suite through the JUnit Platform Launcher API directly instead of Maven/Surefire, which
// forks a child JVM and reconstructs its environment by regex-parsing `env` output, silently
// dropping a hyphenated var like keycloak-external-url. A single `java -jar` process inherits
// the container's real environment directly, with no such fork. mvn test and an IDE run still
// discover and execute the @Suite below exactly as before — main() is never involved there.
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class ResidentTestRunner {

    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(ResidentTestRunner.class))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        PrintWriter out = new PrintWriter(System.out);
        summary.printTo(out);
        summary.printFailuresTo(out, 100);
        out.flush();

        // Always 0, regardless of scenario pass/fail — the Extent report is the result, not
        // this exit code (matches apitest-resident's MosipTestRunner convention).
        System.exit(0);
    }
}
