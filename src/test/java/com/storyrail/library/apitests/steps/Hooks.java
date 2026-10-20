package com.storyrail.library.apitests.steps;

import com.storyrail.library.apitests.support.Api;
import com.storyrail.library.apitests.support.ScenarioContext;
import io.cucumber.java.After;
import java.util.ArrayList;
import java.util.List;

/** Deletes every book the scenario created, so scenarios are independent and safe to run against QA. */
public final class Hooks {

    private final ScenarioContext context;
    private final Api api;

    public Hooks(ScenarioContext context, Api api) {
        this.context = context;
        this.api = api;
    }

    @After
    public void deleteCreatedBooks() {
        List<String> failures = new ArrayList<>();
        for (String id : context.createdIds()) {
            int status = api.deleteQuietly(id);
            if (status != 204 && status != 404) {
                failures.add(id + " -> " + status);
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Cleanup failed for " + failures);
        }
    }
}
