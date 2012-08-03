---
layout: default
title: Dialog structure
---

# Dialog structure

The base of the Dialog toolset is a JSON-based protocol for describing the meta structure of the conversation. This protocol is medium independent and therefore facilitates multi-channel conversations between the agent and humans.

## JSON structure
Each dialog consists of a sequential set of uterances, in this protocol called "Question". The Question is not limited to "natural questions", they also include comments, referrals, etc.
The basic container structure is the Dialog, which is basically the addition of a label to a series of interactions:

	Dialog = {
		"dialog_id":<some_id>,
		"questions":[ <Question> ]
	}

Within this container a list of questions can be placed, called Questions:

	Question = {
		"requester":"<id url>",
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

Another important structure which is used in the dialog protocol is the Answer which needs to be posted to the answer callback-urls:

	Answer =  {
		"responder":"<id url>",
		"dialog_id":<originating dialog>,
		"question_id":<originating question>,
		"answer_id":<originating answer>,
		"answer_text":"<url>"
	}

Similarly there is an Event structure for usage with the event_callback callback-urls:

	Event =  {
		"responder":"<id url>",
		"dialog_id":<originating dialog>,
		"question_id":<originating question>,
		"event":"<originating event>",
		"message":"<string describing the event, error message>"
	}

Although the above definitions list a set of possible Events, this list is actually adapter (and therefor medium) specific.Not every adapter will support all Events, nor is this list complete.

Normally generation of `"_id"` fields in these structures is optional, but if a question does contain `"_id"`s, these should be returned in the Answer and Event structures.

## Obtaining content
The most important concept in this meta-description of the dialog, is the `"<url>"` references. All content of the actual dialog is collected during the interpretation of the dialog. This allows the JSON structure to be completely language and medium independent. 

By passing various GET parameters with the URL the content-type and content-language can be set:

	http://example.com/question/1?preferred_medium=audio/wav&preferred_language=en/us
	
Conceptually this content is being collected from the agent who created this dialog. But in many cases the agent might reply to this http-request by sending a 307-Redirect, letting the dialog adapter collect the content from a document server or TTS system.

## Handling human input
In similar fashion to content collection, the dialog standard also defines the way the resulting human input needs to be handled. In the meta-description of the dialog several 'callback'-urls are defined.
It is required for implementations of the dialog to POST the before mentioned "Answer" structure to this callback url, when the (human) responder gives the corresponding answer or the corresponding event occurs. In general this post will return a (new) question JSON structure, allowing the dynamic creation of the dialog.

## Basic agent design
Using these JSON objects as the API, a software agent can provide dialog services. Although implementations may vary considerably, basically each agent will provide at least the following functions:

+	StartDialog()	-  Provides the first question of a dialog.
+	Answer()	-  Handles the answer callback functions, generally returning the next question.
+	GetQuestionText()	-  Handles the content collection, for various media and languages.
+	GetAnswerText()	-  Idem, but then for answers.
+   GetId()	-  Obtains a structure describing the participant, possibly in some agent/medium specific way.
 
