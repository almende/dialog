package com.almende.dialog.accounts;

import org.junit.Assert;
import org.junit.Test;

import com.almende.dialog.TestFramework;

public class AdapterConfigTest extends TestFramework
{
    @Test
    public void fetchAdapterConfigTest() throws Exception
    {
        AdapterConfig adapterConfig = new AdapterConfig();
        adapterConfig.setPublicKey( "agent1@ask-cs.com" );
        adapterConfig.preferred_language = "nl";
        adapterConfig.initialAgentURL = "http://askfastmarket.appspot.com/resource/dialogobject/dummy2";
        adapterConfig.myAddress = "dialogObject@dialog-handler.appspotchat.com";
        adapterConfig.anonymous = false;
        adapterConfig.adapterType = "XMPP";
        String adapterJson = AdapterConfig.om.writeValueAsString( adapterConfig );
        adapterConfig.createConfig( adapterJson );
        AdapterConfig findAdapterConfig = AdapterConfig.findAdapterConfig( adapterConfig.getAdapterType(), adapterConfig.getMyAddress().toLowerCase(), adapterConfig.getKeyword() );
        Assert.assertTrue( findAdapterConfig != null );
    }
}
