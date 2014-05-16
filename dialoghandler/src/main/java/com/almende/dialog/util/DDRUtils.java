package com.almende.dialog.util;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.AdapterType;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * Helper functions for creating DDR records and processing of them
 * @author Shravan
 */
public class DDRUtils
{
    private static final Logger log = Logger.getLogger( DDRUtils.class.getSimpleName() );
    //create a single static connection for publishing ddrs
    private static ConnectionFactory rabbitMQConnectionFactory;
    private static final String PUBLISH_QUEUE_NAME = "DDR_PUBLISH_QUEUE";
    
    /** creates a DDR Record for this adapterid and owner. <br>
     * Preconditions: <br>
     * 1. expects to find a {@link DDRType} of Type {@link DDRTypeCategory#ADAPTER_PURCHASE} <br>
     * 2. {@link DDRPrice#getUnitType()} = {@link UnitType#PART} in the datastore <br>
     * @param config AdapterConfig having a non-null {@link AdapterConfig#getConfigId()} and {@link AdapterConfig#getOwner()} 
     * @throws Exception
     */
    public static double createDDRRecordOnAdapterPurchase( AdapterConfig config ) throws Exception
    {
        DDRType adapterPurchaseDDRType = DDRType.getDDRType( DDRTypeCategory.ADAPTER_PURCHASE );
        if ( adapterPurchaseDDRType != null )
        {
            log.info( String.format( "Applying charges for account: %s and adapter: %s with address: %s",
                config.getOwner(), config.getConfigId(), config.getMyAddress() ) );
            List<DDRPrice> adapterPurchaseDDRPrices = DDRPrice.getDDRPrices( adapterPurchaseDDRType.getTypeId(),
                AdapterType.getByValue( config.getAdapterType() ), config.getConfigId() );
            if ( adapterPurchaseDDRPrices != null && !adapterPurchaseDDRPrices.isEmpty()
                && config.getConfigId() != null && config.getOwner() != null )
            {
                //applying charges
                DDRPrice ddrPrice = adapterPurchaseDDRPrices.iterator().next();
                DDRRecord ddrRecord = new DDRRecord( adapterPurchaseDDRType.getTypeId(), config.getConfigId(),
                    config.getOwner(), 1, ddrPrice.getPrice() );
                ddrRecord.setStart( TimeUtils.getServerCurrentTimeInMillis() );
                ddrRecord.createOrUpdate();
                return ddrRecord.getTotalCost();
            }
        }
        log.warning( String.format( "Not charging this adapter purchase with address: %s adapterid: %s anything!!",
            config.getMyAddress(), config.getConfigId() ) );
        return 0.0;
    }
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config. picks the quantify from the toAddress map 
     * @param config
     * @param unitType
     * @param toAddress
     * @throws Exception
     */
    public static double createDDRRecordOnOutgoingCommunication( AdapterConfig config, Map<String, String> toAddress )
    throws Exception
    {
        return createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, toAddress,
            CommunicationStatus.SENT );
    }
    
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config and the quantity 
     * @param config
     * @param toAddress
     * @param quantity no of units to be charged. Typically for the SMS, it depends on the length of the SMS text and 
     * the address list
     * @throws Exception
     */
    public static double createDDRRecordOnOutgoingCommunication( AdapterConfig config, String toAddress, int quantity )
    throws Exception
    {
        HashMap<String, String> toAddressMap = new HashMap<String, String>();
        toAddressMap.put( toAddress, "" );
        return createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, toAddressMap,
            CommunicationStatus.SENT, quantity );
    }
    
    /**
     * creates a ddr record for incoming communication charges
     * @param config
     * @param fromAddress
     * @param quantity
     * @throws Exception
     */
    public static double createDDRRecordOnIncomingCommunication( AdapterConfig config, String fromAddress, int quantity )
    throws Exception
    {
        HashMap<String, String> fromAddressMap = new HashMap<String, String>();
        fromAddressMap.put( fromAddress, "" );
        return createDDRRecordOnCommunication( config, DDRTypeCategory.INCOMING_COMMUNICATION_COST, fromAddressMap,
            CommunicationStatus.RECEIEVED, quantity );
    }
    
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config and the quantity based 
     * on the number of recepients in the toAddress 
     * @param config
     * @param unitType
     * @param toAddress 
     * the address list
     * @throws Exception
     */
    public static double createDDRRecordOnOutgoingCommunication( AdapterConfig config, UnitType unitType,
        Map<String, String> toAddress) throws Exception
    {
        return createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, toAddress,
            CommunicationStatus.SENT, toAddress.size() );
    }
    
    /**
     * creates a ddr record based on the adapterId and accoutnId from config and the quantity 
     * @param config
     * @param unitType
     * @param toAddress
     * @param quantity no of units to be charged. Typically for the SMS, it depends on the length of the SMS text and 
     * the address list
     * @throws Exception
     */
    public static double createDDRRecordOnOutgoingCommunication( AdapterConfig config, UnitType unitType,
        Map<String, String> toAddress, int quantity ) throws Exception
    {
        return createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, toAddress,
            CommunicationStatus.SENT, quantity );
    }
    
    public static double createDDRRecordOnIncomingCommunication( AdapterConfig config, String fromAddress )
    throws Exception
    {
        Map<String, String> fromAddresses = new HashMap<String, String>();
        fromAddresses.put( fromAddress, "" );
        return createDDRRecordOnCommunication( config, DDRTypeCategory.INCOMING_COMMUNICATION_COST, fromAddresses,
            CommunicationStatus.RECEIEVED );
    }
    
    /**
     * updates the DDR record matching the adapter, category and the status
     * @param config
     * @param category
     * @param status
     * @param remoteID
     * @param startTime actual call start timestamp
     * @param answerTime actual call answer timestamp
     * @param releaseTime actual call hangup timestamp
     * @throws Exception
     */
    public static double updateDDRRecordOnCallStops( AdapterConfig config, DDRTypeCategory category,
        CommunicationStatus status, String remoteID, Long startTime, Long answerTime, Long releaseTime ) throws Exception
    {
        DDRType communicationCostDDRType = DDRType.getDDRType( category );
        if ( communicationCostDDRType != null )
        {
            log.info( String.format( "Applying charges for account: %s and adapter: %s with address: %s",
                config.getOwner(), config.getConfigId(), config.getMyAddress() ) );
            List<DDRPrice> communicationDDRPrices = DDRPrice.getDDRPrices( communicationCostDDRType.getTypeId(),
                AdapterType.getByValue( config.getAdapterType() ), config.getConfigId() );
            if ( communicationDDRPrices != null && !communicationDDRPrices.isEmpty() && config.getConfigId() != null
                && config.getOwner() != null )
            {
                //applying charges
                DDRPrice ddrPrice = communicationDDRPrices.iterator().next();
                List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords( config.getConfigId(), config.getOwner(),
                    config.getMyAddress(), communicationCostDDRType.getTypeId(), status );
                if(ddrRecords != null && !ddrRecords.isEmpty())
                {
                    DDRRecord ddrRecord = ddrRecords.iterator().next();
                    if(answerTime != null)
                    {
                        ddrRecord.setStart( answerTime );
                    }
                    Long duration = ( releaseTime != null ? releaseTime : TimeUtils.getServerCurrentTimeInMillis() )
                        - ddrRecord.getStart();
                    ddrRecord.setDuration( duration );
                    Long totalTime = null;
                    switch ( ddrPrice.getUnitType() ) 
                    {
                        case SECOND:
                            totalTime = duration / 1000; //in secs
                            break;
                        case MINUTE:
                            totalTime = duration / (60 * 1000); //in mins
                            break;
                        case HOUR:
                            totalTime = duration / ( 60 * 60 * 1000); //in hrs
                            break;
                        case DAY:
                            totalTime = duration / ( 24* 60 * 60 * 1000); //in days
                            break;
                        case MONTH:
                            int monthOfYear = TimeUtils.getServerCurrentTime().getMonthOfYear();
                            int totalDays = Calendar.getInstance( TimeUtils.getServerTimeZone() ).getActualMaximum(
                                monthOfYear );
                            totalTime = duration / ( totalDays * 24 * 60 * 60 * 1000 ); //in months
                            break;
                        default:
                            throw new Exception( "Update ddr not implemented for this UnitType: "
                                + ddrPrice.getUnitType() );
                    }
                    double noOfComsumedUnits = totalTime / ((double)ddrPrice.getUnits());
                    ddrRecord.setTotalCost( noOfComsumedUnits * ddrPrice.getPrice() );
                    ddrRecord.createOrUpdate();
                    return ddrRecord.getTotalCost();
                }
            }
        }
        return 0.0;
    }
    
    public static void publishDDREntryToQueue( String accountId, double totalCost ) throws Exception
    {
        try
        {
            rabbitMQConnectionFactory = rabbitMQConnectionFactory != null ? rabbitMQConnectionFactory : new ConnectionFactory();
            rabbitMQConnectionFactory.setHost( "localhost" );
            Connection connection = rabbitMQConnectionFactory.newConnection();
            Channel channel = connection.createChannel();
            //create a message
            HashMap<String, String> message = new HashMap<String, String>();
            message.put( "accountId", accountId );
            message.put( "cost", String.valueOf( totalCost ) );
            channel.queueDeclare( PUBLISH_QUEUE_NAME, false, false, false, null );
            log.info( String.format( "Publishing costs: %s for account: %s", totalCost, accountId ) );
            channel.basicPublish( "", PUBLISH_QUEUE_NAME, null, ServerUtils.serialize( message )
                .getBytes() );
            channel.close();
            connection.close();
        }
        catch ( Exception e )
        {
            log.severe( "Error seen: " + e.getLocalizedMessage() );
        }
    }
    
    /**
     * creates a ddr record based on the input parameters and quantity is the size of the addresses map
     * @param config
     * @param category
     * @param unitType
     * @param addresses
     * @param status
     * @throws Exception
     */
    private static double createDDRRecordOnCommunication( AdapterConfig config, DDRTypeCategory category,
        Map<String, String> addresses, CommunicationStatus status ) throws Exception
    {
        return createDDRRecordOnCommunication( config, category, addresses, status, addresses.size() );
    }
    
    /**
     * creates a ddr record based on the input parameters
     * @param config picks the adpaterId, owner and myAddress from this 
     * @param category 
     * @param unitType
     * @param addresses
     * @param status
     * @param quantity
     * @throws Exception
     */
    private static double createDDRRecordOnCommunication( AdapterConfig config, DDRTypeCategory category,
        Map<String, String> addresses, CommunicationStatus status, int quantity ) throws Exception
    {
        DDRType communicationCostDDRType = DDRType.getDDRType( category );
        if ( communicationCostDDRType != null )
        {
            log.info( String.format( "Applying charges for account: %s and adapter: %s with address: %s",
                config.getOwner(), config.getConfigId(), config.getMyAddress() ) );
            List<DDRPrice> communicationDDRPrices = DDRPrice.getDDRPrices( communicationCostDDRType.getTypeId(),
                AdapterType.getByValue( config.getAdapterType() ), config.getConfigId() );
            if ( communicationDDRPrices != null && !communicationDDRPrices.isEmpty() && config.getConfigId() != null
                && config.getOwner() != null )
            {
                //applying charges
                DDRPrice ddrPrice = communicationDDRPrices.iterator().next();
                DDRRecord ddrRecord = new DDRRecord( communicationCostDDRType.getTypeId(), config.getConfigId(),
                    config.getOwner(), 1, ddrPrice.getPrice() * quantity );
                ddrRecord.setStart( TimeUtils.getServerCurrentTimeInMillis() );
                switch ( status )
                {
                    case SENT:
                        ddrRecord.setFromAddress( config.getMyAddress() );
                        ddrRecord.setToAddress( addresses );
                        break;
                    case RECEIEVED:
                        ddrRecord.setFromAddress( addresses.keySet().iterator().next() );
                        Map<String, String> toAddresses = new HashMap<String, String>();
                        toAddresses.put( config.getMyAddress(), config.getAddress() );
                        ddrRecord.setToAddress( toAddresses );
                        break;
                    default:
                        throw new Exception("Unknown CommunicationStatus seen: "+ status.name());
                }
                ddrRecord.setQuantity( quantity );
                ddrRecord.setStatus( status );
                ddrRecord.createOrUpdate();
                return ddrRecord.getTotalCost();
            }
        }
        log.warning( String.format( "Not charging this communication from: %s adapterid: %s anything!!",
            config.getMyAddress(), config.getConfigId() ) );
        return 0.0;
    }
}
