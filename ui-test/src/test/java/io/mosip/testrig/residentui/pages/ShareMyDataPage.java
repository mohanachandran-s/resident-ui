package io.mosip.testrig.residentui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.mosip.testrig.uitestrig.pages.BasePage;
import io.mosip.testrig.uitestrig.assertions.VisualValidator;
import io.mosip.testrig.uitestrig.reporting.UiExtentReportManager;

public class ShareMyDataPage extends BasePage {

    private final VisualValidator visual;

    public ShareMyDataPage(Page page) {
        super(page);
        this.visual = new VisualValidator(page);
    }

    public void navigateTo() {
        dismissPopupIfPresent("#dismissBtn"); // guard against a leftover popup blocking the click
        click("#uinservices\\/sharewithpartner", "Open the Share My Data service");
        waitForNetworkIdle();
    }

    // Field id follows the identity schema's attributeName, which varies per environment.
    public void selectFullName() {
        page.locator("#fullName, #name").first().click();
    }

    public void selectDateOfBirth()  { click("#dateOfBirth", "Select the 'Date of Birth' attribute"); }
    public void selectUin()          { click("#UIN", "Select the 'UIN' attribute"); }
    public void selectPerpetualVid() { click("#perpetualVID", "Select the 'Perpetual VID' attribute"); }
    public void selectPhone()        { click("#phone", "Select the 'Phone' attribute"); }
    public void selectEmail()        { click("#email", "Select the 'Email' attribute"); }
    public void selectFullAddress()  { click("#fullAddress", "Select the 'Full Address' attribute"); }
    public void selectGender()       { click("#gender", "Select the 'Gender' attribute"); }
    public void selectPhoto()        { click("#photo", "Select the 'Photo' attribute"); }

    /**
     * Opens the partner dropdown and picks its first option, retrying the whole open+pick
     * sequence — mirrors UpdateMyDataPage.openAndPickFirstOption: these mat-select dropdowns can
     * tear down and re-render their option panel mid-click. Logs each attempt as its own step so
     * a failure here is reported against this locator, not a stale earlier step.
     */
    public void selectPartner() {
        click("#partnerDetails", "Open the partner dropdown");
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                UiExtentReportManager.logStepWithLocator("Select a partner from the dropdown", "mat-option");
                page.locator("mat-option").first().click(new Locator.ClickOptions().setTimeout(10_000));
                return;
            } catch (RuntimeException e) {
                lastFailure = e;
                logger.debug("Attempt {}/3 to select a partner failed: {}", attempt, e.getMessage());
                page.locator("#partnerDetails").click();
            }
        }
        throw lastFailure;
    }

    public void fillSharingReason(String reason) {
        fill("#sharingReasonPlaceholder", reason, "Enter the reason for sharing data");
    }

    public void clearSharingReason() {
        page.locator("#sharingReasonPlaceholder").clear();
    }

    public void clickShare() {
        click("#shareBtn", "Click the 'Share' button");
    }

    public void confirmShare() {
        click("#confirmShareInfo", "Tick the share-information consent checkbox");
        click("#shareInfoBtn", "Confirm sharing the information");
        waitForNetworkIdle();
    }

    public void assertShareSuccessPopup() {
        visual.assertSuccessPopup();
    }

    public void dismissPopup() {
        click("#dismissBtn", "Dismiss the popup");
    }
}
