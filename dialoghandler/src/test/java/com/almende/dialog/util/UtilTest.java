package com.almende.dialog.util;

import org.junit.Assert;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.askfast.commons.utils.PhoneNumberUtils;

public class UtilTest extends TestFramework
{
    @Test
    public void testIfValidPhoneNumberTest() throws Exception
    {
        String formattedNumber = PhoneNumberUtils.formatNumber( "0614765852", null );
        Assert.assertEquals( "+31614765852", formattedNumber );
    }
}
