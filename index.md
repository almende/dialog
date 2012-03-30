---
layout: default
title: Introduction
---

# Introduction

One important part of CHAP is the hybrid nature of the agent models. To facilitate the interaction between agents and humans the "Dialog" toolset provides the means for multi-channel communication. This project contains tools for describing the meta-structure of dialogs, the exchange of dialog data (texts, sounds, etc.) and an adapter design for plug-in media. 

## Dialog structure

The base of the Dialog toolset is a JSON-based protocol for describing the meta structure of the conversation. This protocol is medium independent and therefore facilitates multi-channel conversations between the agent and humans.

### JSON structure
The basic container structure is the Dialog, which is basically the addition of a label to a series of interactions:

	Dialog = {
    	"dialog_id":<some_id>,
    	"questions":[ <Question> ]
	}

Within this container a list of questions can be placed, either statically up-front or dynamically during the interpretation of the dialog.

	Question = {
		"question_id":<some_id>,
		"question_text":"<url>",
		"type":"[ open | closed | comment | referral ]",
		"url":"<referral url>",
		"answers":[
			{ 
				"answer_id":<some_id>,
				"answer_text":"<url>",
				"callback":"<url>"
			}
		],
		"event_callbacks":[
			{
				"event":"[delivered|read|answered|timeout|cancelled|exception]",
				"callback":"<url>"
			}
		]
	}

### Obtaining content
The most important concept in this meta-description of the dialog, is the `"<url>"` references. All content of the actual dialog is collected during the interpretation of the dialog. This allows the JSON structure to be completely language and medium independent. By passing various GET parameters with the URL the content-type and content-language can be set:

	http://example.com/question/1?preferred_medium=audio/wav&preferred_language=en/us
	
Conceptually this content is being collected from the agent who created this dialog. But in many cases the agent might reply to this http-request by sending a 307-Redirect, letting the dialog adapter collect the content from a document server or TTS system. 

### Handling human input
In similar fashion to content collection, the dialog standard also defines the way the resulting human input needs to be handled. In the meta-description of the dialog several 'callback'-urls are defined. 

For giving an answer, the following POST data is expected:

	Answer      =  {
		"dialog_id":<originating dialog>,
	    "question_id":<originating question>,
    	"answer_id":<originating answer>,
    	"answer_text":"<url>"
	}

## Tooling

SimpleQ jQuery widget - <a href="projects/simpleQ/index.html">Preview</a> - <a href="projectExports/simpleQ.zip">Download</a>