package com.almende.dialog.util;

import java.util.List;
import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.ddr.DDRPrice;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
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
}
