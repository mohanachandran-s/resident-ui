package io.mosip.testrig.residentui.runner;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

/**
 * Executable entry point — runs the same suite as `mvn test`, through the JUnit Platform
 * Launcher API directly instead of Maven/Surefire.
 *
 * Surefire forks a child JVM and reconstructs its environment by regex-parsing `env` output —
 * a hyphenated var like keycloak-external-url doesn't match and silently drops. A single
 * `java -jar` process inherits the container's real environment directly, with no such fork.
 */
public class MainRunner {

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

        System.exit(0);
    }
}
