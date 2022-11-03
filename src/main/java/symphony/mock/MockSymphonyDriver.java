package symphony.mock;

import com.avispl.symphony.api.tal.dto.TalTicket;
import com.insightsystems.symphony.tal.sample.SampleTalAdapterImpl;

import java.net.MalformedURLException;

public class MockSymphonyDriver {
    public static void main(String[] args) {

        // --- MOCK ENVIRONMENT SETUP --- //
        MockSymphony Symphony = new MockSymphony();

        // Instance of TalAdapter implementation is created
        SampleTalAdapterImpl talAdapter = new SampleTalAdapterImpl();

        Symphony.setTAL(talAdapter);

        // Symphony injects TalProxy and TalConfigService
        // This is on the SampleTalAdapterImpl constructor (delete after development)

        // Symphony calls init()
        talAdapter.init();


        // --- TEST TICKET UPDATE --- //

        // Get symphony ticket
        TalTicket newTicket = Symphony.getTicket();
        System.out.println("MockSymphonyDriver: Created ticket: " + newTicket);

        // Symphony updates TAL
        TalTicket ThirdPartyTicket = Symphony.updateTal(newTicket);
        System.out.println("MockSymphonyDriver: Complete ticket: " + ThirdPartyTicket);



    }
}
