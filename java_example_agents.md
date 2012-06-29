---
layout: default
title: Example agents
---

#Example agents

In the sources of Charlotte (<a target="_blank" href="https://github.com/almende/dialog/tree/master/Charlotte%20-%20Java%20dialog%20tooling/src/com/almende/dialog/agent">goto gitHub</a>) several agents are defined, each showing how to use several parts of the dialog protocol.
These agents are based on RESTFull interface, work is being done to provide some Eve agents as well.

###HowIsTheWeather
This agent show how to create a multi-medium agent, this agent can both work through VoiceXML and through XMPP. More precisely: the agent supports text based and voice based conversation.

###Kastje & Muur
These two agents show the referral mechanism, sending the user from Pillar to Post on any input.

###Calendar Conversation
This agent accesses a Google calendar and prints the events for today. (Uses the old Eve agents for calendar contact)

###Pass Along
This agent demonstrates a message passing capability, outbound call setup in XMPP and identity collection.






