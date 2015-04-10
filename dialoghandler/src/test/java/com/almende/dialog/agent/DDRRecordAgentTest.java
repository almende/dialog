
package com.almende.dialog.agent;


import static org.junit.Assert.assertThat;
import java.util.Collection;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.TypeUtil;
import com.askfast.commons.entity.AdapterType;

public class DDRRecordAgentTest extends TestFramework
{
    DDRRecordAgent ddrRecordAgent = new DDRRecordAgent();
    AdapterAgent adapterAgent = new AdapterAgent();
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
            null, null, TEST_ACCOUNTID, null, null );
        //check if a ddr record is created
        Object ddrRecords = ddrRecordAgent.getDDRRecords(null, TEST_ACCOUNTID, null, null, null, null, null, null,
                                                         null, null, null, null);
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>()
        {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject( ddrRecords );
        assertThat( allDdrRecords.size(), Matchers.is( 1 ) );
        DDRRecord ddrRecord = allDdrRecords.iterator().next();
        assertThat( ddrRecord.getAccountId(), Matchers.is( TEST_ACCOUNTID ) );
        assertThat( ddrRecord.getAdapterId(), Matchers.is( createAdapter ) );
        assertThat( DDRUtils.calculateDDRCost( ddrRecord ), Matchers.is( 10.0 ) );
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
        adapterAgent.createEmailAdapter( "test@test.com", "test", null, null, null, null, null, null, null, null, null, null );
        //check if a ddr record is created
        Object ddrRecords = ddrRecordAgent.getDDRRecords(null, null, null, null, null, null, null, null, null, null,
                                                         null, null);
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>()
        {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject( ddrRecords );
        assertThat( allDdrRecords.size(), Matchers.is( 0 ) );
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
        String adapterId = adapterAgent.createMBAdapter( "TEST", null, "", "", null, TEST_ACCOUNTID, null );
        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
        //create a new price
        getTestDDRPrice(DDRTypeCategory.SUBSCRIPTION_COST, 0.5, "Test", UnitType.HOUR,
                        AdapterType.getByValue(adapterConfig.getAdapterType()), adapterConfig.getConfigId());
        //check if the adapter is charged a subscription fee
        DDRRecord ddrForSubscription = DDRUtils.createDDRForSubscription(adapterConfig, false);
        assertThat(ddrForSubscription.getStart(), Matchers.greaterThan(serverCurrentTime.minusHours(1).getMillis()));
        //assert two ddrs are created. 1 for adapter creation. 2nd for suscription
        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( TEST_ACCOUNTID );
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
        String adapterId = adapterAgent.createMBAdapter("TEST", null, "", "", null, TEST_ACCOUNTID, null);
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
        Collection<DDRRecord> allDdrRecords = getDDRRecordsByAccountId( TEST_ACCOUNTID );
        assertThat(allDdrRecords.size(), Matchers.is(3));
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

        Object ddrRecords = new DDRRecordAgent().getDDRRecords(null, accountId, null, null, null, null, null, null,
                                                               null, null, null, null);
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>() {
        };
        Collection<DDRRecord> allDdrRecords = typesInjector.inject(ddrRecords);
        return allDdrRecords;
    }
}
