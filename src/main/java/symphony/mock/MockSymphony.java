package symphony.mock;

import com.avispl.symphony.api.tal.TalAdapter;
import com.avispl.symphony.api.tal.dto.Attachment;
import com.avispl.symphony.api.tal.dto.Comment;
import com.avispl.symphony.api.tal.dto.TalTicket;
import com.avispl.symphony.api.tal.error.TalAdapterSyncException;

import java.lang.reflect.Array;
import java.util.*;

public class MockSymphony{

    TalAdapter TAL;

    public MockSymphony() {

    }

    public void setTAL(TalAdapter TAL) {
        this.TAL = TAL;
    }

    public TalTicket getTicket() {
        // Setting parameters for new ticket
        String symphonyId = "1067758";
        String symphonyLink = "1067758";
        String thirdPartyId = "187562";
        String thirdPartyLink = null;
        String customerId = "";
        String priority = "Minor";
        String status = "Open";
        String subject = "<TEST> Test ticket for TAL";
        String description = "Mock ticket for TAL Adapter test";
        String requester = "lucap@insightsystems.com.au";
        String assignedTo = "lucap@insightsystems.com.au";
        Set< Comment > comments = new HashSet<Comment>();

        Set< Attachment > attachments = new HashSet<Attachment>();

        Map<String, String> extraParams = new HashMap<String, String>();
        // Current date time
        Date date = new Date();
        Long lastModified = date.getTime();

        // Mock comment
        Comment patchComment = new Comment("00000", "262344", "lucap@insightsystems.com.au",
                "TAL Comment PATCH", lastModified);
        Comment postComment = new Comment("00001", null, "lucap@insightsystems.com.au",
                "TAL Comment POST", lastModified);
        Comment postComment2 = new Comment("00003", null, "lucap@insightsystems.com.au",
                "TAL Comment POST 2", lastModified);
        Comment initialDescription = new Comment("00002", "262346", "lucap@insightsystems.com.au",
                "Creating mock ticket for TAL Adapter test", lastModified);

        comments.add(patchComment);
        //comments.add(postComment);
        //comments.add(postComment2);
        comments.add(initialDescription);

        extraParams.put("connectionFailed", "true");

        // Creating new ticket
        TalTicket newTicket = new TalTicket(symphonyId, symphonyLink, thirdPartyId,
                thirdPartyLink, customerId, priority, status, subject,
                description, requester, assignedTo, comments,
                attachments, extraParams, lastModified);

        return newTicket;
    }

    public TalTicket getTicketPATCHMissingInfo() {
        // Setting parameters for new ticket
        String symphonyId = "1067758";
        String symphonyLink = "1067758";
        String thirdPartyId = "1875602";
        String thirdPartyLink = null;
        String customerId = "";
        String priority = null;
        String status = null;
        String subject = null;
        String description = null;
        String requester = null;
        String assignedTo = null;
        Set< Comment > comments = new HashSet<Comment>();
        Set< Attachment > attachments = new HashSet<Attachment>();
        Map<String, String> extraParams = new HashMap<String, String>();
        // Current date time
        Date date = new Date();
        Long lastModified = date.getTime();

        // Creating new ticket
        TalTicket newTicket = new TalTicket(symphonyId, symphonyLink, thirdPartyId,
                thirdPartyLink, customerId, priority, status, subject,
                description, requester, assignedTo, comments,
                attachments, extraParams, lastModified);

        return newTicket;
    }

    public TalTicket getTicket2() {
        // Setting parameters for new ticket
        String symphonyId = "1067759";
        String symphonyLink = "1067759";
        String thirdPartyId = null;
        String thirdPartyLink = null;
        String customerId = "";
        String priority = "Major";
        String status = "Open";
        String subject = "<TEST> NEW Test ticket for TAL";
        String description = "Creating CW ticket through Symphony";
        String requester = "lucap@insightsystems.com.au";
        String assignedTo = "lucap@insightsystems.com.au";
        Set< Comment > comments = new HashSet<Comment>();

        Set< Attachment > attachments = new HashSet<Attachment>();

        Map<String, String> extraParams = new HashMap<String, String>();
        // Current date time
        Date date = new Date();
        Long lastModified = date.getTime();

        // Mock comment
        Comment postComment = new Comment("00001", null, null,
                "TAL NEW POST Test", lastModified);

        comments.add(postComment);


        // Creating new ticket
        return new TalTicket(symphonyId, symphonyLink, thirdPartyId,
                thirdPartyLink, customerId, priority, status, subject,
                description, requester, assignedTo, comments,
                attachments, extraParams, lastModified);
    }

    public TalTicket getTicketPOSTMissingInfo() {
        // Setting parameters for new ticket
        String symphonyId = "1067759";
        String symphonyLink = "1067759";
        String thirdPartyId = null;
        String thirdPartyLink = null;
        String customerId = null;
        String priority = null;
        String status = null;
        String subject = null;
        String description = null;
        String requester = null;
        String assignedTo = null;
        Set< Comment > comments = new HashSet<Comment>();

        Set< Attachment > attachments = new HashSet<Attachment>();

        Map<String, String> extraParams = new HashMap<String, String>();
        // Current date time
        Date date = new Date();
        Long lastModified = date.getTime();

        // Mock comment
        Comment postComment = new Comment("00001", null, "lucap@insightsystems.com.au",
                "TAL NEW POST Test", lastModified);

        comments.add(postComment);

        // Creating new ticket
        return new TalTicket(symphonyId, symphonyLink, thirdPartyId,
                thirdPartyLink, customerId, priority, status, subject,
                description, requester, assignedTo, comments,
                attachments, extraParams, lastModified);
    }


    public TalTicket updateTal(TalTicket ticket) {
        TalTicket ticketToReturn = null;

        if (TAL != null) {
            try {
                // capture returned ticket for testing purposes
                ticketToReturn = TAL.syncTalTicket(ticket);

            } catch (TalAdapterSyncException e) {
                System.out.println("SampleTalAdapterImpl was unable to retrieve " +
                        "configuration from TalConfigService: " + e.getMessage());
            }
        } else {
            throw new NullPointerException("TAL variable not set");
        }

        return ticketToReturn;
    }
}
