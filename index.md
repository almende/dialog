---
layout: default
title: Introduction
---

# Introduction

One important part of CHAP is the hybrid nature of the agent models. To facilitate the interaction between agents and humans the "Dialog" toolset provides the means for multi-channel communication. This project contains tools for describing the meta-structure of dialogs, the exchange of dialog data (texts, sounds, etc.) and an adapter design for plug-in media. 

## Protocol

The base of the Dialog toolset is a JSON-based protocol for describing the meta structure of the conversation. This protocol is medium independent and therefore facilitates multi-channel conversations between the agent and humans.
Please refer to: <a href="protocol.html">protocol</a> for a indepth description of the protocol.

## Dialog Handler

To allow agents to converse with humans, medium specific adapters need to be implemented. These adapters interpret the protocol and map the dialog to their own protocol or medium. For example, a VoiceXML adapter will map the dialog to a voiceXML document describing the question and its answers.
The Dialog toolset provides a Google AppEngine implementation of such adapters (and supporting code), containing a VoiceXML adapters and an XMPP (gTalk/Jabber) adapter.  

## Tooling



SimpleQ jQuery widget - <a href="projects/simpleQ/index.html">Preview</a> - <a href="projectExports/simpleQ.zip">Download</a>
