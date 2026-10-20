package com.storyrail.library.apitests;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runs every feature under src/test/resources/features with the Cucumber engine.
 * Select scenarios with -Dcucumber.filter.tags (e.g. "@qa and not @local-only") and the API with -Dapi.baseUrl.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class RunCucumberTest {
}
