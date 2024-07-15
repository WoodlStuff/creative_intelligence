# webapp.py
# from: https://realpython.com/python-http-server/

import json 

from functools import cached_property
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qsl, urlparse

import video_extractor 
import openAI_caller

import os
import logging

class WebRequestHandler(BaseHTTPRequestHandler):
    @cached_property
    def url(self):
        return urlparse(self.path)

    @cached_property
    def query_data(self):
        return dict(parse_qsl(self.url.query))

    @cached_property
    def post_data(self):
        content_length = int(self.headers.get("Content-Length", 0))
        return self.rfile.read(content_length)

    @cached_property
    def form_data(self):
        return dict(parse_qsl(self.post_data.decode("utf-8")))

    @cached_property
    def cookies(self):
        return SimpleCookie(self.headers.get("Cookie"))
        
    def get_response(self):
        postData = self.post_data.decode("utf-8")
        logging.info("Video Processor: " + postData)
        jsonData = json.loads(postData)

        videoName = jsonData['video_name']
        
        path = '/Users/martin/work/tmp/ai-data/videos/'
        if 'path' in jsonData:
            path = jsonData['path']

        refresh = False
        if 'refresh' in jsonData:
            refresh = jsonData['refresh']

        callLLM = False
        if 'llm' in jsonData:
            callLLM = jsonData['llm']

        verbose = False
        if 'verbose' in jsonData:
            verbose = jsonData['verbose']

        maxDistanceForSimilarity=60 
        if 'maxSimilarityDistance' in jsonData:
            maxDistanceForSimilarity = jsonData['maxSimilarityDistance']

        scoreThreshold=.70
        if 'sceneChangeScoreThreshold' in jsonData:
            scoreThreshold = jsonData['sceneChangeScoreThreshold']

        llmSceneChanges = []
        sceneChanges = []

        if refresh:
            # split the calls!
            if callLLM:
                scoredMetaPath = os.path.join(path, videoName, videoName + "-scenes-orb.json")
                with open(scoredMetaPath, 'r') as f:
                    orbScores = json.loads(f.read())
                    sceneChanges = orbScores['scored_scene_changes']

                llmSceneChanges = video_extractor.labelSceneChanges(sceneChanges, path, videoName)
            else:
                sceneChanges = video_extractor.findSceneChanges(path, videoName,
                                                    max_distance_for_similarity=maxDistanceForSimilarity,
                                                    scene_change_threshold=scoreThreshold, verbose=verbose)

        # file at <folder>/<video_name>/<video_name>_scenes.json
        if callLLM:
            metaPath = os.path.join(path, videoName, videoName + "-scenes-llm.json")
        else:
            metaPath = os.path.join(path, videoName, videoName + "-scenes-orb.json")
        with open(metaPath, 'r') as f:
            jsonResponse = json.loads(f.read())

        if refresh and callLLM:
            # extract audio from the video
            video_extractor.extractAudio(path, videoName)
            
            audio_path = os.path.join(path, videoName, videoName + '.mp3')
            jsonResponse.update({"audio_path": audio_path})

            # ask the model to transcribe the sound , and create a summary for it
            openAI_caller.transcribeVideo(path, videoName)

            # read transcripts and add them to the json response
            textPath = os.path.join(path, videoName, videoName + '_transcript.json')
            textSummaryPath = os.path.join(path, videoName, videoName + '_transcript_summary.json')
            jsonResponse.update({"audio_transcript": textPath})
            jsonResponse.update({"audio_summary": textSummaryPath})

            # summarize the video based on a set of scene images
            # if there aren't any scene changes detected from labels, try to use the raw changes (before labeling)
            # if we have more than max_scenes_for_summary scenes, then filter the list by score until we get to no more than max_scenes_for_summary
            max_scenes_for_summary = 16
            video_extractor.summarizeVideo(path, videoName, sceneChanges, llmSceneChanges, max_scenes_for_summary)
        
            summaryPath = os.path.join(path, videoName, videoName + '_scene_summary.json')
            jsonResponse.update({"video_summary": summaryPath})
        
        return json.dumps(jsonResponse)

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(self.get_response().encode("utf-8"))

    def do_POST(self):
            self.do_GET()

if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8000), WebRequestHandler)
    server.serve_forever()
    