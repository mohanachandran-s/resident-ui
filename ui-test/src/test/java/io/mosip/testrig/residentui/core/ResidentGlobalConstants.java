package io.mosip.testrig.residentui.core;

/**
 * Resident Portal element selectors and constants.
 * These are portal-specific and must not live in uitest-commons.
 */
public class ResidentGlobalConstants {

    // ── Section navigation IDs ─────────────────────────────────────────────────
    public static final String SECTION_VIEW_HISTORY      = "#uinservices\\/viewhistory";
    public static final String SECTION_MANAGE_VID        = "#uinservices\\/managemyvid";
    public static final String SECTION_UPDATE_DATA       = "#uinservices\\/updatedemographic";
    public static final String SECTION_PERSONALISED_CARD = "#uinservices\\/personalisedcard";
    public static final String SECTION_SHARE_DATA        = "#uinservices\\/sharewithpartner";
    public static final String SECTION_LOCK_AUTH         = "#uinservices\\/lockunlockauth";
    public static final String SECTION_TRACK_REQUEST     = "#uinservices\\/trackservicerequest";
    public static final String SECTION_GET_INFORMATION   = "#getInformation";
    public static final String SECTION_GET_MY_UIN        = "#getMyUIN";
    public static final String SECTION_VERIFY_EMAIL      = "#verifyPhoneNumber\\/EmailID";

    // ── Language selector — set data-testid="language-dropdown" on the element
    // until that is available, this auto-generated Angular Material ID is used
    public static final String LANGUAGE_DROPDOWN = "#mat-select-0";

    // ── Authentication / login ─────────────────────────────────────────────────
    public static final String LOGIN_UIN_SERVICES_BTN = "#UINservices";
    public static final String LOGIN_WITH_OTP_BTN     = "#login_with_otp";
    public static final String LOGIN_VID_INPUT        = "input[id$='vid']";
    public static final String GET_OTP_BTN            = "#get_otp";
    public static final String LOGIN_GET_OTP_BTN      = GET_OTP_BTN;
    public static final String LOGIN_OTP_INPUT_WRAPPER = "#otp_verify_input";
    public static final String OTP_INPUT_FIELDS       = "#otp_verify_input div input";
    public static final String VERIFY_OTP_BTN         = "#verify_otp";
    public static final String LOGIN_VERIFY_OTP_BTN   = VERIFY_OTP_BTN;
    public static final String LOGIN_AUTHORIZE_SCOPE  = "#authorize_scope";
    public static final String LOGIN_VOLUNTARY_CLAIMS = "#voluntary_claims";
    public static final String LOGIN_CONTINUE_BTN     = "#continue";

    private ResidentGlobalConstants() {}
}
