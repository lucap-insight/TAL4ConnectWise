/*
 * Copyright (c) 2019 AVI-SPL Inc. All Rights Reserved.
 */

package com.insightsystems.symphony.tal.sample;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.avispl.symphony.api.tal.TalAdapter;
import com.avispl.symphony.api.tal.dto.Comment;
import com.avispl.symphony.api.tal.dto.TicketSourceConfigProperty;
import com.avispl.symphony.api.tal.dto.TicketSystemConfig;
import com.insightsystems.symphony.tal.mocks.MockTalConfigService;
import com.insightsystems.symphony.tal.mocks.MockTalProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avispl.symphony.api.tal.TalConfigService;
import com.avispl.symphony.api.tal.TalProxy;
import com.avispl.symphony.api.tal.dto.TalTicket;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;

//import org.apache.logging.log4j.Level;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

/**
 * Sample TAL adapter implementation.
 *
 * @author Symphony Dev Team<br> Created on 7 Dec 2018
 * @since 4.6
 */
public class SampleTalAdapterImpl implements TalAdapter {

    /**
     * Logger instance
     */
    private static final Logger logger = LoggerFactory.getLogger(SampleTalAdapterImpl.class);

    /**
     * Instance of a TalConfigService, set by Symphony via {@link #setTalConfigService(TalConfigService)}
     * In sake of testing simplicity, one may use MockTalConfigService provided with this sample
     */
    private TalConfigService talConfigService;

    /**
     * Instance of a TalProxy, set by Symphony via {@link #setTalProxy(TalProxy)}
     * In sake of testing simplicity, one may use MockTalProxy provided with this sample
     */
    private TalProxy talProxy;

    /**
     * Instance of TicketSystemConfig that contains mappings and destination
     * ticketing system configuration
     */
    private TicketSystemConfig config;

    /**
     * Account identifier - have to be provided to 3rd party adapter implementors by Symphony team
     */
    private UUID accountId = UUID.fromString("e8ab4178-81fb-43c9-8eae-1a61d609a991");

    /**
     * Default no-arg constructor
     */
    public SampleTalAdapterImpl() {
        /**
         * Uncomment following in order to use mocks instead of setter-injected objects
         * Warning: use for development purposes only!
         */
        this.talConfigService = new MockTalConfigService();
        this.talProxy = new MockTalProxy();
    }

    /**
     * Called by Symphony automatically after instance of adapter is created and talConfigService/talProxy setters
     *
     * Important: In this method developer must not perform any heavy synchronous initialization or I/O bound operations.
     * All such operations must be performed asynchronously in background thread(s).
     */
    @Override
    public void init() {
        logger.info("Initializing Sample TAL adapter");

        // In order to get ticket updates from Symphony adapter must subscribe to this explicitly here
        // After subscription is done, all updates will come to this adapter instance via calls to syncTalTicket method
        talProxy.subscribeUpdates(accountId, this);

        try {
            // obtain adapter configuration
            setConfig(talConfigService.retrieveTicketSystemConfig(accountId));
        } catch (Exception e) {
            throw new RuntimeException("SampleTalAdapterImpl was unable to retrieve " +
                    "configuration from TalConfigService: " + e.getMessage(), e);
        }

        // subscribe for getting adapter configuration updates
        talConfigService.subscribeForTicketSystemConfigUpdate(accountId,
                (ticketSystemConfig) -> setConfig(ticketSystemConfig));

    }

    /**
     * Called by Symphony when application is about to exit
     */
    @Override
    public void destroy() {
        // destroy any persistent resources
        // such as thread pools or persistent connections
    }

    /**
     * Invoked on each ticket update that happens in Symphony
     * @param talTicket instance of ticket that contains updated data. Ticket always come containing all fields even those that didn't change
     * @return instance of TalTicket that contains thirdPartyId and thirdPartyLink set for ticket, comments and attachments provisioned in 3rd party system
     * @throws TalAdapterSyncException
     */
    @Override
    public TalTicket syncTalTicket(TalTicket talTicket) throws TalAdapterSyncException {
        try {
            // map status, priorities, users to comply with 3rd party ticketing system
            try {
                TicketMapper.mapSymphonyToThirdParty(talTicket, config);
            } catch (NullPointerException e) {
                logger.error("syncTalTicket: error mapping Ticket info to CW equivalent");
                throw e;
            }

            // 1. make call to 3rd party ticketing system

            // Setup information to connect to ConnectWise API
            String url = null; // this will hold the url to access the ticket
            JSONObject CWTicket = null; // This is the ConnectWise synced ticket

            // If the ConnectWise connection has been set using TalTicket's ThirdPartyLink
            // False if connection was set up using the ID or if a new ticket was created
            boolean connectionByLink = false;

            boolean createTicket = true; // If adapter needs to create ConnectWise ticket
            boolean connectionFailed = false; // If connection was attempted but failed

            if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) == null) {
                logger.warn("syncTalTicket: URL not setup on Config");
            }
            if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null) {
                logger.warn("syncTalTicket: API_PATH not setup on Config");
            }

            // If ticket has Third Party ID and Third Party Link (already exists in ConnectWise)
            if (talTicket.getThirdPartyId() != null || talTicket.getThirdPartyLink() != null) {
                logger.info("syncTalTicket: Ticket has ID or Third Party link");

                // Try to access ticket via Third Party Link
                url = talTicket.getThirdPartyLink();

                // API call body
                logger.info("syncTalTicket: Attempting API call using Third Party Link");
                try {
                    CWTicket = ConnectWiseAPICall(url, "GET", null);
                    connectionByLink = true; // Connection was successful using ThirdPartyLink
                } catch (Exception e) {
                    logger.error("syncTalTicket: Attempt failed - " + e.getMessage());
                }

                // If response is null API call resulted in error: try manually building url
                if (CWTicket == null) {
                    logger.info("syncTalTicket: Attempting API call using Third Party ID");

                    // Build url from config and ticket Third Party ID:
                    // URL example: "https://connect.myCompany.com.au"
                    // API_PATH example: "/api/ticket/v4_6_release/apis/3.0/service/tickets"
                    // ThirdPartyId example: "187204"
                    // url example: "https://connect.myCompany.com.au/api/ticket/v4_6_release/apis/3.0/service/tickets/187204"

                    url = config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                            config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) +
                            "/" + talTicket.getThirdPartyId();

                    try {
                        CWTicket = ConnectWiseAPICall(url, "GET", null);
                    } catch (Exception e) {
                        logger.error("syncTalTicket: Attempt failed - " + e.getMessage());
                    }
                }

                // if response is still null API calls failed
                if (CWTicket == null) {
                    // Log error
                    logger.error("syncTalTicket: Both API attempts unsuccessful");

                    // Check if a connectionFailed already happen to prevent creating multiple tickets
                    if (Objects.equals(talTicket.getExtraParams().get("connectionFailed"), "true")) {
                        logger.info("synTalTicket: Ticket has failed before - not creating new ticket");
                        createTicket = false;
                        // TODO: return null? What to do when connection is an error?
                        throw new TalAdapterSyncException("Cannot sync TAL ticket");
                    } else {
                        logger.info("syncTalTicket: Attempting to create new ticket");
                        connectionFailed = true;
                    }

                }
                // if response has value it means API call was successful
                else {
                    createTicket = false;
                    logger.info("syncTalTicket: Attempt successful");

                    // Add extra parameter to show connection was successful
                    if (talTicket.getExtraParams().putIfAbsent("connectionFailed", "false") != null) {
                        // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                        talTicket.getExtraParams().replace("connectionFailed","false");
                    }
                }
            }

            // If ticket does not exist in ConnectWise: Create ticket in ConnectWise
            if (createTicket) {
                if (!connectionFailed) {
                    logger.info("syncTalTicket: Ticket has no ID and Third Party Link");
                } else {
                    // Add extra parameter to not duplicate ticket in case it happens again
                    System.out.println("Setting the connectionFailed parameter: true");
                    if (talTicket.getExtraParams().putIfAbsent("connectionFailed", "true") != null) {
                        // "putIfAbsent" returns null if "put" worked, and returns the value found otherwise
                        talTicket.getExtraParams().replace("connectionFailed","true");
                    }
                    // Change description to note that the ticket has failed before
                    if (talTicket.getSubject() != null) {
                        talTicket.setSubject(talTicket.getSubject() + " - ERROR: previous synced ticket not found");
                    } else {
                        talTicket.setSubject("<Symphony> NEW Ticket - ERROR: previous synced ticket not found");
                    }
                }

                // Create new ticket on ConnectWise
                logger.info("syncTalTicket: Attempting to create ticket on ConnectWise");

                // Check if URL and API_PATH are not null
                if (config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) == null ||
                        config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) == null) {
                    logger.error("syncTalTicket: URL or API_PATH not setup on Config");
                    // TODO: What to do when connection fails?
                    throw new TalAdapterSyncException("Cannot create a new ticket: URL or API_PATH not setup on config");
                }

                url = config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                        config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH);

                // Body of the request
                // FIXME: Get board and company from ticketSourceConfig
                String requestBody = "{\n" +
                        "    \"summary\" : \"" + talTicket.getSubject() + "\",\n" +
                        "    \"board\" : {\n" +
                        "        \"id\": 199\n" +
                        "    },\n" +
                        "    \"company\": {\n" +
                        "        \"id\": 250\n" +
                        "    },\n" +
                  //      "    \"contactEmailAddress\" : \"" + talTicket.getRequester() + "\"\n" +
                        "}";

                // Writing the body
                try {
                    CWTicket = ConnectWiseAPICall(url, "POST", requestBody);
                } catch (Exception e) {
                    // TODO: What to do when connection fails?
                    logger.error("syncTalTicket: Unable to POST ticket - {}", e.getMessage());
                    throw new RuntimeException(e);
                }

                // Setting URL to proper value with ticket id
                if (CWTicket != null) {
                    logger.info("syncTalTicket: setting TalTicket id");
                    url += "/" + CWTicket.get("id");
                    talTicket.setThirdPartyId(CWTicket.get("id") + "");
                }
            }


            // 2. handle response from 3rd party ticketing system

            // url should now be set to a valid value
            if (url == null) {
                throw new RuntimeException("An unexpected error occurred: URL not set properly");
            } else {
                logger.info("syncTalTicket: Connection set to: " + url);
            }


            // 3. if succeeded change talTicket, set thirdPartyId and thirdPartyLink set for ticket, comments and attachments provisioned in 3rd party system
            // Check and update:

            // Check if connection was established correctly
            if (CWTicket == null) {
                logger.info("syncTalTicket: ConnectWise ticket error");
                throw new RuntimeException();
            }

            // Ticket's third party link and ID check
            if (connectionByLink) {
                // Connection was successful using Link, now we need to try to check if the ID matches the link

                // Get ticket ID from link
                int beginIndex = talTicket.getThirdPartyLink().lastIndexOf('/') +1;
                String IDFromLink = talTicket.getThirdPartyLink().substring(beginIndex);

                if (!Objects.equals(IDFromLink, talTicket.getThirdPartyId())) {
                    // If ID is incorrect: fix it
                    logger.info("syncTalTicket: Fixing ThirdPartyID from: " +
                            talTicket.getThirdPartyId() + " to: " + IDFromLink);
                    talTicket.setThirdPartyId(IDFromLink);
                }
            } else {
                // This means Link is not functional but connection was successful using ThirdPartyID

                String testUrl = config.getTicketSourceConfig().get(TicketSourceConfigProperty.URL) +
                        config.getTicketSourceConfig().get(TicketSourceConfigProperty.API_PATH) +
                        "/" + talTicket.getThirdPartyId();

                if (!Objects.equals(testUrl, talTicket.getThirdPartyLink())) {
                    // If Url is incorrect: fix it
                    logger.info("syncTalTicket: Fixing ThirdPartyLink");
                    talTicket.setThirdPartyLink(testUrl);
                }
            }

            // Variable for body of PATCH request
            String requestBody = "";
            String path = null;
            String SymphonyValue = null;
            String ConnectWiseValue = null;

            // Ticket summary
            path = "summary";
            SymphonyValue = talTicket.getSubject();
            try { // try to get ConnectWise value
                ConnectWiseValue = CWTicket.getString(path);
            } catch (JSONException e) { // It is possible that it does not exist on GET/POST response
                logger.info("syncTalTicket: {} not found on ConnectWise", path);
                ConnectWiseValue = null;
            }

            if (SymphonyValue == null && (ConnectWiseValue == null || Objects.equals(ConnectWiseValue, "null"))) {
                if (talTicket.getDescription() != null) {
                    // If ticket summary does not exist (Symphony or CW), use description instead
                    SymphonyValue = talTicket.getDescription();
                    logger.info("SampleTalAdapter: syncTalTicket: Setting ticket summary to ticket description");
                } else {
                    // If ticket description also does not exist, use pre-set value for ticket summary
                    logger.info("SampleTalAdapter: syncTalTicket: Symphony ticket does not have summary or description. Using standard summary.");
                    // TODO: Hard coded summary standard
                    SymphonyValue = "<Symphony> NEW Ticket";
                    talTicket.setSubject(SymphonyValue);
                }
            }

            String requestResult = createRequestBody(SymphonyValue, ConnectWiseValue, path, true);
            if (requestResult != null)  {
                if (Objects.equals(requestResult, "Update Symphony")) {
                    // This means there is a CW value but no Symphony value
                    logger.info("syncTalTicket: Updating Symphony using CW value");
                    talTicket.setSubject(ConnectWiseValue);
                } else {
                    requestBody += requestResult;
                }
            }


            // Ticket priority
            path = "priority/id";
            SymphonyValue = talTicket.getPriority();
            try {
                ConnectWiseValue = CWTicket.getJSONObject("priority").getInt("id") + "";
            } catch (JSONException e) {
                logger.info("syncTalTicket: {} not found on ConnectWise", path);
                ConnectWiseValue = null;
            }
            requestResult = createRequestBody(SymphonyValue, ConnectWiseValue, path, false);
            if (requestResult != null) {
                if (Objects.equals(requestResult, "Update Symphony")) {
                    // This means there is a CW value but no Symphony value
                    logger.info("syncTalTicket: Updating Symphony using CW value");
                    talTicket.setPriority(ConnectWiseValue);
                } else {
                    if (!requestBody.isEmpty()) {
                        requestBody += ",\n";
                    }
                    requestBody += requestResult;
                }
            }

            // Ticket status
            path = "status/name";
            SymphonyValue = talTicket.getStatus();
            try {
                ConnectWiseValue = CWTicket.getJSONObject("status").getString("name");
            } catch (JSONException e) {
                logger.info("syncTalTicket: {} not found on ConnectWise", path);
                ConnectWiseValue = null;
            }
            requestResult = createRequestBody(SymphonyValue, ConnectWiseValue, path, true);
            if (requestResult!= null) {
                if (Objects.equals(requestResult, "Update Symphony")) {
                    // This means there is a CW value but no Symphony value
                    logger.info("syncTalTicket: Updating Symphony using CW value");
                    talTicket.setStatus(ConnectWiseValue);
                } else {
                    if (!requestBody.isEmpty()) {
                        requestBody += ",\n";
                    }
                    requestBody += requestResult;
                }
            }

            // User assigned to ticket
            path = "owner/identifier";
            SymphonyValue = talTicket.getAssignedTo();
            try {
                ConnectWiseValue = CWTicket.getJSONObject("owner").getString("identifier");
            } catch (JSONException e) {
                logger.info("syncTalTicket: {} not found on ConnectWise", path);
                ConnectWiseValue = null;
            }
            requestResult = createRequestBody(SymphonyValue, ConnectWiseValue, path, true);
            if (requestResult != null)  {
                if (Objects.equals(requestResult, "Update Symphony")) {
                    // This means there is a CW value but no Symphony value
                    logger.info("syncTalTicket: Updating Symphony using CW value");
                    talTicket.setAssignedTo(ConnectWiseValue);
                } else {
                    if (!requestBody.isEmpty()) {
                        requestBody += ",\n";
                    }
                    requestBody += requestResult;
                }
            }

            // Attachments
            //logger.info("syncTalTicket: Updating ticket attachments");
            // TODO: Place to add attachments to ticket sync PATCH

            //System.out.println(requestBody);

            // PATCH
            if (!requestBody.isEmpty()) {
                requestBody = "[" + requestBody + "]"; // Final request formatting
                logger.info("syncTalTicket: Making PATCH request");
                ConnectWiseAPICall(url, "PATCH", requestBody);
            } else {
                logger.info("syncTalTicket: No API call made");
            }

            // Comments
            logger.info("syncTalTicket: Updating ticket comments");
            syncComments(talTicket);


            // 4. return updated instance using "return statement" to the caller

            return talTicket;

        } catch (Exception e) {
            logger.warn("Failed to sync ticket from TAL to InMemory Ticket System {}", talTicket);
            throw new TalAdapterSyncException("Cannot sync TAL ticket", e);
        }
    }

    public JSONObject ConnectWiseAPICall(String url, String method, String requestBody) throws MalformedURLException {
        // Optional: Formalize input error checking on ConnectWiseAPICall

        String clientID = config.getTicketSourceConfig().get(TicketSourceConfigProperty.LOGIN);
        String authorization = config.getTicketSourceConfig().get(TicketSourceConfigProperty.PASSWORD);

        if (clientID == null || authorization == null) {
            logger.error("ConnectWiseAPICall: Unable to retrieve client ID and/or authorization from configuration");
            throw new NullPointerException("Error retrieving client ID and/or authorization");
        }

        if (url == null) {
            logger.error("ConnectWiseAPICall: URL cannot be null");
            throw new NullPointerException("URL for API call cannot be null");
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = null;

        try {
            if (requestBody != null) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method(method, HttpRequest.BodyPublishers.ofString(requestBody))
                        .header("Content-Type", "application/json")
                        .header("clientID", clientID)
                        .header("Authorization", authorization)
                        .build();
            } else if (Objects.equals(method, "GET")) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .header("clientID", clientID)
                        .header("Authorization", authorization)
                        .build();
            }
        } catch (Exception e) {
            logger.error("ConnectWiseAPICall: " + e.toString());
            throw new MalformedURLException(e.getMessage());
        }

        // Response
        HttpResponse<String> response;
        logger.info("ConnectWiseAPICall: Getting response");
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e + " - HTTP request error");
        }

        if (response != null && response.statusCode() == 200 || response.statusCode() == 201) {
            logger.info("ConnectWiseAPICall: "+method+" call successful - HTTP Code:"+
                    response.statusCode());
        } else {
            logger.error("ConnectWiseAPICall: " + method +
                    " call unsuccessful - HTTP Code:"+ response.statusCode());
            throw new RuntimeException(method + " Request error");
        }

        JSONObject jsonObject;
        try {
            //System.out.println(response.body());
            jsonObject = new JSONObject(response.body());
        } catch (JSONException e) {
            try {
                // It is possible that the response is a JSON array, so it is put in a JSON object under JSONArray
                jsonObject = new JSONObject("{ \"JSONArray\" : " + response.body() + "}");
            } catch (JSONException e2) {
                // If it is also not an Array: give up and report error
                logger.warn("ConnectWiseAPICall: error parsing content to JSON - " + e2);
                return null;
            }
        }
        return jsonObject;
    }

    public void syncComments(TalTicket talTicket) throws IOException, InterruptedException {
        // Getting an array of ConnectWise comments
        logger.info("syncComments: Getting ConnectWise comments");

        String url = talTicket.getThirdPartyLink() + "/notes"; // + "/notes" to get ticket comments on CW
        JSONArray jsonArray;
        // API Call
        try {
            jsonArray = ConnectWiseAPICall(url, "GET", null).getJSONArray("JSONArray");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        // Sync description - returns the comment with the description
        JSONObject descriptionCW = syncDescription(talTicket, url, jsonArray);

        // Compare each talTicket comment to CW comment
        Set<Comment> commentsToPatch = new HashSet<>();
        Set<Comment> commentsToPost = new HashSet<>();
        Iterator<Comment> itr = talTicket.getComments().iterator();
        Comment talComment;

        // for each TalTicket comment:
        logger.info("syncComments: Comparing Symphony comments to ConnectWise");
        while (itr.hasNext()) {
            talComment = itr.next();

            // Base case
            boolean ticketNeedsPatching = true;
            boolean ticketExists = false;

            // Ignore the description comment - it should already be synced by the syncDescription method
            if (descriptionCW != null && Objects.equals(talComment.getThirdPartyId(), descriptionCW.getInt("id") + "")) {
                continue;
            }

            // Check if ticket in CW
            for (int i = 0; i < jsonArray.length(); i++) {
                // Compare IDs
                if (talComment.getThirdPartyId() != null &&
                        Objects.equals(talComment.getThirdPartyId(), jsonArray.getJSONObject(i).get("id") + "")) {

                    ticketExists = true;
                    JSONObject cwComment = jsonArray.getJSONObject(i);

                    // Compare text to check if ticket needs patching
                    ticketNeedsPatching = (!Objects.equals(talComment.getText(), cwComment.get("text")));
                    break;
                }
            }

            if (ticketNeedsPatching) {
                if (ticketExists) {
                    commentsToPatch.add(talComment);
                }
                else {
                    commentsToPost.add(talComment);
                }
            }
        }

        // If there are tickets in CW that are NOT in Symphony (Direction CW -> Symphony)
        logger.info("syncComments: Comparing ConnectWise comments to Symphony");
        DateTimeFormatter ConnectWiseDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:m:sX");
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject commentCW = jsonArray.getJSONObject(i);

            boolean commentNotInSymphony = true;

            // Check if CW ticket is in TAL
            itr = talTicket.getComments().iterator();
            while (itr.hasNext()) {
                talComment = itr.next();
                if (Objects.equals(talComment.getThirdPartyId(), commentCW.getInt("id") + "")) {
                    commentNotInSymphony = false;
                }
            }

            if (commentNotInSymphony) {
                logger.info("syncComments: ConnectWise comment found not in Symphony - Updating Symphony");
                LocalDateTime commentDate = LocalDateTime.parse(commentCW.getString("dateCreated"),
                        ConnectWiseDateTimeFormatter);
                ZonedDateTime zdt = ZonedDateTime.of(commentDate, ZoneId.systemDefault());
                long lastModified = zdt.toInstant().toEpochMilli();

                Comment newComment = new Comment(null, commentCW.getInt("id") + "",
                        commentCW.getString("createdBy"), commentCW.getString("text"),
                        lastModified);

                talTicket.getComments().add(newComment);
            }
        }

        // PATCH tickets
        if (!commentsToPatch.isEmpty()) {
            logger.info("syncComments: Patching {} comments", commentsToPatch.size());
            itr = commentsToPatch.iterator();
            String requestBody = "";

            while (itr.hasNext()) {
                talComment = itr.next();
                String noteUrl = url + "/" + talComment.getThirdPartyId();

                requestBody = "[\n" +
                        (talComment.getCreator() != null ? // Ensure comment has creator
                        "    {\n" + // ticket creator
                        "        \"op\": \"replace\",\n" +
                        "        \"path\": \"member/identifier\",\n" +
                        "        \"value\": \""+ talComment.getCreator() +"\"\n" +
                        "    },\n" : "") +
                        "    {\n" + // ticket text
                        "        \"op\" : \"replace\",\n" +
                        "        \"path\" : \"text\",\n" +
                        "        \"value\" : \""+ talComment.getText() +"\"\n" +
                        "    }\n" +
                        "]";

                ConnectWiseAPICall(noteUrl, "PATCH", requestBody);

            }
            logger.info("syncComments: All comments PATCH SUCCESSFUL");
        } else {
            logger.info("syncComments: No comments to patch");
        }

        // POST tickets
        if (!commentsToPost.isEmpty()) {
            logger.info("syncComments: Posting {} new comments to ConnectWise",
                    commentsToPost.size());

            itr = commentsToPost.iterator();
            String requestBody = "";

            while (itr.hasNext()) {
                talComment = itr.next();

                requestBody = "{\n" +
                        "    \"text\" : \"" + talComment.getText() + "\",\n" +
                        "    \"internalAnalysisFlag\": true" + // Set to default internal notes
                        (talComment.getCreator() != null ? // Make sure comment creator is not null
                        ",\n" +
                        "    \"member\": {\n" +
                        "        \"identifier\": \"" + talComment.getCreator() + "\"\n" +
                        "    }\n" : "\n") +
                        "}";

                JSONObject jsonObject = ConnectWiseAPICall(url, "POST", requestBody);

                // Add ThirdParty ticket ID to ticket
                logger.info("syncComments: Updating Comment ID on Symphony");
                talComment.setThirdPartyId(jsonObject.getInt("id") + "");
            }
            logger.info("syncComments: All comments POST SUCCESSFUL");
        } else {
            logger.info("syncComments: No comments to post");
        }

    }

    public JSONObject syncDescription(TalTicket talTicket, String url, JSONArray CWComments) {
        /*
        This method attempts to sync the Symphony and CW descriptions.

        Inputs:
            - talTicket: The ticket to sync
            - url: URL for API call to POST/PATCH the ticket.
                i.e.: "https://connect.myCompany.com.au/api/ticket/v4_6_release/apis/3.0/service/tickets/187204/notes"
            - CWComments: JSONArray of all comments for this ticket currently present in CW
         */

        logger.info("syncDescription: Searching for ticket description on ConnectWise");
        DateTimeFormatter ConnectWiseDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'H:m:sX");
        LocalDateTime descriptionCWDate = null;
        JSONObject descriptionCW = null;

        // for each CW comment
        for (int i = 0; i < CWComments.length(); i++) {
            JSONObject comment = CWComments.getJSONObject(i);
            // if comment is in Discussion tab
            if (comment.getBoolean("detailDescriptionFlag")) {
                //get date created
                LocalDateTime commentDate = LocalDateTime.parse(comment.getString("dateCreated"),
                        ConnectWiseDateTimeFormatter);

                if (descriptionCWDate == null) {
                    logger.info("syncDescription: Ticked description comment found");
                    descriptionCWDate = commentDate;
                    descriptionCW = comment;
                }
                // On ConnectWise the description is the oldest comment
                else if (commentDate.isBefore(descriptionCWDate)) {
                    descriptionCWDate = commentDate;
                    descriptionCW = comment;
                }
            }
        }


        // Make sure ticket's description is valid and not null
        if (talTicket.getDescription() == null) {
            logger.info("syncDescription: Symphony ticket has no description");
            // Check if CW has a description
            if (descriptionCW != null && descriptionCW.getString("text") != null &&
                        !Objects.equals(descriptionCW.getString("text"), "null")) {
                // set description to CW
                logger.info("syncDescription: Description found in ConnectWise - Updating Symphony");
                talTicket.setDescription(descriptionCW.getString("text"));
            }
            // try using the ticket summary
            else if (talTicket.getSubject() != null) {
                logger.info("syncDescription: Using ticket summary as description");
                talTicket.setDescription(talTicket.getSubject());
            } else {
                // if for some reason the ticket does not have a description or summary, interrupt sync
                logger.warn("syncDescription: No valid description found");
                return null;
            }
        }

        if (descriptionCW != null) {
            // If CW description does not equal Symphony description ticket needs a PATCH
            if (!Objects.equals(descriptionCW.getString("text"), talTicket.getDescription())) {

                String descriptionUrl = url + "/" + descriptionCW.getInt("id");
                String requestBody = "[\n{" +
                        "   \"op\": \"replace\",\n" +
                        "   \"path\": \"text\",\n" +
                        "   \"value\": \""+ talTicket.getDescription() +"\"\n" +
                        "}\n]";
                logger.info("syncDescription: Updating ticket description");

                // API CALL
                try {
                    ConnectWiseAPICall(descriptionUrl, "PATCH", requestBody);
                } catch (MalformedURLException e) {
                    logger.error("syncDescription: CW API Call error - unable to sync description");
                    return null;
                }

            } else {
                logger.info("syncDescription: No update required for ticket description");
            }
        } else {
            // Needs to create the Discussion ticket with the description text
            String requestBody = "{\n" +
                    "    \"text\" : \"" + talTicket.getDescription() + "\",\n" +
                    "    \"detailDescriptionFlag\": true" + // Set to default internal notes
                    (talTicket.getRequester() != null ? // make sure ticket requester is not null
                    "    ,\n" +
                    "    \"member\": {\n" +
                    "        \"identifier\": \"" + talTicket.getRequester() + "\"\n" +
                    "    }\n" : "\n") +
                    "}";
            logger.info("syncDescription: Creating ticket description comment");
            try {
                ConnectWiseAPICall(url, "POST", requestBody);
            } catch (MalformedURLException e) {
                logger.error("syncDescription: CW API Call error - unable to sync description");
                return null;
            }
        }

        return descriptionCW;
    }

    public String createRequestBody(String SymphonyValue, String ConnectWiseValue, String requestPath, boolean isString) {
        /* createRequestBody
             This method returns a string with the correct structure and values to perform the PATCH request based on
            the inputs. The PATCH action used is "replace", using the requestPath input as the path and the
            SymphonyValue as the value that will replace. In case the ConnectWise value is null, the PATCH action is
            changed to add instead of replace.
             Returns null if there is no need for a PATCH request (Symphony and ConnectWise values match) or if
            SymphonyValue is null.

            INPUTS:
                - SymphonyValue: value in Symphony ticket
                - ConnectWiseValue: value in Symphony ticket
                - requestPath: ConnectWise API path for the value, i.e.: "priority/id"
                - isString: Quotes are needed on the API request body if the value is a String
         */

        // Check for null values
        if (SymphonyValue == null) {
            logger.info("createRequestBody: Symphony value {} does not exist",
                    requestPath);

            if (ConnectWiseValue != null && !Objects.equals(ConnectWiseValue, "null")) {
                return "Update Symphony";
            }
            return null; // null if Symphony AND ConnectWise are null because no change is needed
        }
        else if (ConnectWiseValue == null) {
            // ConnectWiseValue will ONLY be null if it does not appear at all on the API GET call
            // For this reason it is safe to use "add" as the operation
            logger.info("createRequestBody: ConnectWise value {} does not exist",
                    requestPath);
            logger.info("createRequestBody: Updating ticket " + requestPath);

            return  " {\n" +
                    "        \"op\": \"add\",\n" +
                    "        \"path\": \"" + requestPath + "\",\n" +
                    "        \"value\": " + (isString ? "\"" + SymphonyValue + "\"" : SymphonyValue) + " \n" +
                    "    }\n";
        }

        String requestBody = null;

        // Check if there is a need for update
        if (!Objects.equals(SymphonyValue, ConnectWiseValue)) {
            logger.info("createRequestBody: Updating ticket " + requestPath);
            requestBody = " {\n" +
                    "        \"op\": \"replace\",\n" +
                    "        \"path\": \"" + requestPath + "\",\n" +
                    "        \"value\": " + (isString ? "\"" + SymphonyValue + "\"" : SymphonyValue) + " \n" +
                    "    }\n";
        } else {
            logger.info("createRequestBody: No update in ticket " + requestPath);
        }

        return requestBody;
    }

    public void setTalConfigService(TalConfigService talConfigService) {
        this.talConfigService = talConfigService;
    }

    public TalConfigService getTalConfigService() {
        return this.talConfigService;
    }

    public void setTalProxy(TalProxy talProxy) {
        this.talProxy = talProxy;
    }

    public TalProxy getTalProxy() {
        return this.talProxy;
    }

    public TicketSystemConfig getConfig() {
        return config;
    }

    public void setConfig(TicketSystemConfig config) {
        this.config = config;
    }
}
