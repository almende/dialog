package com.almende.dialog.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.protocol.jsonrpc.annotation.Access;
import com.almende.eve.protocol.jsonrpc.annotation.AccessType;
import com.almende.eve.protocol.jsonrpc.annotation.Name;
import com.almende.eve.protocol.jsonrpc.annotation.Optional;
import com.almende.eve.protocol.jsonrpc.formats.JSONRequest;
import com.almende.util.TypeUtil;
import com.almende.util.jackson.JOM;
import com.askfast.commons.RestResponse;
import com.askfast.commons.agent.ScheduleAgent;
import com.askfast.commons.agent.intf.DDRRecordAgentInterface;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRRecord.CommunicationStatus;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.entity.ScheduledTask;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Access(AccessType.PUBLIC)
public class DDRRecordAgent extends ScheduleAgent implements DDRRecordAgentInterface
{
    private static final Logger log = Logger.getLogger( DDRRecordAgent.class.getName() );
    private static final String SUBSCRIPTION_EXCLUDED_ADAPTERS_KEY = "SUBSCRIPTION_EXCLUDED_ADAPTER";
    
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
    public Object getDDRRecord(@Name("ddrRecordId") String id, @Name("accountId") String accountId,
        @Name("shouldGenerateCosts") @Optional Boolean shouldGenerateCosts,
        @Name("shouldIncludeServiceCosts") @Optional Boolean shouldIncludeServiceCosts) throws Exception {

        DDRRecord ddrRecord = DDRRecord.getDDRRecord(id, accountId);
        if (ddrRecord != null) {
            ddrRecord.setShouldGenerateCosts(shouldGenerateCosts);
            ddrRecord.setShouldIncludeServiceCosts(shouldIncludeServiceCosts);
        }
        return ddrRecord;
    }
    
    @Override
    public Object getDDRRecords(@Name("accountId") String accountId,
        @Name("adapterTypes") @Optional Collection<AdapterType> adapterTypes,
        @Name("adapterIds") @Optional Collection<String> adapterIds, @Name("fromAddress") @Optional String fromAddress,
        @Name("toAddress") @Optional String toAddress, @Name("typeId") @Optional Collection<String> typeIds,
        @Name("communicationStatus") @Optional String status, @Name("startTime") @Optional Long startTime,
        @Name("endTime") @Optional Long endTime, @Name("sessionKeys") @Optional Collection<String> sessionKeys,
        @Name("offset") @Optional Integer offset, @Name("limit") @Optional Integer limit,
        @Name("shouldGenerateCosts") @Optional Boolean shouldGenerateCosts,
        @Name("shouldIncludeServiceCosts") @Optional Boolean shouldIncludeServiceCosts) throws Exception {

        CommunicationStatus communicationStatus = status != null && !status.isEmpty()
            ? CommunicationStatus.fromJson(status) : null;
        return DDRRecord.getDDRRecords(accountId, adapterTypes, adapterIds, fromAddress, toAddress, typeIds,
            communicationStatus, startTime, endTime, sessionKeys, offset, limit, shouldGenerateCosts,
            shouldIncludeServiceCosts);
    }
    
    @Override
    public RestResponse getDDRRecordsQuantity(@Name("accountId") String accountId,
        @Name("adapterTypes") @Optional Collection<AdapterType> adapterTypes,
        @Name("adapterIds") @Optional Collection<String> adapterIds, @Name("fromAddress") @Optional String fromAddress,
        @Name("toAddress") @Optional String toAddress, @Name("typeId") @Optional Collection<String> typeIds,
        @Name("communicationStatus") @Optional CommunicationStatus status, @Name("startTime") Long startTime,
        @Name("endTime") Long endTime, @Name("sessionKeys") @Optional Collection<String> sessionKeys,
        @Name("offset") @Optional Integer offset) throws Exception {

        Integer ddrRecordsQuantity = DDRRecord.getDDRRecordsQuantity(accountId, adapterTypes, adapterIds, fromAddress,
            toAddress, typeIds, status, startTime, endTime, sessionKeys, offset);
        return RestResponse.ok(Settings.DIALOG_HANDLER_VERSION, ddrRecordsQuantity);
    }
    
    /**
     * Recursively fetch all the ddrRecords for the given timeframe
     * 
     * @param accountId
     * @param adapterType
     * @param adapterIds
     * @param status
     * @param startTime
     *            cannot be null
     * @param endTime
     *            cannot be null
     * @param sessionKeys
     * @return
     * @throws Exception
     */
    @Override
    public Object getDDRRecordsRecursively(@Name("accountId") String accountId,
        @Name("adapterTypes") @Optional Collection<AdapterType> adapterTypes,
        @Name("adapterIds") @Optional Collection<String> adapterIds, @Name("fromAddress") @Optional String fromAddress,
        @Name("toAddress") @Optional String toAddress, @Name("typeId") @Optional Collection<String> typeIds,
        @Name("communicationStatus") @Optional String status, @Name("startTime") Long startTime,
        @Name("endTime") Long endTime, @Name("sessionKeys") @Optional Collection<String> sessionKeys,
        @Name("offset") @Optional Integer offset, @Name("limit") @Optional Integer limit,
        @Name("shouldGenerateCosts") @Optional Boolean shouldGenerateCosts,
        @Name("shouldIncludeServiceCosts") @Optional Boolean shouldIncludeServiceCosts) throws Exception {

        Object ddrRecordObjects = getDDRRecords(accountId, adapterTypes, adapterIds, fromAddress, toAddress, typeIds,
            status, startTime, endTime, sessionKeys, offset, limit, shouldGenerateCosts, shouldIncludeServiceCosts);
        ArrayList<DDRRecord> ddrRecords = JOM.getInstance().convertValue(ddrRecordObjects,
            new TypeReference<ArrayList<DDRRecord>>() {
            });
        if (startTime != null && !ddrRecords.isEmpty() &&
            ddrRecords.get(ddrRecords.size() - 1).getStart() > startTime) {

            ddrRecordObjects = getDDRRecordsRecursively(accountId, adapterTypes, adapterIds, fromAddress, toAddress,
                typeIds, status, startTime, ddrRecords.get(ddrRecords.size() - 1).getStart() - 1, sessionKeys, offset,
                limit, shouldGenerateCosts, shouldIncludeServiceCosts);
            ArrayList<DDRRecord> ddrRecordsRecursiveFetch = JOM.getInstance().convertValue(ddrRecordObjects,
                new TypeReference<ArrayList<DDRRecord>>() {
                });
            ddrRecords.addAll(ddrRecordsRecursiveFetch);
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
    public Object getAllDDRTypes(@Name("name") @Optional String name,
        @Name("category") @Optional DDRTypeCategory category) throws Exception {

        ArrayList<DDRType> result = new ArrayList<DDRType>();
        if (category != null) {
            DDRType ddrType = DDRType.getDDRType(category);
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
     * Update a particular name for a ddrType
     *
     * @param typeId
     * @param name
     * @return Returns the updated DDRTYpe
     * @throws Exception
     */
    public Object updateDDRType(@Name("id") String id, @Name("name") String name) throws Exception {

        DDRType ddrType = DDRType.getDDRType(id);
        if (ddrType != null) {
            ddrType.setName(name);
            ddrType.createOrUpdate();
            return ddrType;
        }
        return null;
    }
    
    @Override
    public Object getDDRType(@Name("id") String id) throws Exception {

        return DDRType.getDDRType(id);
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
                startSchedulerForDDRPrice(ddrPrice);
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
    
    @Override
    public Object getDDRPricesWithCategory(@Name("ddrTypeId") @Optional String ddrTypeId,
        @Name("adapterType") @Optional String adapterTypeString, @Name("adapterId") @Optional String adapterId,
        @Name("unitType") @Optional String unitTypeString, @Name("keyword") @Optional String keyword,
        @Name("searchCode") @Optional String code) {

        AdapterType adapterType = adapterTypeString != null && !adapterTypeString.isEmpty() ? AdapterType.getByValue(adapterTypeString)
            : null;
        UnitType unitType = unitTypeString != null && !unitTypeString.isEmpty() ? UnitType.fromJson(unitTypeString)
            : null;
        List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrTypeId, adapterType, adapterId, unitType, keyword);
        ArrayList<Object> result = new ArrayList<Object>();
        for (DDRPrice ddrPrice : ddrPrices) {

            ObjectNode ddrPriceNode = JOM.getInstance().valueToTree(ddrPrice);
            DDRType ddrType = DDRType.getDDRType(ddrPrice.getDdrTypeId());
            if (ddrType != null) {
                ddrPriceNode.put("ddrTypeName", ddrType.getName());
                ddrPriceNode.put("category", ddrType.getCategory().toString());
                try {
                    if (ddrPrice.getKeyword() != null && ddrPrice.getKeyword().split("\\|").length == 2) {

                        String ddrPriceCountryCode = ddrPrice.getKeyword().split("\\|")[0];
                        String phoneNumberType = ddrPrice.getKeyword().split("\\|")[1];
                        String priceRegionCode = PhoneNumberUtils.getRegionCode(Integer.parseInt(ddrPriceCountryCode));
                        //if the code if given fetch the ones matching else continue
                        if (code != null) {

                            String displayCountry = new Locale("", priceRegionCode).getDisplayCountry();
                            
                            /**
                             * 1. CountryCode (e.g. 31) First in the order of
                             * precedence 2. RegionCode (e.g. NL) Second in the
                             * order of precedence 3. CountryName (E.g.
                             * Netherlands)Third in the order of precedence. A
                             * contains match is done on this query. So that et
                             * must match both nETherlands, EThiopia etc
                             */
                            if (code.equals(ddrPriceCountryCode) || code.equalsIgnoreCase(priceRegionCode) ||
                                (displayCountry != null && displayCountry.toLowerCase().contains(code.toLowerCase()))) {

                                ddrPriceNode.put("country", new Locale("", priceRegionCode).getDisplayCountry());
                                ddrPriceNode.put("phoneNumberType", phoneNumberType);
                            }
                            else {
                                continue;
                            }
                        }
                        else {
                            ddrPriceNode.put("country", new Locale("", priceRegionCode).getDisplayCountry());
                            ddrPriceNode.put("phoneNumberType", phoneNumberType);
                        }
                    }
                }
                catch (NumberFormatException e) {
                    e.printStackTrace();
                    log.severe(String.format("Country code is not parsed to Integer with Keyword: %s",
                                             ddrPrice.getKeyword()));
                }
            }
            result.add(ddrPriceNode);
        }
        return result;
    }

    /**
     * Creates a scheduler for all ddr prices of {@link DDRTypeCategory}
     * {@link DDRTypeCategory#SUBSCRIPTION_COST} which has a distinct
     * {@link UnitType}. So basically creates one scheduler per unit type.
     * 
     * @return all the scheduler ids. can be useful to stop them if needed
     * @throws Exception
     */
    public ArrayList<String> startSchedulerForSubscriptions() throws Exception {

        ArrayList<String> result = new ArrayList<String>();
        DDRType ddrType = DDRType.getDDRType(DDRTypeCategory.SUBSCRIPTION_COST);
        List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(), null, null, null, null);
        for (DDRPrice ddrPrice : ddrPrices) {
            String id = startSchedulerForDDRPrice(ddrPrice);
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
    public ArrayList<String> stopSchedulerForSubscriptions(@Name("adapterId") @Optional String adapterId,
        @Name("ddrPriceId") @Optional String ddrPriceId, @Name("deleteDDRPrice") @Optional Boolean deleteDDRPrice)
        throws Exception {

        List<DDRPrice> ddrPrices = new ArrayList<DDRPrice>();
        ArrayList<String> result = new ArrayList<String>();
        if (ddrPriceId != null) {
            DDRPrice ddrPrice = DDRPrice.getDDRPrice(ddrPriceId);
            if (ddrPrice != null) {
                ddrPrices.add(ddrPrice);
            }
        }
        else if (adapterId != null) {
            AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
            if (adapterConfig != null) {
                DDRType ddrType = DDRType.getDDRType(DDRTypeCategory.SUBSCRIPTION_COST);
                ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(),
                    AdapterType.getByValue(adapterConfig.getAdapterType()), adapterId, null, null);
            }
        }
        for (DDRPrice ddrPrice : ddrPrices) {
            //if the ddr is processed succesfully then delete the scheduled task
            String schedulerId = getState().get(getSubscriptionTaskId(ddrPrice), String.class);
            if (schedulerId != null) {
                stopScheduledTask(schedulerId);
                getState().remove(getSubscriptionTaskId(ddrPrice));
                //flush the exclusive adapter types and ids
                deleteExcludedAdapterIdsFromSubscription(ddrPrice.getAdapterId());
                if (ddrPrice.getAdapterType() != null) {
                    deleteExcludedAdapterTypesFromSubscription(ddrPrice.getAdapterType().getName().toLowerCase());
                }
                if(ddrPrice.getKeyword() != null) {
                    for (String adapterType : ddrPrice.getKeyword().split(",")) {
                        deleteExcludedAdapterTypesFromSubscription(adapterType);
                    }
                }
                result.add(schedulerId);
                if (Boolean.TRUE.equals(deleteDDRPrice)) {
                    DDRPrice.removeDDRPrice(ddrPrice.getId());
                }
            }
        }
        return result;
    }
    
    /**
     * Creates a ddr record for the adapters subscription
     * 
     * @param adapterIds
     *            If not null, it will apply costs for these adapters only.
     * @param adapterTypes
     *            If adapterIds is null, it will apply subscription costs for
     *            all the adapters of these type
     * @throws Exception
     */
    public void applySubscriptionChargesForAdapters(@Name("adapterIds") @Optional Collection<String> adapterIds,
        @Name("adapterTypes") @Optional Collection<String> adapterTypes) throws Exception {

        ArrayList<AdapterConfig> adapterConfigs = new ArrayList<AdapterConfig>(1);
        if (adapterIds != null && !adapterIds.isEmpty()) {
            for (String adapterId : adapterIds) {
                AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
                if (adapterConfig != null) {
                    adapterConfigs.add(adapterConfig);
                }
            }
        }
        else if (adapterTypes != null) {

            for (String adapterType : adapterTypes) {
                ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(adapterType, null, null);
                if (adapters != null) {
                    adapterConfigs.addAll(adapters);
                }
            }
        }
        if (adapterConfigs != null) {
            //fetch the exclusion lists
            HashSet<String> excludedAdapterIds = getExcludedAdapterIdsFromSubscription();
            HashSet<String> excludedAdapterTypes = getExcludedAdapterTypesFromSubscription();
            for (AdapterConfig adapterConfig : adapterConfigs) {
                if (adapterConfig.getOwner() != null && !adapterConfig.getOwner().isEmpty()) {

                    if (excludedAdapterIds != null && excludedAdapterIds.contains(adapterConfig.getConfigId())) {
                        log.info("Excluded subsciption scheduler processing for: " + adapterConfig.getConfigId());
                        continue;
                    }
                    if (excludedAdapterTypes != null && excludedAdapterTypes.contains(adapterConfig.getAdapterType().toLowerCase())) {
                        log.info("Excluded subsciption scheduler processing for: " + adapterConfig.getAdapterType());
                        continue;
                    }
                    DDRUtils.createDDRForSubscription(adapterConfig, true);
                }
            }
        }
    }
    
    /**
     * Get details about subscription tasks running
     * @return 
     * @return
     * @throws Exception 
     */
    public ArrayList<ScheduledTask> getSubsriptionCostScedulerDetails(@Name("ddrPriceId") @Optional String ddrPriceId)
        throws Exception {

        ArrayList<DDRPrice> ddrPriceIds = new ArrayList<DDRPrice>(1);
        ArrayList<ScheduledTask> result = new ArrayList<ScheduledTask>(1);
        if (ddrPriceId != null) {
            DDRPrice ddrPrice = DDRPrice.getDDRPrice(ddrPriceId);
            if (ddrPrice != null) {
                ddrPriceIds.add(ddrPrice);
            }
        }
        else {
            DDRType ddrType = DDRType.getDDRType(DDRTypeCategory.SUBSCRIPTION_COST);
            if (ddrType != null) {
                List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrType.getTypeId(), null, null, null, null);
                if (ddrPrices != null) {
                    for (DDRPrice ddrPrice : ddrPrices) {
                        ddrPriceIds.add(ddrPrice);
                    }
                }
            }
        }
        for (DDRPrice ddrPrice : ddrPriceIds) {
            String id = getState().get(getSubscriptionTaskId(ddrPrice), String.class);
            ScheduledTask scheduledTaskDetails = getScheduledTaskDetails(id);
            if (scheduledTaskDetails != null) {
                result.add(scheduledTaskDetails);
            }
        }
        return result;
    }
    
    /**
     * Script to update all ddrRecords with adapterType if there is an
     * associated adapterId found too. This is to fix the issue with removing an
     * adapter, before the billing date, and then fetching all teh ddr for that
     * adapterType. Added on 9-Nov-2015
     * 
     * @return
     * @throws Exception
     */
    public double updateDDRRecordsWithAdapterType() throws Exception {

        return DDRRecord.updateDDRRecordsWithAdapterType();
    }
    
    /**
     * Returns the scheduler id initiated for this ddrPrice. The id is chosen
     * based on the unit type (frequency) and the keyword in the subscription
     * parameter. Valid values are "ALL" for all adapterTypes or SMS,Call etc
     * for specific adapter types.
     * 
     * @param ddrPrice
     * @return
     */
    private String startSchedulerForDDRPrice(DDRPrice ddrPrice) {

        String id = getState().get(getSubscriptionTaskId(ddrPrice), String.class);
        if (id == null) {
            try {
                ObjectNode params = JOM.createObjectNode();
                if (ddrPrice.getAdapterId() != null) {
                    params.putPOJO("adapterIds", Arrays.asList(ddrPrice.getAdapterId()));
                    addExcludedAdapterIdsFromSubscription(ddrPrice.getAdapterId());
                }
                else if (ddrPrice.getAdapterType() != null) {
                    params.putPOJO("adapterTypes", Arrays.asList(ddrPrice.getAdapterType().getName()));
                    addExcludedAdapterTypesFromSubscription(ddrPrice.getAdapterType().getName().toLowerCase());
                }
                else if (ddrPrice.getKeyword() != null) {
                    List<AdapterType> adapterTypes = new ArrayList<AdapterType>();
                    switch (ddrPrice.getKeyword().toLowerCase()) {
                        case "all":
                            adapterTypes = Arrays.asList(AdapterType.values());
                            break;
                        default:
                            Collection<String> excludedAdapterTypes = getExcludedAdapterTypesFromSubscription();
                            excludedAdapterTypes = excludedAdapterTypes != null ? excludedAdapterTypes
                                : new HashSet<String>();
                            String[] adapterTypesString = ddrPrice.getKeyword().split(",");
                            for (String adapterTypeString : adapterTypesString) {
                                AdapterType type = AdapterType.getByValue(adapterTypeString);
                                if (type != null) {
                                    adapterTypes.add(type);
                                    excludedAdapterTypes.add(type.getName().toLowerCase());
                                    setExcludedAdapterTypesFromSubscription(excludedAdapterTypes);
                                }
                            }
                            break;
                    }
                    params.putPOJO("adapterTypes", adapterTypes);
                }
                JSONRequest req = new JSONRequest("applySubscriptionChargesForAdapters", params);
                ScheduleFrequency frequency = null;
                switch (ddrPrice.getUnitType()) {
                    case MINUTE:
                        frequency = ScheduleFrequency.MINUTELY;
                        break;
                    case HOUR:
                        frequency = ScheduleFrequency.HOURLY;
                        break;
                    case DAY:
                        frequency = ScheduleFrequency.DAILY;
                        break;
                    case MONTH:
                        frequency = ScheduleFrequency.MONTHLY;
                        break;
                    case YEAR:
                        frequency = ScheduleFrequency.YEARLY;
                        break;
                    default:
                        throw new Exception("DDR cannot be created for Subsciption for UnitType: " +
                            ddrPrice.getUnitType().name());
                }
                log.info(String.format(
                    "-------Starting scheduler for processing adapter subscriptions. DDRPrice: %s -------",
                    ddrPrice.getId()));
                //create a scheduled job now, so -1 from current time. 
                id = schedule(req, TimeUtils.getServerCurrentTimeInMillis() - 1, frequency);
                getState().put(getSubscriptionTaskId(ddrPrice), id);
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

    /**
     * Returns the subscription Id that is used to save the task details
     * corresponding to the given ddrPrice
     * 
     * @param ddrPrice
     * @param keyword
     * @return
     */
    private String getSubscriptionTaskId(DDRPrice ddrPrice) {

        String keyword = "";
        if (ddrPrice.getKeyword() != null) {
            keyword = "_" + ddrPrice.getKeyword().toLowerCase();
        }
        else if(ddrPrice.getAdapterType() != null) {
            keyword = "_" + ddrPrice.getAdapterType().getName().toLowerCase();
        }
        else if(ddrPrice.getAdapterId() != null) {
            keyword = "_" + ddrPrice.getAdapterId().toLowerCase();
        }
        return DDRTypeCategory.SUBSCRIPTION_COST + "_" + ddrPrice.getUnitType() + keyword;
    }
    
    /**
     * Gets the list of adapterTypes which are excluded from the "all" adapters
     * subscription. a subscription can be started with adapterIds specific,
     * adpaterTypes specific or all. 
     * 
     * @return
     */
    private HashSet<String> getExcludedAdapterTypesFromSubscription() {

        return getState().get(SUBSCRIPTION_EXCLUDED_ADAPTERS_KEY + "_TYPES", new TypeUtil<HashSet<String>>() {
        });
    }
    
    /**
     * Sets the list of adapterTypes which are excluded from the "all" adapters
     * subscription. a subscription can be started with adapterIds specific,
     * adpaterTypes specific or all. 
     * 
     * @return
     */
    private HashSet<String> setExcludedAdapterTypesFromSubscription(Collection<String> excludedAdapterType) {

        getState().put(SUBSCRIPTION_EXCLUDED_ADAPTERS_KEY + "_TYPES" , excludedAdapterType);
        return getExcludedAdapterTypesFromSubscription();
    }
    
    /**
     * Add an adapterType which is excluded from the "all" adapters
     * subscription. a subscription can be started with adapterIds specific,
     * adpaterTypes specific or all.
     * 
     * @return
     */
    private HashSet<String> addExcludedAdapterTypesFromSubscription(String excludedAdapterType) {

        HashSet<String> excludedAdapterTypes = getExcludedAdapterTypesFromSubscription();
        excludedAdapterTypes = excludedAdapterTypes != null ? excludedAdapterTypes : new HashSet<String>();
        excludedAdapterTypes.add(excludedAdapterType);
        return setExcludedAdapterTypesFromSubscription(excludedAdapterTypes);
    }
    
    /**
     * Deletes an adapterType which is excluded from the "all" adapters
     * subscription. a subscription can be started with adapterIds specific,
     * adpaterTypes specific or all.
     * 
     * @return
     */
    private HashSet<String> deleteExcludedAdapterTypesFromSubscription(String excludedAdapterType) {

        if (excludedAdapterType != null) {
            HashSet<String> excludedAdapterTypes = getExcludedAdapterTypesFromSubscription();
            excludedAdapterTypes = excludedAdapterTypes != null ? excludedAdapterTypes : new HashSet<String>();
            excludedAdapterTypes.remove(excludedAdapterType.toLowerCase());
            return setExcludedAdapterTypesFromSubscription(excludedAdapterTypes);
        }
        return null;
    }
    
    /**
     * Gets the list of adapterIds which are excluded from the "all" adapters
     * subscription. a subscription can be started with adapterIds specific,
     * adpaterTypes specific or all. 
     * 
     * @return
     */
    private HashSet<String> getExcludedAdapterIdsFromSubscription() {

        return getState().get(SUBSCRIPTION_EXCLUDED_ADAPTERS_KEY + "_IDs", new TypeUtil<HashSet<String>>() {
        });
    }
    
    /**
     * Sets the list of adapterIds which are excluded from the "all" adapters
     * subscription. a subscription can be started with adapterIds specific,
     * adpaterTypes specific or all. 
     * 
     * @return
     */
    private HashSet<String> setExcludedAdapterIdsFromSubscription(Collection<String> excludedAdapterIds) {

        getState().put(SUBSCRIPTION_EXCLUDED_ADAPTERS_KEY + "_IDs", excludedAdapterIds);
        return getExcludedAdapterIdsFromSubscription();
    }
    
    /**
     * Sets the list of adapterIds which are excluded from the "all" adapters
     * subscription. a subscription can be started with adapterIds specific,
     * adpaterTypes specific or all. 
     * 
     * @return
     */
    private HashSet<String> addExcludedAdapterIdsFromSubscription(String excludedAdapterId) {

        HashSet<String> excludedAdapterIds = getExcludedAdapterIdsFromSubscription();
        excludedAdapterIds = excludedAdapterIds != null ? excludedAdapterIds : new HashSet<String>();
        excludedAdapterIds.add(excludedAdapterId);
        return setExcludedAdapterIdsFromSubscription(excludedAdapterIds);
    }
    
    /**
     * Deletes a adapterId which are excluded from the "all" adapters
     * subscription. a subscription can be started with adapterIds specific,
     * adpaterTypes specific or all.
     * 
     * @return
     */
    private HashSet<String> deleteExcludedAdapterIdsFromSubscription(String excludedAdapterId) {

        if (excludedAdapterId != null) {
            HashSet<String> excludedAdapterIds = getExcludedAdapterIdsFromSubscription();
            excludedAdapterIds = excludedAdapterIds != null ? excludedAdapterIds : new HashSet<String>();
            excludedAdapterIds.remove(excludedAdapterId);
            return setExcludedAdapterIdsFromSubscription(excludedAdapterIds);
        }
        return null;
    }
}
