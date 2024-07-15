# pip install ffmpeg
# pip install opencv-python
# pip install moviepy
# (pip install numpy)
# pip install pytube
import cv2
import base64
from moviepy.editor import VideoFileClip
from pytube import YouTube
from io import BytesIO
from PIL import Image
import sys, os
import json
import openAI_caller
import uuid

def test():
    path = '/Users/martin/work/tmp/ai-data/videos/'

    video_name = None

    if len(sys.argv) > 1:
        video_name = sys.argv[len(sys.argv) - 1]

    if video_name is None:
        video_name = '4K_Sample_Video_Alpha_6700_Sony-α'

    print('video=' + video_name)

    videoPath = path + video_name + '.mp4'
    video = cv2.VideoCapture(videoPath)
    total_frames = int(video.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = video.get(cv2.CAP_PROP_FPS)
    seconds_per_frame = 2
    frames_to_skip = int(fps * seconds_per_frame)

    # pick a random frame in the middle
    frame_num = int(total_frames / 2)
    video.set(cv2.CAP_PROP_POS_FRAMES, frame_num)
    success, frame = video.read()
    _, buffer = cv2.imencode(".jpg", frame)
    base64Frame = base64.b64encode(buffer).decode("utf-8")
    # save the frame as a jpeg file
    img_data = base64.b64decode(base64Frame.encode("utf-8"))
    img = Image.open(BytesIO(img_data))
    img.save(path + video_name + '_frame' + str(frame_num) + '.jpg')

    # write the sounds to a separate file
    clip = VideoFileClip(videoPath)
    audio_path = path + video_name + '.mp3'
    clip.audio.write_audiofile(audio_path, bitrate="32k")
    clip.audio.close()
    clip.close()

def storeSnapShots(video, path, videoName, startFrame, snap_range=5):
    totalFrames = int(video.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = video.get(cv2.CAP_PROP_FPS)
    for x in range(snap_range):
        # frame one second later 
        nextFrame = startFrame + int(fps * (x + 1))
        if nextFrame >= totalFrames - 1:
            break
        video.set(cv2.CAP_PROP_POS_FRAMES, nextFrame)
        success, frame = video.read()
        if not success:
            break
        _, buffer = cv2.imencode(".jpg", frame)
        img = Image.open(BytesIO(buffer))
        snapPath = os.path.join(path, videoName, 'snaps')
        if not os.path.exists(snapPath):
            os.mkdir(snapPath)
        # imgURL = os.path.join(snapPath, videoName + str(nextFrame) + '.' + img.format)
        imgURL = os.path.join(snapPath, videoName + '_frame' + str(nextFrame) + '.jpg')
        img.save(imgURL)


# look for scene changes in this video, and store first scene frame(s) as jpg file(s)
# returns a dict with format: {'frame': 99, 'image_url': '/local file URL', 'similarity_score': 0.88}
def process_video2(path, videoName, frames_to_skip=0, max_distance_for_similarity=70, scene_change_threshold=.75):
    sceneChangeImages = []
    videoPath = os.path.join(path, videoName + '.mp4')
    # temp staging for comparison of image scores
    imgA_URL = os.path.join(path,  videoName + '_frameA.jpg')
    imgB_URL = os.path.join(path, videoName + '_frameB.jpg')
    video = cv2.VideoCapture(videoPath)
    totalFrames = int(video.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = video.get(cv2.CAP_PROP_FPS)
    print(
        f"process_video2: max_distance_for_similarity={max_distance_for_similarity}, scene_change_threshold={scene_change_threshold}")
    print(f"total frames: {totalFrames}; fps: {fps}")
    # Loop through the video and extract frames at specified sampling rate
    currentFrame = 0
    lastFrame = 0
    sceneStartFrame = 0
    while currentFrame < totalFrames:
        video.set(cv2.CAP_PROP_POS_FRAMES, currentFrame)
        success, frame = video.read()
        if not success:
            break
        _, buffer = cv2.imencode(".jpg", frame)
        imgA = Image.open(BytesIO(buffer))
        imgA.save(imgA_URL)
        if currentFrame > 0:
            # compare this with the previous frame
            score = sift_sim2(imgA_URL, imgB_URL, max_distance_for_similarity)
            # we always store the last frame!
            if (score > 0 and score < scene_change_threshold) or (currentFrame == totalFrames - 1):
                # we have a scene change!
                print(f"we have a scene change at frame {currentFrame} for score={score}!")
                #  save the new scene image to file (refresh the buffer before, just in case the imgA handler changed anything ...)
                imageURL = saveFrameToFile(path, videoName, video, currentFrame)
                # ensure the last frame is stores as well (we need it for logging purposes later!)
                beforeURL = saveFrameToFile(path, videoName, video, lastFrame)
                
                print(f"saved scene change image at {imageURL}!")
                sceneChangeImages.append({'frame': currentFrame, 'frame_before': lastFrame, 'image_url': imageURL, 'image_url_before': beforeURL, 'similarity_score': score})
                storeSnapShots(video, path, videoName, currentFrame)
        
        imgA.save(imgB_URL)
        lastFrame = currentFrame
        currentFrame += (frames_to_skip + 1)
    
    video.release()
    os.remove(imgA_URL)
    os.remove(imgB_URL)
    print(f"Extracted {len(sceneChangeImages)} scene changes")
    return [sceneChangeImages, totalFrames, fps]

# look for scene changes in this video, and store first scene frame(s) as jpg file(s)
# returns a dict with format: {'frame': 99, 'image_url': '/local file URL', 'similarity_score': 0.88}
def findSceneChanges(path, videoName, frames_to_skip=0, max_distance_for_similarity=70, scene_change_threshold=.75, verbose=False):
    response = {}
    ensureVideoFolder(path, videoName)
    sceneChangeImages = []
    videoPath = os.path.join(path, videoName + '.mp4')
    seekImageURL = os.path.join(path, videoName + '_seeker.jpg')
    
    video = cv2.VideoCapture(videoPath)
    totalFrames = int(video.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = video.get(cv2.CAP_PROP_FPS)
    print(
        f"findSceneChanges: max_distance_for_similarity={max_distance_for_similarity}, scene_change_threshold={scene_change_threshold}")
    print(f"total frames: {totalFrames}; fps: {fps}")
    # Loop through the video and extract frames at specified sampling rate
    currentFrame = 0
    sceneStartFrame = 0
    while currentFrame < totalFrames:
        if(verbose):
            print(f"currentFrame: {currentFrame}")

        video.set(cv2.CAP_PROP_POS_FRAMES, currentFrame)
        success, frame = video.read()
        if not success:
            break
        
        _, buffer = cv2.imencode(".jpg", frame)
        seekImage = Image.open(BytesIO(buffer))
        
        # check if the image is all white or black, and if so skip it!
        extrema = seekImage.convert("L").getextrema()
        # all black: (0, 0); all white: (1, 1)
        if extrema == (0, 0) or extrema == (1, 1):
            # handle blank images at the beginning (skip them ... )
            if sceneStartFrame >= currentFrame:
                sceneStartFrame = currentFrame + frames_to_skip + 1
            currentFrame += (frames_to_skip + 1)
            continue

        seekImage.save(seekImageURL)
        sceneStartURL = constructImageURL(path, videoName, sceneStartFrame)
        if currentFrame == sceneStartFrame:
            # store the first frame as our starting point
            if(verbose):
                print(f"new sceneStartFrame: {sceneStartFrame}")
            seekImage.save(sceneStartURL)
        else:
            # compare this with the previous frame
            score = sift_sim2(sceneStartURL, seekImageURL, max_distance_for_similarity)
            if(verbose):
                print(f"startFrame: {sceneStartFrame} - currentFrame: {currentFrame}: score={score}")

            # we always store the last frame!
            if (score > 0 and score < scene_change_threshold) or (currentFrame == totalFrames - 1):
                # we have a scene change!
                print(f"we have a scene change at frame {currentFrame} for score={score}!")
                #  save the new scene image to file (refresh the buffer before, just in case the imgA handler changed anything ...)
                imageURL = saveFrameToFile(path, videoName, video, currentFrame)
                # ensure the last frame is stores as well (we need it for logging purposes later!)
                sceneStartURL = saveFrameToFile(path, videoName, video, sceneStartFrame)
                
                print(f"saved scene change image at {imageURL}!")
                sceneChangeImages.append({'frame': currentFrame, 'frame_before': sceneStartFrame, 'image_url': imageURL, 'image_url_before': sceneStartURL, 'similarity_score': score})
                storeSnapShots(video, path, videoName, currentFrame)
                sceneStartFrame = currentFrame
        
        currentFrame += (frames_to_skip + 1)
    
    video.release()
    if os.path.exists(seekImageURL):
        os.remove(seekImageURL)
    print(f"Extracted {len(sceneChangeImages)} scene changes")

    response['video_length_seconds'] = int(totalFrames / fps)
    response['total_frames'] = totalFrames
    response['frames_per_second'] = fps
    response['score_threshold'] = scene_change_threshold
    response['max_distance_for_similarity'] = max_distance_for_similarity
    response['scored_scene_changes'] = sceneChangeImages

    # write this to file
    metaPath = os.path.join(path, videoName, videoName + "-scenes-orb.json")
    with open(metaPath, 'w') as f:
        json.dump(response, f)
        f.close()

    return sceneChangeImages

def saveFrameToFile(path, videoName, video, frameNumber):
    imgURL = constructImageURL(path, videoName, frameNumber)
    video.set(cv2.CAP_PROP_POS_FRAMES, frameNumber)
    success, frame = video.read()
    if not success:
        return
    _, buffer = cv2.imencode(".jpg", frame)
    img = Image.open(BytesIO(buffer))
    img.save(imgURL)
    return imgURL

def ensureVideoFolder(path, videoName):
    folder = os.path.join(path, videoName)
    if not os.path.exists(folder):
        os.mkdir(folder)
    return folder

def constructImageURL(path, videoName, frameNumber):
    ensureVideoFolder(path, videoName)
    return os.path.join(path, videoName, videoName + '_frame' + str(frameNumber) + '.jpg')

# score for image similarity [0..1]
# see https://zwolf12.medium.com/video-scene-detection-and-classification-pyscenedetect-places365-and-mozilla-deepspeech-engine-51338e3dbacc
def sift_sim2(path_a, path_b, max_distance_for_similarity=70):
    orb = cv2.ORB_create()
    # find the key points and descriptors with SIFT
    img_a = cv2.imread(path_a)
    img_b = cv2.imread(path_b)
    kp_a, desc_a = orb.detectAndCompute(img_a, None)
    kp_b, desc_b = orb.detectAndCompute(img_b, None)
    # initialize the bruteforce matcher
    bf = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
    # match.distance is a float between {0:100} - lower means more similar
    try:
        matches = bf.match(desc_a, desc_b)
        similar_regions = [i for i in matches if i.distance < max_distance_for_similarity]
        if len(matches) == 0:
            return 0
        return len(similar_regions) / len(matches)
    except Exception as err:
        print(f"WARNING: unexpected {err=}, {type(err)=}")
        return 0


def extractAudio(folderPath, videoName):
    path = os.path.join(folderPath, videoName)
    if not os.path.exists(path):
        os.mkdir(path)
    audio_path = os.path.join(folderPath, videoName, videoName + '.mp3')
    video_path = os.path.join(folderPath, videoName + '.mp4')
    clip = VideoFileClip(video_path)
    clip.audio.write_audiofile(audio_path, bitrate="32k")
    clip.audio.close()
    clip.close()


# call OpenAI model to label images at position and position-1 to look for scene similarities
def labelForSameVideoScene(openAI_caller, sceneChanges, position):
    if position > (len(sceneChanges) - 1):
        print(f"out of position! {position}/{len(sceneChanges) - 1}")
        return
    url_old = sceneChanges[position]['image_url_before']
    frame_old = sceneChanges[position]['frame_before']
    url_new = sceneChanges[position]['image_url']
    frame_new = sceneChanges[position]['frame']

    [same_scene, explanation] = openAI_caller.labelForSameVideoScene(url_old, url_new)
    return [same_scene, frame_old, frame_new, explanation]


# summarize the video based on a set of scenes we send.
# llmSceneChanges trump the raw sceneChanges (i.e. if there are enough of them, we use those over the raw)
# if there are too many scenes (count > max_scenes_for_summary), we filter them by score, lowering the threshold until we get to the max count
def summarizeVideo(path, videoName, sceneChanges, llmSceneChanges, max_scenes_for_summary):
    scoreFilterThreshold = 0.25
    scenes = llmSceneChanges
    if len(llmSceneChanges) <= 2 and len(sceneChanges) > len(llmSceneChanges):
        print(f"WARNING: not enough labeled scene changes {len(llmSceneChanges)}: attempting a fallback ...")
        scenes = sceneChanges
    # if we have too many scenes: keep lowering the filter score threshold until we have no more than the max scenes
    while len(scenes) > max_scenes_for_summary:
        # try to filter by score (low similarity score), in case we have too many scenes (will run into token limits with model!)
        print(f"fallback: filter {len(scenes)} scene changes to max={max_scenes_for_summary}...")
        # only keep the ones with a similarity score greater than the threshold!
        scenes = [scene for scene in scenes if (scene['similarity_score'] >= scoreFilterThreshold)]
        scoreFilterThreshold += 0.01
    openAI_caller.videoSummaryURL(scenes, path, videoName)


def downloadYouTubeVideo(videoURL, outputPath, fileName):
    # output_path, filename, skip_existing: bool = True
    YouTube(videoURL).streams[0].download(outputPath, fileName)
    return os.path.join(outputPath, fileName)


def scoreFramesAndLabelSceneChanges(path, videoName, maxDistanceForSimilarity=60, scoreThreshold=.80, verbose=False):
    sceneChanges = findSceneChanges(path, videoName,
                                                    max_distance_for_similarity=maxDistanceForSimilarity,
                                                    scene_change_threshold=scoreThreshold, verbose=verbose)

    llmSceneChanges = labelSceneChanges(sceneChanges, path, videoName)

    return [sceneChanges, llmSceneChanges]

def labelSceneChanges(sceneChanges, path, videoName):
    response = {}
    llmSceneChanges = []
    llmSceneRejections = []
    
    if len(sceneChanges) <= 0:
        return llmSceneChanges;

    for counter, sceneChange in enumerate(sceneChanges):
        try:
            # generate uuid for this request
            uid = uuid.uuid4()
            [is_same_scene, frame_old, frame_new, explanation] = labelForSameVideoScene(openAI_caller, sceneChanges, counter)
            if sceneChange['frame'] != frame_new:
                print(f"ERROR: sceneChange['frame'] is not equal to frame_new: {sceneChange['frame']} <-> {frame_new}")

            # score the similarity of these two images (so we can compare ours to theirs!)
            # labeledImageScore = sift_sim2(sceneChange['image_url'], url_old, maxDistanceForSimilarity)

            label_dict = {'uuid': str(uid), 'frame': frame_new, 'frame_before': frame_old, 'image_url': sceneChange['image_url'],
                            'image_url_before': sceneChange['image_url_before'], 'explanation': explanation, 'similarity_score': sceneChange['similarity_score']}
            print(
                f"{counter + 1}/{len(sceneChanges)}: label found scene change from frame {frame_old} to {frame_new}? {not is_same_scene}")
            if not is_same_scene:
                # the model thinks this is a new scene!
                print(f"{counter + 1}/{len(sceneChanges)}: frame {sceneChange['frame']}: {explanation}")
                llmSceneChanges.append(label_dict)
            else:
                llmSceneRejections.append(label_dict)
        except Exception as e:
            print(f"error labeling for frame={sceneChange['frame']}: {sceneChange['image_url']}")
            print(e)
            llmSceneChanges.append(
                {'frame': sceneChange['frame'], 'image_url': sceneChange['image_url'], 'frame_before': sceneChange['frame_before'], 'image_url_before': sceneChange['image_url_before'],
                'explanation': 'fallback: encountered labeling error for this frame!', 'similarity_score': sceneChange['similarity_score']})
    
    response["same_scene_prompt_user"] = openAI_caller.USER_PROMPT
    response["same_scene_prompt_system"] = openAI_caller.SYSTEM_PROMPT
    response["same_scene_model"] = openAI_caller.MODEL_NAME

    response['labeled_changes'] = llmSceneChanges
    response['labeled_rejections'] = llmSceneRejections

    # write llm label responses to scenes meta file
    metaPath = os.path.join(path, videoName, videoName + "-scenes-llm.json")
    with open(metaPath, 'w') as f:
        json.dump(response, f)
        f.close()
    
    return llmSceneChanges

def main():
    print("extracting video details ...")
    path = '/Users/martin/work/tmp/ai-data/videos/'

    videoName = None
    if len(sys.argv) > 1:
        videoName = sys.argv[1]

    if videoName is None:
        videoName = '4K_Sample_Video_Alpha_6700_Sony-α'

    print('processing video: ' + videoName)

    scoreThreshold = None
    if len(sys.argv) > 2:
        scoreThreshold = float(sys.argv[2].strip())

    # default
    if scoreThreshold is None:
        scoreThreshold = .80

    maxDistanceForSimilarity = None
    if len(sys.argv) > 3:
        maxDistanceForSimilarity = int(sys.argv[3].strip())
    if maxDistanceForSimilarity is None:
        maxDistanceForSimilarity = 60

    print(
        f'scoring similarities with threshold: {scoreThreshold} and max distance for similarity: {maxDistanceForSimilarity}')

    sceneChanges = findSceneChanges(path, videoName,
                                                    max_distance_for_similarity=maxDistanceForSimilarity,
                                                    scene_change_threshold=scoreThreshold)


    # get a list of first images of a new scene in the video
    # [sceneChanges, llmSceneChanges] = scoreFramesAndLabelSceneChanges(path, videoName,
    #                                                                     maxDistanceForSimilarity=maxDistanceForSimilarity,
    #                                                                     scoreThreshold=scoreThreshold)
    # extractAudio(path, videoName)
    # openAI_caller.transcribeVideo(path, videoName)

    # summarize the video based on a set of scene images
    # if there aren't any scene changes detected from labels, try to use the raw changes (before labeling)
    # max_scenes_for_summary = 16
    # summarizeVideo(path, videoName, sceneChanges, llmSceneChanges, max_scenes_for_summary)


# usage:
# python video_extractor.py big_buck_bunny .80
# python video_extractor.py [video file (mp4)] [scene change score threshold]
if __name__ == "__main__":
    main()
