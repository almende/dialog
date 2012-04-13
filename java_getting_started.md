---
layout: default
title: Getting started
---

#Getting started

##Prerequisites
The current dialog code has several dependencies, as one of the goals has been to provide basic infrastructure for agents to participate in the dialog through various media.
All these packages are provided in the 'war/WEB-INF/lib/' folder in the source code: <a target="_blank" href="https://github.com/almende/dialog/tree/master/Charlotte%20-%20Java%20dialog%20tooling/war/WEB-INF/lib/">goto sources</a>

The dialog tooling is based on the following external tools:
+ &nbsp;<a href="appengine.google.com" target="_blank">Google App Engine</a> - Cloud server environment, the dialog uses it's Java servlet, datastore and XMPP APIs. 
+ &nbsp;<a href="almende.github.com/eve" target="_blank">Eve</a> - The webtechnology based agent platform of CHAP.
+ &nbsp;<a href="http://jersey.java.net/" target="_blank">Jersey</a> - RESTfull webservices framework, both client and server-side is used.
+ &nbsp;<a href="http://flexjson.sourceforge.net/" target="_blank">FlexJson</a> - FlexJson JSON (de)Serializer
+ &nbsp;<a href="http://xmlenc.sourceforge.net/" target="_blank">XML Enc</a> - Lightweight XML creation library
+ &nbsp;<a href="http://johannburkard.de/software/uuid/" target="_blank">UUID</a> - Lightweight, fast UUID generator library

Below is a list of all required packages: (versions may probably vary, mentioned versions are known to work)
+ asm-3.1.jar
+ commons-beanutils.jar
+ commons-collections3.jar
+ commons-lang.jar
+ commons-logging.jar
+ eve-core.jar
+ ezmorph-1.0.6.jar
+ flexjson-2.1.jar
+ guava-r09.jar
+ jersey-client-1.12.jar
+ jersey-core-1.12.jar
+ jersey-server-1.12.jar
+ jersey-servlet-1.12.jar
+ uuid-3.3.jar
+ xmlenc-0.52.jar

Currently the dialog has been tested to work with the Google App Engine SDK, version 1.6.2.1.

##Setup
The dialog environment can be deployed through an Eclipse project and/or directly through Ant. The Ant version has an added benefit over the Eclipse deployment, as the build.xml file contains a script to bundle all class files into one JAR before deployment. As the Google App Engine has a relative high latency on disk-operations, this will significantly increase startup performance of new instances.

###Eclipse project notes:
Within Eclipse the sources can "imported" as an existing project. Probably several paths will need to be fixed, especially with regard to the location of the Google App Engine SDK. Also, the current projectfile is created on Linux, so Windows users might need to fix some references. 

After importing the project into the workspace, you need to modify the App Engine setting to reflect the name of your application and version. You can find these settings through the context menu in the Package Explorer on the project root folder. Select the context menu, within it the "Google" submenu, and then "App Engine Settings...".

###Ant building/Deploying
The project sources also contain an Ant buildfile. This file needs to be modified to reflect the correct "project base dir", "eclipse_home" and "sdk.dir". The Eclipse dependency can quite easily be removed from this file, if required. 

If deploying through Ant, you need to manually edit the App Engine settings through "/war/WEB-INF/appengine-web.xml".

###Tailoring
In several places some hardcoded links are used. These need to be tailored to the new application path.
+ All agents in com.almende.dialog.proxy.agent contain their own URL and sometimes other agent's URLs as well
+ The CCXMLProxy file in com.almende.dialog.proxy contains an URL to the Charlotte agent.
+ The XMPPReceiverServlet file in com.almende.dialog also contains an URL to the Charlotte agent. 

##Initial test
After deploying the project to the Google App Engine, you can test the setup by contacting the Charlotte agent directly through: "http://&lt;applicationName&gt;.appspot.com/charlotte" or (the ultimate test!) by starting a gtalk chat with "&lt;applicationName&gt;@appspot.com". 

