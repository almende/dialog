package com.almende.dialog.agent;

import static org.junit.Assert.assertThat;
import java.util.ArrayList;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.Adapter;
import com.askfast.commons.entity.AdapterType;

public class AdapterAgentTest extends TestFramework
{
    /**
     * test if an adapter initially having an initialAgentURL, being updated with a dialogId, will return the
     * dialog url
     * @throws Exception 
     */
    @Test
    public void updatingDialogIdInAdapterTest() throws Exception {

        String testMessage = "testMessage";
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", testMessage);
        createEmailAdapter("askfasttest@gmail.com", "", null, null, null, null, null, null, null, TEST_PRIVATE_KEY,
                           url, null);
        //fetch the adapter again
        ArrayList<AdapterConfig> adapterConfigs = AdapterConfig.findAdapterByAccount(TEST_PRIVATE_KEY);
        assertThat(adapterConfigs.size(), Matchers.is(1));
        AdapterConfig config = adapterConfigs.iterator().next();
        assertThat(config.getInitialAgentURL(), Matchers.is(url));
        assertThat(config.getURLForInboundScenario(null), Matchers.is(url));

        //create a dialog and attach it to the user
        String dialogURL = url + "&dummy=test";
        Dialog dialog = Dialog.createDialog("Test dialog", dialogURL, TEST_PRIVATE_KEY);
        Adapter adapter = new Adapter();
        adapter.setDialogId(dialog.getId());
        new AdapterAgent().updateAdapter(TEST_PRIVATE_KEY, config.getConfigId(), adapter);

        //refetch the adapter
        config = AdapterConfig.getAdapterConfig(config.getConfigId());
        assertThat(config.getInitialAgentURL(), Matchers.is(url));
        assertThat(config.getURLForInboundScenario(null), Matchers.is(dialogURL));
    }
    
    /**
     * test if updating initialAgentURL to "" (empty), still returns the dialog url
     * @throws Exception 
     */
    @Test
    public void updatingEmptyInitialAgentURLTest() throws Exception {
        String testMessage = "testMessage";
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       TestServlet.QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams( url, "question", testMessage );
        createEmailAdapter("askfasttest@gmail.com", "", null, null, null, null, null, null, null, TEST_PRIVATE_KEY,
                           url, null);
        //fetch the adapter again
        ArrayList<AdapterConfig> adapterConfigs = AdapterConfig.findAdapterByAccount(TEST_PRIVATE_KEY);
        assertThat(adapterConfigs.size(), Matchers.is(1));
        AdapterConfig config = adapterConfigs.iterator().next();
        assertThat(config.getInitialAgentURL(), Matchers.is(url));
        assertThat(config.getURLForInboundScenario(null), Matchers.is(url));
        
        //create a dialog and attach it to the user
        String dialogURL = url + "&dummy=test";
        Dialog dialog = Dialog.createDialog("Test dialog", dialogURL, TEST_PRIVATE_KEY);
        Adapter adapter = new Adapter();
        adapter.setInitialAgentURL("");
        adapter.setDialogId(dialog.getId());
        new AdapterAgent().updateAdapter(TEST_PRIVATE_KEY, config.getConfigId(), adapter);
        
        //refetch the adapter
        config = AdapterConfig.getAdapterConfig(config.getConfigId());
        assertThat(config.getInitialAgentURL(), Matchers.is(""));
        assertThat(config.getURLForInboundScenario(null), Matchers.is(dialogURL));
    }
    
    /**
     * test if updating dialogID to "" (empty), still returns the initialAgentURL
     * @throws Exception 
     */
    @Test
    public void updatingEmptyDialogURLTest() throws Exception {

        //create an adapter with a dialogId
        updatingDialogIdInAdapterTest();
        //fetch the adapter again
        ArrayList<AdapterConfig> adapterConfigs = AdapterConfig.findAdapterByAccount(TEST_PRIVATE_KEY);
        assertThat(adapterConfigs.size(), Matchers.is(1));
        AdapterConfig config = adapterConfigs.iterator().next();
        Adapter adapter = new Adapter();
        adapter.setDialogId("");
        new AdapterAgent().updateAdapter(TEST_PRIVATE_KEY, config.getConfigId(), adapter);

        //refetch the adapter
        AdapterConfig refetchedConfig = AdapterConfig.getAdapterConfig(config.getConfigId());
        assertThat(refetchedConfig.getInitialAgentURL(), Matchers.is(config.getInitialAgentURL()));
        assertThat(refetchedConfig.getURLForInboundScenario(null), Matchers.is(config.getInitialAgentURL()));
        assertThat(refetchedConfig.getURLForInboundScenario(null), Matchers.is(refetchedConfig.getInitialAgentURL()));
        assertThat(refetchedConfig.getProperties().get(AdapterConfig.DIALOG_ID_KEY), Matchers.nullValue());
        assertThat(refetchedConfig.getDialog(), Matchers.nullValue());
    }
    
    /**
     * Check if attaching an adapter to a account, adds a new attach timestamp 
     * @throws Exception 
     */
    @Test
    public void attachIngAnAdapterAddsATimestampTest() throws Exception {

        AdapterConfig adapter = createAdapterConfig(AdapterType.CALL.toString(), null, null, localFullAddressBroadsoft,
            null);
        Assert.assertThat(adapter, Matchers.notNullValue());
        //attach the adapter
        AdapterAgent adapterAgent = new AdapterAgent();
        adapterAgent.addAccount(adapter.getConfigId(), TEST_PUBLIC_KEY);
        adapterAgent.addAccount(adapter.getConfigId(), TEST_PRIVATE_KEY);

        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapter.getConfigId());
        //assert that the timestamp is added
        Assert.assertThat(adapterConfig.getAttachTimestamp(TEST_PRIVATE_KEY), Matchers.notNullValue());
        Assert.assertThat(adapterConfig.getAttachTimestamp(TEST_PRIVATE_KEY),
            Matchers.greaterThanOrEqualTo(adapterConfig.getAttachTimestamp(TEST_PUBLIC_KEY)));
    }
}
