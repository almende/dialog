
package com.almende.dialog.util;


import java.util.Locale;
import java.util.logging.Logger;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder;


public class PhoneNumberUtils
{
    private static final Logger log = Logger.getLogger( PhoneNumberUtils.class.getName() );
    private static ThreadLocal<PhoneNumberUtil> phoneNumberUtil = new ThreadLocal<PhoneNumberUtil>();

    /**
     * returns the PhoneNumberType based on the opensource library:
     * libphonenumber
     * 
     * @param phoneNumber
     * @return
     * @throws ASKFastCheckedException
     */
    public static PhoneNumberType getPhoneNumberType( String phoneNumber )
    {
        try
        {
            return getPhoneNumberUtil().getNumberType( getPhoneNumberProto( phoneNumber, null ) );
        }
        catch ( Exception e )
        {
            log.severe( String.format( "PHONE number parsing threw an exception for: %s with message: %s", phoneNumber,
                e.getMessage() ) );
            return PhoneNumberType.UNKNOWN;
        }
    }

    public static PhoneNumber getPhoneNumberProto( String phoneNumber, String locale ) throws Exception
    {
        locale = locale != null ? locale : "NL";
        PhoneNumber numberProto;
        try
        {
            numberProto = getPhoneNumberUtil().parse( phoneNumber, locale );
        }
        catch ( NumberParseException e )
        {
            log.severe( String.format( "Unable to fetch number type for number: %s", phoneNumber ) );
            throw e;
        }
        return numberProto;
    }

    /**
     * returns the phone number in the format 00 + Country_Code + National_Number
     * @param phoneNumber
     * @return
     * @throws NumberParseException 
     */
    public static String formatNumber( String phoneNumber, PhoneNumberFormat phoneNumberFormat ) throws Exception
    {
        PhoneNumber parse = getPhoneNumberProto( phoneNumber, null );
        phoneNumberFormat = phoneNumberFormat != null ? phoneNumberFormat : PhoneNumberFormat.E164;
        String formattedNumber = getPhoneNumberUtil().format( parse, phoneNumberFormat );
        if ( formattedNumber != null && getPhoneNumberType( formattedNumber ) != PhoneNumberType.UNKNOWN )
        {
            return formattedNumber.replaceFirst( "\\+", "00" ).replace( "-", "" );
        }
        else
        {
            String error = String.format( "Phonenumber: %s is not valid", phoneNumber );
            log.severe( error );
            throw new Exception( error );
        }
    }

    public static String getPhoneNumberGeocode( String phoneNumber, Locale locale ) throws Exception
    {
        locale = locale != null ? locale : new Locale( "en", "NL" );
        PhoneNumberOfflineGeocoder geocoder = PhoneNumberOfflineGeocoder.getInstance();
        return geocoder.getDescriptionForNumber( getPhoneNumberProto( phoneNumber, null ), locale );
    }

    private static PhoneNumberUtil getPhoneNumberUtil()
    {
        if ( phoneNumberUtil.get() == null )
        {
            phoneNumberUtil.set( PhoneNumberUtil.getInstance() );
        }
        return phoneNumberUtil.get();
    }
}
