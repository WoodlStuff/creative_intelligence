import os, sys, base64, uuid
import json
from openai import OpenAI
from PIL import Image
from io import BytesIO

# prompts used to ask LLM for scene changes
SYSTEM_PROMPT = "You are a helpful assistant specialized in finding differences in images depicting scenes from a video. Please respond in valid json format."
USER_PROMPT = "Are the following two images from the same movie scene? Consider it a different scene if a new character appears, or if there are any changes in setting or camera angle. Please respond with this format: {\"from_same_scene\": true|false, \"explanation\": \"please explain your result here\"}"
MODEL_NAME = "gpt-4o"

def base64encode_image(image_path):
    with open(image_path, "rb") as image_file:
        return base64.b64encode(image_file.read()).decode("utf-8")

# call the model to determine if the two images are from the same movie scene
def labelForSameVideoScene(image_url_old, image_url_new, model=MODEL_NAME):
    if image_url_old == image_url_new:
        return [True, "same url!"]
    base64_image_old = base64encode_image(image_url_old)
    base64_image_new = base64encode_image(image_url_new)
    client = OpenAI()
    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system",
            "content": SYSTEM_PROMPT},
            {"role": "user", "content": [
                {"type": "text",
                "text": USER_PROMPT},
                {"type": "image_url", "image_url": {
                    "url": f"data:image/png;base64,{base64_image_old}"}
                },
                {"type": "image_url", "image_url": {
                    "url": f"data:image/png;base64,{base64_image_new}"}
                }
            ]}
        ]
    )
    # print(response.choices[0].message.content)
    # parse the response for a boolean (from_same_scene), and an explanation
    jsonResponse = json.loads(
        response.choices[0].message.content.replace("```json\n", "").replace("\n```", "").replace("\n", ""))
    same_scene = bool(jsonResponse['from_same_scene'])
    explanation = jsonResponse['explanation']
    return [same_scene, explanation]

# Transcribe the audio and create a summary text
def transcribeVideo(folderPath, videoName, transcription_model="whisper-1", completion_model=MODEL_NAME):
    audio_path = os.path.join(folderPath, videoName, videoName + '.mp3')
    if not os.path.exists(audio_path):
        print('audio file for video {videoName} not found!')
        return
    client = OpenAI()
    uid = uuid.uuid4()
    transcription = client.audio.transcriptions.create(
        model=transcription_model,
        file=open(audio_path, "rb"),
    )
    
    # format a json doc to hold the transcription info 
    transcriptJson = {}
    transcriptJson['sound_url'] = audio_path
    transcriptJson['uuid'] = str(uid)
    transcriptJson['model_name'] = transcription_model
    transcriptJson['transcription'] = transcription.text 

    # store as json file
    textPath = os.path.join(folderPath, videoName, videoName + '_transcript.json')
    with open(textPath, 'w') as f:
        # f.write(transcription.text)
        json.dump(transcriptJson, f)
        f.close()
    
    sid = uuid.uuid4()
    system_prompt = "You are generating a transcript summary. Create a summary of the provided transcription. Respond in Markdown."
    user_prompt = "The audio transcription is: {transcription.text}"
    response = client.chat.completions.create(
        model=completion_model,
        messages=[
            {"role": "system",
            "content": system_prompt},
            {"role": "user", "content": [
                {"type": "text", "text": f"The audio transcription is: {transcription.text}"}
            ],
            }
        ],
        temperature=0,
    )
    
    summaryJson = {}
    summaryJson['uuid'] = str(sid)
    summaryJson['model_name'] = completion_model
    summaryJson['system_prompt'] = system_prompt
    summaryJson['user_prompt'] = user_prompt
    summaryJson['summary'] = response.choices[0].message.content

    # store summary
    textSummaryPath = os.path.join(folderPath, videoName, videoName + '_transcript_summary.json')
    with open(textSummaryPath, 'w') as f:
        # f.write(response.choices[0].message.content)
        json.dump(summaryJson, f)
        f.close()
    return transcription

# call the model to create a video summary, based on a set of images (the scene change images)
def videoSummaryURL(sceneChanges, folderPath, videoName, model=MODEL_NAME):
    print(f"summarizing {len(sceneChanges)} scenes for {videoName} ...")
    path = os.path.join(folderPath, videoName)
    if not os.path.exists(path):
        os.mkdir(path)
    base64Frames = []

    if len(sceneChanges) <= 0:
        return
    
    # add the very first frame (before the first change)
    base64Frames.append(imageURLToBase64String(sceneChanges[0]['image_url_before']))

    # loop urls and convert the content to base64
    for sceneChange in sceneChanges:
        base64Frames.append(imageURLToBase64String(sceneChange['image_url']))

    print(f"done summarizing scenes for {videoName}")
    return videoSummary(sceneChanges, base64Frames, folderPath, videoName, model)

def imageURLToBase64String(image_url):
    img = Image.open(image_url)
    buffered = BytesIO()
    img.save(buffered, img.format)
    return base64.b64encode(buffered.getvalue()).decode('utf-8')

# generate summary from local video scene images
def videoSummary(sceneChanges, base64Frames, folderPath, videoName, model=MODEL_NAME):
    path = os.path.join(folderPath, videoName)
    if not os.path.exists(path):
        os.mkdir(path)
    client = OpenAI()
    uid = uuid.uuid4()
    system_prompt = "You are generating a video summary. Please provide a summary of the video. Respond in Markdown."
    user_prompt = "These are the frames from the video."
    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system",
            "content": system_prompt},
            {"role": "user", "content": [
                user_prompt,
                *map(lambda x: {"type": "image_url",
                                "image_url": {"url": f'data:image/jpg;base64,{x}', "detail": "low"}}, base64Frames)
            ],
            }
        ],
        temperature=0,
    )

    # log the urls for all images used to create the summary
    sceneURLs = []
    if len(sceneChanges) > 0:
        # append the very first scene
        sceneURLs.append(sceneChanges[0]['image_url_before'])

    for sceneChange in sceneChanges:
        sceneURLs.append(sceneChange['image_url'])

    summaryJson = {}
    summaryJson['uuid'] = str(uid) 
    summaryJson['model_name'] = model 
    summaryJson['system_prompt'] = system_prompt
    summaryJson['user_prompt'] = user_prompt
    summaryJson['scenes']= sceneURLs
    summaryJson['summary'] = response.choices[0].message.content
    summaryPath = os.path.join(folderPath, videoName, videoName + '_scene_summary.json')
    with open(summaryPath, 'w') as f:
        json.dump(summaryJson, f)
        f.close()
    return summaryPath

def main():
    folderPath = './videos'
    videoName = None
    if len(sys.argv) > 1:
        videoName = sys.argv[1]

    if videoName is None:
        exit(-1)

    startSceneIndex = 0
    if len(sys.argv) > 2:
        startSceneIndex = int(sys.argv[2].strip())

    maxSceneCount = 12
    if len(sys.argv) > 3:
        maxSceneCount = int(sys.argv[3].strip())

    print(f'processing video: {videoName} with startSceneIndex={startSceneIndex} and maxSceneCount={maxSceneCount}')

    files = os.listdir(os.path.join(folderPath, videoName))
    # filter for image files only
    filtered = [f for f in files if (f.endswith(".jpg") or f.endswith(".DS_Store"))]
    # create dictionary keyed by frame number, so we can sort by that number
    frames = {}
    for file in filtered:
        frames[int(file.replace(videoName + '_frame', '').replace('.jpg', ''))] = file
    print(f"summarizing video based on {len(frames)} frames ...")
    #
    sceneChanges = []
    count = 0
    for i in sorted(frames.keys()):
        count += 1
        if count < startSceneIndex:
            continue
        sceneChanges.append({'image_url': os.path.join(folderPath, videoName, frames[i]), 'frame': i})
        if (count - startSceneIndex) > maxSceneCount:
            break
    print(
        f"creating video summary for {videoName} from frame {sceneChanges[0]['frame']} to frame {sceneChanges[len(sceneChanges) - 1]['frame']}")
    videoSummaryURL(sceneChanges, folderPath, videoName, model=MODEL_NAME)

# usage:
# python openAI_caller.py big_buck_bunny [0] [12]
# python openAI_caller.py [video file (mp4)] [startSceneIndex (i.e. 2 == the 2nd scene)] [maxSceneCount (only send 12 scene images max)]
if __name__ == "__main__":
    main()