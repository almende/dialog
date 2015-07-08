package com.almende.dialog.model.ddr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bson.types.ObjectId;
import org.mongojack.DBCursor;
import org.mongojack.DBQuery;
import org.mongojack.DBQuery.Query;
import org.mongojack.DBSort;
import org.mongojack.Id;
import org.mongojack.JacksonDBCollection;
import com.almende.dialog.LogLevel;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.ParallelInit;
import com.almende.util.jackson.JOM;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mongodb.DB;

/**
 * The actual price charged as part of the service and/or communication cost
 * @author Shravan
 */
@JsonPropertyOrder({"totalCost"})
public class DDRRecord
{
    protected static final Logger log = Logger.getLogger(DDRRecord.class.getName());
    public static final String DDR_TOTALCOST_KEY = "totalCost";
    public static final String DDR_RECORD_KEY = "DDR_RECORD_ID";
    public static final String ANSWER_INPUT_KEY = "ANSWER_INPUT";
    private static final String DOT_REPLACER_KEY = "[%dot%]";
    
    /**
     * status of the communication
     */
    public enum CommunicationStatus {
        DELIVERED, RECEIVED("RECEIEVED"), SENT, FINISHED, MISSED, ERROR, UNKNOWN;

        /**
         * use to collect alternate name. there was a typo in the enum back in
         * the days :) But we want to fetch both RECEIVED as well as RECEIEVED
         * 
         * @param name
         */
        private String alternateName;

        private CommunicationStatus(String name) {

            this.alternateName = name;
        }

        private CommunicationStatus() {

        }

        @JsonCreator
        public static CommunicationStatus fromJson(String name) {

                name = name != null && name.equals(RECEIVED.alternateName) ? RECEIVED.toString() : name; 
                return valueOf(name.toUpperCase());
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
    Collection<String> sessionKeys;
    CommunicationStatus status;
    Map<String, CommunicationStatus> statusPerAddress;
    
    @JsonIgnore
    Boolean shouldGenerateCosts = false;
    @JsonIgnore
    Boolean shouldIncludeServiceCosts = false;
    Map<String, Object> additionalInfo;
    /**
     * PRE_PAID accounts must have a fixed ddrCost. POST_PAID can have a variable one. 
     * This can be got from the adapterId too, but just makes it more explicit
     */
    AccountType accountType;
    
    /**
     * total cost is not sent for any ddr if its a post paid account
     */
    Double totalCost = 0.0;
    /**
     * cache the adapter linked with this ddrRecord
     */
    @JsonIgnore
    private AdapterConfig config;
    /**
     * Flag to make sure that the data with (.) is correcting on a get from any level other than 
     * database/mongo e.g. resource/agent 
     */
    @JsonIgnore
    private boolean correctDotData;
    
    public DDRRecord(){}
    
    public DDRRecord(String ddrTypeId, String adapterId, String accountId, Integer quantity) {

        this.ddrTypeId = ddrTypeId;
        this.adapterId = adapterId;
        this.accountId = accountId;
        this.quantity = quantity;
    }
    
    public DDRRecord(String ddrTypeId, AdapterConfig adapterConfig, String accountId, Integer quantity) {

        this.ddrTypeId = ddrTypeId;
        this.adapterId = adapterConfig != null ? adapterConfig.getConfigId() : null;
        this.accountId = accountId;
        this.quantity = quantity;
    }
    
    @JsonIgnore
    public DDRRecord createOrUpdate() {

        _id = _id != null && !_id.isEmpty() ? _id : org.bson.types.ObjectId.get().toStringMongod();
        correctDotData = true;
        JacksonDBCollection<DDRRecord, String> collection = getCollection();
        DDRRecord existingDDRRecord = collection.findOneById(_id);
        //update if existing
        if (existingDDRRecord != null) {
            collection.updateById(_id, this);
        }
        else { //create one if missing
            this.start = this.start != null ? this.start : TimeUtils.getServerCurrentTimeInMillis();
            collection.insert(this);
        }
        return this;
    }
    
    /**
     * creates/updates a ddr record and creates a log of type {@link LogLevel#DDR} 
     */
    @JsonIgnore
    public DDRRecord createOrUpdateWithLog(Session session) {

        if(session != null && session.isTestSession()) {
            return null;
        }
        return createOrUpdate();
    }
    
    /**
     * creates/updates a ddr record and creates a log of type {@link LogLevel#DDR} 
     */
    public DDRRecord createOrUpdateWithLog(Map<String, Session> sessionKeyMap) {

        DDRRecord ddrRecord = null;
        for (Session session : sessionKeyMap.values()) {
            ddrRecord = createOrUpdateWithLog(session);
        }
        return ddrRecord;
    }
    
    /**
     * Gets the DDRRecord for the given id only if accountId matches that of the
     * owner of the DDRRecord itself
     * 
     * @param id
     * @param accountId
     * @return
     * @throws Exception
     */
    public static DDRRecord getDDRRecord(String id, String accountId) throws Exception {

        JacksonDBCollection<DDRRecord, String> coll = getCollection();
        DDRRecord ddrRecord = id != null ? coll.findOneById(id) : null;
        if (ddrRecord != null && ddrRecord.getAccountId() != null && !ddrRecord.getAccountId().equals(accountId)) {
            throw new Exception(String.format("DDR record: %s is not owned by account: %s", id, accountId));
        }
        return ddrRecord;
    }
    
    /**
     * Fetch the ddr records based the input parameters. fetches the records
     * that matches to all the parameters given. The accountId is mandatory and
     * rest are optional
     * 
     * @param adapterId
     * @param accountId
     *            Mandatory field
     * @param fromAddress
     * @param ddrTypeIds
     * @param status
     * @param startTime
     * @param endTime
     * @param offset
     * @param limit
     *            fetchs 1000 records if limit is null or greater than 1000.
     * @return
     */
    public static List<DDRRecord> getDDRRecords(String adapterId, String accountId, String fromAddress,
        Collection<String> ddrTypeIds, CommunicationStatus status, Long startTime, Long endTime,
        Collection<String> sessionKeys, Integer offset, Integer limit) {

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
        if (ddrTypeIds != null) {
            queryList.add(DBQuery.in("ddrTypeId", ddrTypeIds));
        }
        if (startTime != null) {
            queryList.add(DBQuery.greaterThanEquals("start", startTime));
        }
        if (endTime != null) {
            queryList.add(DBQuery.lessThanEquals("start", endTime));
        }

        Query[] dbQueries = new Query[queryList.size()];
        for (int queryCounter = 0; queryCounter < queryList.size(); queryCounter++) {
            dbQueries[queryCounter] = queryList.get(queryCounter);
        }
        DBCursor<DDRRecord> ddrCursor = collection.find(DBQuery.and(dbQueries));

        if (sessionKeys != null) {
            ddrCursor = ddrCursor.in("sessionKeys", sessionKeys);
        }
        List<DDRRecord> result = ddrCursor.skip(offset).limit(limit).sort(DBSort.desc("start")).toArray();
        if (result != null && !result.isEmpty() && status != null) {
            ArrayList<DDRRecord> resultByStatus = new ArrayList<DDRRecord>();
            for (DDRRecord ddrRecord : result) {
                if (status.equals(ddrRecord.getStatus()) ||
                    (ddrRecord.getStatusPerAddress() != null && ddrRecord.getStatusPerAddress().values()
                                                                         .contains(status))) {
                    resultByStatus.add(ddrRecord);
                }
            }
            return resultByStatus;
        }
        else {
            return result;
        }
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
        if (session != null) {
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
                    queryList.add(DBQuery.is("status", CommunicationStatus.RECEIVED));
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
                    log.severe("Error while serializing. Message: " + e.toString());
                }
            }
            Query[] dbQueries = new Query[queryList.size()];
            for (int queryCounter = 0; queryCounter < queryList.size(); queryCounter++) {
                dbQueries[queryCounter] = queryList.get(queryCounter);
            }
            DBCursor<DDRRecord> ddrCursor = collection.find(DBQuery.and(dbQueries));
            if (ddrCursor != null) {
                while (ddrCursor.hasNext()) {
                    DDRRecord ddrRecord = ddrCursor.next();
                    //return the ddrRecord whose startTime matches the creationTime or answerTime of the session
                    if (ddrRecord.getStart() != null &&
                        (ddrRecord.getStart().toString().equals(session.getCreationTimestamp()) || ddrRecord.getStart()
                                                        .toString().equals(session.getAnswerTimestamp()))) {
                        return ddrRecord;
                    }
                }
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
    public Map<String, String> getToAddress() {

        if (toAddress == null && toAddressString == null) {
            toAddress = new HashMap<String, String>();
        }
        else if ((toAddress == null || toAddress.isEmpty()) && toAddressString != null) {
            try {
                toAddress = ServerUtils.deserialize(toAddressString, new TypeReference<HashMap<String, String>>() {});
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Exception while deserializing toAddress: %s", toAddress));
            }
        }
        return toAddress;
    }
    
    @JsonIgnore
    public void setToAddress( Map<String, String> toAddress )
    {
        this.toAddress = toAddress;
    }
    @JsonIgnore
    public void addToAddress( String toAddress )
    {
        this.toAddress = this.toAddress != null ? this.toAddress : new HashMap<String, String>();
        this.toAddress.put(toAddress, "");
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
    /**
     * @deprecated
     * kept for backward compatibility. Use {@link DDRRecord#getStatusForAddress(String)} to get status
     * for an address or {@link DDRRecord#getStatusPerAddress()} for fetching all statuses
     * @return
     */
    public CommunicationStatus getStatus()
    {
        //return the only statusPerAddress if its there and this is null
        if(status == null && statusPerAddress != null && statusPerAddress.size() == 1) {
            return statusPerAddress.values().iterator().next();
        }
        return status;
    }
    
    /**
     * @deprecated
     * kept for backward compatibility. Use {@link DDRRecord#setStatusPerAddress(Map)} to set status
     * for all addresses or {@link DDRRecord#addStatusForAddress(String, CommunicationStatus)} for 
     * a single addres
     * @return
     */
    public void setStatus( CommunicationStatus status )
    {
        this.status = status;
    }

    /**
     * only used by the mongo serializing/deserializing
     * 
     * @return
     * @throws Exception
     */
    public String getToAddressString() {

        if ((toAddress == null || toAddress.isEmpty()) && toAddressString != null) {
            toAddressString = getDotReplacedString(toAddressString);
        }
        else {
            try {
                toAddressString = ServerUtils.serialize(toAddress);
                //replace dot(.) by - as mongo doesnt allow map variables with (.)
                toAddressString = getDotReplacedString(toAddressString);
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Exception while serializing toAddress: %s", toAddress));
            }
        }
        return toAddressString;
    }

    /**
     * only used by the mongo serializing/deserializing
     * @param toAddressString
     * @throws Exception
     */
    public void setToAddressString( String toAddressString ) throws Exception
    {
        this.toAddressString = correctDotReplacedString(toAddressString);
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
        if(adapterId != null && !adapterId.isEmpty() && config == null)
        {
            config = AdapterConfig.getAdapterConfig( adapterId );
        }
        return config;
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
    
    /**
     * Generally only called by the serializer method to lazy load all the
     * charges involved. This method will generate costs if it is a
     * {@link AccountType#POST_PAID} account
     * 
     * @return
     * @throws Exception
     */
    public Double getTotalCost() throws Exception {

        //generate the costs at runtime, only when requested and when the accountTYpe is not prepaid or trial. These accounts
        //have fixed costs
        if (Boolean.TRUE.equals(shouldGenerateCosts) &&
            (accountType == null || accountType.equals(AccountType.POST_PAID))) {

            DDRType ddrType = getDdrType();
            switch (ddrType.getCategory()) {
                case INCOMING_COMMUNICATION_COST:
                case OUTGOING_COMMUNICATION_COST:
                    totalCost = DDRUtils.calculateDDRCost(this, shouldIncludeServiceCosts);
                    break;
                case ADAPTER_PURCHASE:
                case SERVICE_COST:
                case SUBSCRIPTION_COST:
                default:
                    totalCost = DDRUtils.calculateDDRCost(this);
                    break;
            }
        }
        return DDRUtils.getCeilingAtPrecision(totalCost, 3);
    }
    
    /**
     * Generally just used by JACKSON to (de)serialize the variable for all
     * accounts except PRE_PAID. Setting the value does not matter as the actual
     * cost is calculated lazily when the {@link DDRRecord#shouldGenerateCosts}
     * is set for all cases apart from for PRE_PAID customers to true
     * 
     * @param totalCost
     */
    public void setTotalCost(Double totalCost) {
        
        this.totalCost = totalCost != null ? totalCost : 0.0;
    }

    public Map<String, Object> getAdditionalInfo() {

        additionalInfo = additionalInfo != null ? additionalInfo : new HashMap<String, Object>();
        if (!additionalInfo.isEmpty()) {
            Map<String, Object> additionalInfoCopy = getDotReplaceKeys(additionalInfo);
            for (String key : additionalInfo.keySet()) {
                Object value = additionalInfo.get(key);
                if (value instanceof Map) {
                    try {
                        Map<String, Object> valueAsMap = JOM.getInstance()
                                                        .convertValue(value, new TypeReference<Map<String, Object>>() {
                                                        });
                        valueAsMap = getDotReplaceKeys(valueAsMap);
                        additionalInfoCopy.put(key, valueAsMap);
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                        log.severe("Typecasting failed. Saving serialized data instead");
                        additionalInfoCopy.put(key, ServerUtils.serializeWithoutException(value));
                    }
                }
            }
            additionalInfo = additionalInfoCopy;
        }
        return additionalInfo;
    }

    public void setAdditionalInfo(Map<String, Object> additionalInfo) {
    
        if (additionalInfo != null) {
            this.additionalInfo = this.additionalInfo != null ? this.additionalInfo : new HashMap<String, Object>();
            for (String key : additionalInfo.keySet()) {
                this.additionalInfo.put(correctDotReplacedString(key), additionalInfo.get(key));
            }
        }
        else {
            this.additionalInfo = additionalInfo;
        }
    }
    
    @JsonIgnore
    public void addAdditionalInfo(String key, Object value) {

        additionalInfo = additionalInfo != null ? additionalInfo : new HashMap<String, Object>();
        if (!DDR_RECORD_KEY.equals(key)) {
            //dot(.) is not allowed to be saved in mongo. just replace with -
            additionalInfo.put(getDotReplacedString(key), value);
        }
    }
    
    public AccountType getAccountType() {
        
        return accountType;
    }
    
    public void setAccountType(AccountType accountType) {
    
        //update null accountType with POST_PAID
        accountType = accountType != null ? accountType : AccountType.POST_PAID;
        this.accountType = accountType;
    }
    
    public Collection<String> getSessionKeys() {
        
        return sessionKeys;
    }

    public void setSessionKeys(Collection<String> sessionKeys) {
    
        this.sessionKeys = sessionKeys;
    }
    
    public void addSessionKey(String sessionKey) {
        
        sessionKeys = sessionKeys != null ? sessionKeys : new HashSet<String>();
        sessionKeys.add(sessionKey);
    }
    
    private static JacksonDBCollection<DDRRecord, String> getCollection() {

        DB db = ParallelInit.getDatastore();
        return JacksonDBCollection.wrap(db.getCollection(DDRRecord.class.getCanonicalName().toLowerCase() + "s"),
                                        DDRRecord.class, String.class);
    }

    
    public Map<String, CommunicationStatus> getStatusPerAddress() {

        statusPerAddress = statusPerAddress != null ? statusPerAddress : new HashMap<String, CommunicationStatus>();
        //if status is there but statusPerAddress is empty, use status
        if (status != null && (statusPerAddress == null || statusPerAddress.isEmpty())) {

            try {
                Map<String, String> toAddresses = ServerUtils.deserialize(toAddressString, false,
                                                                          new TypeReference<Map<String, String>>() {
                                                                          });
                if (toAddresses != null) {
                    for (String address : toAddresses.keySet()) {
                        statusPerAddress.put(address, status);
                    }
                }
            }
            catch (Exception e) {
                log.severe(String.format("ToAddress map couldnt be deserialized: %s", toAddressString));
            }
        }
        //make sure each of the key is dot replaced
        if (!statusPerAddress.isEmpty()) {
            HashMap<String, CommunicationStatus> statusPerAddressCopy = new HashMap<String, CommunicationStatus>();
            for (String key : statusPerAddress.keySet()) {
                statusPerAddressCopy.put(getDotReplacedString(key), statusPerAddress.get(key));
            }
            statusPerAddress = statusPerAddressCopy;
        }
        return statusPerAddress;
    }

    public void setStatusPerAddress(Map<String, CommunicationStatus> statusPerAddress) {

        //make sure each of the key is dot corrected
        if (statusPerAddress != null) {
            this.statusPerAddress = this.statusPerAddress != null ? this.statusPerAddress
                                                                 : new HashMap<String, CommunicationStatus>();
            for (String key : statusPerAddress.keySet()) {
                this.statusPerAddress.put(correctDotReplacedString(key), statusPerAddress.get(key));
            }
        }
        else {
            this.statusPerAddress = statusPerAddress;
        }
    }
    
    /**
     * add a status per address
     * @param address
     * @param status
     */
    public void addStatusForAddress(String address, CommunicationStatus status) {

        AdapterConfig adapter = getAdapter();
        if (adapter != null && (adapter.isCallAdapter() || adapter.isSMSAdapter())) {
            address = PhoneNumberUtils.formatNumber(address, null);
        }
        getStatusPerAddress().put(getDotReplacedString(address), status);
    }
    
    /**
     * add a status all all the address
     * @param addresses
     * @param status
     */
    public void setStatusForAddresses(Collection<String> addresses, CommunicationStatus status) {

        for (String address : addresses) {
            addStatusForAddress(getDotReplacedString(address), CommunicationStatus.ERROR);
        }
    }
    
    /**
     * return status based on address
     * @param address
     * @param status
     */
    public CommunicationStatus getStatusForAddress(String address) {

        return getStatusPerAddress().get(getDotReplacedString(address));
    }
    
    /**
     * gets the direction of this ddrRecord based on the toAddress and the adapter address
     * @return either "inbound" or "outbound"
     */
    @JsonIgnore
    public String getDirection() {

        //if the from address is not equal to the adapter address, its an incoming communication
        if (getToAddress() != null && getAdapter() != null) {
            return getToAddress().containsKey(getAdapter().getFormattedMyAddress()) && getToAddress().size() == 1 ? "inbound"
                : "outbound";
        }
        return null;
    }
    
    /**
     * Reloads the instance from the db
     * @return
     * @throws Exception 
     */
    @JsonIgnore
    public DDRRecord reload() throws Exception {

        return getDDRRecord(getId(), accountId);
    }
    
    /**
     * Adds the given sessionKeys to additionalInfo, and to collection of
     * sessionKeys. If the session is found. adds the external caller id too
     * 
     * @param sessionMap
     */
    @JsonIgnore
    public void setSessionKeysFromMap(Map<String, Session> sessionMap) {

        if (sessionMap != null) {
            HashMap<String, String> sessionKeyMap = new HashMap<String, String>();
            setSessionKeys(new HashSet<String>());
            for (String address : sessionMap.keySet()) {
                Session session = sessionMap.get(address);
                if (session != null) {
                    sessionKeys.add(session.getKey());
                    sessionKeyMap.put(address, session.getKey());
                    if (session.getExternalSession() != null) {
                        addAdditionalInfo("externalSessionKey", session.getExternalSession());
                    }
                }
            }
            addAdditionalInfo(Session.SESSION_KEY, sessionKeyMap);
        }
    }
    
    @JsonIgnore
    public DDRTypeCategory getTypeCategory() {

        DDRType ddrType = DDRType.getDDRType(ddrTypeId);
        return ddrType != null ? ddrType.getCategory() : null;
    }
    
    /**
     * Ideally should be called by the GETTER methods of fields whose dot (.) values are to be
     * replaced by {@link DDRRecord#DOT_REPLACER_KEY}
     * @param data
     * @return
     */
    private String getDotReplacedString(String data) {

        if (Boolean.TRUE.equals(correctDotData)) {
            return data != null ? data.replace(".", DOT_REPLACER_KEY) : null;
        }
        else {
            return correctDotReplacedString(data);
        }
    }
    
    /**
     * Ideally should be called by the SETTER methods of fields whose dot (.) values are 
     * replaced by {@link DDRRecord#DOT_REPLACER_KEY}
     * @param data
     * @return
     */
    private String correctDotReplacedString(String data) {
        return data != null ? data.replace(DOT_REPLACER_KEY, ".") : null;
    }
    
    /**
     * Replace all dots (.) in keys of a map object 
     * @param data
     */
    private Map<String, Object> getDotReplaceKeys(Map<String, Object> data) {

        if (data != null) {
            Map<String, Object> copyOfData = new HashMap<String, Object>();
            for (String key : data.keySet()) {
                copyOfData.put(getDotReplacedString(key), data.get(key));
            }
            return copyOfData;
        }
        return null;
    }
}
