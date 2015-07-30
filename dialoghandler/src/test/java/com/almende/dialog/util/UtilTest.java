package com.almende.dialog.util;

import java.io.IOException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.util.ParallelInit;
import com.askfast.commons.entity.ResponseLog;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;

public class UtilTest extends TestFramework {

    @Test
    public void testIfValidPhoneNumberTest() throws Exception {

        String formattedNumber = PhoneNumberUtils.formatNumber("0614765852", null);
        Assert.assertEquals("+31614765852", formattedNumber);
    }
    
    @Test
    public void testIfValidGermanLandlinePhoneNumberTest() throws Exception {
        
        String address = "tel:+491739230752";
        String formattedAddress = address.replaceFirst("tel:", "").trim();
        Boolean valid = PhoneNumberUtils.isValidPhoneNumber( formattedAddress );
        Assert.assertTrue( valid );
    }
    
    @Test
    public void testIfValidDutchLandlinePhoneNumberTest() throws Exception {
        
        String address = "tel:+31851234567";
        String formattedAddress = URLDecoder.decode(address.replaceFirst("tel:", "").trim(), "UTF-8");
        Boolean valid = PhoneNumberUtils.isValidPhoneNumber( formattedAddress );
        Assert.assertTrue( valid );
    }

    /**
     * This test checks if the {@link AFHttpClient} logs an error when the url
     * given is invalid
     * 
     * @throws IOException
     */
    @Test
    public void httpClientRequestFailTest() throws Exception {

        AFHttpClient afHttpClient = ParallelInit.getAFHttpClient();
        ResponseLog response = afHttpClient.get("blablatest/blaPathParam", true, UUID.randomUUID().toString(),
                                                TEST_PUBLIC_KEY, UUID.randomUUID().toString());
        Assert.assertThat(response.getHttpCode(), Matchers.is(-1));
    }
    
    /**
     * This test checks if the {@link AFHttpClient} logs an error when the url
     * given is valid
     * @throws IOException
     */
    @Test
    public void httpClientRequestSuccessTest() throws Exception {

        AFHttpClient afHttpClient = ParallelInit.getAFHttpClient();
        ResponseLog response = afHttpClient.get("http://api.ask-fast.com", true, UUID.randomUUID().toString(),
                                                TEST_PUBLIC_KEY, UUID.randomUUID().toString());
        Assert.assertThat(response.getHttpCode(), Matchers.is(200));
        Assert.assertThat(response.getHttpResponseTime(), Matchers.instanceOf(Integer.class));
        Assert.assertThat(response.getHttpResponseTime(), Matchers.notNullValue());
        Assert.assertThat(response.getHeaders().isEmpty(), Matchers.is(false));
    }
    
    @Test
    public void splitTest() {
        List<String> keys = Arrays.asList("A", "B", "C");
        String test = " [[A]] > 2 && ([[B]] + [[C]] > 2) ";
        Pattern compile = Pattern.compile("\\[\\[(.+?)\\]\\]");
        Matcher matcher = compile.matcher(test);
        List<String> result = new ArrayList<String>();
        while (matcher.find()) {
            result.add(matcher.group().replace("[[", "").replace("]]", ""));
        }
        Assert.assertArrayEquals(result.toArray(), keys.toArray());
    }
    
    /**
     * Test to check the utils method that was updated to format the given date
     * in millis directly rather than new Date(millis), this fails in a server
     * environment which doesnt have proper configured date.
     */
    @Test
    public void timeFormatTest() {

        long currentTime = TimeUtils.getServerCurrentTimeInMillis();
        String timeFormat1 = TimeUtils.getStringFormatFromDateTime(currentTime, null);
        String timeformat2 = TimeUtils.getStringFormatFromDateTime(new Date(currentTime).getTime(), null);
        Assert.assertThat(timeFormat1, Matchers.is(timeformat2));

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss Z", Locale.ENGLISH);
        dateFormat.setTimeZone(TimeUtils.getServerTimeZone());
        String timeFormat3 = dateFormat.format(new Date(currentTime));
        Assert.assertThat(timeFormat3, Matchers.is(timeformat2));
    }
}
