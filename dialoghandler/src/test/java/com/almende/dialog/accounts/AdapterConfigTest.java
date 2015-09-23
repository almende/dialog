package com.almende.dialog.accounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.util.AFHttpClient;
import com.almende.util.ParallelInit;
import com.almende.util.jackson.JOM;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.askfast.commons.Status;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.squareup.okhttp.Credentials;

public class AdapterConfigTest extends TestFramework
{
    @Test
    public void fetchAdapterConfigTest() throws Exception
    {
        AdapterConfig adapterConfig = new AdapterConfig();
        adapterConfig.setPublicKey( TEST_PUBLIC_KEY );
        adapterConfig.preferred_language = "nl";
        adapterConfig.initialAgentURL = "http://askfastmarket.appspot.com/resource/dialogobject/dummy2";
        adapterConfig.myAddress = "dialogObject@dialog-handler.appspotchat.com";
        adapterConfig.anonymous = false;
        adapterConfig.adapterType = "XMPP";
        String adapterJson = AdapterConfig.om.writeValueAsString( adapterConfig );
        adapterConfig.createConfig( adapterJson );
        AdapterConfig findAdapterConfig = AdapterConfig.findAdapterConfig( adapterConfig.getAdapterType(), adapterConfig.getMyAddress().toLowerCase(), adapterConfig.getKeyword() );
        assertTrue( findAdapterConfig != null );
    }
    
    /**
     * This test check if the adapter having a particular owner is fetched properly
     * @throws Exception 
     */
    @Test
    public void adapterFetchByOwnerTest() throws Exception {

        String accountID = UUID.randomUUID().toString();
        createAdapterWithOwnerAndSharedAccount(accountID, null);
        ArrayList<AdapterConfig> allAdapters = AdapterConfig.findAdapterByAccount(accountID);
        assertTrue(allAdapters.size() == 1);
        assertTrue(allAdapters.iterator().next().getOwner().equals(accountID));
    }
    
    /**
     * This test check if the adapter having a particular owner is fetched properly by using the 
     * findAdapterByAccount method
     * @throws Exception 
     */
    @Test
    public void adapterFetchBySharedAccountsTest() throws Exception {

        String linkedAccountId = UUID.randomUUID().toString();
        createAdapterWithOwnerAndSharedAccount(UUID.randomUUID().toString(), Arrays.asList(linkedAccountId));
        ArrayList<AdapterConfig> allAdapters = AdapterConfig.findAdapterByAccount(linkedAccountId);
        assertTrue(allAdapters.size() == 1);
        assertTrue(allAdapters.iterator().next().getAccounts().contains(linkedAccountId));
    }
    
    /**
     * This test check if the adapter having an owner and a shared linked account is not fetched 
     * by the findAdapterByOwner method with the shared linked accoaunt.
     * @throws Exception 
     */
    @Test
    public void adapterFetchByOwnedAccountsOnlyTest() throws Exception {

        String linkedAccountId = UUID.randomUUID().toString();
        createAdapterWithOwnerAndSharedAccount(UUID.randomUUID().toString(), Arrays.asList(linkedAccountId));
        
        //fetch all adapters to verify that indeed one adapter is created
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<AdapterConfig> cmd = datastore.find().type(AdapterConfig.class);
        List<AdapterConfig> allAdapters = cmd.now().toArray();
        assertTrue(allAdapters.size() == 1);
        
        //assert that fetch by linked accounts fetches no adapters
        allAdapters = AdapterConfig.findAdapterByOwner(linkedAccountId, null, null);
        assertTrue(allAdapters.size() == 0);
    }

    /**
     * This test check if the adapter having an owner and a shared linked account is fetched 
     * by the findAdapterByAccount method with the shared linked accoaunt.
     * @throws Exception 
     */
    @Test
    public void adapterFetchBySharedAccountTest() throws Exception {

        String linkedAccountId = UUID.randomUUID().toString();
        //create adapters
        createAdapterWithOwnerAndSharedAccount(UUID.randomUUID().toString(), Arrays.asList(linkedAccountId));
        
        //assert that fetch by linked accounts fetches adapters
        ArrayList<AdapterConfig> allAdapters = AdapterConfig.findAdapterByAccount(linkedAccountId, null, null);
        assertTrue(allAdapters.size() == 1);
        assertTrue(allAdapters.iterator().next().getAccounts().contains(linkedAccountId));
    }
    
    /**
     * This test check if the adapter having only a shared account while creation, has a an owner too. 
     * And is fetched by both the findAdapterByOwner and findAdapterByAccount method.
     * @throws Exception 
     */
    @Test
    public void adapterWithNoOwnerFetchBySharedAccountTest() throws Exception {

        String accountId = UUID.randomUUID().toString();
        //create adapters
        createAdapterWithOwnerAndSharedAccount(null, Arrays.asList(accountId));
        
        //assert that fetch by linked accounts fetches 1 adapters
        ArrayList<AdapterConfig> allAdapters = AdapterConfig.findAdapterByAccount(accountId, null, null);
        assertTrue(allAdapters.size() == 1);
        assertTrue(allAdapters.iterator().next().getAccounts().contains(accountId));
        
        //assert that fetch by linked accounts fetches adapters
        allAdapters = AdapterConfig.findAdapterByOwner(accountId, null, null);
        assertTrue(allAdapters.size() == 1);
        assertTrue(allAdapters.iterator().next().getOwner().equals(accountId));
    }
    
    /**
     * This test check if the adapter is made free, when removing its sole owner.
     * @throws Exception 
     */
    @Test
    public void adapterUnlinkOwnerTest() throws Exception {

        String accountId = UUID.randomUUID().toString();
        //create adapters
        AdapterConfig adapter = createAdapterWithOwnerAndSharedAccount(null, Arrays.asList(accountId));
        
        //assert that fetch by linked accounts fetches adapters
        ArrayList<AdapterConfig> allAdapters = AdapterConfig.findAdapterByAccount(accountId, null, null);
        assertTrue(allAdapters.size() == 1);
        assertTrue(allAdapters.iterator().next().getAccounts().contains(accountId));
        
        AdapterAgent adapterAgent = new AdapterAgent();
        adapterAgent.removeAdapter(adapter.getConfigId(), accountId);
        
        //fetch the adapter again
        ArrayNode freeAdapterNodes = adapterAgent.findFreeAdapters(null, null);
        Collection<AdapterConfig> freeAdapters = JOM.getInstance()
                                        .convertValue(freeAdapterNodes, new TypeReference<Collection<AdapterConfig>>() {});
        assertTrue(freeAdapters.size() == 1);
    }
    
    /**
     * This test checks if the adapter is only unliked of a shared account, when removing it.
     * @throws Exception 
     */
    @Test
    public void adapterUnlinkSharedAccountTest() throws Exception {

        Collection<String> linkedAccountIds = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        String ownerID = UUID.randomUUID().toString();
        //create adapters
        AdapterConfig adapter = createAdapterWithOwnerAndSharedAccount(ownerID, linkedAccountIds);
        
        //assert that fetch by linked accounts fetches no adapters
        ArrayList<AdapterConfig> allAdapters = AdapterConfig.findAdapterByAccount(linkedAccountIds.iterator().next(),
                                                                                  null, null);
        assertTrue(allAdapters.size() == 1);
        assertTrue(allAdapters.iterator().next().getAccounts().equals(linkedAccountIds));
        
        AdapterAgent adapterAgent = new AdapterAgent();
        String removedAccountId = linkedAccountIds.iterator().next();
        adapterAgent.removeAdapter(adapter.getConfigId(), removedAccountId);
        
        //fetch the adapter again by the removed accountId
        ArrayNode adapterNodes = adapterAgent.getAdapters(removedAccountId, null, null);
        allAdapters = JOM.getInstance().convertValue(adapterNodes, new TypeReference<Collection<AdapterConfig>>() {});
        assertEquals(0, allAdapters.size());
        
        //fetch the adapter again by the owner accountId
        adapterNodes = adapterAgent.getAdapters(ownerID, null, null);
        allAdapters = JOM.getInstance().convertValue(adapterNodes, new TypeReference<Collection<AdapterConfig>>() {});
        assertEquals(1, allAdapters.size());
        
        //check if the unlinked adapter is still ACTIVE
        adapter = allAdapters.get(0);
        assertEquals(Status.ACTIVE, adapter.getStatus());
    }
    
    /**
     * This test checks if the adapter is only unliked of a shared account, when
     * removing it.
     * 
     * @throws Exception
     */
    @Test
    public void adapterUnlinkSharedPrivateAccountTest() throws Exception {

        Collection<String> linkedAccountIds = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        String ownerID = UUID.randomUUID().toString();
        //create adapters
        AdapterConfig adapter = createAdapterWithOwnerAndSharedAccount(ownerID, linkedAccountIds);
        adapter.markAsPrivate();
        adapter.update();

        //assert that fetch by linked accounts fetches no adapters
        ArrayList<AdapterConfig> allAdapters = AdapterConfig.findAdapterByAccount(linkedAccountIds.iterator().next(),
                                                                                  null, null);
        assertTrue(allAdapters.size() == 1);
        assertTrue(allAdapters.iterator().next().getAccounts().equals(linkedAccountIds));

        AdapterAgent adapterAgent = new AdapterAgent();
        String removedAccountId = linkedAccountIds.iterator().next();
        adapterAgent.removeAdapter(adapter.getConfigId(), removedAccountId);

        //fetch the adapter again by the removed accountId
        ArrayNode adapterNodes = adapterAgent.getAdapters(removedAccountId, null, null);
        allAdapters = JOM.getInstance().convertValue(adapterNodes, new TypeReference<Collection<AdapterConfig>>() {
        });
        assertEquals(0, allAdapters.size());

        //fetch the adapter again by the owner accountId
        adapterNodes = adapterAgent.getAdapters(ownerID, null, null);
        allAdapters = JOM.getInstance().convertValue(adapterNodes, new TypeReference<Collection<AdapterConfig>>() {
        });
        assertEquals(1, allAdapters.size());
        assertEquals(true, allAdapters.iterator().next().isPrivate());

        //check if the unlinked adapter is still ACTIVE
        adapter = allAdapters.get(0);
        assertEquals(Status.ACTIVE, adapter.getStatus());

        //remove the adapter as the owner
        adapterAgent.removeAdapter(adapter.getConfigId(), ownerID);
        adapterNodes = adapterAgent.getAdapters(ownerID, null, null);
        allAdapters = JOM.getInstance().convertValue(adapterNodes, new TypeReference<Collection<AdapterConfig>>() {
        });
        assertEquals(0, allAdapters.size());
        assertEquals(null, AdapterConfig.getAdapterConfig(adapter.getConfigId()));
    }
    
    /**
     * This test checks if the adapter is free when it has linked accounts too.
     * @throws Exception 
     */
    @Test
    public void adapterUnlinkOwnerAccountTest() throws Exception {

        Collection<String> linkedAccountIds = Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        //create adapters
        String ownerAccountId = UUID.randomUUID().toString();
        AdapterConfig adapter = createAdapterWithOwnerAndSharedAccount(ownerAccountId, linkedAccountIds);
        
        //assert that fetch by linked accounts fetches adapters
        ArrayList<AdapterConfig> allAdapters = AdapterConfig.findAdapterByAccount(linkedAccountIds.iterator().next(),
                                                                                  null, null);
        assertTrue(allAdapters.size() == 1);
        assertTrue(allAdapters.iterator().next().getAccounts().equals(linkedAccountIds));
        
        AdapterAgent adapterAgent = new AdapterAgent();
        adapterAgent.removeAdapter(adapter.getConfigId(), ownerAccountId);
        
        //fetch the adapter again by the owner Account id
        ArrayNode adapterNodes = adapterAgent.getAdapters(ownerAccountId, null, null);
        allAdapters = JOM.getInstance().convertValue(adapterNodes, new TypeReference<Collection<AdapterConfig>>() {});
        assertTrue(allAdapters.size() == 0);
        
        //assert that fetch by linked accounts fetches no adapters
        allAdapters = AdapterConfig.findAdapterByAccount(linkedAccountIds.iterator().next(),
                                                                                  null, null);
        assertTrue(allAdapters.size() == 0);
        
        //assert that the adapter is free now
        ArrayNode freeAdapters = adapterAgent.findFreeAdapters(null, null);
        allAdapters = JOM.getInstance().convertValue(freeAdapters, new TypeReference<Collection<AdapterConfig>>() {});
        assertTrue(allAdapters.size() == 1);
    }
    
    /**
     * Check if an BROADSOFT adapter is correctly recognized as a calling adapter
     * @throws Exception 
     */
    @Test
    public void broadsoftAdapterProviderMatchTest() throws Exception {

        AdapterConfig adapter = createAdapterConfig(AdapterType.CALL.getName(), AdapterProviders.BROADSOFT,
                                                    TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft,
                                                    "");
        assertTrue(adapter.isCallAdapter());
        assertTrue(AdapterProviders.isCallAdapter(adapter.getProvider().toString()));
    }
    
    /**
     * Check if a call adapter with broadsoft as a provider in the mediaproperty
     * is correctly recognized as a calling adapter
     * 
     * @throws Exception
     */
    @Test
    public void broadsoftInMediaPropertiesMatchTest() throws Exception {

        AdapterConfig adapter = createAdapterConfig(AdapterType.CALL.getName(), AdapterProviders.BROADSOFT,
                                                    TEST_ACCOUNT_ID, localAddressBroadsoft, localFullAddressBroadsoft,
                                                    "");
        assertTrue(AdapterProviders.isCallAdapter(adapter.getProvider().toString()));
        adapter.setAdapterType(AdapterType.CALL.toString());
        adapter.update();
        assertTrue(adapter.isCallAdapter());
    }
    
    /**
     * Check if an CM adapter is correctly recognized as a SMS adapter
     * @throws Exception 
     */
    @Test
    public void smsAdapterProviderMatchTest() throws Exception {

        AdapterConfig adapter = createAdapterConfig(AdapterType.SMS.getName(), AdapterProviders.CM, TEST_ACCOUNT_ID,
                                                    localAddressBroadsoft, localFullAddressBroadsoft, null);
        assertTrue(adapter.isSMSAdapter());
        assertTrue(AdapterProviders.isSMSAdapter(adapter.getProvider().toString()));
    }
    
    /**
     * Check if a call adapter with broadsoft as a provider in the mediaproperty
     * is correctly recognized as a calling adapter
     * 
     * @throws Exception
     */
    @Test
    public void routeSMSInMediaPropertiesMatchTest() throws Exception {

        AdapterConfig adapter = createAdapterConfig(AdapterType.SMS.getName(), AdapterProviders.CM, TEST_ACCOUNT_ID,
                                                    localAddressBroadsoft, localFullAddressBroadsoft, "");
        adapter.setAdapterType(AdapterType.SMS.toString());
        adapter.update();
        assertTrue(AdapterProviders.isSMSAdapter(adapter.getProvider().toString()));
        assertTrue(AdapterProviders.CM.equals(adapter.getProvider()));
        assertTrue(adapter.isSMSAdapter());
    }
    
    /**
     * Check if the basic authentication credentials are flushed for a new client fetch 
     * @throws Exception
     */
    @Test
    public void emptyCredentialsTest() throws Exception {

        String credentials = Credentials.basic("test", "test");
        AFHttpClient afHttpClient = ParallelInit.getAFHttpClient();
        Assert.assertThat(afHttpClient.getCredentials(), Matchers.nullValue());
        afHttpClient.addBasicAuthorizationHeader(credentials);
        Assert.assertThat(afHttpClient.getCredentials(), Matchers.is(credentials));
        afHttpClient = ParallelInit.getAFHttpClient();
        Assert.assertThat(afHttpClient.getCredentials(), Matchers.nullValue());
    }
    
    /** Create a dummy adapter with the given owner id and the accoundId
     * @return
     * @throws Exception
     */
    private AdapterConfig createAdapterWithOwnerAndSharedAccount(String ownerId, Collection<String> accountIds)
        throws Exception {

        AdapterConfig adapter = createAdapterConfig(AdapterType.CALL.getName(), ownerId, accountIds,
                                                    localAddressBroadsoft, null);
        if (accountIds != null) {
            adapter.setAccounts(accountIds);
        }
        adapter.update();
        return adapter;
    }
}
