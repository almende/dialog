
package com.almende.dialog.adapter;


import java.lang.reflect.Method;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import com.almende.dialog.Settings;
import com.almende.dialog.TestFramework;
import com.almende.dialog.adapter.tools.CMStatus;
import com.askfast.commons.utils.PhoneNumberUtils;


public class CMServletTest extends TestFramework
{
    @Test
    public void hostInSMSReferenceIsParsedTest() throws Exception {

        String remoteNumber = PhoneNumberUtils.formatNumber(remoteAddressVoice, null);
        String reference = CMStatus.generateSMSReferenceKey(UUID.randomUUID().toString(), "0031636465236");
        Assert.assertThat(CMStatus.getHostFromReference(reference), Matchers.is("http://" + Settings.HOST));
        String testXml = getTestSMSStatusXML(remoteNumber, reference);
        Method handleStatusReport = fetchMethodByReflection("handleDeliveryStatusReport", CMSmsServlet.class,
                                                            String.class);
        CMSmsServlet cmSmsServlet = new CMSmsServlet();
        Object reportReply = invokeMethodByReflection(handleStatusReport, cmSmsServlet, testXml);
        Assert.assertThat(reportReply, Matchers.nullValue());
    }
    
    private String getTestSMSStatusXML(String to, String reference)
    {
        return "<?xml version=\"1.0\"?> \r\n<MESSAGES SENT=\"2009-06-15T13:45:30\" > \r\n <MSG RECEIVED="
        + "\"2009-06-15T13:45:30\" > \r\n <TO>" + to + "</TO> "
        + "\r\n <REFERENCE>"+ reference +"</REFERENCE> "
        + "\r\n <STATUS> \r\n <CODE>200</CODE> \r\n <ERRORCODE>0</ERRORCODE> "
        + "\r\n <ERRORDESCRIPTION>No Error</ERRORDESCRIPTION> \r\n </STATUS> \r\n </MSG> \r\n</MESSAGES>";
    }
}
