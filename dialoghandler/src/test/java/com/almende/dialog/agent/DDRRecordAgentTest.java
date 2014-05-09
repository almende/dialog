package com.almende.dialog.agent;

import static org.junit.Assert.assertThat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.MailServlet;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.TypeUtil;

public class DDRRecordAgentTest extends TestFramework
{
    DDRRecordAgent ddrRecordAgent = new DDRRecordAgent();
    AdapterAgent adapterAgent = new AdapterAgent();
    
    /**
     * test if there cant be two DDRTypes with the same category possible to create. 
     * @throws Exception 
     */
    @Test
    public void checkMultipleDDRTypeWithTheSameCategoryTest() throws Exception
    {
        Object ddrTypeObject = ddrRecordAgent.createDDRType( "Test DDRType", DDRTypeCategory.ADAPTER_PURCHASE.name() );
        TypeUtil<DDRType> injector = new TypeUtil<DDRType>(){};
        DDRType ddrType = injector.inject( ddrTypeObject );
        assertThat( ddrType.getTypeId(), Matchers.notNullValue() );
        
        //create another DDTYpe with the same category
        ddrRecordAgent.createDDRType( "Test DDRType1", DDRTypeCategory.ADAPTER_PURCHASE.name() );
        Object allDDRTypes = ddrRecordAgent.getAllDDRTypes();
        TypeUtil<Collection<DDRType>> typesInjector = new TypeUtil<Collection<DDRType>>(){};
        Collection<DDRType> allDdrTypes = typesInjector.inject( allDDRTypes );
        assertThat( allDdrTypes.size(), Matchers.is( 1 ) );
        ddrType = allDdrTypes.iterator().next();
        assertThat( ddrType.getTypeId(), Matchers.notNullValue() );
        assertThat( ddrType.getName(), Matchers.is( "Test DDRType1" ) );
    }
    
    /**
     * check if purchasing an adapter will charge the account
     * @throws Exception 
     */
    @Test
    public void adapterPurchaseTest() throws Exception
    {
        final String testAccountId = UUID.randomUUID().toString();
        DDRPrice ddrPrice = getTestDDRPrice(DDRTypeCategory.ADAPTER_PURCHASE, 10.0, "Test", UnitType.PART);
        assertThat( ddrPrice.getDdrTypeId(), Matchers.notNullValue() );
        String createAdapter = adapterAgent.createEmailAdapter( "test@test.com", "test", null, null, null, null, null, null, null,
            testAccountId, null );
        //check if a ddr record is created
        Object ddrRecords = ddrRecordAgent.getDDRRecords( null, testAccountId, null, null, null );
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>(){};
        Collection<DDRRecord> allDdrRecords = typesInjector.inject( ddrRecords );
        assertThat( allDdrRecords.size(), Matchers.is( 1 ) );
        DDRRecord ddrRecord = allDdrRecords.iterator().next();
        assertThat( ddrRecord.getAccountId(), Matchers.is( testAccountId ) );
        assertThat( ddrRecord.getAdapterId(), Matchers.is( createAdapter ) );
        assertThat( ddrRecord.getTotalCost(), Matchers.is( 10.0 ) );
    }
    
    /**
     * check if creating an adapter without being assigned to any account will not created a DDRRecord
     * @throws Exception 
     */
    @Test
    public void adapterNotOwnedCreateTest() throws Exception
    {
        DDRPrice ddrPrice = getTestDDRPrice(DDRTypeCategory.ADAPTER_PURCHASE, 10.0, "Test", UnitType.PART);
        assertThat( ddrPrice.getDdrTypeId(), Matchers.notNullValue() );
        adapterAgent.createEmailAdapter( "test@test.com", "test", null, null, null, null, null, null, null, null, null );
        //check if a ddr record is created
        Object ddrRecords = ddrRecordAgent.getDDRRecords( null, null, null, null, null );
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>(){};
        Collection<DDRRecord> allDdrRecords = typesInjector.inject( ddrRecords );
        assertThat( allDdrRecords.size(), Matchers.is( 0 ) );
    }
    
    /**
     * check if initiating an outgoing call will create a ddr record
     * Ignore this in a maven test build as it will send emails automatically
     * @throws Exception 
     */
    @Test
    @Ignore
    public void outgoingCallAddsADDRRecordTest() throws Exception
    {
        //create a ddr price and type
        DDRPrice ddrPriceForCommunication = getTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.5, "Test", UnitType.PART);
        assertThat( ddrPriceForCommunication.getDdrTypeId(), Matchers.notNullValue() );
        
        //create an adapter 
        DDRPrice ddrPriceForAdapterPurchase = getTestDDRPrice(DDRTypeCategory.ADAPTER_PURCHASE, 10.0, "Test", UnitType.PART);
        assertThat( ddrPriceForAdapterPurchase.getDdrTypeId(), Matchers.notNullValue() );
        final String testAccountId = UUID.randomUUID().toString();
        String emailAdapterId = adapterAgent.createEmailAdapter( MailServlet.DEFAULT_SENDER_EMAIL, MailServlet.DEFAULT_SENDER_EMAIL_PASSWORD,
            "Test", null, null, null, null, null, null, testAccountId, null );
        
        //check if a ddr record is created by sending an outbound email
        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( "info@ask-cs.com", "Test" );
        new MailServlet().startDialog( addressNameMap, null, null,
            "http://askfastmarket.appspot.com/resource/question/open?message=Test", "Test Customer", "Test subject",
            AdapterConfig.getAdapterConfig( emailAdapterId ) );
        
        //check if a ddr record is created
        Object ddrRecords = ddrRecordAgent.getDDRRecords( null, testAccountId, null, null, null );
        TypeUtil<Collection<DDRRecord>> typesInjector = new TypeUtil<Collection<DDRRecord>>(){};
        Collection<DDRRecord> allDdrRecords = typesInjector.inject( ddrRecords );
        assertThat( allDdrRecords.size(), Matchers.is( 2 ) );
        
        double totalCost = 0.0;
        for ( DDRRecord ddrRecord : allDdrRecords )
        {
            totalCost += ddrRecord.getTotalCost();
            assertThat( ddrRecord.getAccountId(), Matchers.is( testAccountId ) );
            assertThat( ddrRecord.getAdapterId(), Matchers.is( emailAdapterId ) );
            if(ddrRecord.getDdrTypeId().equals( ddrPriceForCommunication.getDdrTypeId() ))
            {
                assertThat( ddrRecord.getTotalCost(), Matchers.is( 0.5 ) );
                assertThat( ddrRecord.getFromAddress(), Matchers.is( MailServlet.DEFAULT_SENDER_EMAIL ) );
                assertThat( ddrRecord.getToAddress(), Matchers.is( addressNameMap ) );
                assertThat( ddrRecord.getStatus(), Matchers.is( CommunicationStatus.SENT ) );
            }
            else if(ddrRecord.getDdrTypeId().equals( ddrPriceForAdapterPurchase.getDdrTypeId() ))
            {
                assertThat( ddrRecord.getTotalCost(), Matchers.is( 10.0 ) );
            }
        }
        assertThat( totalCost, Matchers.is( 10.5 ) );
    }

    /**
     * @param name 
     * @param unitType 
     * @return
     * @throws Exception
     */
    protected DDRPrice getTestDDRPrice( DDRTypeCategory category, double price, String name, UnitType unitType )
    throws Exception
    {
        Object ddrPriceObject = ddrRecordAgent.createDDRPriceWithNewDDRType( name, category.name(),
            TimeUtils.getServerCurrentTimeInMillis(), TimeUtils.getCurrentServerTimePlusMinutes( 100 ), price, 0, 10,
            1, unitType.name() );
        TypeUtil<DDRPrice> injector = new TypeUtil<DDRPrice>(){};
        return injector.inject( ddrPriceObject );
    }
}
