package com.almende.dialog.util;

import java.io.IOException;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.util.ParallelInit;
import com.askfast.commons.entity.ResponseLog;
import com.askfast.commons.utils.PhoneNumberUtils;

public class UtilTest extends TestFramework {

    @Test
    public void testIfValidPhoneNumberTest() throws Exception {

        String formattedNumber = PhoneNumberUtils.formatNumber("0614765852", null);
        Assert.assertEquals("+31614765852", formattedNumber);
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
}
