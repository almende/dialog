package com.almende.dialog.model.ddr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.mongojack.DBCursor;
import org.mongojack.DBQuery;
import org.mongojack.DBQuery.Query;
import org.mongojack.DBSort;
import org.mongojack.Id;
import org.mongojack.JacksonDBCollection;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.askfast.commons.entity.AccountType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mongodb.DB;

/**
 * The actual price charged as part of the service and/or communication cost
 * @author Shravan
 */
public class DDRRecord
{
    protected static final Logger log = Logger.getLogger(DDRRecord.class.getName());
    public static final String DDR_TOTALCOST_KEY = "totalCost";
    public static final String DDR_RECORD_KEY = "DDR_RECORD";
    
    /**
     * status of the communication
     */
    public enum CommunicationStatus
    {
        DELIVERED, RECEIEVED, SENT, FINISHED, ERROR, UNKNOWN;
        @JsonCreator
        public static CommunicationStatus fromJson( String name )
        {
            return valueOf( name.toUpperCase() );
        }
    }
    
    @Id
    public String _id;
    //used for backward compatibility as TwigMongoWrapper was used before. so it maintained two ids: id and _id in the datastore.
    String id;
    String adapterId;
    String accountId;
    String fromAddress;
    @JsonIgnore
    Map<String, String> toAddress;
    //creating a dummy serialized version of toAddress as dot(.) in keys is not allowed by mongo 
    String toAddressString;
    String ddrTypeId;
    Integer quantity;
    Long start;
    Long duration;
    CommunicationStatus status;
    @JsonIgnore
    Boolean shouldGenerateCosts = false;
    @JsonIgnore
    Boolean shouldIncludeServiceCosts = false;
    Map<String, String> additionalInfo;
    /**
     * PRE_PAID accounts must have a fixed ddrCost. POST_PAID can have a variable one. 
     * This can be got from the adapterId too, but just makes it more explicit
     */
    AccountType accountType;
    
    /**
     * total cost is not sent for any ddr generally
     */
    Double totalCost = 0.0;
    
    public DDRRecord(){}
    
    public DDRRecord( String ddrTypeId, String adapterId, String accountId, Integer quantity )
    {
        this.ddrTypeId = ddrTypeId;
        this.adapterId = adapterId;
        this.accountId = accountId;
        this.quantity = quantity;
    }
    
    public void createOrUpdate() {

        _id = _id != null && !_id.isEmpty() ? _id : org.bson.types.ObjectId.get().toStringMongod();
        JacksonDBCollection<DDRRecord, String> collection = getCollection();
        DDRRecord existingDDRRecord = collection.findOneById(_id);
        //update if existing
        if(existingDDRRecord != null){
            collection.updateById(_id, this);
        }
        else { //create one if missing
            collection.insert(this);
        }
    }
    
    public static DDRRecord getDDRRecord(String id, String accountId) throws Exception {

        JacksonDBCollection<DDRRecord, String> coll = getCollection();
        DDRRecord ddrRecord = id != null ? coll.findOneById(id) : null;
        if (ddrRecord != null && ddrRecord.getAccountId() != null && !ddrRecord.getAccountId().equals(accountId)) {
            throw new Exception(String.format("DDR record: %s is not owned by account: %s", id, accountId));
        }
        return ddrRecord;
    }
    
    /**
     * fetch the ddr records based the input parameters. fetches the records
     * that matches to all the parameters given
     * 
     * @param adapterId
     * @param accountId
     * @param fromAddress
     * @param ddrTypeId
     * @param status
     * @param startTime
     * @param endTime
     * @param offset
     * @param limit
     *            fetchs 1000 records if limit is null or greater than 1000.
     * @return
     */
    public static List<DDRRecord> getDDRRecords(String adapterId, String accountId, String fromAddress,
                                                String ddrTypeId, CommunicationStatus status, Long startTime,
                                                Long endTime, Integer offset, Integer limit) {

        limit = limit != null && limit <= 1000 ? limit : 1000;
        offset = offset != null ? offset : 0;
        JacksonDBCollection<DDRRecord, String> collection = getCollection();
        ArrayList<Query> queryList = new ArrayList<Query>();
        //fetch accounts that match
        queryList.add(DBQuery.is("accountId", accountId));
        if (adapterId != null) {
            queryList.add(DBQuery.is("adapterId", adapterId));
        }
        if (fromAddress != null) {
            queryList.add(DBQuery.is("fromAddress", fromAddress));
        }
        if (ddrTypeId != null) {
            queryList.add(DBQuery.is("ddrTypeId", ddrTypeId));
        }
        if (status != null) {
            queryList.add(DBQuery.is("status", status.name()));
        }
        if (startTime != null) {
            queryList.add(DBQuery.greaterThanEquals("start", startTime));
        }
        if (endTime != null) {
            queryList.add(DBQuery.lessThanEquals("start", endTime));
        }
        return collection.find(DBQuery.and(queryList.toArray(new Query[queryList.size()]))).skip(offset).limit(limit)
                                        .sort(DBSort.desc("start")).toArray();
    }
    
    /**
     * fetch the ddr record for a particular Session. This inverts the lookup. Normally used when a 
     * ddr is not found corresponding to a session {@link Session#getDdrRecordId()} 
     * @param session
     * @return
     * @throws Exception 
     */
    public static DDRRecord getDDRRecord(String sessionKey) {

        Session session = Session.getSession(sessionKey);
        JacksonDBCollection<DDRRecord, String> collection = getCollection();
        ArrayList<Query> queryList = new ArrayList<Query>();
        //fetch accounts that match
        queryList.add(DBQuery.is("accountId", session.getAccountId()));
        if (session.getAdapterID() != null) {
            queryList.add(DBQuery.is("adapterId", session.getAdapterID()));
        }
        if (session.getDirection() != null) {
            HashMap<String, String> addressMap = new HashMap<String, String>(1);
            if (session.getDirection().equalsIgnoreCase("incoming")) {
                queryList.add(DBQuery.is("fromAddress", session.getRemoteAddress()));
                addressMap.put(session.getLocalAddress(), "");
                queryList.add(DBQuery.is("status", CommunicationStatus.RECEIEVED));
            }
            else {
                queryList.add(DBQuery.is("fromAddress", session.getLocalAddress()));
                addressMap.put(session.getRemoteAddress(), "");
                queryList.add(DBQuery.is("status", CommunicationStatus.SENT));
            }
            try {
                queryList.add(DBQuery.is("toAddressString", ServerUtils.serialize(addressMap)));
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe("Error while serializing. Message: "+ e.toString());
            }
        }
        DBCursor<DDRRecord> cursor = collection.find(DBQuery.and(queryList.toArray(new Query[queryList.size()])));
        ArrayList<DDRRecord> ddrRecordsForSession = new ArrayList<DDRRecord>();
        while (cursor.hasNext()) {
            ddrRecordsForSession.add(cursor.next());
        }
        for (DDRRecord ddrRecord : ddrRecordsForSession) {
            //return the ddrRecord whose startTime matches the creationTime or answerTime of the session
            if(ddrRecord.getStart() != null && (ddrRecord.getStart().toString().equals(session.getCreationTimestamp()) ||
                                            ddrRecord.getStart().toString().equals(session.getAnswerTimestamp()))){
                return ddrRecord;
            }
        }
        return null; 
    }
    
    // -- getters and setters --
    @JsonProperty("_id")
    public String get_Id() {
        
        return _id;
    }
    @JsonProperty("_id")
    public void set_Id(String id) {
    
        this._id = id;
    }
    /**
     * used for backward compatibility as TwigMongoWrapper was used before. so it maintained two ids: id and _id in the datastore.
     * uses the {@link DDRRecord#get_Id()} itself
     */
    @JsonIgnore
    public String getId() {
        
        return get_Id();
    }
    /**
     * used for backward compatibility as TwigMongoWrapper was used before. so it maintained two ids: id and _id in the datastore.
     * uses the {@link DDRRecord#set_Id(String)} itself
     * @param id
     */
    @JsonIgnore
    public void setId(String id) {
    
        set_Id(id);
    }
    public String getAdapterId()
    {
        return adapterId;
    }
    public void setAdapterId( String adapterId )
    {
        this.adapterId = adapterId;
    }
    public String getAccountId()
    {
        return accountId;
    }
    public void setAccountId( String accountId )
    {
        this.accountId = accountId;
    }
    public String getFromAddress()
    {
        return fromAddress;
    }
    public void setFromAddress( String fromAddress )
    {
        this.fromAddress = fromAddress;
    }
    @JsonIgnore
    public Map<String, String> getToAddress() throws Exception
    {
        if ( toAddress == null && toAddressString == null )
        {
            toAddress = new HashMap<String, String>();
        }
        else if ( ( toAddress == null || toAddress.isEmpty() ) && toAddressString != null )
        {
            toAddress = ServerUtils.deserialize( toAddressString,
                new TypeReference<HashMap<String, String>>(){} );
        }
        return toAddress;
    }
    
    @JsonIgnore
    public void setToAddress( Map<String, String> toAddress )
    {
        this.toAddress = toAddress;
    }
    public String getDdrTypeId()
    {
        return ddrTypeId;
    }
    public void setDdrTypeId( String ddrTypeId )
    {
        this.ddrTypeId = ddrTypeId;
    }
    public Integer getQuantity()
    {
        return quantity != null ? quantity : 0;
    }
    
    public Long getStart()
    {
        return start;
    }
    public Long getDuration()
    {
        return duration;
    }
    public void setQuantity( Integer quantity )
    {
        this.quantity = quantity;
    }

    public void setStart( Long start )
    {
        this.start = start;
    }

    public void setDuration( Long duration )
    {
        this.duration = duration;
    }
    public CommunicationStatus getStatus()
    {
        return status;
    }
    public void setStatus( CommunicationStatus status )
    {
        this.status = status;
    }

    /**
     * only used by the mongo serializing/deserializing
     * @return
     * @throws Exception
     */
    public String getToAddressString() throws Exception
    {
        if ( ( toAddress == null || toAddress.isEmpty() ) && toAddressString != null )
        {
            return toAddressString;
        }
        else
        {
            toAddressString = ServerUtils.serialize( toAddress );
            return toAddressString;
        }
    }

    /**
     * only used by the mongo serializing/deserializing
     * @param toAddressString
     * @throws Exception
     */
    public void setToAddressString( String toAddressString ) throws Exception
    {
        this.toAddressString = toAddressString;
    }
    
    @JsonIgnore    
    public DDRType getDdrType()
    {
        if(ddrTypeId != null && !ddrTypeId.isEmpty())
        {
            return DDRType.getDDRType( ddrTypeId );
        }
        return null;
    }

    @JsonIgnore
    public AdapterConfig getAdapter()
    {
        if(adapterId != null && !adapterId.isEmpty())
        {
            return AdapterConfig.getAdapterConfig( adapterId );
        }
        return null;
    }

    @JsonIgnore
    public void setShouldGenerateCosts( Boolean shouldGenerateCosts )
    {
        this.shouldGenerateCosts = shouldGenerateCosts;
    }
    
    @JsonIgnore
    public void setShouldIncludeServiceCosts( Boolean shouldIncludeServiceCosts )
    {
        this.shouldIncludeServiceCosts = shouldIncludeServiceCosts;
    }
    
    public Double getTotalCost() throws Exception {

        //generate the costs at runtime, only when requested and when the accountTYpe is not prepaid. Prepaid accounts
        //have fixed costs
        if (shouldGenerateCosts && (accountType == null || !accountType.equals(AccountType.PRE_PAID))) {
            DDRType ddrType = getDdrType();
            switch (ddrType.getCategory()) {
                case INCOMING_COMMUNICATION_COST:
                case OUTGOING_COMMUNICATION_COST:
                    return DDRUtils.calculateCommunicationDDRCost(this, shouldIncludeServiceCosts);
                case ADAPTER_PURCHASE:
                case SERVICE_COST: {
                    //fetch the ddrPrice
                    List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrTypeId, null, adapterId, UnitType.PART, null);
                    if (ddrPrices != null && !ddrPrices.isEmpty()) {
                        return DDRUtils.calculateDDRCost(this, ddrPrices.iterator().next());
                    }
                    break;
                }
                case SUBSCRIPTION_COST: {
                    //fetch the ddrPrice
                    List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices(ddrTypeId, null, adapterId, null, null);
                    if (ddrPrices != null && !ddrPrices.isEmpty()) {
                        return DDRUtils.calculateDDRCost(this, ddrPrices.iterator().next());
                    }
                    break;
                }
                default:
                    break;
            }
        }
        return totalCost;
    }
    
    /**
     * generally just used by JACKSON to (de)serialize the variable for all accounts except PRE_PAID. 
     * Setting the value does not matter as the actual cost is calculated lazily when the {@link DDRRecord#shouldGenerateCosts} is set
     * for all cases apart from for PRE_PAID customers.  
     * to true
     * @param totalCost
     */
    public void setTotalCost(Double totalCost) {
        
        this.totalCost = totalCost != null ? totalCost : 0.0;
    }

    public Map<String, String> getAdditionalInfo() {
    
        return additionalInfo;
    }

    public void setAdditionalInfo(Map<String, String> additionalInfo) {
    
        this.additionalInfo = additionalInfo;
    }
    
    @JsonIgnore
    public void addAdditionalInfo(String key, String value) {
        
        additionalInfo = additionalInfo != null ? additionalInfo : new HashMap<String, String>();
        additionalInfo.put(key, value);
    }
    
    public AccountType getAccountType() {
        
        return accountType;
    }
    public void setAccountType(AccountType accountType) {
    
        this.accountType = accountType;
    }
    
    private static JacksonDBCollection<DDRRecord, String> getCollection() {

        DB db = ParallelInit.getDatastore();
        return JacksonDBCollection.wrap(db.getCollection(DDRRecord.class.getCanonicalName().toLowerCase() + "s"),
                                        DDRRecord.class, String.class);
    }
}
