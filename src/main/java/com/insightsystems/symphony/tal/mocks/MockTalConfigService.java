/*
 * Copyright (c) 2019 AVI-SPL Inc. All Rights Reserved.
 */

package com.insightsystems.symphony.tal.mocks;

import com.avispl.symphony.api.tal.TalAdapter;
import com.avispl.symphony.api.tal.TalConfigService;
import com.avispl.symphony.api.tal.dto.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Mock implementation of TalConfigService
 * This mock could be used to simplify adapter testing having parts of Symphony infrastructure
 *
 * @author Symphony Dev Team<br> Created on Jan 20, 2019
 */
public class MockTalConfigService implements TalConfigService {

    @Override
    public TicketSystemConfig retrieveTicketSystemConfig(UUID uuid) throws ExecutionException {
        TicketSystemConfig config = new TicketSystemConfig();

        // Add custom
        Map<String, String> instanceConfigMapping = new HashMap<>();
        // URL example: "https://connect.myCompany.com.au"
        instanceConfigMapping.put(TicketSourceConfigProperty.URL, null);
        // API_PATH example: "/v4_6_release/apis/3.0/service/tickets"
        instanceConfigMapping.put(TicketSourceConfigProperty.API_PATH, null);
        instanceConfigMapping.put(TicketSourceConfigProperty.LOGIN, null); // ConnectWise clientID
        instanceConfigMapping.put(TicketSourceConfigProperty.PASSWORD, null); // ConnectWise Authorization

        // keys are Symphony priorities and values are third party mapped statuses
        Map<String, String> customerPriorityMappingForThirdParty  = new HashMap<>();
        customerPriorityMappingForThirdParty.put("Critical", "6");
        customerPriorityMappingForThirdParty.put("Major", "91");
        customerPriorityMappingForThirdParty.put("Minor", "8");
        customerPriorityMappingForThirdParty.put("Informational", "12");

        // keys are third party priorities and values are Symphony mapped statuses
        Map<String, String> customerPriorityMappingForSymphony  = new HashMap<>();
        customerPriorityMappingForThirdParty.put("6", "Critical");
        customerPriorityMappingForThirdParty.put("91", "Major");
        customerPriorityMappingForThirdParty.put("8", "Minor");
        customerPriorityMappingForThirdParty.put("12", "Informational");

        // keys are third party users and values are Symphony mapped user
        Map<String, String> userMappingForSymphony = new HashMap<>();
        userMappingForSymphony.put("LPisano", "lucap@insightsystems.com.au");

        // keys are Symphony users and values are third party mapped users
        Map<String, UserIdMapping> userMappingForThirdParty = new HashMap<>();
        userMappingForThirdParty.put("lucap@insightsystems.com.au",
                new UserIdMapping("LPisano", "username"));

        // keys are third party statuses and values are Symphony mapped statuses
        Map<String, String> statusMappingForSymphony = new HashMap<>();
        statusMappingForSymphony.put("New", "Open");
        statusMappingForSymphony.put("Open", "Open");
        statusMappingForSymphony.put("ClosePending", "ClosePending");
        statusMappingForSymphony.put("Closed", "Close");

        // keys are Symphony statuses and values are third party mapped statuses
        Map<String, String> statusMappingForThirdParty = new HashMap<>();
        statusMappingForThirdParty.put("Open", "Open");
        statusMappingForThirdParty.put("Close", "Closed");
        statusMappingForThirdParty.put("ClosePending", "ClosePending");

        config.setPriorityMappingForThirdParty(customerPriorityMappingForThirdParty);
        config.setPriorityMappingForSymphony(customerPriorityMappingForSymphony);
        config.setUserMappingForSymphony(userMappingForSymphony);
        config.setUserMappingForThirdParty(userMappingForThirdParty);
        config.setTicketSourceConfig(instanceConfigMapping);
        config.setStatusMappingForSymphony(statusMappingForSymphony);
        config.setStatusMappingForThirdParty(statusMappingForThirdParty);

        return config;
    }

    @Override
    public void subscribeForTicketSystemConfigUpdate(UUID uuid, Consumer<TicketSystemConfig> consumer) {
        // no op
    }

    @Override
    public List<UUID> listCustomerAccounts(TalAdapter talAdapter) {
        return null;
    }

    @Override
    public void subscribeForTicketSystemConfigUpdate(TalAdapter talAdapter, BiConsumer<UUID, TicketSystemConfig> biConsumer) {

    }
}
