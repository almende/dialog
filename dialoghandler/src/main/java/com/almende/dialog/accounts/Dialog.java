package com.almende.dialog.accounts;

import java.util.List;
import java.util.UUID;

import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.askfast.commons.intf.DialogInterface;

public class Dialog implements DialogInterface 
{
    @Id
	public String id = null;
	String name = null;
	String url = null;
	String owner = null; 
	
	public Dialog() {}
	
	public Dialog(String id) {
		this.id = id;
	}
	
	public Dialog(String name, String url) {
		this.id = UUID.randomUUID().toString();
		this.name = name;
		this.url = url;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Dialog) {
			Dialog dialog = (Dialog) obj;
			return dialog.getId().equals(id);
		}
		return false;
	}
	
	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getUrl() {
		return url;
	}
	
	public void setUrl(String url) {
		this.url = url;
	}
	
    public String getOwner()
    {
        return owner;
    }

    public void setOwner( String owner )
    {
        this.owner = owner;
    }
	
    /**
     * stores or updates the dialog object
     */
	public void storeOrUpdate()
	{
	    TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        datastore.storeOrUpdate( this );
	}
	
	/**
	 * creates a simple dialog with name and url
	 * @param name
	 * @param url
	 * @return
	 * @throws Exception 
	 */
	public static Dialog createDialog(String name, String url, String owner) throws Exception
	{
	    if(url != null && !url.isEmpty())
	    {
	        List<Dialog> dialogs = getDialogs( owner, url );
	        if(dialogs == null || dialogs.isEmpty())
	        {
	            Dialog dialog = new Dialog( name, url );
	            dialog.setOwner( owner );
	            dialog.storeOrUpdate();
	            dialogs.add( dialog );
	        }
            return dialogs.iterator().next();
	    }
	    return null;
	}
	
	/**
	 * gets the dialog if its owned by the accountId. If owner is null, fetches any dialog 
	 * @param id
	 * @param accountId
	 * @return
	 * @throws Exception
	 */
    public static Dialog getDialog( String id, String accountId ) throws Exception
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        Dialog dialog = datastore.load( Dialog.class, id );

        if ( dialog != null && accountId != null )
        {
            if ( dialog.getOwner().equals( accountId ) )
            {
                return dialog;
            }
            else
            {
                throw new Exception( String.format( "AccountId: %s does not own Dialog: %s", accountId, dialog.getId() ) );
            }
        }
        return dialog;
    }
    
    /**
     * gets all the dialogs owned by the accountId. If accountId is null, gets all the dialogs not owned by any
     * @param id
     * @param accountId
     * @return
     * @throws Exception
     */
    public static List<Dialog> getDialogs( String accountId ) throws Exception
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<Dialog> cmd = datastore.find().type( Dialog.class );
        cmd.addFilter("owner", FilterOperator.EQUAL, accountId);
        return cmd.now().toArray();
    }
    
    /**
     * get all dialogs owner by a user by url
     * @param accountId
     * @return
     * @throws Exception
     */
    public static List<Dialog> getDialogs( String accountId, String url ) throws Exception
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<Dialog> cmd = datastore.find().type( Dialog.class );
        cmd.addFilter("owner", FilterOperator.EQUAL, accountId);
        cmd.addFilter("url", FilterOperator.EQUAL, url);
        return cmd.now().toArray();
    }
    
    public static void deleteDialog( String id, String accountId ) throws Exception
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        Dialog dialog = datastore.load( Dialog.class, id );

        if ( dialog != null )
        {
            //delete if the dialog is either not owned by any or owned by the logged in user
            if ( accountId == null || ( accountId != null && dialog.getOwner().equals( accountId ) ) )
            {
                datastore.delete( dialog );
            }
            else
            {
                throw new Exception( String.format( "AccountId: %s does not own Dialog: %s", accountId, dialog.getId() ) );
            }
        }
    }
}
