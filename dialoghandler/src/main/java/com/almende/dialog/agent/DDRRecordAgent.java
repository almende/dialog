package com.almende.dialog.agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.eve.protocol.jsonrpc.annotation.Access;
import com.almende.eve.protocol.jsonrpc.annotation.AccessType;
import com.almende.eve.protocol.jsonrpc.annotation.Name;
import com.almende.eve.protocol.jsonrpc.annotation.Optional;
import com.almende.eve.protocol.jsonrpc.formats.JSONRequest;
import com.almende.util.TypeUtil;
import com.almende.util.jackson.JOM;
import com.askfast.commons.agent.intf.DDRRecordAgentInterface;
import com.askfast.commons.entity.AdapterType;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Access(AccessType.PUBLIC)
public class DDRRecordAgent extends ScheduleAgent implements DDRRecordAgentInterface
{
    private static final Logger log = Logger.getLogger( DDRRecordAgent.class.getName() );
    
    
    @Override
    protected void onInit() {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            generateDefaultDDRTypes();
            try {
                startSchedulerForSubscriptions();
            }
            catch (Exception e) {
                log.severe("Scheduler for checking subscriptions failed. Message: " + e.toString());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * generates all DDRTypes with default names
     */
    public void generateDefaultDDRTypes()
    {
        //check if all DDR categories are created on bootstrapping this agent
        for ( DDRTypeCategory ddrCategory : DDRTypeCategory.values() )
        {
            if ( !ddrCategory.equals( DDRTypeCategory.OTHER ) ) //ignore other
            {
                try
                {
                    DDRType ddrType = DDRType.getDDRType( ddrCategory );
                    if ( ddrType == null )
                    {
                        ddrType = new DDRType();
                        ddrType.setCategory( ddrCategory );
                        ddrType.setName( "DEFAULT - " + ddrCategory.name() );
                        ddrType.createOrUpdate();
                    }
                }
                catch ( Exception e )
                {
                    log.severe( String.format( "DDRType creation failed for type: %s. Error: %s", ddrCategory.name(),
                        e.getLocalizedMessage() ) );
                }
            }
        }
    }
    
    /**
     * get a specific DDR record if it is owned by the account
     * @param id
     * @param accountId
     * @return
     * @throws Exception
     */
    public Object getDDRRecord( @Name( "ddrRecordId" ) String id, @Name( "accountId" ) String accountId,
        @Name( "shouldGenerateCosts" ) @Optional Boolean shouldGenerateCosts,
        @Name( "shouldIncludeServiceCosts" ) @Optional Boolean shouldIncludeServiceCosts) throws Exception
    {
        DDRRecord ddrRecord = DDRRecord.getDDRRecord( id, accountId );
        ddrRecord.setShouldGenerateCosts(shouldGenerateCosts);
        ddrRecord.setShouldIncludeServiceCosts( shouldIncludeServiceCosts );
        return ddrRecord;
    }
    
    /**
     * get a specific DDR record if it is owned by the account
     * 
     * @param id
     * @param accountId
     * @return
     * @throws Exception
     */
    public Object getDDRRecords(@Name("adapterId") @Optional String adapterId, @Name("accountId") String accountId,
        @Name("fromAddress") @Optional String fromAddress, @Name("typeId") @Optional String typeId,
        @Name("communicationStatus") @Optional String status, @Name("startTime") @Optional Long startTime,
        @Name("endTime") @Optional Long endTime, @Name("sessionKeys") @Optional Collection<String> sessionKeys,
        @Name("offset") @Optional Integer offset, @Name("limit") @Optional Integer limit,
        @Name("shouldGenerateCosts") @Optional Boolean shouldGenerateCosts,
        @Name("shouldIncludeServiceCosts") @Optional Boolean shouldIncludeServiceCosts) throws Exception {

        CommunicationStatus communicationStatus = status != null && !status.isEmpty() ? CommunicationStatus
                                        .fromJson(status) : null;
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(adapterId, accountId, fromAddress, typeId,
                                                             communicationStatus, startTime, endTime, sessionKeys,
                                                             offset, limit);
        if (shouldGenerateCosts != null && shouldGenerateCosts) {
            for (DDRRecord ddrRecord : ddrRecords) {
                ddrRecord.setShouldGenerateCosts(shouldGenerateCosts);
                ddrRecord.setShouldIncludeServiceCosts(shouldIncludeServiceCosts);
            }
        }
        return ddrRecords;
    }
    
    /**
     * create a DDR Type. Access to this
     * @param name
     * @throws Exception
     */
    public Object createDDRType(@Name("nameForDDR") String name,
                                                  @Name("ddrTypeCategory") String categoryString) throws Exception {

        DDRTypeCategory category = categoryString != null && !categoryString.isEmpty() ? DDRTypeCategory
                                        .fromJson(categoryString) : null;
        DDRType ddrType = new DDRType();
        ddrType.setName(name);
        ddrType.setCategory(category);
        return ddrType.createOrUpdate();
    }
    
    /**
     * Get all the DDR Type. Available in the system
     * 
     * @param name
     * @param category
     * @return returns a collection of all the ddrTypes matching the given
     *         parameters
     * @throws Exception
     */
    @Override
    public Object getAllDDRTypes(@Name("name") @Optional String name, @Name("category") @Optional String category)
        throws Exception {

        ArrayList<DDRType> result = new ArrayList<DDRType>();
        if (category != null) {
            DDRTypeCategory ddrTypeCategory = DDRTypeCategory.fromJson(category);
            DDRType ddrType = DDRType.getDDRType(ddrTypeCategory);
            result.addAll(addDDRTypeIfNameMatches(name, ddrType));
        }
        else {
            List<DDRType> allDDRTypes = DDRType.getAllDDRTypes();
            for (DDRType ddrType : allDDRTypes) {
                result.addAll(addDDRTypeIfNameMatches(name, ddrType));
            }
        }
        return result;
    }
    
    /**
     * create a new Price Type. Access to this must be from root account 
     * @param ddrTypeId
     * @param startTime
     * @param endTime
     * @param price
     * @param staffleStart
     * @param staffleEnd
     * @param unit
     * @param unitTypeString
     * @return
     * @throws Exception 
     */
    public Object createDDRPrice( @Name( "ddrTypeId" ) String ddrTypeId, @Name( "startTime" ) @Optional Long startTime,
        @Name( "endTime" ) @Optional Long endTime, @Name( "price" ) Double price,
        @Name( "staffleStart" ) @Optional Integer staffleStart, @Name( "staffleEnd" ) @Optional Integer staffleEnd,
        @Name( "unit" ) @Optional Integer unit, @Name( "unitType" ) @Optional String unitTypeString,
        @Name( "adapterType" ) @Optional String adapterTypeString, @Name( "adapterId" ) @Optional String adapterId,
        @Name( "keyword" ) @Optional String keyword ) throws Exception
    {
        //check if ddrtype exists
        DDRType ddrType = DDRType.getDDRType(ddrTypeId);
        if (ddrType != null) {
            //set default values for some optional field
            startTime = startTime != null ? startTime : TimeUtils.getServerCurrentTimeInMillis();
            //add end time as 5yrs from now
            endTime = endTime != null ? endTime : TimeUtils.getServerCurrentTime().plusYears(5).getMillis();
            unit = unit != null ? unit : 1;
            unitTypeString = unitTypeString != null ? unitTypeString : UnitType.PART.name();

            DDRPrice ddrPrice = new DDRPrice();
            ddrPrice.setDdrTypeId(ddrTypeId);
            ddrPrice.setEndTime(endTime);
            ddrPrice.setPrice(price);
            ddrPrice.setStaffleEnd(staffleEnd);
            ddrPrice.setStaffleStart(staffleStart);
            ddrPrice.setStartTime(startTime);
            ddrPrice.setUnits(unit);
            ddrPrice.setAdapterId(adapterId);
            ddrPrice.setKeyword(keyword != null ? keyword.toUpperCase() : keyword);
            UnitType unitType = unitTypeString != null && !unitTypeString.isEmpty() ? UnitType.fromJson(unitTypeString)
                                                                                   : null;
            if (adapterId != null) {
                AdapterConfig config = AdapterConfig.getAdapterConfig(adapterId);
                adapterTypeString = config != null ? config.getAdapterType() : adapterTypeString;
            }
            AdapterType adapterType = adapterTypeString != null && !adapterTypeString.isEmpty() ? AdapterType
                                            .getByValue(adapterTypeString) : null;
            ddrPrice.setUnitType(unitType);
            ddrPrice.setAdapterType(adapterType);
            ddrPrice.createOrUpdate();
            //start subscription scheduler if its of that type
            if (ddrType.getCategory().equals(DDRTypeCategory.SUBSCRIPTION_COST) &&
                !ServerUtils.isInUnitTestingEnvironment()) {
                startSchedulerScedulerForDDRPrice(ddrPrice);
            }
            return ddrPrice;
        }
        throw new Exception("DDRType of id not found: "+ ddrTypeId);
    }

    /**
     * create a new {@link DDRPrice}Price, a new {@link DDRType} DDRType based
     * on the supplied name and links them
     * 
     * @param ddrTypeName
     * @param startTime
     * @param endTime
     * @param price
     * @param staffleStart
     * @param staffleEnd
     * @param unit
     * @param unitTypeString
     * @return
     * @throws Exception
     */
    public Object createDDRPriceWithNewDDRType(@Name("nameForDDR") String name,
                                               @Name("ddrTypeCategory") String categoryString,
                                               @Name("startTime") @Optional Long startTime,
                                               @Name("endTime") @Optional Long endTime, @Name("price") Double price,
                                               @Name("staffleStart") @Optional Integer staffleStart,
                                               @Name("staffleEnd") @Optional Integer staffleEnd,
                                               @Name("unit") @Optional Integer unit,
                                               @Name("unitType") @Optional String unitTypeString,
                                               @Name("adapterType") @Optional String adapterTypeString,
                                               @Name("adapterId") @Optional String adapterid,
                                               @Name("keyword") @Optional String keyword) throws Exception {

        Object ddrTypeObject = createDDRType(name, categoryString);
        TypeUtil<DDRType> injector = new TypeUtil<DDRType>() {
        };
        DDRType ddrType = injector.inject(ddrTypeObject);
        return createDDRPrice(ddrType.getTypeId(), startTime, endTime, price, staffleStart, staffleEnd, unit,
                              unitTypeString, adapterTypeString, adapterid, keyword);
    }
    
    /**
     * deletes a ddr price from the datastore
     * @param id
     */
    public void removeDDRPrice(@Name( "id" ) String id)
    {
        DDRPrice.removeDDRPrice(id);
    }
    
    /**
     * get all the DDR Type. Access to this 
     * @param name
     */
    public Object getDDRPrice(@Name( "id" ) String id)
    {
        return DDRPrice.getDDRPrice( id );
    }
    
    /**
     * get DDRPrices based on the the supplied params 
     * @param ddrTypeId 
     * @param units 
     * @param unitType 
     */
    @Override
    public Object getDDRPrices( @Name( "ddrTypeId" ) @Optional String ddrTypeId,
        @Name( "adapterType" ) @Optional String adapterTypeString, @Name( "adapterId" ) @Optional String adapterId,
        @Name( "unitType" ) @Optional String unitTypeString, @Name( "keyword" ) @Optional String keyword )
    {
        AdapterType adapterType = adapterTypeString != null && !adapterTypeString.isEmpty() ? AdapterType
            .getByValue( adapterTypeString ) : null;
        UnitType unitType = unitTypeString != null && !unitTypeString.isEmpty() ? UnitType.fromJson( unitTypeString )
                                                                               : null;
        return DDRPrice.getDDRPrices( ddrTypeId, adapterType, adapterId, unitType, keyword );
    }
    
    /**
     * creates a scheduler for all ddr prices of {@link DDRTypeCategory}
     * {@link DDRTypeCategory#SUBSCRIPTION_COST}
     * 
     * @return all the scheduler ids. can be useful to stop them if needed
     * @throws Exception
     */
    public ArrayList<String> startSchedulerForSubscriptions() throws Exception {

        ArrayList<String> result = new ArrayList<String>();
        DDRType ddrType = DDRType.getDDRType(DDRTypeCategory.SUBSCRIPTION_COST);
        List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(), null, null, null, null);
        for (DDRPrice ddrPrice : ddrPrices) {
            String id = startSchedulerScedulerForDDRPrice(ddrPrice);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    /**
     * process the session and delete the scheduler if processed
     * @return
     * @throws Exception
     */
    public ArrayList<String> stopSchedulerForSubscriptions(@Name("adapterId") String adapterId,
                                                @Name("ddrPriceId") @Optional String ddrPriceId,
                                                @Name("deleteDDRPrice") @Optional Boolean deleteDDRPrice) throws Exception {

        List<DDRPrice> ddrPrices = new ArrayList<DDRPrice>();
        ArrayList<String> result = new ArrayList<String>();
        if (ddrPriceId != null) {
            DDRPrice ddrPrice = DDRPrice.getDDRPrice(ddrPriceId);
            if (ddrPrice != null) {
                ddrPrices.add(ddrPrice);
            }
        }
        else {
            AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
            if (adapterConfig != null) {
                DDRType ddrType = DDRType.getDDRType(DDRTypeCategory.SUBSCRIPTION_COST);
                ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(),
                                                  AdapterType.getByValue(adapterConfig.getAdapterType()), adapterId,
                                                  null, null);
            }
        }
        for (DDRPrice ddrPrice : ddrPrices) {
            //if the ddr is processed succesfully then delete the scheduled task
            String schedulerId = getState().get(DDRTypeCategory.SUBSCRIPTION_COST + "_" + ddrPrice.getId(),
                                                String.class);
            if (schedulerId != null) {
                stopScheduledTask( schedulerId );
                getState().remove(DDRTypeCategory.SUBSCRIPTION_COST + "_" + ddrPrice.getId());
                result.add(schedulerId);
                if(deleteDDRPrice) {
                    DDRPrice.removeDDRPrice(ddrPrice.getId());
                }
            }
        }
        return result;
    }
    
    public void applySubscriptionChargesForAdapters(@Name("adapterId") String adapterId) throws Exception {

        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
        if (adapterConfig.getOwner() != null && !adapterConfig.getOwner().isEmpty()) {
            DDRUtils.createDDRForSubscription(adapterConfig, true);
        }
    }
    
    /**
     * returns the scheduler id initiated for this ddrPrice
     * 
     * @param ddrPrice
     * @return
     */
    private String startSchedulerScedulerForDDRPrice(DDRPrice ddrPrice) {

        String id = getState().get(DDRTypeCategory.SUBSCRIPTION_COST + "_" + ddrPrice.getId(), String.class);
        if (id == null && ddrPrice.getAdapterId() != null && !ddrPrice.getAdapterId().isEmpty()) {
            try {
                ObjectNode params = JOM.createObjectNode();
                params.put("adapterId", ddrPrice.getAdapterId());
                JSONRequest req = new JSONRequest("applySubscriptionChargesForAdapters", params);
                Integer interval = null;
                switch (ddrPrice.getUnitType()) {
                    case SECOND:
                        interval = 1000;
                        break;
                    case MINUTE:
                        interval = 60 * 1000;
                        break;
                    case HOUR:
                        interval = 60 * 60 * 1000;
                        break;
                    case DAY:
                        interval = 24 * 60 * 60 * 1000;
                        break;
                    case MONTH:
                        interval = 30 * 24 * 60 * 60 * 1000;
                        break;
                    case YEAR:
                        interval = 12 * 30 * 24 * 60 * 60 * 1000;
                        break;
                    default:
                        throw new Exception("DDR cannot be created for Subsciption for UnitType: " +
                                            ddrPrice.getUnitType().name());
                }
                log.info(String.format("-------Starting scheduler for processing adapter subscriptions. DDRPrice: %s -------",
                                       ddrPrice.getId()));
                id = schedule(req, interval, true);
                getState().put(DDRTypeCategory.SUBSCRIPTION_COST + "_" + ddrPrice.getId(), id);
            }
            catch (Exception e) {
                e.printStackTrace();
                log.warning("Exception in scheduler creation: " + e.getLocalizedMessage());
            }
        }
        else {
            log.warning("Task already running");
        }
        return id;
    }
    
    /**
     * Adds a ddrType if the name is contained in the {@link DDRType#getName()}.
     * If name is null, it just returns the given ddrTYpe
     * 
     * @param name
     * @param result
     * @param ddrType
     * @return
     */
    private ArrayList<DDRType> addDDRTypeIfNameMatches(String name, DDRType ddrType) {

        ArrayList<DDRType> result = new ArrayList<DDRType>();
        if (ddrType != null) {
            if (name != null && !name.isEmpty()) {
                if (ddrType.getName().contains(name)) {
                    result.add(ddrType);
                }
            }
            else {
                result.add(ddrType);
            }
        }
        return result;
    }

}
