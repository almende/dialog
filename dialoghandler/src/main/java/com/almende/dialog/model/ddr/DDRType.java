package com.almende.dialog.model.ddr;

import java.util.List;

import org.bson.types.ObjectId;

import com.almende.dialog.util.ServerUtils;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * This is the Type/category for the price charged.
 * E.g. specific types can be used for: Buying a number, charge for a Call, Monthly subscription fee etc   
 * @author Shravan
 */
public class DDRType
{
    /**
     * category of this type
     */
    public enum DDRTypeCategory
    {
        /**
         * used when an adapter is purchased
         */
        ADAPTER_PURCHASE,
        /**
         * incoming communication cost applied
         */
        INCOMING_COMMUNICATION_COST,
        /**
         * outgoing communication cost applied
         */
        OUTGOING_COMMUNICATION_COST,
        /**
         * service cost applied for performing/completing one dialog scenario
         */
        SERVICE_COST,
        /**
         * cost applied for an adapter on a repeating basis. E.g. monthly basis
         */
        SUBSCRIPTION_COST,
        /**
         * any other subsription cost
         */
        OTHER;

        @JsonCreator
        public static DDRTypeCategory fromJson( String name )
        {
            return valueOf( name.toUpperCase() );
        }
    }
    
    public DDRType() {}
    
    @Id
    public String typeId = null;
    String name = null;
    DDRTypeCategory category = null;
    
    /**
     * create (if missing) or updates this document instance
     * @throws Exception throws an Exception if more than one DDRTypes are existing for this category
     */
    public void createOrUpdate() throws Exception
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        DDRType ddrType = getDDRType( category );
        if ( ddrType != null )
        {
            ddrType.name = this.name;
            datastore.update( ddrType );
        }
        else 
        {
            typeId = typeId != null && !typeId.isEmpty() ? typeId : ObjectId.get().toStringMongod();
            datastore.storeOrUpdate( this );
        }
    }
    
    /**
     * fetch the ddr type from the datastore
     * @param id
     * @return
     * @throws Exception
     */
    public static DDRType getDDRType(String id)
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.load(DDRType.class, id);
    }
    
    /**
     * fetch the ddr type from the datastore based on the category it belongs to
     * @param category
     * @return
     * @throws Exception
     */
    public static DDRType getDDRType( DDRTypeCategory category ) throws Exception
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<DDRType> cmd = datastore.find().type( DDRType.class )
            .addFilter( "category", FilterOperator.EQUAL, category.name() );
        List<DDRType> ddrTypes = cmd.now().toArray();
        if ( ddrTypes != null && ddrTypes.size() > 1 )
        {
            throw new Exception( String.format( "Multiple DDRTypes found with same category: %s. DDRTypes: %s",
                category, ServerUtils.serialize( ddrTypes ) ) );
        }
        return ddrTypes != null && !ddrTypes.isEmpty() ? ddrTypes.iterator().next() : null;
    }
    
    /**
     * get all the ddr types
     * @return
     * @throws Exception
     */
    public static List<DDRType> getAllDDRTypes()
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.find().type( DDRType.class ).now().toArray();
    }
    
    public String getTypeId()
    {
        return typeId;
    }
    public void setTypeId( String typeId )
    {
        this.typeId = typeId;
    }
    
    public String getName()
    {
        return name;
    }
    
    public void setName( String name )
    {
        this.name = name;
    }
    
    public DDRTypeCategory getCategory()
    {
        return category;
    }
    
    public void setCategory( DDRTypeCategory category )
    {
        this.category = category;
    }    
}
