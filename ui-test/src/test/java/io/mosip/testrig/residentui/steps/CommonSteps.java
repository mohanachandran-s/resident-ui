package io.mosip.testrig.residentui.steps;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.mosip.testrig.apirig.utils.NotificationListener;
import io.mosip.testrig.residentui.core.ResidentGlobalConstants;
import io.mosip.testrig.residentui.hooks.ResidentTestData;
import io.mosip.testrig.uitestrig.browser.PlaywrightFactory;
import io.mosip.testrig.uitestrig.assertions.VisualValidator;
import io.mosip.testrig.uitestrig.core.UiConfigManager;
import io.mosip.testrig.uitestrig.reporting.UiExtentReportManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * Shared Cucumber step definitions: login, logout, popup dismissal.
 */
public class CommonSteps {

    private static final Logger logger = LoggerFactory.getLogger(CommonSteps.class);

    // Real login happens once per run; every other "logged in with their VID" reuses this
    // saved session instead, to avoid exhausting the environment's per-identity OTP rate limit.
    private static final String STORAGE_STATE_PATH = "target/perpetual-vid-storage-state.json";
    private static volatile boolean storageStateReady = false;
    private static final Object LOGIN_LOCK = new Object();

    protected Page page() {
        return PlaywrightFactory.getPage();
    }

    // ── Login steps ───────────────────────────────────────────────────────────

    // Locked end-to-end, including the reuse check — freshLoginWithVid() overwrites
    // STORAGE_STATE_PATH under this same lock, and an unsynchronized read here could race
    // that write under parallel execution.
    @Given("the user is logged in with their VID")
    public void loginWithPerpetualVid() {
        synchronized (LOGIN_LOCK) {
            if (storageStateReady && tryReuseSession()) {
                return;
            }
            performOtpLogin(ResidentTestData.perpetualVid);
            PlaywrightFactory.saveStorageState(STORAGE_STATE_PATH);
            storageStateReady = true;
        }
    }

    /**
     * Loads the saved session into a fresh browser context/page and checks it landed on the
     * authenticated dashboard rather than the public landing page — an OIDC token can expire
     * mid-suite, in which case the app shows the login screen despite the cookies.
     */
    private boolean tryReuseSession() {
        UiExtentReportManager.logStep("Reuse saved session — navigate to " + UiConfigManager.getBaseUrl());
        PlaywrightFactory.initPageWithStorageState(STORAGE_STATE_PATH);
        page().navigate(UiConfigManager.getBaseUrl());
        page().waitForLoadState(LoadState.NETWORKIDLE);

        if (!isOnAuthenticatedDashboard()) {
            logger.warn("Saved session no longer valid (likely expired) — falling back to a fresh OTP login");
            UiExtentReportManager.logStep("Saved session no longer valid — falling back to a fresh OTP login");
            return false;
        }
        // The post-login welcome popup may or may not appear for a reused session.
        try {
            Locator dismiss = page().locator("#dismissBtn");
            dismiss.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5_000));
            UiExtentReportManager.logStepWithLocator("Dismiss the post-login popup", "#dismissBtn");
            dismiss.click();
        } catch (Exception e) {
            logger.debug("No post-login popup this time (reused session) — continuing");
        }
        logger.info("Reused saved session — skipped OTP login");
        UiExtentReportManager.logStep("Reused saved session — skipped OTP login");
        return true;
    }

    // #UINservices is the login trigger on the PUBLIC landing page only (see performOtpLogin) —
    // if it's still there after navigating with a saved session, that session wasn't valid.
    private boolean isOnAuthenticatedDashboard() {
        try {
            page().locator("#UINservices").waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(5_000));
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @Given("the user is logged in with a perpetual VID")
    public void loginWithPerpetualVidExplicit() {
        loginWithPerpetualVid();
    }

    // All credential types belong to the same MOSIP identity, which the backend blocks on
    // concurrent auth attempts regardless of which credential each used — hence the shared lock.
    @Given("the user is logged in with a temporary VID")
    public void loginWithTemporaryVid() {
        synchronized (LOGIN_LOCK) {
            performOtpLogin(ResidentTestData.temporaryVid);
        }
    }

    @Given("the user is logged in with a one-time VID")
    public void loginWithOneTimeVid() {
        synchronized (LOGIN_LOCK) {
            performOtpLogin(ResidentTestData.oneTimeVid);
        }
    }

    // Always a real login — for scenarios that specifically verify this login flow works,
    // which must not silently turn into a cookie replay via the cache.
    @Given("the user performs a fresh login with their VID")
    public void freshLoginWithVid() {
        synchronized (LOGIN_LOCK) {
            performOtpLogin(ResidentTestData.perpetualVid);
            PlaywrightFactory.saveStorageState(STORAGE_STATE_PATH);
            storageStateReady = true;
        }
    }

    @Given("the user performs a fresh login with their UIN")
    public void freshLoginWithUin() {
        synchronized (LOGIN_LOCK) {
            performOtpLogin(ResidentTestData.uin);
        }
    }

    @Then("the login should be confirmed")
    public void assertLoginConfirmed() {
        if (!isOnAuthenticatedDashboard()) {
            throw new AssertionError("Expected to land on the authenticated dashboard after login");
        }
    }

    // ── Popup steps ───────────────────────────────────────────────────────────

    @Then("a success popup should appear")
    public void assertSuccessPopup() {
        new VisualValidator(page()).assertSuccessPopup();
    }

    @When("the user dismisses the popup")
    public void dismissPopup() {
        UiExtentReportManager.logStepWithLocator("Dismiss the popup", "#dismissBtn");
        page().locator("#dismissBtn").click();
    }

    // ── Internal OTP login ────────────────────────────────────────────────────

    void performOtpLogin(String vid) {
        logger.info("Performing OTP login with VID ...{}", vid.length() > 4 ? vid.substring(vid.length() - 4) : "****");

        try {
            UiExtentReportManager.logStepWithLocator("Click the UIN/VID login trigger", "#UINservices");
            page().locator("#UINservices").click();
            UiExtentReportManager.logStepWithLocator("Click 'Login with OTP'", "#login_with_otp");
            page().locator("#login_with_otp").click();
            UiExtentReportManager.logStepWithLocator("Enter the VID/UIN", ResidentGlobalConstants.LOGIN_VID_INPUT);
            page().locator(ResidentGlobalConstants.LOGIN_VID_INPUT).fill(vid);
            NotificationListener.markRequestStart();
            requestOtpWithRetry();

            // Login OTP can still go to the original email even after Update My Data switches
            // loginEmail to a new one — poll both rather than assume which one the backend used.
            String otp = ResidentTestData.getOtpFromCurrentOrPreviousEmail();
            NotificationListener.markRequestRemove();
            if (otp == null || otp.isEmpty()) {
                throw new IllegalStateException("OTP not received for " + ResidentTestData.loginEmail
                        + (ResidentTestData.previousLoginEmail.isBlank() ? "" : " or " + ResidentTestData.previousLoginEmail));
            }

            UiExtentReportManager.logStepWithLocator("Enter the OTP", "#otp_verify_input div input");
            Locator inputs = page().locator("#otp_verify_input div input");
            for (int i = 0; i < otp.length() && i < inputs.count(); i++) {
                inputs.nth(i).fill(String.valueOf(otp.charAt(i)));
            }

            UiExtentReportManager.logStepWithLocator("Click 'Verify OTP'", "#verify_otp");
            page().locator("#verify_otp").click();
            page().waitForLoadState(LoadState.NETWORKIDLE);

            // First-time OAuth consent (shown only once per VID+app pair). A sibling <div> overlays
            // the real checkbox and intercepts clicks, hence evaluate("el => el.click()") instead
            // of a regular click().
            Locator consentCheckbox = page().locator("#authorize_scope");
            try {
                consentCheckbox.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(10_000));
                UiExtentReportManager.logStepWithLocator("Accept the OAuth consent", "#authorize_scope");
                consentCheckbox.evaluate("el => el.click()");
                page().locator("#voluntary_claims").evaluate("el => el.click()");
                page().locator("#continue").click();
                page().waitForLoadState(LoadState.NETWORKIDLE);
            } catch (Exception e) {
                logger.debug("No first-time consent screen this run — continuing");
            }

            // Can take a while to open for a freshly-generated identity's first login.
            Locator dismiss = page().locator("#dismissBtn");
            try {
                dismiss.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(60_000));
                UiExtentReportManager.logStepWithLocator("Dismiss the login-success popup", "#dismissBtn");
                dismiss.click();
            } catch (Exception e) {
                logger.warn("Login-success popup (#dismissBtn) did not appear within 60s — continuing");
            }

            logger.info("Login successful");
            UiExtentReportManager.logStep("Login successful");
        } catch (RuntimeException e) {
            // Unlike BasePage's helpers, these calls go straight through the raw Playwright
            // locator API with no per-call try/catch, so without this the real exception
            // (locator timeout, rejected OTP, etc.) never reached the report — teardownScenario's
            // generic "Scenario failed" line was the only trace. Report against whichever step was
            // last logged, since that's the one that was in flight when this was thrown.
            captureLoginFailure(e);
            throw e;
        }
    }

    /** Mirrors BasePage's captureFailure() — CommonSteps issues raw Playwright calls instead of
     *  going through a page object, so it needs its own copy of the same capture-and-report. */
    private void captureLoginFailure(Throwable error) {
        String description = UiExtentReportManager.getLastStepDescription();
        String locator = UiExtentReportManager.getLastStepLocator();
        String screenshotBase64 = null;
        try {
            screenshotBase64 = Base64.getEncoder().encodeToString(
                    page().screenshot(new Page.ScreenshotOptions().setFullPage(true)));
        } catch (Exception e) {
            logger.warn("Could not capture failure screenshot: {}", e.getMessage());
        }
        UiExtentReportManager.logFailureWithScreenshot(
                description != null ? description : "OTP login failed", locator, error, screenshotBase64);
    }

    /**
     * "Get OTP" can be rejected with a "Request could not be processed" banner (OTP-send rate
     * limiting). Checks for that specific error and retries with backoff instead of hanging on
     * an OTP panel that will never appear.
     */
    private void requestOtpWithRetry() {
        UiExtentReportManager.logStepWithLocator("Click 'Get OTP'", "#get_otp");
        Locator otpPanel = page().locator("#otp_verify_input");
        Locator errorBanner = page().locator("text=Request could not be processed");
        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            page().locator("#get_otp").click();
            for (int i = 0; i < 10; i++) {
                if (otpPanel.isVisible()) return;
                if (errorBanner.isVisible()) break;
                page().waitForTimeout(1_000);
            }
            if (otpPanel.isVisible()) return;
            logger.warn("Get OTP attempt {}/{} was rejected — retrying", attempt, maxAttempts);
            page().waitForTimeout(5_000);
        }
        throw new IllegalStateException("Get OTP kept being rejected ('Request could not be processed') after "
                + maxAttempts + " attempts");
    }
}
