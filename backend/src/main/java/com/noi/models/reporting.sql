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

-- ORB similarity requests
set @video_id=39;
set @status=11; — complete
--set @status=0; — active
--set @status=-3 ; — failed
select uuid, img_a.video_frame_number from_frame, img_b.video_frame_number to_frame, score, r.status
  from ai_similarity_requests r join ai_images img_a on r.ai_image_before_id = img_a.id
   join ai_images img_b on r.ai_image_id = img_b.id
   join ai_models m on m.id = r.ai_model_id
  where r.ai_video_id=@video_id
    and r.status = @status
    and m.name = 'ORB'
  order by img_a.video_frame_number;

-- LLM similarity requests:
set @video_id=39;
select uuid, img_a.video_frame_number from_frame, img_b.video_frame_number to_frame, score, r.status
  from ai_similarity_requests r
   join ai_images img_a on r.ai_image_before_id = img_a.id
   join ai_images img_b on r.ai_image_id = img_b.id
   join ai_models m on m.id = r.ai_model_id
  where r.ai_video_id=@video_id
    and r.status = @status
    and m.name != 'ORB'
  order by img_a.video_frame_number;

-- Video sound and summaries
-- transcription
set @video_id=39;
select r.uuid, v.name video_name, s.sound_url, m.name model_name, r.updated_at, r.status
  from ai_videos v
   join ai_sounds s on s.ai_video_id=v.id
   join ai_transcribe_requests r on r.ai_sound_id = s.id
   join ai_models m on m.id = r.ai_model_id
  where v.id = @video_id
  order by r.updated_at desc

-- sound summary
set @video_id=39;
select r.uuid, v.name video_name, s.sound_url, m.name model_name, r.updated_at, r.status
  from ai_videos v
   join ai_sounds s on s.ai_video_id=v.id
   join ai_transcriptions t on t.ai_sound_id = s.id
   join ai_sound_summary_requests r on r.ai_transcription_id = t.id
   join ai_models m on m.id = r.ai_model_id
  where v.id = @video_id
  order by r.updated_at desc;

-- video summary
set @video_id=39;
select r.uuid, v.name video_name, m.name model_name, r.updated_at, r.status
  from ai_videos v
   join ai_video_summary_requests r on r.ai_video_id = v.id
   join ai_models m on m.id = r.ai_model_id
  where v.id = @video_id order by r.updated_at desc;

-- video summary scenes:
set @video_id=39;
select r.uuid, v.name video_name, m.name model_name, r.updated_at, r.status, i.video_frame_number
  from ai_videos v
   join ai_video_summary_requests r on r.ai_video_id = v.id
   join ai_models m on m.id = r.ai_model_id
   join ai_video_summary_scenes s on s.ai_video_id=v.id and s.ai_video_summary_request_id=r.id
   join ai_images i on i.id = s.ai_image_id
  where v.id = @video_id
  order by r.updated_at desc;


-- most frequent labels/categories across the images of a video
set @video_id = 23;
select category_name, meta_key, meta_value, count(distinct ai_image_id) image_count, group_concat(distinct ai_image_id) image_ids
  from (
    select ai_image_id, name category_name, meta_key, meta_value, count(*) _count
      from (
        select distinct lmc.ai_image_id, i.video_frame_number, c.name, lmc.meta_key, lmc.meta_value
          from ai_image_label_meta_categories lmc
          join meta_categories c on c.id = lmc.meta_category_id
          join ai_images i on i.id = lmc.ai_image_id
          where i.ai_video_id=@video_id
      )a
      group by 1,2,3,4
    )x
    group by 1,2,3
    order by count(distinct ai_image_id);
