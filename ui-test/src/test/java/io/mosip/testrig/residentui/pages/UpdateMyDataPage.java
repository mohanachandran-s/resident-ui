package io.mosip.testrig.residentui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.FilePayload;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.mosip.testrig.uitestrig.pages.BasePage;
import io.mosip.testrig.uitestrig.assertions.VisualValidator;
import io.mosip.testrig.residentui.config.ResidentConfigManager;
import io.mosip.testrig.residentui.hooks.ResidentTestData;
import io.mosip.testrig.uitestrig.core.MosipTestDataManager;
import io.mosip.testrig.uitestrig.core.UiConfigManager;
import org.apache.commons.lang3.RandomStringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class UpdateMyDataPage extends BasePage {

    private final VisualValidator visual;
    // Loaded from the classpath, not a filesystem path — only resolves under mvn test/IDE; the
    // packaged jar's working directory has no src/ tree, but the file is on its classpath either way.
    private static final String UPLOAD_FILE_RESOURCE = "/BulkUploadFiles/tonyId.png";

    public UpdateMyDataPage(Page page) {
        super(page);
        this.visual = new VisualValidator(page);
    }

    /** Thrown when the "Existing request found" popup blocked the update; the caller retries it. */
    public static class PendingRequestException extends RuntimeException {
        public PendingRequestException() {
            super("An earlier Update My Data request is still pending");
        }
    }

    private static final String PENDING_REQUEST_POPUP = "#draftCancelPopup";

    /**
     * Re-navigates to the base URL first rather than assuming we're already on the dashboard —
     * the existing session routes straight back to it, no re-login involved — so this is safe
     * to call from any nested Update My Data sub-page, not just the dashboard itself.
     */
    public void navigateTo() {
        page.navigate(UiConfigManager.getBaseUrl());
        waitForNetworkIdle();
        dismissPopupIfPresent("#dismissBtn"); // guard against a leftover popup blocking the click
        click("#uinservices\\/updatedemographic", "Open the Update My Data service");
        waitForNetworkIdle();
    }

    /**
     * Handles the "Existing request found" popup and throws PendingRequestException when shown,
     * so the caller always retries. {@code pollBriefly} waits up to 8s for the popup to render,
     * since the app's pending-request check is asynchronous.
     *
     * The popup has two states: a green "Confirm" button that deletes the pending request, or
     * — while the backend still refuses deletion — only a "Cancel" button that just dismisses
     * the dialog. Either is handled; only whether the request also got deleted differs.
     */
    public void cancelPendingRequestPopupIfShown(boolean pollBriefly) {
        Locator popup = page.locator(PENDING_REQUEST_POPUP);
        for (int i = 0; pollBriefly && i < 8 && popup.count() == 0; i++) {
            page.waitForTimeout(1_000);
        }
        if (popup.count() == 0) return;

        Locator confirmBtn = popup.locator("button:has-text('Confirm')");
        if (confirmBtn.count() > 0) {
            logger.info("'Existing request found' popup shown: confirming deletion of the pending request");
            confirmBtn.click();
        } else {
            logger.info("'Existing request found' popup shown but still Pending (not yet cancellable) — "
                    + "dismissing and retrying later");
            popup.locator("button:has-text('Cancel')").click();
        }
        throw new PendingRequestException();
    }

    // mat-tab-group ids are assigned by an app-wide Angular Material counter, not fixed per
    // instance — #mat-tab-label-0-N can drift. Match tabs by their label text instead.

    public void selectNameTab() {
        page.locator(".mat-tab-label-content:text-is('Identity')").click();
    }

    public void selectAddressTab() {
        page.locator(".mat-tab-label-content:text-is('Address')").click();
    }

    public void selectContactTab() {
        page.locator(".mat-tab-label-content:text-is('Contact')").click();
    }

    public void selectLanguageTab() {
        page.locator(".mat-tab-label-content:text-is('Language Preference')").click();
    }

    // ── Name tab ──────────────────────────────────────────────────────────────

    public void fillNameFields() {
        cancelPendingRequestPopupIfShown(false);
        String lang = MosipTestDataManager.getLanguageList().isEmpty() ? "eng"
                : MosipTestDataManager.getLanguageList().get(0);
        page.locator("[id$='" + lang + "']").all().forEach(loc -> {
            if (loc.isVisible()) loc.fill(RandomStringUtils.randomAlphabetic(8));
        });
        selectGender();
        fillProofOfIdentity();
    }

    public void selectGender() {
        // No language suffix on this id — eng and ara instances render side by side, both visible.
        openAndPickFirstOption("#gender:visible");
    }

    public void fillProofOfIdentity() {
        fill("#proofOfIdentityvalue", ResidentTestData.testLabel(), "Enter the Proof of Identity document name");
        openAndPickFirstOption("#proofOfIdentity");
        page.setInputFiles("#fileDropRef", loadUploadFilePayload());
    }

    private FilePayload loadUploadFilePayload() {
        try (InputStream in = UpdateMyDataPage.class.getResourceAsStream(UPLOAD_FILE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Classpath resource not found: " + UPLOAD_FILE_RESOURCE);
            }
            return new FilePayload("tonyId.png", "image/png", in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Opens a mat-select and picks its first option, retrying the whole open+pick sequence —
     * the schema-driven dropdowns can tear down and re-render their option panel mid-click, and
     * confirms the panel actually closed before trusting the selection took effect (the click
     * can be reported as executed even when the panel just stays open).
     */
    private void openAndPickFirstOption(String triggerSelector) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                page.locator(triggerSelector).first().click();
                Locator overlayPane = page.locator(".cdk-overlay-pane");
                overlayPane.locator("mat-option").first()
                        .click(new Locator.ClickOptions().setTimeout(10_000));
                overlayPane.first().waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.DETACHED)
                        .setTimeout(3_000));
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                logger.debug("Attempt {}/3 to open '{}' failed: {}", attempt, triggerSelector, e.getMessage());
                cancelPendingRequestPopupIfShown(false);
            }
        }
        throw lastFailure;
    }

    // ── Address tab ───────────────────────────────────────────────────────────

    public void fillAddressFields() {
        cancelPendingRequestPopupIfShown(false);
        String lang = MosipTestDataManager.getLanguageList().isEmpty() ? "eng"
                : MosipTestDataManager.getLanguageList().get(0);
        fill("#addressLine1" + lang, RandomStringUtils.randomAlphabetic(10), "Enter Address Line 1");
        // Each dropdown fetches its child's options, so wait before opening the next one.
        selectDropdownFirstOption("#Region" + lang);
        waitForNetworkIdle();
        selectDropdownFirstOption("#Province" + lang);
        waitForNetworkIdle();
        selectDropdownFirstOption("#City" + lang);
        waitForNetworkIdle();
        selectDropdownFirstOption("#Zone" + lang);
        selectDropdownFirstOption("[id='Postal Code']");
        selectDropdownFirstOption("#proofOfAddress");
        fill("#proofOfAddressvalue", ResidentTestData.testLabel(), "Enter the Proof of Address document name");
        page.setInputFiles("#fileAddRef", loadUploadFilePayload());
    }

    private void selectDropdownFirstOption(String selector) {
        openAndPickFirstOption(selector + ":visible");
    }

    // ── Contact tab (email OTP) ────────────────────────────────────────────────

    public void fillNewEmail(String email) {
        cancelPendingRequestPopupIfShown(false);
        fill("#email", email, "Enter the new email address");
    }

    /**
     * "Send OTP" can be server-rejected with RES-SER-524/525 ("previous packet is pending") via
     * the shared #messagePopup dialog — identified by its text, since the dialog itself is
     * reused for every message in the app. Retries only for this exact message.
     */
    public void sendEmailOtp() {
        int maxMinutes = ResidentConfigManager.getPacketUpdateWaitMinutes();
        long deadline = System.currentTimeMillis() + maxMinutes * 60_000L;
        while (true) {
            click("#sendOTPemail", "Click the 'Send OTP' button");
            page.waitForTimeout(2_000); // let the click's response render (OTP panel or error popup)

            Locator errorPopup = page.locator("#messagePopup");
            if (errorPopup.count() == 0 || !errorPopup.textContent().contains("previous packet is pending")) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new IllegalStateException("Email update still blocked by a pending packet after "
                        + maxMinutes + " minute(s) — giving up.");
            }
            page.locator("#dismissBtn").click();
            page.waitForTimeout(10_000);
        }
    }

    public void fillOtp(String otp) {
        // The Submit button's enabled state is driven by a (keyup) handler — fill() bypasses it.
        textBox("#otp-input").type(otp);
    }

    public void submitOtp() {
        click("#submitOtpBtn", "Click the 'Submit OTP' button");
    }

    // ── Language tab ──────────────────────────────────────────────────────────

    // Submits directly (unlike Name/Address's preview -> submit -> confirm), so the pending-
    // request check after Submit is the only one needed for this flow.
    public void selectPreferredLanguage() {
        cancelPendingRequestPopupIfShown(false);
        openAndPickFirstOption("#preferredLang");
        click("#submit", "Click the 'Submit' button");
        cancelPendingRequestPopupIfShown(true);
    }

    // ── Shared submit flow ─────────────────────────────────────────────────────

    public void preview() {
        click("#previewBtn", "Click the 'Preview' button");
        waitForNetworkIdle();
    }

    public void submit() {
        click("#submitBtn", "Click the 'Submit' button");
    }

    public void confirmUpdate() {
        click("#confirmUpdateData", "Tick the update-data consent checkbox");
        click("#updateMyDataBtn", "Confirm the data update");
        waitForNetworkIdle();
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    public void assertUpdateSuccessPopup() {
        visual.assertSuccessPopup();
    }

    public void dismissPopup() {
        click("#dismissBtn", "Dismiss the popup");
    }

}
