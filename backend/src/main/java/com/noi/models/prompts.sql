-- prompt_type:
-- 10=image categories  :
-- 11=image objects     :
-- 12=image properties  :

-- request analysis of image objects [prompt type=11]
insert into ai_prompts (prompt, system_prompt, prompt_type, status, created_at, updated_at)
 values('Create a json identifying all objects, and any overlay text in this image. Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation: "image_objects": [{"name": "the name of the object", "type": "the type of the object (examples: person, text, animal, plant, sky, water)", "location": "the location of the object within the image", "relative_size": "the relative sice of the object", "color": "the color of the object", "background_color": "the background color", "brand": "the affiliated brand, if any", "gender": "the gender of the object, if any", "font": "the font of the text in the image, if any"}]',
    'You are a advertising expert.',
     11, 1, now(), now());

-- request analysis of image properties [prompt type=12]
insert into ai_prompts (prompt, system_prompt, prompt_type, status, created_at, updated_at)
 values('Create a json describing the size, image format, overall color, any color gradients present in the image, any shadows, and lighting used. Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation: image_properties: {"size": "the size of the image", "format": "the encoding of the file (JPEG, PNG, ...)", "overall_color": "vibrant", "color_gradients": "Sky blue to white", "shadows": "minimal", "lighting": "natural daylight"}',
    'You are a advertising expert.',
     12, 1, now(), now());

-- request analysis of image text language [prompt type=??]
insert into ai_prompts (prompt, system_prompt, prompt_type, status, created_at, updated_at)
 values('Create a json highlighting any themes, persuasive language, calls to action, claims language, trust queues, figures of speech implied or explicit in text, any sense of urgency, any health claims outlined in the image. Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation: {"image_language": {"themes": "", "persuasive_language": "", "calls_to_action": "", "claims_language": "", "trust_queues": "", "figures_of_speech": "", "sense_of_urgency": "", "health_claims": ""}}',
    'You are a advertising expert.',
     10, 1, now(), now());

-- request analysis of image context [prompt type=??]
insert into ai_prompts (prompt, system_prompt, prompt_type, status, created_at, updated_at)
 values('Create a json analyzing the image for context including all of the following: substance: the physical material which carries or relays text music and pictures; paralanguage: meaningful behaviour accompanying language, such as voice quality, gestures, facial expressions and touch (in speech), and choice of typeface and letter sizes (in writing); situation: the properties and relations of objects and people in the vicinity of the text, as perceived by the participants; co-text: text which precedes or follows that under analysis, and which participants judge to belong to the same discourse; intertext: text which the participants perceive as belonging to other discourse, but which they associate with the text under consideration, and which affects their interpretation; participants: their intentions and interpretations, knowledge and beliefs, attitudes, affiliations and feelings. Each participant is simultaneously a part of the context and an observer of it. Participants are usually described as senders and receivers. (The sender of a message is not always the same as the addresser, however, the person who relays it. In a television ad, for example, the addresser may be an actor, though the sender is an advertising agency. Neither is the receiver always the addressee, the person for whom it is intended. The addressees may be a specific target group, but the receiver is anyone who sees the ad.); function: what the text is intended to do by the senders and addressers, or perceived to do by the receivers and addressees. Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation: {"image_context": {"physical_material": "",  "para_language": {"voice_quality": "", "gestures":"", "facial_expressions": "", "touch": ""}, "choice_of_typeface": "", "choice_of_letter_sizes": ""; "situation": {"properties": "", "relations_of_objects": "", "people_in_the_vicinity_of_the_text": ""}, "co_text": ""; "inter_text": "", "participants": { "intentions": "", "interpretations": "", "knowledge": "",  "beliefs": "", "attitudes": "", "affiliations": "", "feelings": "", "senders": "", "receivers": "", "addresser": "", "addressee": ""}, "function": ""}}',
    'You are a advertising expert.',
     10, 1, now(), now());


--
--insert into ai_prompts (prompt, system_prompt, prompt_type, status, created_at, updated_at)
-- values('create a json identifying all objects, any overlay text, and layout (basic structural elements) in this image.',
--    'Please output the information as json. The labels should be concise, no more than four words each. Objects in the image should contain a name, a type, a primary color, the background color, the location within the image, a relative size, and any brand and gender information. Overlay text should contain the text content, the font and location within the image, and a relative size. The layout should list the aspect ratio, image orientation, and any dominant element in the image.',
--     11, 1, now(), now());
--
--insert into ai_prompts (prompt, system_prompt, prompt_type, status, created_at, updated_at)
-- values('Create a json describing the size, image format, overall color, any color gradients present in the image, any shadows, and lighting used.',
--    'Please output the information as json. The labels should be concise, no more than four words each. Please use primitive json types in the response.',
--     12, 1, now(), now());
--
--insert into ai_prompts (prompt, system_prompt, prompt_type, status, created_at, updated_at)
-- values('Create a json highlighting any themes, persuasive language, calls to action, claims language, trust queues, figures of speech implied or explicit in text, any sense of urgency, any health claims outlined in the image.',
--    'Please output the information as json. The labels should be concise, no more than four words each. Please use primitive json types in the response.',
--     10, 1, now(), now());
--
--insert into ai_prompts (prompt, system_prompt, prompt_type, status, created_at, updated_at)
-- values('Output a JSON analyzing the image for context including all of the following: substance: the physical material which carries or relays text music and pictures; paralanguage: meaningful behaviour accompanying language, such as voice quality, gestures, facial expressions and touch (in speech), and choice of typeface and letter sizes (in writing); situation: the properties and relations of objects and people in the vicinity of the text, as perceived by the participants; co-text: text which precedes or follows that under analysis, and which participants judge to belong to the same discourse; intertext: text which the participants perceive as belonging to other discourse, but which they associate with the text under consideration, and which affects their interpretation; participants: their intentions and interpretations, knowledge and beliefs, attitudes, affiliations and feelings. Each participant is simultaneously a part of the context and an observer of it. Participants are usually described as senders and receivers. (The sender of a message is not always the same as the addresser, however, the person who relays it. In a television ad, for example, the addresser may be an actor, though the sender is an advertising agency. Neither is the receiver always the addressee, the person for whom it is intended. The addressees may be a specific target group, but the receiver is anyone who sees the ad.); function: what the text is intended to do by the senders and addressers, or perceived to do by the receivers and addressees.',
--    null, -- use default system prompt!
--     10, 1, now(), now());
