package io.mosip.testrig.residentui.config;

import io.mosip.testrig.apirig.utils.ConfigManager;
import io.mosip.testrig.uitestrig.core.UiConfigManager;
import io.mosip.testrig.uitestrig.listeners.CucumberHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Resident Portal config — five-layer loading (lowest → highest priority):
 *   1. Kernel.properties      (apitest-commons JAR — common MOSIP defaults)
 *   2. uitest.properties      (uitest-commons JAR  — UI/browser defaults)
 *   3. valueMapping.properties (this module         — default identity field values for
 *                                dynamic UIN generation; see ResidentUtil.buildAddIdentityRequest)
 *   4. resident.properties    (this module         — env-specific overrides)
 *   5. Environment variables / Rancher overrides
 *
 * Loading order ensures each layer correctly overrides the one below it.
 * Call ResidentConfigManager.init() once at suite setup before anything reads config.
 */
public class ResidentConfigManager extends UiConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ResidentConfigManager.class);

    // resident.properties ships tracked with these blank — every one is read downstream (page
    // navigation, Keycloak auth for identity generation) with no further fallback, so a blank
    // value otherwise surfaces as a cryptic NPE deep inside apitest-commons/uitest-commons
    // instead of a clear "fill this in" message.
    private static final List<String> REQUIRED_KEYS = List.of(
            "baseUrl", "apiInternalEndPoint", "keycloak-external-url",
            "mosip_idrepo_client_secret", "mosip_admin_client_secret", "mosip_testrig_client_secret");

    public static void init() {
        Map<String, Object> moduleProps = new HashMap<>();
        // Layer 2: uitest-commons UI defaults — loaded first so later layers override them
        moduleProps.putAll(loadProperties("config/uitest.properties"));
        // Layer 3: default identity field values (dateOfBirth, email, phone, postalCode, ...)
        moduleProps.putAll(loadProperties("config/valueMapping.properties"));
        // Layer 4: module-specific overrides
        moduleProps.putAll(loadResidentProperties());
        moduleProps.putAll(loadProperties("config/bioValue.properties"));

        validateRequiredConfig(moduleProps);
        bridgeEnvEndpoint(moduleProps);

        // ConfigManager.init(): loads Kernel.properties first (layer 1), then putAll(moduleProps)
        // so layers 2-4 override Kernel; env vars (layer 5) override everything inside init().
        ConfigManager.init(moduleProps);

        bridgeScenarioToRun();
    }

    /** Reads a key the same way ConfigManager.init()'s own generic fallback eventually will
     *  (apitest-commons ConfigManager.getValueForKeyAddToPropertiesMap's else branch): an
     *  environment variable (Rancher's actual config-injection mechanism for this module — plain
     *  ConfigMap/Secret env vars, not a mounted file) wins over whatever resident.properties has
     *  baked in. Must be duplicated here rather than just reading moduleProps, because both
     *  callers below run *before* ConfigManager.init() ever merges that env-var layer in. */
    private static String resolveWithEnvOverride(Map<String, Object> moduleProps, String key) {
        String envValue = System.getenv(key);
        if (envValue != null) return envValue;
        Object fileValue = moduleProps.get(key);
        return fileValue == null ? null : (String) fileValue;
    }

    /** Fails fast with every missing key named at once, instead of letting the suite run and
     *  crash minutes later with an unrelated-looking NPE the first time it reads one of these. */
    private static void validateRequiredConfig(Map<String, Object> moduleProps) {
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            // Logs presence/absence only, never the actual value (some of these are secrets) —
            // lets a failed run's log answer "did the env var actually reach this container"
            // without needing to exec into the pod separately.
            String envValue = System.getenv(key);
            Object fileValue = moduleProps.get(key);
            logger.info("Config check for '{}': env={}, resident.properties={}", key,
                    envValue == null ? "<unset>" : (envValue.isBlank() ? "<blank>" : "<set>"),
                    fileValue == null ? "<unset>" : (((String) fileValue).isBlank() ? "<blank>" : "<set>"));

            String value = resolveWithEnvOverride(moduleProps, key);
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required config for: " + String.join(", ", missing)
                            + " - set these as environment variables (Rancher ConfigMap/Secret) or in "
                            + "resident.properties before running the suite "
                            + "(see ui-test/README.md's Setup section).");
        }
    }

    /** ConfigManager.init() derives eSignetbaseurl/signupBaseUrl/injiCertifyBaseURL from the JVM
     *  system property "env.endpoint" (the apitest-rig -Denv.endpoint=... convention) whenever
     *  those keys are blank — which they always are here, since this module uses its own
     *  apiInternalEndPoint key instead and never sets env.endpoint. Without this bridge,
     *  ConfigManager.init() NPEs calling .replace() on a null env.endpoint. A -Denv.endpoint=...
     *  passed directly still wins over the derived value. */
    private static void bridgeEnvEndpoint(Map<String, Object> moduleProps) {
        if (System.getProperty("env.endpoint") != null) return;
        String apiInternalEndPoint = resolveWithEnvOverride(moduleProps, "apiInternalEndPoint");
        if (apiInternalEndPoint != null && !apiInternalEndPoint.isBlank()) {
            System.setProperty("env.endpoint", apiInternalEndPoint.trim());
        }
    }

    /** Bridges resident.properties' scenarioToRun into the system property CucumberHooks reads.
     *  A -DscenarioToRun=... passed directly on the command line still wins over the file. */
    private static void bridgeScenarioToRun() {
        if (System.getProperty(CucumberHooks.SCENARIO_FILTER_PROPERTY) != null) return;
        String scenarioToRun = getproperty("scenarioToRun");
        if (scenarioToRun != null && !scenarioToRun.isBlank()) {
            System.setProperty(CucumberHooks.SCENARIO_FILTER_PROPERTY, scenarioToRun.trim());
            logger.info("Running a single scenario (scenarioToRun): {}", scenarioToRun.trim());
        }
    }

    // Rancher deployments configure this module entirely via env vars (confirmed in production —
    // no mounted config is ever found), but local/Docker runs document mounting a real
    // resident.properties over this classpath-baked, blank-default one (see README.md). Since
    // this module now ships as a self-contained jar (see MainRunner), that classpath resource is
    // frozen inside the jar at build time — entrypoint.sh can no longer override it by copying a
    // file into a source tree that no longer exists at runtime. Reading the external file here
    // instead keeps that documented workflow working.
    private static final String DEFAULT_RESIDENT_CONFIG_PATH = "/home/mosip/config/resident.properties";

    private static Map<String, Object> loadResidentProperties() {
        Map<String, Object> props = loadProperties("config/resident.properties");

        String externalPath = System.getenv("RESIDENT_CONFIG_PATH");
        if (externalPath == null || externalPath.isBlank()) {
            externalPath = DEFAULT_RESIDENT_CONFIG_PATH;
        }
        File externalFile = new File(externalPath);
        if (externalFile.isFile()) {
            Properties external = new Properties();
            try (InputStream in = new FileInputStream(externalFile)) {
                external.load(in);
                logger.info("Loaded mounted config from {} — {} properties", externalPath, external.size());
            } catch (IOException e) {
                logger.error("Failed to load mounted config from {}: {}", externalPath, e.getMessage());
            }
            for (String key : external.stringPropertyNames()) {
                props.put(key, external.getProperty(key));
            }
        }
        return props;
    }

    private static Map<String, Object> loadProperties(String resourcePath) {
        Properties props = new Properties();
        try (InputStream in = ResidentConfigManager.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (in != null) {
                props.load(in);
                logger.info("Loaded {} — {} properties", resourcePath, props.size());
            } else {
                logger.warn("{} not found on classpath", resourcePath);
            }
        } catch (IOException e) {
            logger.error("Failed to load {}: {}", resourcePath, e.getMessage());
        }
        Map<String, Object> map = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            map.put(key, props.getProperty(key));
        }
        return map;
    }

    public static String getExternalEmail()     { return getproperty("externalemail"); }
    public static String getUin()               { return getproperty("uin"); }
    public static String getPerpetualVid()      { return getproperty("perpetualvid"); }
    public static String getTemporaryVid()      { return getproperty("temporaryvid"); }
    public static String getOneTimeVid()        { return getproperty("onetimevid"); }
    public static boolean isIdentityGenManual() { return "yes".equalsIgnoreCase(getproperty("identityGenManual")); }

    /** Max minutes to poll for a pending demographic-update request to clear before giving up. */
    public static int getPacketUpdateWaitMinutes() {
        String val = getproperty("packetUpdateWait");
        return (val == null || val.isBlank()) ? 2 : Integer.parseInt(val.trim());
    }

    /** Delay between each parallel worker thread's first scenario, to avoid bursting the
     *  environment's auth rate limit when several threads start at the same instant. */
    public static long getScenarioStaggerMillis() {
        String val = getproperty("scenarioStaggerMillis");
        return (val == null || val.isBlank()) ? 3000L : Long.parseLong(val.trim());
    }

    /** A freshly-generated identity's "ACTIVATED" response only confirms the request was
     *  accepted, not that MOSIP's async pipeline has finished — logging in too soon gets
     *  rejected and retried against an identity the auth service isn't ready for yet. */
    public static long getIdentitySettleMillis() {
        String val = getproperty("identitySettleMillis");
        return (val == null || val.isBlank()) ? 30000L : Long.parseLong(val.trim());
    }
}
