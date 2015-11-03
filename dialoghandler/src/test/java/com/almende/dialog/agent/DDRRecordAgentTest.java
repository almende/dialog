
package com.almende.dialog.agent;


import static org.junit.Assert.assertThat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.util.DDRUtils;
import com.almende.util.TypeUtil;
import com.askfast.commons.RestResponse;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRRecord.CommunicationStatus;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;

public class DDRRecordAgentTest extends TestFramework
{
    DDRRecordAgent ddrRecordAgent = new DDRRecordAgent();
    AdapterAgent adapterAgent = new AdapterAgent();

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
        Object allDDRTypes = ddrRecordAgent.getAllDDRTypes(null, null);
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
        String createAdapter = adapterAgent.createEmailAdapter("test@test.com", "test", null, null, null, null, null,
                                                               null, null, TEST_ACCOUNT_ID, null, null, null);
        //check if a ddr record is created
        Object ddrRecords = ddrRecordAgent.getDDRRecords(TEST_ACCOUNT_ID, null, null, null, null, null, null, null,
                                                         null, null, null, null, null);
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>()
        {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject( ddrRecords );
        assertThat( allDdrRecords.size(), Matchers.is( 1 ) );
        DDRRecord ddrRecord = allDdrRecords.iterator().next();
        assertThat( ddrRecord.getAccountId(), Matchers.is( TEST_ACCOUNT_ID ) );
        assertThat( ddrRecord.getAdapterId(), Matchers.is( createAdapter ) );
        assertThat( DDRUtils.calculateDDRCost( ddrRecord ), Matchers.is( 10.0 ) );
    }
    
    /**
     * check if purchasing an adapter will charge the account
     * 
     * @throws Exception
     */
    @Test
    public void ddrRecursiveFetchTest() throws Exception {

        int ddrCount = 15;
        String adapterId = adapterAgent.createEmailAdapter("test@test.com", "test", null, null, null, null, null, null,
            null, TEST_ACCOUNT_ID, null, null, null);
        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);

        //create a test ddr price
        DDRPrice ddrPrice = getTestDDRPrice(DDRTypeCategory.ADAPTER_PURCHASE, 10.0, "Test", UnitType.PART,
            AdapterType.EMAIL, null);
        assertThat(ddrPrice.getDdrTypeId(), Matchers.notNullValue());

        //create dummy ddrRecords
        for (int count = 0; count < ddrCount; count++) {
            DDRRecord ddrRecord = new DDRRecord(ddrPrice.getDdrTypeId(), adapterConfig, TEST_ACCOUNT_ID, 1);
            ddrRecord.createOrUpdate();
            Thread.sleep(1);
        }

        //fetch the records in batches of 4
        Object ddrRecords = ddrRecordAgent.getDDRRecordsRecursively(TEST_ACCOUNT_ID, null, null, null, null, null,
            TimeUtils.getPreviousMonthEndTimestamp(), TimeUtils.getServerCurrentTimeInMillis(), null, null, 4, true,
            true);

        TypeUtil<ArrayList<DDRRecord>> typesInjector = new TypeUtil<ArrayList<DDRRecord>>() {
        };
        ArrayList<DDRRecord> allDdrRecords = typesInjector.inject(ddrRecords);
        assertThat(allDdrRecords.size(), Matchers.is(ddrCount));
        //test if the records are sorted in decending order of startTimestamp
        Assert.assertTrue(allDdrRecords.get(0).getStart() > allDdrRecords.get(14).getStart());
    }

    /**
     * check if creating an adapter without being assigned to any account will
     * not created a DDRRecord
     * 
     * @throws Exception
     */
    @Test
    public void adapterNotOwnedCreateTest() throws Exception {

        DDRPrice ddrPrice = getTestDDRPrice(DDRTypeCategory.ADAPTER_PURCHASE, 10.0, "Test", UnitType.PART,
            AdapterType.EMAIL, null);
        assertThat(ddrPrice.getDdrTypeId(), Matchers.notNullValue());
        adapterAgent.createEmailAdapter("test@test.com", "test", null, null, null, null, null, null, null, null, null,
            null, null);
        //check if a ddr record is created
        Object ddrRecords = ddrRecordAgent.getDDRRecords(TEST_ACCOUNT_ID, null, null, null, null, null, null, null,
            null, null, null, null, null);
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>() {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject(ddrRecords);
        assertThat(allDdrRecords.size(), Matchers.is(0));
    }

    /**
     * tests if the subscription ddrs are created properly for an HOURLY DDRPrice model
     * @throws Exception 
     */
    @Test
    public void subscriptionDDRsAreCreatedHourlyTest() throws Exception{
        
        ddrRecordAgent = new DDRRecordAgent();
        DateTime serverCurrentTime = TimeUtils.getServerCurrentTime();
        //create an adapter
        getTestDDRPrice(DDRTypeCategory.ADAPTER_PURCHASE, 0.5, "Test", UnitType.PART, null, null);
        String adapterId = adapterAgent.createMBAdapter( "TEST", null, "", "", null, TEST_ACCOUNT_ID, null, null);
        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
        //create a new price
        getTestDDRPrice(DDRTypeCategory.SUBSCRIPTION_COST, 0.5, "Test", UnitType.HOUR,
                        AdapterType.getByValue(adapterConfig.getAdapterType()), adapterConfig.getConfigId());
        //check if the adapter is charged a subscription fee
        DDRRecord ddrForSubscription = DDRUtils.createDDRForSubscription(adapterConfig, false);
        assertThat(ddrForSubscription.getStart(), Matchers.greaterThan(serverCurrentTime.minusHours(1).getMillis()));
        //assert two ddrs are created. 1 for adapter creation. 2nd for suscription
        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( TEST_ACCOUNT_ID );
        assertThat(allDdrRecords.size(), Matchers.is(2));
    }
    
    /**
     * tests if the subscription ddrs are created properly for a every SECOND
     * DDRPrice model
     * 
     * @throws Exception
     */
    @Test
    public void subscriptionDDRsAreCreatedEverySecondTest() throws Exception {

        DateTime serverCurrentTime = TimeUtils.getServerCurrentTime();
        //create an adapter
        getTestDDRPrice(DDRTypeCategory.ADAPTER_PURCHASE, 0.5, "Test", UnitType.PART, null, null);
        String adapterId = adapterAgent.createMBAdapter("TEST", null, "", "", null, TEST_ACCOUNT_ID, null, null);
        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
        //create a new price
        getTestDDRPrice(DDRTypeCategory.SUBSCRIPTION_COST, 0.5, "Test", UnitType.SECOND,
                        AdapterType.getByValue(adapterConfig.getAdapterType()), adapterConfig.getConfigId());
        //check if the adapter is charged a subscription fee
        DDRRecord ddrForSubscription1stSecond = DDRUtils.createDDRForSubscription(adapterConfig, false);
        Thread.sleep(1000); //sleep for a second. ugly but works
        DDRRecord ddrForSubscriptionFor2ndSecond = DDRUtils.createDDRForSubscription(adapterConfig, false);
        assertThat(ddrForSubscription1stSecond.getStart(),
                   Matchers.greaterThan(serverCurrentTime.minusSeconds(1).getMillis()));
        assertThat(ddrForSubscriptionFor2ndSecond.getStart(),
                   Matchers.greaterThan(ddrForSubscription1stSecond.getStart()));
        //assert three ddrs are created. 1 for adapter creation. 2 for suscriptions
        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( TEST_ACCOUNT_ID );
        assertThat(allDdrRecords.size(), Matchers.is(3));
    }
    
    /**
     * Check if the ddr record is saved properly and the toAddressString is
     * serialized properly and saved
     * @throws Exception 
     */
    @Test
    public void saveDDRRecordTest() throws Exception {

        AdapterConfig adapterConfig = createBroadsoftAdapter();
        Session session = createSession(adapterConfig, remoteAddressVoice);

        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.MINUTE, AdapterType.CALL,
            null);
        DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, TEST_PUBLIC_KEY, remoteAddressVoice, 1,
            "some test message", session);
        //fetch the ddrRecord
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(TEST_PUBLIC_KEY, null, null, null, null, null, null, null,
            null, null, null);
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        //toaddress check
        Map<String, String> toAddress = new HashMap<String, String>(1);
        toAddress.put(session.getRemoteAddress(), "");
        assertThat(ddrRecord.getToAddress(), Matchers.is(toAddress));

        //add one more element to the toList
        String secondRemoteAddress = "0612345678";
        toAddress.put(secondRemoteAddress, "");

        ddrRecord.addToAddress(secondRemoteAddress);
        ddrRecord.createOrUpdate();

        ddrRecords = DDRRecord.getDDRRecords(TEST_PUBLIC_KEY, null, null, null, null, null, null, null, null, null,
            null);
        ddrRecord = ddrRecords.iterator().next();
        assertThat(ddrRecord.getToAddress(), Matchers.is(toAddress));
    }
    
    /**
     * Test to verify if filtering by {@link CommunicationStatus} works as expected for a large set
     * @throws Exception
     */
    @Test
    public void fetchLargeDDRRecordByStatus() throws Exception {

        int ddrCount = 1000;
        String secondAddress = "0612345678";
        AdapterConfig adapterConfig = createBroadsoftAdapter();
        HashMap<String, Session> sessionKeyMap = new HashMap<String, Session>(1);
        sessionKeyMap.put(remoteAddressVoice, createSession(adapterConfig, remoteAddressVoice));
        sessionKeyMap.put(secondAddress, createSession(adapterConfig, secondAddress));

        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.1, "test", UnitType.MINUTE, AdapterType.CALL,
            null);
        HashMap<String, String> toAddress = new HashMap<String, String>();
        toAddress.put(remoteAddressVoice, null);
        toAddress.put(secondAddress, null);
        //create a 1000 ddrRecords
        int count = 0;
        while (count++ < ddrCount) {
            DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, TEST_ACCOUNT_ID, toAddress,
                "some test message", sessionKeyMap);
        }
        //fetch the ddrRecords
        List<DDRRecord> allDdrRecords = getAllDdrRecords(TEST_ACCOUNT_ID);
        assertThat(allDdrRecords.size(), Matchers.is(ddrCount));
        DDRRecord ddrRecord = allDdrRecords.iterator().next();
        String formattedRemoteAddressVoice = PhoneNumberUtils.formatNumber(remoteAddressVoice, null);
        secondAddress = PhoneNumberUtils.formatNumber(secondAddress, null);
        assertThat(ddrRecord.getStatusForAddress(formattedRemoteAddressVoice), Matchers.is(CommunicationStatus.SENT));
        assertThat(ddrRecord.getStatusForAddress(secondAddress), Matchers.is(CommunicationStatus.SENT));

        //fetch ddr by status
        allDdrRecords = DDRRecord.getDDRRecords(TEST_ACCOUNT_ID, null, null, null, null, CommunicationStatus.SENT, null,
            null, null, null, null);
        assertThat(allDdrRecords.size(), Matchers.is(ddrCount));

        //update ddrRecoed with different Communication status
        ddrRecord.addStatusForAddress(formattedRemoteAddressVoice, CommunicationStatus.FINISHED);
        ddrRecord.addStatusForAddress(secondAddress, CommunicationStatus.MISSED);
        ddrRecord.createOrUpdate();
        //fetch ddr by status
        allDdrRecords = DDRRecord.getDDRRecords(TEST_ACCOUNT_ID, null, null, null, null, CommunicationStatus.FINISHED,
            null, null, null, null, null);
        assertThat(allDdrRecords.size(), Matchers.is(1));
        allDdrRecords = DDRRecord.getDDRRecords(TEST_ACCOUNT_ID, null, null, null, null, CommunicationStatus.MISSED,
            null, null, null, null, null);
        RestResponse ddrRecordsCount = new DDRRecordAgent().getDDRRecordsQuantity(TEST_ACCOUNT_ID, null, null, null, null,
            CommunicationStatus.MISSED, null, null, null, null);
        long startTimestamp = TimeUtils.getServerCurrentTimeInMillis();
        Integer ddrQuantity = DDRRecord.getDDRRecordsQuantity(TEST_ACCOUNT_ID, null, null, null, null, CommunicationStatus.MISSED, null, null,
            null, null);
        
        //fetch time should be less than 1second
        long fetchTime = TimeUtils.getServerCurrentTimeInMillis() - startTimestamp;
        log.info(String.format("Actual quantity fetch time is: %s", fetchTime));
        assertThat(String.format("Actual quantity time is: %s", fetchTime), fetchTime, Matchers.lessThan(500L));
        
        assertThat(allDdrRecords.size(), Matchers.is(1));
        assertThat((Integer) ddrRecordsCount.getResult(), Matchers.is(2));
        //each ddr record has two message.. 
        assertThat(ddrQuantity, Matchers.is(2));
    }

    /**
     * @param name
     * @param unitType
     * @return
     * @throws Exception
     */
    protected DDRPrice getTestDDRPrice(DDRTypeCategory category, double price, String name, UnitType unitType,
                                       AdapterType adapterType, String adapterId) throws Exception {

        Object ddrPriceObject = ddrRecordAgent
                                        .createDDRPriceWithNewDDRType(name, category.name(),
                                                                      TimeUtils.getServerCurrentTimeInMillis(),
                                                                      TimeUtils.getCurrentServerTimePlusMinutes(100),
                                                                      price, 0, 10, 1, unitType.name(),
                                                                      adapterType != null ? adapterType.name() : null,
                                                                      adapterId, null);
        TypeUtil<DDRPrice> injector = new TypeUtil<DDRPrice>() {
        };
        return injector.inject(ddrPriceObject);
    }
    
    /** get ddr records for this accountId
     * @param resultMap
     * @return
     * @throws Exception
     */
    private static Collection<DDRRecord> getDDRRecordsByAccountId(String accountId) throws Exception {

        Object ddrRecords = new DDRRecordAgent().getDDRRecords(accountId, null, null, null, null, null, null, null,
                                                               null, null, null, null, null);
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>() {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject(ddrRecords);
        return allDdrRecords;
    }
}
