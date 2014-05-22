
package com.almende.dialog.agent;


import static org.junit.Assert.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.UriInfo;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.Mockito;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.MBSmsServlet;
import com.almende.dialog.adapter.MailServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
import com.almende.dialog.adapter.XMPPServlet;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.AdapterType;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.PhoneNumberUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.TypeUtil;


public class DDRRecordAgentTest extends TestFramework
{
    DDRRecordAgent ddrRecordAgent = new DDRRecordAgent();
    AdapterAgent adapterAgent = new AdapterAgent();
    private static final String DDR_ADAPTER_PRICE_KEY = "DDR_ADAPTER_PRICE";
    private static final String DDR_COMMUNICATION_PRICE_KEY = "DDR_COMMUNICATION_PRICE";
    private static final String ADAPTER_ID_KEY = "ADAPTER_ID";
    private static final String ACCOUNT_ID_KEY = "ACCOUNT_ID";
    private static final String TEST_ACCOUNTID = UUID.randomUUID().toString();

    /**
     * test if there cant be two DDRTypes with the same category possible to
     * create.
     * 
     * @throws Exception
     */
    @Test
    public void checkMultipleDDRTypeWithTheSameCategoryTest() throws Exception
    {
        Object ddrTypeObject = ddrRecordAgent.createDDRType( "Test DDRType", DDRTypeCategory.ADAPTER_PURCHASE.name() );
        TypeUtil<DDRType> injector = new TypeUtil<DDRType>()
        {
        };
        DDRType ddrType = injector.inject( ddrTypeObject );
        assertThat( ddrType.getTypeId(), Matchers.notNullValue() );

        //create another DDTYpe with the same category
        ddrRecordAgent.createDDRType( "Test DDRType1", DDRTypeCategory.ADAPTER_PURCHASE.name() );
        Object allDDRTypes = ddrRecordAgent.getAllDDRTypes();
        TypeUtil<Collection<DDRType>> typesInjector = new TypeUtil<Collection<DDRType>>()
        {
        };
        Collection<DDRType> allDdrTypes = typesInjector.inject( allDDRTypes );
        assertThat( allDdrTypes.size(), Matchers.is( 1 ) );
        ddrType = allDdrTypes.iterator().next();
        assertThat( ddrType.getTypeId(), Matchers.notNullValue() );
        assertThat( ddrType.getName(), Matchers.is( "Test DDRType1" ) );
    }

    /**
     * check if purchasing an adapter will charge the account
     * 
     * @throws Exception
     */
    @Test
    public void adapterPurchaseTest() throws Exception
    {
        DDRPrice ddrPrice = getTestDDRPrice( DDRTypeCategory.ADAPTER_PURCHASE, 10.0, "Test", UnitType.PART,
            AdapterType.EMAIL, null );
        assertThat( ddrPrice.getDdrTypeId(), Matchers.notNullValue() );
        String createAdapter = adapterAgent.createEmailAdapter( "test@test.com", "test", null, null, null, null, null,
            null, null, TEST_ACCOUNTID, null );
        //check if a ddr record is created
        Object ddrRecords = ddrRecordAgent.getDDRRecords( null, TEST_ACCOUNTID, null, null, null, null, null );
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>()
        {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject( ddrRecords );
        assertThat( allDdrRecords.size(), Matchers.is( 1 ) );
        DDRRecord ddrRecord = allDdrRecords.iterator().next();
        assertThat( ddrRecord.getAccountId(), Matchers.is( TEST_ACCOUNTID ) );
        assertThat( ddrRecord.getAdapterId(), Matchers.is( createAdapter ) );
        assertThat( DDRUtils.calculateCommunicationDDRCost( ddrRecord ), Matchers.is( 10.0 ) );
    }

    /**
     * check if creating an adapter without being assigned to any account will
     * not created a DDRRecord
     * 
     * @throws Exception
     */
    @Test
    public void adapterNotOwnedCreateTest() throws Exception
    {
        DDRPrice ddrPrice = getTestDDRPrice( DDRTypeCategory.ADAPTER_PURCHASE, 10.0, "Test", UnitType.PART,
            AdapterType.EMAIL, null );
        assertThat( ddrPrice.getDdrTypeId(), Matchers.notNullValue() );
        adapterAgent.createEmailAdapter( "test@test.com", "test", null, null, null, null, null, null, null, null, null );
        //check if a ddr record is created
        Object ddrRecords = ddrRecordAgent.getDDRRecords( null, null, null, null, null, null, null );
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>()
        {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject( ddrRecords );
        assertThat( allDdrRecords.size(), Matchers.is( 0 ) );
    }

    /**
     * check if initiating an outgoing call will create a ddr record Ignore this
     * in a maven test build as it will send emails automatically
     * 
     * @throws Exception
     */
    @Test
    public void outgoingEMAILCallAddsADDRRecordTest() throws Exception
    {
        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( "inffo@ask.com", "Test" );
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound( UnitType.PART, AdapterType.EMAIL,
            "test", addressNameMap );

        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( resultMap.get( ACCOUNT_ID_KEY ) );
        assertThat( allDdrRecords.size(), Matchers.is( 2 ) );

        double totalCost = 0.0;
        int assertCount = 0;
        for ( DDRRecord ddrRecord : allDdrRecords )
        {
            Double ddrCost = DDRUtils.calculateCommunicationDDRCost( ddrRecord );
            totalCost += ddrCost;
            assertThat( ddrRecord.getAccountId(), Matchers.is( resultMap.get( ACCOUNT_ID_KEY ) ) );
            assertThat( ddrRecord.getAdapterId(), Matchers.is( resultMap.get( ADAPTER_ID_KEY ) ) );
            if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_COMMUNICATION_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 0.5 ) );
                assertThat( ddrRecord.getFromAddress(), Matchers.is( MailServlet.DEFAULT_SENDER_EMAIL ) );
                assertThat( ddrRecord.getToAddress(), Matchers.is( addressNameMap ) );
                assertThat( ddrRecord.getStatus(), Matchers.is( CommunicationStatus.SENT ) );
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
        addressNameMap.put( "0031614765863", "Test" );
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound( UnitType.PART, AdapterType.SMS,
            message, addressNameMap );
        
        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( TEST_ACCOUNTID );
        assertThat( allDdrRecords.size(), Matchers.is( 2 ) );

        double totalCost = 0.0;
        int assertCount = 0;
        for ( DDRRecord ddrRecord : allDdrRecords )
        {
            Double ddrCost = DDRUtils.calculateCommunicationDDRCost( ddrRecord );
            totalCost += ddrCost;
            assertThat( ddrRecord.getAccountId(), Matchers.is( resultMap.get( ACCOUNT_ID_KEY ) ) );
            assertThat( ddrRecord.getAdapterId(), Matchers.is( resultMap.get( ADAPTER_ID_KEY ) ) );
            if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_COMMUNICATION_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 1.0 ) );
                assertThat( ddrRecord.getQuantity(), Matchers.is( 2 ) );
                assertThat( ddrRecord.getFromAddress(), Matchers.is( "TEST" ) );
                assertThat( ddrRecord.getToAddress(), Matchers.is( addressNameMap ) );
                assertThat( ddrRecord.getStatus(), Matchers.is( CommunicationStatus.SENT ) );
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
     * @throws Exception
     */
    @Test
    public void outgoingPHONECallAddsADDRRecordTest() throws Exception
    {
        String remoteAddressVoice = PhoneNumberUtils.formatNumber( TestFramework.remoteAddressVoice, null );
        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( remoteAddressVoice, "" );
        Map<String, String> resultMap = createDDRPricesAndAdapterAndSendOutBound( UnitType.MINUTE, AdapterType.CALL,
            "http://askfastmarket.appspot.com/resource/question/comment?message=Test", addressNameMap );
        
        //check if a ddr record is created
        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( resultMap.get( ACCOUNT_ID_KEY ) );
        //initially only one record is created corresponding to the adapter creation
        assertThat( allDdrRecords.size(), Matchers.is( 1 ) );
        double totalCost = 0.0;
        int assertCount = 0;
        for ( DDRRecord ddrRecord : allDdrRecords )
        {
            Double ddrCost = DDRUtils.calculateCommunicationDDRCost( ddrRecord );
            totalCost += ddrCost;
            assertThat( ddrRecord.getAccountId(), Matchers.is( resultMap.get( ACCOUNT_ID_KEY ) ) );
            assertThat( ddrRecord.getAdapterId(), Matchers.is( resultMap.get( ADAPTER_ID_KEY ) ) );
            assertThat( ddrRecord.getQuantity(), Matchers.is( 1 ) );
            assertThat( ddrCost, Matchers.is( 10.0 ) );
        }

        totalCost = 0.0;
        assertCount = 0;
        //mimick behaviour or outbound calling being triggered and answered
        UriInfo uri = Mockito.mock( UriInfo.class );
        Mockito.when( uri.getBaseUri() ).thenReturn( new URI( "http://localhost:8082/dialoghandler/vxml/new" ) );
        //-----------------------------------------------------------------------
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        voiceXMLRESTProxy.getNewDialog( "outbound", remoteAddressVoice, localAddressBroadsoft + "@ask.ask.voipit.nl", uri );
        allDdrRecords = getDDRRecordsByAccountId( resultMap.get( ACCOUNT_ID_KEY ) );
        for ( DDRRecord ddrRecord : allDdrRecords )
        {
            Double ddrCost = DDRUtils.calculateCommunicationDDRCost( ddrRecord );
            totalCost += ddrCost;
            assertThat( ddrRecord.getAccountId(), Matchers.is( resultMap.get( ACCOUNT_ID_KEY ) ) );
            assertThat( ddrRecord.getAdapterId(), Matchers.is( resultMap.get( ADAPTER_ID_KEY ) ) );
            if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_COMMUNICATION_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 0.5 ) );
                assertThat( ddrRecord.getQuantity(), Matchers.is( 1 ) );
                assertThat( ddrRecord.getFromAddress(), Matchers.is( localAddressBroadsoft + "@ask.ask.voipit.nl" ) );
                assertThat( ddrRecord.getToAddress(), Matchers.is( addressNameMap ) );
                assertThat( ddrRecord.getStatus(), Matchers.is( CommunicationStatus.SENT ) );
                assertCount++;
            }
            else if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_ADAPTER_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 10.0 ) );
                assertCount++;
            }
        }
        //hangup the call after 5 mins
        long currentTimeInMillis = TimeUtils.getServerCurrentTimeInMillis();
        voiceXMLRESTProxy.hangup( "outbound", remoteAddressVoice, localAddressBroadsoft
            + "@ask.ask.voipit.nl", String.valueOf( currentTimeInMillis ), String.valueOf( currentTimeInMillis + 5000 ),
            String.valueOf( currentTimeInMillis + 5000 + ( 5 * 60 * 1000 ) ), null );
        allDdrRecords = getDDRRecordsByAccountId( resultMap.get( ACCOUNT_ID_KEY ) );
        assertCount = 0;
        totalCost = 0;
        for (DDRRecord ddrRecord : allDdrRecords) 
        {
            Double ddrCost = DDRUtils.calculateCommunicationDDRCost( ddrRecord );
            totalCost += ddrCost;
            assertThat( ddrRecord.getAccountId(), Matchers.is( resultMap.get( ACCOUNT_ID_KEY ) ) );
            assertThat( ddrRecord.getAdapterId(), Matchers.is( resultMap.get( ADAPTER_ID_KEY ) ) );
            if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_COMMUNICATION_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 2.5 ) );
                assertThat( ddrRecord.getQuantity(), Matchers.is( 1 ) );
                assertThat( ddrRecord.getFromAddress(), Matchers.is( "0854881000@ask.ask.voipit.nl" ) );
                assertThat( ddrRecord.getToAddress(), Matchers.is( addressNameMap ) );
                assertThat( ddrRecord.getStatus(), Matchers.is( CommunicationStatus.SENT ) );
                assertCount++;
            }
            else if ( ddrRecord.getDdrTypeId().equals( resultMap.get( DDR_ADAPTER_PRICE_KEY ) ) )
            {
                assertThat( ddrCost, Matchers.is( 10.0 ) );
                assertCount++;
            }
        }
        assertThat( assertCount, Matchers.is( 2 ) );
        assertThat( totalCost, Matchers.is( 12.5 ) );
    }
    
    private Map<String, String> createDDRPricesAndAdapterAndSendOutBound( UnitType unitType, AdapterType adapterType,
        String message, Map<String, String> addressNameMap ) throws Exception
    {
        //create a ddr price and type
        DDRPrice ddrPriceForCommunication = getTestDDRPrice( DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.5, "Test",
            unitType, adapterType, null );
        assertThat( ddrPriceForCommunication.getDdrTypeId(), Matchers.notNullValue() );

        //create an adapter 
        DDRPrice ddrPriceForAdapterPurchase = getTestDDRPrice( DDRTypeCategory.ADAPTER_PURCHASE, 10.0, "Test",
            unitType, adapterType, null );
        assertThat( ddrPriceForAdapterPurchase.getDdrTypeId(), Matchers.notNullValue() );
        String adapterId = null;
        message = message.startsWith( "http" ) ? message
                                              : ( "http://askfastmarket.appspot.com/resource/question/open?message=" + message );
        switch ( adapterType )
        {
            case SMS:
                adapterId = adapterAgent.createMBAdapter( "TEST", null, "5743|AskFast", "wrh58cggr", null,
                    TEST_ACCOUNTID );
                //check if a ddr record is created by sending an outbound email
                new MBSmsServlet().startDialog( addressNameMap, null, null, message, "Test Customer", "Test subject",
                    AdapterConfig.getAdapterConfig( adapterId ) );
                break;
            case EMAIL:
                adapterId = adapterAgent.createEmailAdapter( MailServlet.DEFAULT_SENDER_EMAIL,
                    MailServlet.DEFAULT_SENDER_EMAIL_PASSWORD, "Test", null, null, null, null, null, null,
                    TEST_ACCOUNTID, null );
                new MailServlet().startDialog( addressNameMap, null, null, message, "Test Customer", "Test subject",
                    AdapterConfig.getAdapterConfig( adapterId ) );
                break;
            case XMPP:
                adapterId = adapterAgent.createXMPPAdapter( MailServlet.DEFAULT_SENDER_EMAIL,
                    MailServlet.DEFAULT_SENDER_EMAIL_PASSWORD, "test", null, null, null, null, TEST_ACCOUNTID, null );
                new XMPPServlet().startDialog( addressNameMap, null, null, message, "Test Customer", "Test subject",
                    AdapterConfig.getAdapterConfig( adapterId ) );
                break;
            case CALL:
                adapterId = adapterAgent.createBroadSoftAdapter( localAddressBroadsoft, null, "askask", null, TEST_ACCOUNTID,
                    false );
                VoiceXMLRESTProxy.dial( addressNameMap, message, "Test Customer",
                    AdapterConfig.getAdapterConfig( adapterId ) );
                break;
            default:
                break;
        }
        HashMap<String, String> result = new HashMap<String, String>();
        result.put( ACCOUNT_ID_KEY, TEST_ACCOUNTID );
        result.put( ADAPTER_ID_KEY, adapterId );
        result.put( DDR_ADAPTER_PRICE_KEY, ddrPriceForAdapterPurchase.getDdrTypeId() );
        result.put( DDR_COMMUNICATION_PRICE_KEY, ddrPriceForCommunication.getDdrTypeId() );
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
        Object ddrPriceObject = ddrRecordAgent.createDDRPriceWithNewDDRType( name, category.name(),
            TimeUtils.getServerCurrentTimeInMillis(), TimeUtils.getCurrentServerTimePlusMinutes( 100 ), price, 0, 10,
            1, unitType.name(), adapterType.name(), adapterId, null );
        TypeUtil<DDRPrice> injector = new TypeUtil<DDRPrice>(){};
        return injector.inject( ddrPriceObject );
    }
    
    /** get ddr records for this accountId
     * @param resultMap
     * @return
     * @throws Exception
     */
    private static Collection<DDRRecord> getDDRRecordsByAccountId( String accountId ) throws Exception
    {
        Object ddrRecords = new DDRRecordAgent().getDDRRecords( null, accountId, null, null, null, null, null );
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>()
        {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject( ddrRecords );
        return allDdrRecords;
    }
}
