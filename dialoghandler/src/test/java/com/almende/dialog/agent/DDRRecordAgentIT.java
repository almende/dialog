package com.almende.dialog.agent;

import static org.junit.Assert.assertThat;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.ws.rs.core.UriInfo;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.MBSmsServlet;
import com.almende.dialog.adapter.MailServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
import com.almende.dialog.adapter.XMPPServlet;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.TypeUtil;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.utils.PhoneNumberUtils;

/**
 * Has all the integration tests (might be unit tests too, but they need a
 * servlet running)
 * 
 * @author Shravan
 * 
 */
@Category(IntegrationTest.class)
public class DDRRecordAgentIT extends TestFramework {

    private static final String DDR_ADAPTER_PRICE_KEY = "DDR_ADAPTER_PRICE";
    private static final String DDR_COMMUNICATION_PRICE_KEY = "DDR_COMMUNICATION_PRICE";
    private static final String ADAPTER_ID_KEY = "ADAPTER_ID";
    private static final String ACCOUNT_ID_KEY = "ACCOUNT_ID";
    private static final String TEST_ACCOUNTID = UUID.randomUUID().toString();
    
    /**
     * check if a ddr created for a PRE_PAID accounts stays the same. cost is
     * saved in the ddrs and not calculated at request time.
     * 
     * @throws Exception
     */
    @Test
    public void DDRStaysConsistentForPrePaidAccountTest() throws Exception {

        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAddressEmail, "Test");
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound(UnitType.PART, AdapterType.EMAIL,
                                                                                 "test", addressNameMap, false,
                                                                                 AccountType.PRE_PAID);

        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId(resultMap.get(ACCOUNT_ID_KEY));
        assertThat(allDdrRecords.size(), Matchers.is(2));

        int assertCount = 0;
        for (DDRRecord ddrRecord : allDdrRecords) {
            if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_COMMUNICATION_PRICE_KEY))) {
                assertThat(ddrRecord.getFromAddress(), Matchers.is(MailServlet.DEFAULT_SENDER_EMAIL));
                assertThat(ddrRecord.getToAddress(), Matchers.is(addressNameMap));
                assertThat(ddrRecord.getStatusForAddress(remoteAddressEmail), Matchers.is(CommunicationStatus.SENT));
                assertCount++;
            }
            else if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_ADAPTER_PRICE_KEY))) {
                assertCount++;
            }
        }
        assertThat(assertCount, Matchers.is(2));
        //create a new DDRPrice in the same time range as the one existing.
        List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(resultMap.get(DDR_COMMUNICATION_PRICE_KEY), null, null, null,
                                                         null);
        DDRPrice sameDateRangeDDRPrice = getTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.75,
                                                         "new ddrPrice in the same time range", UnitType.PART,
                                                         AdapterType.EMAIL, null);
        assertThat(ddrPrices.size(), Matchers.is(1));
        //update sameDateRange ddr price with above timestamp
        sameDateRangeDDRPrice.setStartTime(ddrPrices.iterator().next().getStartTime());
        sameDateRangeDDRPrice.setEndTime(ddrPrices.iterator().next().getEndTime());
        sameDateRangeDDRPrice.createOrUpdate();

        //fetch the ddrRecords again!
        allDdrRecords = getDDRRecordsByAccountId(resultMap.get(ACCOUNT_ID_KEY));
        assertThat(allDdrRecords.size(), Matchers.is(2));
        assertCount = 0;
        for (DDRRecord ddrRecord : allDdrRecords) {
            if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_COMMUNICATION_PRICE_KEY))) {
                ddrRecord.setShouldGenerateCosts(true);
                assertThat(ddrRecord.getTotalCost(), Matchers.is(0.5));
                assertThat(ddrRecord.getFromAddress(), Matchers.is(MailServlet.DEFAULT_SENDER_EMAIL));
                assertThat(ddrRecord.getToAddress(), Matchers.is(addressNameMap));
                assertThat(ddrRecord.getStatusForAddress(remoteAddressEmail), Matchers.is(CommunicationStatus.SENT));
                assertCount++;
            }
            else if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_ADAPTER_PRICE_KEY))) {
                assertCount++;
            }
        }
        assertThat(assertCount, Matchers.is(2));
    }
    
    /**
     * check if a ddr is created but only service costs are attached for a PRIVATE adapter. 
     * 
     * @throws Exception
     */
    @Test
    public void ddrHasOnlyServiceCostsForPrivateAdatperTest() throws Exception {

        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAddressEmail, "Test");
        double serviceCost = 0.10101;
        getTestDDRPrice(DDRTypeCategory.SERVICE_COST, serviceCost, "Test service cost", UnitType.PART, null, null);
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound(UnitType.PART, AdapterType.EMAIL,
                                                                                 "test", addressNameMap, true,
                                                                                 AccountType.POST_PAID);

        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId(resultMap.get(ACCOUNT_ID_KEY));
        assertThat(allDdrRecords.size(), Matchers.is(2));

        int assertCount = 0;
        for (DDRRecord ddrRecord : allDdrRecords) {
            if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_COMMUNICATION_PRICE_KEY))) {
                assertThat(ddrRecord.getFromAddress(), Matchers.is(MailServlet.DEFAULT_SENDER_EMAIL));
                assertThat(ddrRecord.getToAddress(), Matchers.is(addressNameMap));
                assertThat(ddrRecord.getStatusForAddress(remoteAddressEmail), Matchers.is(CommunicationStatus.SENT));
                ddrRecord.setShouldGenerateCosts(true);
                ddrRecord.setShouldIncludeServiceCosts(true);
                assertThat(ddrRecord.getTotalCost(),
                           Matchers.is(DDRUtils.getCeilingAtPrecision(serviceCost, 3)));
                assertCount++;
            }
            else if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_ADAPTER_PRICE_KEY))) {
                assertCount++;
            }
        }
        assertThat(assertCount, Matchers.is(2));
    }
    
    /**
     * check if a ddr is created but only service costs are attached for a
     * PRIVATE trial account adapter.
     * 
     * @throws Exception
     */
    @Test
    public void ddrHasOnlyServiceCostsForPrivateTrialAdatperTest() throws Exception {

        ddrHasOnlyServiceCostsForPrivateTrialAdatperTest(AccountType.TRIAL);
    }
    
    /**
     * check if a ddr is created but only service costs are attached for a
     * PRIVATE prepaid account adapter.
     * 
     * @throws Exception
     */
    @Test
    public void ddrHasOnlyServiceCostsForPrivatePrepaidAdatperTest() throws Exception {

        ddrHasOnlyServiceCostsForPrivateTrialAdatperTest(AccountType.PRE_PAID);
    }
    
    /**
     * check if a ddr is created but only service costs are attached for a
     * PRIVATE prepaid account adapter.
     * 
     * @throws Exception
     */
    @Test
    public void ddrHasOnlyServiceCostsForPrivatePostpaidAdatperTest() throws Exception {

        ddrHasOnlyServiceCostsForPrivateTrialAdatperTest(AccountType.POST_PAID);
    }
    
    /**
     * check if a ddr is created but only service costs are attached for a
     * PRIVATE given account type
     * 
     * @throws Exception
     */
    public void ddrHasOnlyServiceCostsForPrivateTrialAdatperTest(AccountType type) throws Exception {

        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAddressEmail, "Test");
        double serviceCost = 0.10101;
        getTestDDRPrice(DDRTypeCategory.SERVICE_COST, serviceCost, "Test service cost", UnitType.PART, null, null);
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound(UnitType.PART, AdapterType.EMAIL,
                                                                                 "test", addressNameMap, true, type);

        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId(resultMap.get(ACCOUNT_ID_KEY));
        assertThat(allDdrRecords.size(), Matchers.is(2));

        int assertCount = 0;
        for (DDRRecord ddrRecord : allDdrRecords) {
            if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_COMMUNICATION_PRICE_KEY))) {
                assertThat(ddrRecord.getFromAddress(), Matchers.is(MailServlet.DEFAULT_SENDER_EMAIL));
                assertThat(ddrRecord.getToAddress(), Matchers.is(addressNameMap));
                assertThat(ddrRecord.getStatusForAddress(remoteAddressEmail), Matchers.is(CommunicationStatus.SENT));
                if (type.equals(AccountType.POST_PAID)) {
                    ddrRecord.setShouldGenerateCosts(true);
                    ddrRecord.setShouldIncludeServiceCosts(true);
                }
                assertThat(ddrRecord.getTotalCost(),
                           Matchers.is(DDRUtils.getCeilingAtPrecision(serviceCost, 3)));
                assertCount++;
            }
            else if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_ADAPTER_PRICE_KEY))) {
                assertCount++;
            }
        }
        assertThat(assertCount, Matchers.is(2));
    }
    
    /**
     * check if initiating an outgoing SMS will create a ddr record and add all its costs correctly
     * @throws Exception
     */
    @Test
    public void outgoingSMSCallAddsADDRRecordTest() throws Exception
    {
        //create a dummy message which is above 160chars length
        String message = URLEncoder.encode(
                "Gunaydin! Bugun yapilacak olan Hollanda yerel secimlerinde oylarimizi kullanalim! Delftte ben Sinan Ozkaya daha fazla is imkanlari ve esit hak ve ozgurlukler icin adayim. Gelecek donemde birlikte olmak uzere!  (GroenLinks, Liste 5, no3)",
                "UTF-8" );
        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressVoice, "Test" );
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound(UnitType.PART, AdapterType.SMS,
                                                                                 message, addressNameMap, false,
                                                                                 AccountType.PRE_PAID);
        
        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( TEST_ACCOUNTID );
        assertThat( allDdrRecords.size(), Matchers.is( 2 ) );

        double totalCost = 0.0;
        int assertCount = 0;
        for ( DDRRecord ddrRecord : allDdrRecords )
        {
            Double ddrCost = DDRUtils.calculateDDRCost( ddrRecord );
            totalCost += ddrCost;
            assertThat( ddrRecord.getAccountId(), Matchers.is( resultMap.get( ACCOUNT_ID_KEY ) ) );
            assertThat( ddrRecord.getAdapterId(), Matchers.is( resultMap.get( ADAPTER_ID_KEY ) ) );
            if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_COMMUNICATION_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 1.0 ) );
                assertThat( ddrRecord.getQuantity(), Matchers.is( 2 ) );
                assertThat( ddrRecord.getFromAddress(), Matchers.is( "Test Customer" ) );
                for (String address : addressNameMap.keySet()) {
                    address = PhoneNumberUtils.formatNumber(address, null);
                    assertThat( ddrRecord.getToAddress().get(address), Matchers.is("Test") );
                }
                assertThat(ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
                           Matchers.is(CommunicationStatus.SENT));
                assertCount ++;
            }
            else if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_ADAPTER_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 10.0 ) );
                assertCount++;
            }
        }
        assertThat( assertCount, Matchers.is( 2 ) );
        assertThat( totalCost, Matchers.is( 11.0 ) );
    }
    
    /**
     * check if initiating an outgoing CALL will create a ddr record and add all
     * its costs correctly.
     * 
     * @throws Exception
     */
    @Test
    public void outgoingPHONECallAddsADDRRecordTest() throws Exception {

        String remoteAddressVoice = PhoneNumberUtils.formatNumber(TestFramework.remoteAddressVoice, null);
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "Test");
        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAddressVoice, "");
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound(UnitType.MINUTE, AdapterType.CALL,
                                                                                 url, addressNameMap, false,
                                                                                 AccountType.POST_PAID);

        //check if a ddr record is created
        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId(resultMap.get(ACCOUNT_ID_KEY));
        //initially only one record is created corresponding to the adapter creation
        assertThat(allDdrRecords.size(), Matchers.is(2));
        double totalCost = 0.0;
        int assertCount = 0;
        for (DDRRecord ddrRecord : allDdrRecords) {
            ddrRecord.setShouldGenerateCosts(true);
            ddrRecord.setShouldIncludeServiceCosts(true);
            assertThat(ddrRecord.getAccountId(), Matchers.is(resultMap.get(ACCOUNT_ID_KEY)));
            assertThat(ddrRecord.getAdapterId(), Matchers.is(resultMap.get(ADAPTER_ID_KEY)));
            assertThat(ddrRecord.getQuantity(), Matchers.is(1));
            Double ddrCost = DDRUtils.calculateDDRCost(ddrRecord);
            totalCost += ddrCost;
            if (ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.ADAPTER_PURCHASE)) {
                assertThat(ddrCost, Matchers.is(10.0));
                assertCount++;
            }
            else if (ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.OUTGOING_COMMUNICATION_COST)) {
                assertThat(ddrCost, Matchers.is(0.0));
                assertCount++;
            }
        }
        assertThat(assertCount, Matchers.is(2));
        totalCost = 0.0;
        assertCount = 0;
        //mimick behaviour or outbound calling being triggered and answered
        UriInfo uri = Mockito.mock(UriInfo.class);
        Mockito.when(uri.getBaseUri()).thenReturn(new URI("http://localhost:8082/dialoghandler/vxml/new"));
        //-----------------------------------------------------------------------
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        voiceXMLRESTProxy.getNewDialog("outbound", remoteAddressVoice, remoteAddressVoice, localAddressBroadsoft +
            "@ask.ask.voipit.nl", uri);
        allDdrRecords = getDDRRecordsByAccountId(resultMap.get(ACCOUNT_ID_KEY));
        for (DDRRecord ddrRecord : allDdrRecords) {
            ddrRecord.setShouldGenerateCosts(true);
            ddrRecord.setShouldIncludeServiceCosts(true);
            Double ddrCost = DDRUtils.calculateDDRCost(ddrRecord);
            totalCost += ddrCost;
            assertThat(ddrRecord.getAccountId(), Matchers.is(resultMap.get(ACCOUNT_ID_KEY)));
            assertThat(ddrRecord.getAdapterId(), Matchers.is(resultMap.get(ADAPTER_ID_KEY)));
            if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_COMMUNICATION_PRICE_KEY))) {
                assertThat(ddrCost, Matchers.is(0.0));
                assertThat(ddrRecord.getQuantity(), Matchers.is(1));
                assertThat(ddrRecord.getFromAddress(), Matchers.is(localAddressBroadsoft + "@ask.ask.voipit.nl"));
                assertThat(ddrRecord.getToAddress(), Matchers.is(addressNameMap));
                assertThat(ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
                           Matchers.is(CommunicationStatus.SENT));
                assertCount++;
            }
            else if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_ADAPTER_PRICE_KEY))) {
                assertThat(ddrCost, Matchers.is(10.0));
                assertCount++;
            }
        }
        //hangup the call after 5 mins
        //send hangup ccxml with an answerTime
        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(resultMap.get(ADAPTER_ID_KEY));
        adapterConfig.setXsiSubscription(UUID.randomUUID().toString());
        adapterConfig.update();
        String hangupXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Event xmlns=\"http://schema.broadsoft.com/xsi-events\" " +
            "xmlns:xsi1=\"http://www.w3.org/2001/XMLSchema-instance\"><sequenceNumber>257</sequenceNumber><subscriberId>" +
            localAddressBroadsoft +
            "@ask.ask.voipit.nl</subscriberId>" +
            "<applicationId>cc</applicationId><subscriptionId>" +
            adapterConfig.getXsiSubscription() +
            "</subscriptionId><eventData xsi1:type=\"xsi:CallEvent\" xmlns:xsi=" +
            "\"http://schema.broadsoft.com/xsi-events\"><eventName>CallSessionEvent</eventName><call><callId>callhalf-12914560105:1</callId><extTrackingId>" +
            "10669651:1</extTrackingId><personality>Originator</personality><callState>Released</callState><releaseCause>Temporarily Unavailable</releaseCause>" +
            "<remoteParty><address>tel:" +
            remoteAddressVoice +
            "</address><callType>Network</callType></remoteParty><startTime>1401809063943</startTime>" +
            "<answerTime>1401809070192</answerTime><releaseTime>1401809370000</releaseTime></call></eventData></Event>";
        voiceXMLRESTProxy.receiveCCMessage(hangupXML);
        allDdrRecords = getDDRRecordsByAccountId(resultMap.get(ACCOUNT_ID_KEY));
        assertCount = 0;
        totalCost = 0;
        for (DDRRecord ddrRecord : allDdrRecords) {
            ddrRecord.setShouldGenerateCosts(true);
            ddrRecord.setShouldIncludeServiceCosts(true);
            Double ddrCost = DDRUtils.calculateDDRCost(ddrRecord);
            totalCost += ddrCost;
            assertThat(ddrRecord.getAccountId(), Matchers.is(resultMap.get(ACCOUNT_ID_KEY)));
            assertThat(ddrRecord.getAdapterId(), Matchers.is(resultMap.get(ADAPTER_ID_KEY)));
            if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_COMMUNICATION_PRICE_KEY))) {
                assertThat(ddrCost, Matchers.is(2.5));
                assertThat(ddrRecord.getQuantity(), Matchers.is(1));
                assertThat(ddrRecord.getFromAddress(), Matchers.is("0854881000@ask.ask.voipit.nl"));
                assertThat(ddrRecord.getToAddress(), Matchers.is(addressNameMap));
                assertThat(ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
                           Matchers.is(CommunicationStatus.FINISHED));
                assertCount++;
            }
            else if (ddrRecord.getDdrTypeId().equals(resultMap.get(DDR_ADAPTER_PRICE_KEY))) {
                assertThat(ddrCost, Matchers.is(10.0));
                assertCount++;
            }
        }
        assertThat(assertCount, Matchers.is(2));
        assertThat(totalCost, Matchers.is(12.5));
    }
    
    /**
     * check if initiating an outgoing call will create a ddr record Ignore this
     * in a maven test build as it will send emails automatically
     * 
     * @throws Exception
     */
    @SuppressWarnings("deprecation")
    @Test
    @Ignore
    public void outgoingEMAILCallAddsADDRRecordTest() throws Exception
    {
        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressEmail, "Test" );
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound(UnitType.PART, AdapterType.EMAIL,
                                                                                 "test", addressNameMap, false,
                                                                                 AccountType.POST_PAID);

        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( resultMap.get( ACCOUNT_ID_KEY ) );
        assertThat( allDdrRecords.size(), Matchers.is( 2 ) );

        double totalCost = 0.0;
        int assertCount = 0;
        for ( DDRRecord ddrRecord : allDdrRecords )
        {
            Double ddrCost = DDRUtils.calculateDDRCost( ddrRecord );
            totalCost += ddrCost;
            assertThat( ddrRecord.getAccountId(), Matchers.is( resultMap.get( ACCOUNT_ID_KEY ) ) );
            assertThat( ddrRecord.getAdapterId(), Matchers.is( resultMap.get( ADAPTER_ID_KEY ) ) );
            if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_COMMUNICATION_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 0.5 ) );
                assertThat( ddrRecord.getFromAddress(), Matchers.is( MailServlet.DEFAULT_SENDER_EMAIL ) );
                assertThat( ddrRecord.getToAddress(), Matchers.is( addressNameMap ) );
                assertThat( ddrRecord.getStatus(), Matchers.is( CommunicationStatus.SENT ) );
                assertThat(ddrRecord.getAdditionalInfo().get(Session.SESSION_KEY), Matchers.notNullValue());
                assertCount++;
            }
            else if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_ADAPTER_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 10.0 ) );
                assertCount++;
            }
        }
        assertThat( assertCount, Matchers.is( 2 ) );
        assertThat( totalCost, Matchers.is( 10.5 ) );
    }
    
    /**
     * this tests the edge case, when a call is answered, but the hangup xml is received before the answerxml.
     * Assert that the session is not deleted and added to the queue. And post processing is done.
     * @throws Exception 
     */
    @SuppressWarnings("deprecation")
    @Test
    public void outgoingCallPickupAndImmediateHangupTest() throws Exception{
        String formattedRemoteAddressVoice = PhoneNumberUtils.formatNumber( remoteAddressVoice, null );
        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( formattedRemoteAddressVoice, "" );
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", "Test");
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound(UnitType.MINUTE, AdapterType.CALL,
                                                                                 url, addressNameMap, false,
                                                                                 AccountType.PRE_PAID);
        
        //check if a ddr record is created
        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( resultMap.get( ACCOUNT_ID_KEY ) );
        //initially only one record is created corresponding to the adapter creation
        assertThat( allDdrRecords.size(), Matchers.is( 2 ) );
        double totalCost = 0.0;
        int assertCount = 0;
        for ( DDRRecord ddrRecord : allDdrRecords )
        {
            assertThat(ddrRecord.getAccountId(), Matchers.is(resultMap.get(ACCOUNT_ID_KEY)));
            assertThat(ddrRecord.getAdapterId(), Matchers.is(resultMap.get(ADAPTER_ID_KEY)));
            assertThat(ddrRecord.getQuantity(), Matchers.is(1));
            Double ddrCost = DDRUtils.calculateDDRCost(ddrRecord);
            totalCost += ddrCost;
            if (ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.ADAPTER_PURCHASE)) {
                assertThat(ddrCost, Matchers.is(10.0));
                assertCount++;
            }
            else if(ddrRecord.getDdrType().getCategory().equals(DDRTypeCategory.OUTGOING_COMMUNICATION_COST)) {
                assertThat(ddrCost, Matchers.is(0.0));
                assertCount++;
            }
        }
        assertThat( totalCost, Matchers.is( 10.0 ) );
        assertThat(assertCount, Matchers.is(2));

        //mimick behaviour or outbound calling being triggered and answered
        UriInfo uri = Mockito.mock( UriInfo.class );
        Mockito.when( uri.getBaseUri() ).thenReturn( new URI( "http://localhost:8082/dialoghandler/vxml/new" ) );
        //-----------------------------------------------------------------------
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        voiceXMLRESTProxy.getNewDialog( "outbound", formattedRemoteAddressVoice, formattedRemoteAddressVoice, localAddressBroadsoft + "@ask.ask.voipit.nl", uri );
        allDdrRecords = getDDRRecordsByAccountId( resultMap.get( ACCOUNT_ID_KEY ) );
        for ( DDRRecord ddrRecord : allDdrRecords )
        {
            Double ddrCost = DDRUtils.calculateDDRCost( ddrRecord );
            assertThat( ddrRecord.getAccountId(), Matchers.is( resultMap.get( ACCOUNT_ID_KEY ) ) );
            assertThat( ddrRecord.getAdapterId(), Matchers.is( resultMap.get( ADAPTER_ID_KEY ) ) );
            if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_COMMUNICATION_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 0.0 ) );
                assertThat( ddrRecord.getQuantity(), Matchers.is( 1 ) );
                assertThat( ddrRecord.getFromAddress(), Matchers.is( localAddressBroadsoft + "@ask.ask.voipit.nl" ) );
                assertThat( ddrRecord.getToAddress(), Matchers.is( addressNameMap ) );
                assertThat( ddrRecord.getStatus(), Matchers.is( CommunicationStatus.SENT ) );
            }
            else if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_ADAPTER_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 10.0 ) );
            }
        }
        //assert that a session exists
        Session session = Session.getSessionByInternalKey(AdapterType.CALL.toString(), localAddressBroadsoft +
                                                                                       "@ask.ask.voipit.nl",
                                                          formattedRemoteAddressVoice);
        assertThat(session, Matchers.notNullValue());
        assertThat(session.getStartTimestamp(), Matchers.notNullValue());
        assertThat(session.getAnswerTimestamp(), Matchers.nullValue());
        assertThat(session.getReleaseTimestamp(), Matchers.nullValue());
        assertThat(session.getCreationTimestamp(), Matchers.notNullValue());

        //start the session agent
        SessionAgent sessionAgent = new SessionAgent();
        sessionAgent.onInit();
        
        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(resultMap.get(ADAPTER_ID_KEY));
        adapterConfig.setXsiSubscription(UUID.randomUUID().toString());
        adapterConfig.update();
        
        //send hangup ccxml without a answerTime
        String hangupXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Event xmlns=\"http://schema.broadsoft.com/xsi-events\" " +
                           "xmlns:xsi1=\"http://www.w3.org/2001/XMLSchema-instance\"><sequenceNumber>257" +
                           "</sequenceNumber><subscriberId>" +
                           localAddressBroadsoft +
                           "@ask.ask.voipit.nl</subscriberId>" +
                           "<applicationId>cc</applicationId><subscriptionId>" +
                           adapterConfig.getXsiSubscription() +
                           "</subscriptionId><eventData xsi1:type=\"xsi:CallEvent\" xmlns:xsi=\"http://schema.broadsoft.com/xsi-events\">" +
                           "<eventName>CallSessionEvent</eventName><call><callId>callhalf-12914560105:1</callId>" +
                           "<extTrackingId>10669651:1</extTrackingId><personality>Originator</personality><callState>Released" +
                           "</callState><releaseCause>Temporarily Unavailable</releaseCause><remoteParty><address>tel:" +
                           remoteAddressVoice +
                           "</address><callType>Network</callType></remoteParty>" +
                           "<startTime>1401809063943</startTime><releaseTime>1401809070192</releaseTime></call></eventData></Event>";

        voiceXMLRESTProxy.receiveCCMessage(hangupXML);
        //assert that a session still exists
        session = Session.getSessionByInternalKey(AdapterAgent.ADAPTER_TYPE_CALL, localAddressBroadsoft +
                                                                                  "@ask.ask.voipit.nl",
                                                  formattedRemoteAddressVoice);
//        assertThat(session, Matchers.notNullValue());
//        assertThat(session.getStartTimestamp(), Matchers.notNullValue());
//        assertThat(session.getAnswerTimestamp(), Matchers.nullValue());
//        assertThat(session.getReleaseTimestamp(), Matchers.notNullValue());
//        assertThat(session.getCreationTimestamp(), Matchers.notNullValue());

//        //send answer ccxml
//        String answerXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Event xmlns=\"http://schema.broadsoft.com/xsi-events\" xmlns:xsi1=" +
//                           "\"http://www.w3.org/2001/XMLSchema-instance\"><sequenceNumber>246</sequenceNumber><subscriberId>" +
//                           localAddressBroadsoft +
//                           "@ask.ask.voipit.nl</subscriberId><applicationId>cc</applicationId><subscriptionId>" +
//                           adapterConfig.getXsiSubscription() +
//                           "</subscriptionId><eventData xsi1:type=\"xsi:CallEvent\" xmlns:xsi=\"http://schema.broadsoft.com/xsi-events\">" +
//                           "<eventName>CallSessionEvent</eventName><call><callId>callhalf-12914431715:1</callId><extTrackingId>10668830:1" +
//                           "</extTrackingId><personality>Originator</personality><callState>Active</callState><remoteParty><address>tel:" +
//                           remoteAddressVoice +
//                           "</address><callType>Network</callType></remoteParty><addressOfRecord>0854881000@ask.ask.voipit.nl</addressOfRecord>" +
//                           "<endPoint xsi1:type=\"xsi:AccessEndpoint\"><addressOfRecord>0854881000@ask.ask.voipit.nl</addressOfRecord></endPoint>" +
//                           "<appearance>2</appearance><startTime>1401809063943</startTime><answerTime>1401809061002</answerTime></call></eventData>" +
//                           "</Event>";
//        voiceXMLRESTProxy.receiveCCMessage(answerXML);
        
        //force start the processing of Session
//        sessionAgent.postProcessSessions(session.getKey());

        //assert that a session doesnt exist and is processed
        Collection<Session> sessions = Session.getAllSessions();
        assertThat(sessions, Matchers.emptyCollectionOf(Session.class));
        
        //check that all ddrs are processed
        Collection<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(null,resultMap.get( ACCOUNT_ID_KEY ), null, null, null, null, null, null, null, null);
        assertThat(ddrRecords.size(), Matchers.is(2));
        for (DDRRecord ddrRecord : ddrRecords) {
            
            DDRType ddrType = DDRType.getDDRType(ddrRecord.getDdrTypeId());
            if (DDRTypeCategory.INCOMING_COMMUNICATION_COST.equals(ddrType.getCategory()) ||
                DDRTypeCategory.OUTGOING_COMMUNICATION_COST.equals(ddrType.getCategory())) {
                
                ddrRecord.setShouldGenerateCosts(true);
                assertThat(ddrRecord, Matchers.notNullValue());
                assertThat(ddrRecord.getStart(), Matchers.notNullValue());
                assertThat(ddrRecord.getDuration(), Matchers.is(0L));
                assertThat(ddrRecord.getTotalCost(), Matchers.is(0.0));
                assertThat(ddrRecord.getStatusForAddress(formattedRemoteAddressVoice),
                           Matchers.is(CommunicationStatus.MISSED));
            }
        }
    }

    private Map<String, String> createDDRPricesAndAdapterAndSendOutBound(UnitType unitType, AdapterType adapterType,
        String message, Map<String, String> addressNameMap, boolean isPrivate, AccountType type) throws Exception {

        AdapterAgent adapterAgent = new AdapterAgent();
        //create a ddr price and type
        DDRPrice ddrPriceForCommunication = getTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.5, "Test",
                                                            unitType, adapterType, null);
        assertThat(ddrPriceForCommunication.getDdrTypeId(), Matchers.notNullValue());

        //create an adapter 
        DDRPrice ddrPriceForAdapterPurchase = getTestDDRPrice(DDRTypeCategory.ADAPTER_PURCHASE, 10.0, "Test",
                                                              UnitType.PART, adapterType, null);
        assertThat(ddrPriceForAdapterPurchase.getDdrTypeId(), Matchers.notNullValue());
        String adapterId = null;
        AdapterConfig adapterConfig = null;
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", message);
        message = message.startsWith("http") ? message : url;
        switch (adapterType) {
            case SMS:
                adapterId = adapterAgent.createMBAdapter("TEST", "TEST", "1111|TEST", "test", null, TEST_ACCOUNTID,
                                                         null, null);
                adapterConfig = AdapterConfig.getAdapterConfig(adapterId);

                adapterConfig.setAccountType(type);
                updateAdapterAsPrivate(isPrivate, adapterConfig);
                //check if a ddr record is created by sending an outbound email
                new MBSmsServlet().startDialog(addressNameMap, null, null, message, "Test Customer", "Test subject",
                                               adapterConfig, adapterConfig.getOwner());
                break;
            case EMAIL:
                adapterId = adapterAgent.createEmailAdapter(MailServlet.DEFAULT_SENDER_EMAIL,
                                                            MailServlet.DEFAULT_SENDER_EMAIL_PASSWORD, "Test", null,
                                                            null, null, null, null, null, TEST_ACCOUNTID, null, null,
                                                            null);
                adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
                adapterConfig.setAccountType(type);
                updateAdapterAsPrivate(isPrivate, adapterConfig);
                new MailServlet().startDialog(addressNameMap, null, null, message, "Test Customer", "Test subject",
                                              adapterConfig, adapterConfig.getOwner());
                break;
            case XMPP:
                adapterId = adapterAgent.createXMPPAdapter(MailServlet.DEFAULT_SENDER_EMAIL,
                                                           MailServlet.DEFAULT_SENDER_EMAIL_PASSWORD, "test", null,
                                                           null, null, null, TEST_ACCOUNTID, null, null, null);
                adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
                adapterConfig.setAccountType(type);
                updateAdapterAsPrivate(isPrivate, adapterConfig);
                new XMPPServlet().startDialog(addressNameMap, null, null, message, "Test Customer", "Test subject",
                                              adapterConfig, adapterConfig.getOwner());
                break;
            case CALL:
                adapterId = adapterAgent.createBroadSoftAdapter(localAddressBroadsoft, null, "askask", null,
                                                                TEST_ACCOUNTID, false, null, null);
                adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
                adapterConfig.setAccountType(type);
                adapterConfig.setXsiSubscription(TEST_PUBLIC_KEY);
                updateAdapterAsPrivate(isPrivate, adapterConfig);
                adapterConfig.update();
                VoiceXMLRESTProxy.dial(addressNameMap, message, adapterConfig, adapterConfig.getOwner(), null);
                break;
            default:
                break;
        }
        HashMap<String, String> result = new HashMap<String, String>();
        result.put(ACCOUNT_ID_KEY, TEST_ACCOUNTID);
        result.put(ADAPTER_ID_KEY, adapterId);
        result.put(DDR_ADAPTER_PRICE_KEY, ddrPriceForAdapterPurchase.getDdrTypeId());
        result.put(DDR_COMMUNICATION_PRICE_KEY, ddrPriceForCommunication.getDdrTypeId());
        return result;
    }
    
    /**
     * @param name
     * @param unitType
     * @return
     * @throws Exception
     */
    protected DDRPrice getTestDDRPrice( DDRTypeCategory category, double price, String name, UnitType unitType,
        AdapterType adapterType, String adapterId ) throws Exception
    {

        DDRRecordAgent ddrRecordAgent = new DDRRecordAgent();
        Object ddrPriceObject = ddrRecordAgent.createDDRPriceWithNewDDRType(name, category.name(),
                                                                      TimeUtils.getServerCurrentTimeInMillis(),
                                                                      TimeUtils.getCurrentServerTimePlusMinutes(100),
                                                                      price, 0, 10, 1, unitType.name(),
                                                                      adapterType != null ? adapterType.name() : null,
                                                                      adapterId, null);
        TypeUtil<DDRPrice> injector = new TypeUtil<DDRPrice>(){};
        return injector.inject( ddrPriceObject );
    }
    
    /** get ddr records for this accountId
     * @param resultMap
     * @return
     * @throws Exception
     */
    private static Collection<DDRRecord> getDDRRecordsByAccountId(String accountId) throws Exception {

        Object ddrRecords = new DDRRecordAgent().getDDRRecords(null, accountId, null, null, null, null, null, null,
                                                               null, null, null, null);
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>() {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject(ddrRecords);
        return allDdrRecords;
    }
    
    private void updateAdapterAsPrivate(Boolean isPrivate, AdapterConfig adapter) {

        if (isPrivate != null && isPrivate) {
            adapter.markAsPrivate();
        }
        adapter.update();
    }
}
