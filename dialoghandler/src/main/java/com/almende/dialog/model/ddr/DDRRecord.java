package com.almende.dialog.model.ddr;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bson.types.ObjectId;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;

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
    public String id;
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
    
    public DDRRecord(){}
    
    public DDRRecord( String ddrTypeId, String adapterId, String accountId, Integer quantity )
    {
        this.ddrTypeId = ddrTypeId;
        this.adapterId = adapterId;
        this.accountId = accountId;
        this.quantity = quantity;
    }
    
    public void createOrUpdate()
    {
        id = id != null && !id.isEmpty() ? id : ObjectId.get().toStringMongod();
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        datastore.storeOrUpdate( this );
    }
    
    public static DDRRecord getDDRRecord(String id, String accountId) throws Exception
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        DDRRecord ddrRecord = datastore.load(DDRRecord.class, id);
        if ( ddrRecord != null && ddrRecord.getAccountId() != null && !ddrRecord.getAccountId().equals( accountId ) )
        {
            throw new Exception( String.format( "DDR record: %s is not owned by account: %s", id, accountId ) );
        }
        return ddrRecord;
    }
    
    /**
     * fetch the ddr records based the input parameters. fetches the records that matches to all the 
     * parameters given
     * @param adapterId
     * @param accountId
     * @param fromAddress
     * @param ddrTypeId
     * @param status
     * @return
     */
    public static List<DDRRecord> getDDRRecords( String adapterId, String accountId, String fromAddress,
        String ddrTypeId, CommunicationStatus status )
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<DDRRecord> query = datastore.find().type( DDRRecord.class );
        //fetch accounts that match
        query = query.addFilter( "accountId", FilterOperator.EQUAL, accountId );
        if ( adapterId != null )
        {
            query = query.addFilter( "adapterId", FilterOperator.EQUAL, adapterId );
        }
        if ( fromAddress != null )
        {
            query = query.addFilter( "fromAddress", FilterOperator.EQUAL, fromAddress );
        }
        if ( ddrTypeId != null )
        {
            query = query.addFilter( "ddrTypeId", FilterOperator.EQUAL, ddrTypeId );
        }
        if ( status != null )
        {
            query = query.addFilter( "status", FilterOperator.EQUAL, status.name() );
        }
        return query.now().toArray();
    }
    
    /**
     * fetch the ddr record for a particular Session 
     * @param session
     * @return
     * @throws Exception 
     */
    public static DDRRecord getDDRRecord(String sessionKey) {

        Session session = Session.getSession(sessionKey);
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<DDRRecord> query = datastore.find().type(DDRRecord.class);
        //fetch accounts that match
        query = query.addFilter("accountId", FilterOperator.EQUAL, session.getAccountId());
        if (session.getAdapterID() != null) {
            query = query.addFilter("adapterId", FilterOperator.EQUAL, session.getAdapterID());
        }
        if (session.getDirection() != null) {
            HashMap<String, String> addressMap = new HashMap<String, String>(1);
            if (session.getDirection().equalsIgnoreCase("incoming")) {
                query = query.addFilter("fromAddress", FilterOperator.EQUAL, session.getRemoteAddress());
                addressMap.put(session.getLocalAddress(), "");
                query = query.addFilter("status", FilterOperator.EQUAL, CommunicationStatus.RECEIEVED.name());
            }
            else {
                query = query.addFilter("fromAddress", FilterOperator.EQUAL, session.getLocalAddress());
                addressMap.put(session.getRemoteAddress(), "");
                query = query.addFilter("status", FilterOperator.EQUAL, CommunicationStatus.SENT.name());
            }
            try {
                query = query.addFilter("toAddressString", FilterOperator.EQUAL, ServerUtils.serialize(addressMap));
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe("Error while serializing. Message: "+ e.toString());
            }
        }
        List<DDRRecord> ddrRecordsForSession = query.now().toArray();
        for (DDRRecord ddrRecord : ddrRecordsForSession) {
            //return the ddrRecord whose startTime matches the creationTime or answerTime of the session
            if(ddrRecord.getStart() != null && (ddrRecord.getStart().toString().equals(session.getCreationTimestamp()) ||
                                            ddrRecord.getStart().toString().equals(session.getAnswerTimestamp()))){
                return ddrRecord;
            }
        }
        return null; 
    }
    
    public String getId()
    {
        return id;
    }
    public void setId( String id )
    {
        this.id = id;
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
    
    @JsonProperty("totalCost")
    public Double getTotalCost() throws Exception
    {
        if ( shouldGenerateCosts )
        {
            DDRType ddrType = getDdrType();
            switch ( ddrType.getCategory() )
            {
                case INCOMING_COMMUNICATION_COST:
                case OUTGOING_COMMUNICATION_COST:
                    return DDRUtils.calculateCommunicationDDRCost( this, shouldIncludeServiceCosts );
                case ADAPTER_PURCHASE:
                case SERVICE_COST:
                case SUBSCRIPTION_COST:
                {
                    //fetch the ddrPrice
                    List<DDRPrice> ddrPrices = DDRPrice.getDDRPrices( ddrTypeId, null, adapterId, UnitType.PART, null );
                    if ( ddrPrices != null && !ddrPrices.isEmpty() )
                    {
                        return DDRUtils.calculateDDRCost( this, ddrPrices.iterator().next() );
                    }
                }
                default:
                    break;
            }
        }
        return 0.0;
    }
}
