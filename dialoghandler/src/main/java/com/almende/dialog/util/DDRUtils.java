package com.almende.dialog.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.model.ddr.DDRType;
import com.almende.dialog.model.ddr.DDRType.DDRTypeCategory;

/**
 * Helper functions for creating DDR records and processing of them
 * @author Shravan
 */
public class DDRUtils
{
    private static final Logger log = Logger.getLogger( DDRUtils.class.getSimpleName() );
    
    /** creates a DDR Record for this adapterid and owner. <br>
     * Preconditions: <br>
     * 1. expects to find a {@link DDRType} of Type {@link DDRTypeCategory#ADAPTER_PURCHASE} <br>
     * 2. {@link DDRPrice#getUnitType()} = {@link UnitType#PART} in the datastore <br>
     * @param config AdapterConfig having a non-null {@link AdapterConfig#getConfigId()} and {@link AdapterConfig#getOwner()} 
     * @throws Exception
     */
    public static void createDDRRecordOnAdapterPurchase( AdapterConfig config ) throws Exception
    {
        DDRType adapterPurchaseDDRType = DDRType.getDDRType( DDRTypeCategory.ADAPTER_PURCHASE );
        if ( adapterPurchaseDDRType != null )
        {
            log.info( String.format( "Applying charges for account: %s and adapter: %s with address: %s",
                config.getOwner(), config.getConfigId(), config.getMyAddress() ) );
            List<DDRPrice> adapterPurchaseDDRPrices = DDRPrice.getDDRPrices( adapterPurchaseDDRType.getTypeId(), null,
                UnitType.PART );
            if ( adapterPurchaseDDRPrices != null && !adapterPurchaseDDRPrices.isEmpty()
                && config.getConfigId() != null && config.getOwner() != null )
            {
                //applying charges
                DDRPrice ddrPrice = adapterPurchaseDDRPrices.iterator().next();
                DDRRecord ddrRecord = new DDRRecord( adapterPurchaseDDRType.getTypeId(), config.getConfigId(),
                    config.getOwner(), 1, ddrPrice.getPrice() );
                ddrRecord.setStart( TimeUtils.getServerCurrentTimeInMillis() );
                ddrRecord.createOrUpdate();
            }
        }
    }
    
    public static void createDDRRecordOnOutgoingCommunication( AdapterConfig config, UnitType unitType,
        Map<String, String> toAddress ) throws Exception
    {
        createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, unitType, toAddress,
            CommunicationStatus.SENT );
    }
    
    public static void createDDRRecordOnIncomingCommunication( AdapterConfig config, UnitType unitType,
        String fromAddress ) throws Exception
    {
        Map<String, String> fromAddresses = new HashMap<String, String>();
        fromAddresses.put( fromAddress, "" );
        createDDRRecordOnCommunication( config, DDRTypeCategory.OUTGOING_COMMUNICATION_COST, unitType, fromAddresses,
            CommunicationStatus.SENT );
    }
    
    private static void createDDRRecordOnCommunication( AdapterConfig config, DDRTypeCategory category,
        UnitType unitType, Map<String, String> addresses, CommunicationStatus status ) throws Exception
    {
        DDRType communicationCostDDRType = DDRType.getDDRType( category );
        if ( communicationCostDDRType != null )
        {
            log.info( String.format( "Applying charges for account: %s and adapter: %s with address: %s",
                config.getOwner(), config.getConfigId(), config.getMyAddress() ) );
            List<DDRPrice> communicationDDRPrices = DDRPrice.getDDRPrices( communicationCostDDRType.getTypeId(), null,
                unitType );
            if ( communicationDDRPrices != null && !communicationDDRPrices.isEmpty() && config.getConfigId() != null
                && config.getOwner() != null )
            {
                //applying charges
                DDRPrice ddrPrice = communicationDDRPrices.iterator().next();
                DDRRecord ddrRecord = new DDRRecord( communicationCostDDRType.getTypeId(), config.getConfigId(),
                    config.getOwner(), 1, ddrPrice.getPrice() );
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
                ddrRecord.setQuantity( addresses.size() );
                ddrRecord.setStatus( status );
                ddrRecord.createOrUpdate();
            }
        }
    }
}
