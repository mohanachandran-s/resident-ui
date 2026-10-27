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
 * Executable entry point — runs the exact same suite as `mvn test` (ResidentTestRunner's
 * @Suite -> cucumber engine), but through the JUnit Platform Launcher API directly instead of
 * through Maven/Surefire.
 *
 * Surefire runs tests in a forked child JVM whose environment it reconstructs by shelling out to
 * `env` and regex-parsing C-identifier-style KEY= lines — a hyphenated Rancher-injected variable
 * like keycloak-external-url doesn't match that regex and silently drops, even though every
 * underscore/camelCase-named variable survives. Running as a single `java -jar` process instead
 * means this JVM inherits the container's real OS environment directly, with no such fork in
 * between — exactly like apitest-resident's own `java -jar` entrypoint.
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

        // Non-zero exit on any failure so Rancher sees a failed Job, same as mvn test's own
        // exit-code propagation.
        System.exit(summary.getTotalFailureCount() > 0 ? 1 : 0);
    }
}
