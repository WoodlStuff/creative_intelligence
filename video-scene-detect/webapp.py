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
        jsonData = json.loads(postData)
        videoName = jsonData['video_name']
        path = '/Users/martin/work/AI/videos'

        if 'path' in jsonData:
            path = jsonData['path']

        refresh = False
        if 'refresh' in jsonData:
            refresh = jsonData['refresh']

        if refresh:
            [sceneChanges, labelSceneChanges] = video_extractor.scoreFramesAndLabelSceneChanges(path, videoName)

        # file at <folder>/<video_name>/<video_name>_scenes.json
        metaPath = os.path.join(path, videoName, videoName + "-scenes.json")
        with open(metaPath, 'r') as f:
            jsonResponse = json.loads(f.read())
    
        # extract audio from the video
        # if refresh:
        #     extractor.extractAudio(path, videoName)
        
        audio_path = os.path.join(path, videoName, videoName + '.mp3')
        jsonResponse.update({"audio_path": audio_path})

        # ask the model to transcribe the sound , and create a summary for it
        if refresh:
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
        if refresh: 
            video_extractor.summarizeVideo(path, videoName, sceneChanges, labelSceneChanges, max_scenes_for_summary)
        
        summaryPath = os.path.join(path, videoName, videoName + '_scene_summary.json')
        jsonResponse.update({"video_summary": summaryPath})
        
        return json.dumps(jsonResponse)
        # return json.dumps(
        #     {
        #         "path": self.url.path,
        #         "query_data": self.query_data,
        #         "post_data": self.post_data.decode("utf-8"),
        #         "form_data": self.form_data,
        #         "cookies": {
        #             name: cookie.value
        #             for name, cookie in self.cookies.items()
        #         },
        #     }
        # )        

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
    