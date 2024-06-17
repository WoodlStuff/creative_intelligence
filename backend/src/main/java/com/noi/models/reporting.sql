-- ==================================================
-- get prompt, image url, and label(s) for a request
select p.prompt, r.model_name img_model, i.image_url, rp.prompt revised_prompt, l.model_name label_model, l.label image_label
  from ai_prompts p join ai_requests r on r.id = p.ai_request_id
  join ai_images i on i.ai_prompt_id = p.id
  left join ai_revised_prompts rp on p.id = rp.ai_prompt_id
  left join ai_image_labels l on i.id = l.ai_image_id
  left join ai_image_label_annotations a on i.id = a.ai_image_id
 where p.ai_request_id=8 \G


-- ==================================================
-- NLP (from prompt text)
-- entities and mentions
select e.name entity_name, e.entity_type, m.mention_text, m.mention_type, m.mention_offset, m.probability
 from ai_prompt_entities e left join ai_prompt_mentions m on e.id = m.ai_prompt_entity_id
 where e.ai_prompt_id = 8 order by e.id, m.id;

-- sentiments: sentiment_text null == document sentiment
select coalesce(sentiment_text, p.prompt) _prompt, sentiment_offset, magnitude, score from ai_prompts p join ai_prompt_sentiments s on p.id = s.ai_prompt_id
 where ai_prompt_id = 8;

-- classifiers
select name, category_type, confidence from ai_prompt_classifiers
 where ai_prompt_id = 8 order by category_type, confidence desc;


-- ==============
-- image labels (OpenAI)

-- image objects
set @image_id = 762;
set @label_request_uuid = '94106221-3626-4349-bd8f-b831376fff6c';
select r.uuid label_request_uuid, p.prompt_type, r.model_name, o.object, o.object_type, o.location, o.brand, o.relative_size, o.color, o.background_color, o.gender
 from ai_label_requests r join ai_prompts p on p.id=r.ai_prompt_id
   join ai_image_labels l on l.ai_label_request_id = r.id
   join  ai_image_label_meta_objects o on o.ai_image_label_id = l.id
 where r.ai_image_id = @image_id;
 where r.uuid = @label_request_uuid;

-- image meta categories
select r.uuid label_request_uuid, p.prompt_type, r.model_name, c.name category_name, mc.meta_key, mc.meta_value
 from ai_label_requests r join ai_prompts p on p.id=r.ai_prompt_id
   join ai_image_labels l on l.ai_label_request_id = r.id
   join ai_image_label_meta_categories mc on mc.ai_image_label_id = l.id
   join meta_categories c on c.id = mc.meta_category_id
 where r.ai_image_id = @image_id;
 where r.uuid = @label_request_uuid;

-- image annotations
select r.uuid label_request_uuid, r.model_name, label, mid, score, topicality
 from ai_label_requests r join ai_image_label_annotations a on a.ai_label_request_id = r.id
 where a.ai_image_id = @image_id;

set @video_id=7;
-- raw similarity scores for frames of a video
set @model_name='ORB';
-- labeled (by LLM) similarity scores)
-- set @model_name='gpt-4o';
select distinct m.name, r.is_scene_change, last.image_url last_scene_url, first.image_url first_scene_url, r.score, r.explanation
 from ai_similarity_requests r
 join ai_models m on m.id = r.ai_model_id
 join ai_images last on r.ai_image_before_id = last.id
 join ai_images first on r.ai_image_id = first.id
 where last.ai_video_id = first.ai_video_id
   and first.ai_video_id = @video_id and m.name=@model_name
  -- and r.is_scene_change=1
 ;

 -- video frames used to create a summary:
set @video_id = 8;
select v.frame_rate, s.ai_image_id, i.image_url, i.video_frame_number, round(i.video_frame_number/v.frame_rate) _seconds_in  from ai_video_summary_scenes s join ai_images i on i.id = s.ai_image_id join ai_videos v on v.id = s.ai_video_id and v.id = i.ai_video_id
 where s.status=1 and i.status != -1
   and s.ai_video_id = @video_id
 order by i.video_frame_number asc;
