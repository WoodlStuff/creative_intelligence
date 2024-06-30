import { React, useState, useEffect } from "react";
import { useParams, NavLink } from "react-router-dom";
import axios from "axios";

function Video () {
    const params = useParams();

    const [videoData, setVideoData] = useState([]);

    const image_path = '/image/';
    const story_path = '/story/';


    function showProgressbar(){
      // find progress bar with id='progressbar' and show it
      let progress = document.getElementById('progressbar');
      progress.hidden=false;
    }
  
    function hideProgressbar(){
      // find progress bar with id='progressbar' and hide it
      let progress = document.getElementById('progressbar');
      progress.hidden=true;
    }
  
    const Checkbox = (props) => {
        return <input type="checkbox" checked={props.value} disabled/>
      }
    
      const ORBVideoScenes = (props) => {
        if(props.hasScenes == true){
            return (
                props.videoData[0].orb_scenes.map((scene) => (
                    <tr key={scene.last_scene_frame}>
                      <td className="image_thumb"><a id={scene.last_scene_frame} name={scene.last_scene_frame}> </a><img className="image_thumb" src={'http://localhost:8080/noi-server/api/image/' + scene.last_scene_image_id } /></td>
                      <td>
                        <NavLink
                          to={image_path + scene.last_scene_image_id }
                          key={scene.last_scene_image_id}
                          className="image-link"
                        >
                          {/* <div className="icon">{image.icon}</div> */}
                          <div className="image_link_text">{scene.last_scene_frame}</div>
                        </NavLink>
                      </td>

                      <td className="image_thumb"><a id={scene.first_scene_frame} name={scene.first_scene_frame}> </a><img className="image_thumb" src={'http://localhost:8080/noi-server/api/image/' + scene.first_scene_image_id } /></td>
                      <td>
                        <NavLink
                          to={image_path + scene.first_scene_image_id }
                          key={scene.first_scene_image_id}
                          className="image-link"
                        >
                          {/* <div className="icon">{image.icon}</div> */}
                          <div className="image_link_text">{scene.first_scene_frame}</div>
                        </NavLink>
                      </td>
                      <td>
                        <span>{scene.score}</span>
                      </td>
                    </tr>
                ))
            )
        }
        return <></>
      }

      const LLMVideoScenes = (props) => {
        if(props.hasScenes == true){
            return (
                props.videoData[0].llm_scenes.map((scene) => (
                    <tr key={scene.last_scene_image_id}>
                      <td className="image_thumb"><a id={scene.last_scene_frame} name={scene.last_scene_frame}> </a><img className="image_thumb" src={'http://localhost:8080/noi-server/api/image/' + scene.last_scene_image_id } /></td>
                      <td>
                        <NavLink
                          to={image_path + scene.last_scene_image_id }
                          key={scene.last_scene_image_id}
                          className="image-link"
                        >
                          {/* <div className="icon">{image.icon}</div> */}
                          <div className="image_link_text">{scene.last_scene_frame}</div>
                        </NavLink>
                      </td>

                      <td className="image_thumb"><a id={scene.first_scene_frame} name={scene.first_scene_frame}> </a><img className="image_thumb" src={'http://localhost:8080/noi-server/api/image/' + scene.first_scene_image_id } /></td>
                      <td>
                        <NavLink
                          to={image_path + scene.first_scene_image_id }
                          key={scene.first_scene_image_id}
                          className="image-link"
                        >
                          {/* <div className="icon">{image.icon}</div> */}
                          <div className="image_link_text">{scene.first_scene_frame}</div>
                        </NavLink>
                      </td>
                      <td>
                        <span>{scene.score}</span>
                      </td>
                      <td className="image_checkbox">
                        <div className="tooltip-wrap">
                          <Checkbox value={scene.is_new_video_scene}/>
                           <div className="tooltip-content">
                            {scene.explanation}
                           </div> 
                        </div>
                      </td>
                    </tr>
                ))
            )
        }
        return <></>
      }

      async function handleLabelClick() {
      if (Object.entries(videoData).length <= 0){
        console.log("no videoData!");
        return;
      }
      showProgressbar();
      console.log("label images for video " + params.id);
      // Note!: this requires the python server to be running!
      axios.post('http://localhost:8080/noi-server/api/labels/' + params.id).then((response) => {
        console.log(response.data);
        hideProgressbar();
      });
    }

    async function handleClick() {
      if (Object.entries(videoData).length <= 0){
        console.log("no videoData!");
        return;
      }

      showProgressbar();
      console.log("process video " + params.id);
      console.log("process video " + videoData[0].name);
      // curl -v -X POST http://localhost:8000/video -d '{"video_name": "big_buck_bunny", "refresh": false}'
      let postData = {"video_name": videoData[0].name, "refresh": true}
      // Note!: this requires the python server to be running!
      axios.post('http://localhost:8000/video', postData).then((response) => {
        console.log(response.data);
        let videoJson = response.data;
        if (Object.entries(videoJson).length > 0){
          axios.post('http://localhost:8080/noi-server/api/video/' + params.id, videoJson).then((sqlResponse) => {
            // we posted the json to be parsed and written into the db, now what? 
            let data = sqlResponse.data;
            setVideoData(data.videos);
            hideProgressbar();
          })
        }
      });
    } 

    useEffect(() => {
      let isCalled = false;
      // Call the async function
      const fetchData = async (videoId) => {
        try {
          console.log("calling for video meta...")
          showProgressbar();
          axios.get("http://localhost:8080/noi-server/api/videos/" + videoId).then((response) => {
            let data = response.data;
            if (!isCalled) {
              if (Object.entries(data).length >= 0) {
                setVideoData(data.videos);
                hideProgressbar();
              }
            }
          });
        } catch (error) {
          console.error(error);
        }
      };
  
      console.log({params});
      fetchData(params.id);
      return () => isCalled = true;
    }, []);

    return (
        <div className='video'>
          <div className="card">
            <div className="card-body">
              <video width="400" controls>
                <source src={"http://localhost:8080/noi-server/api/video/" + params.id} type="video/mp4" />
                Your browser does not support HTML video.
              </video>
            </div>
            <div className="card-button">
              <button onClick={async () => { await handleClick();}}>Process Video</button>
            </div>
            <div className="card-button">
              <button onClick={async () => { await handleLabelClick();}}>Label All Images</button>
            </div>
          </div>
          
          <progress id='progressbar' value={null} hidden />

          <div className="card">
            <div className="card-header">
              <h3>Video Details</h3>
            </div>
            <div className="card-body">
              <div className="table-responsive">
                <table width="10%">
                  <thead>
                    <tr>
                      <td>URL</td>
                      <td>Frame Rate</td>
                      <td>Frame Count</td>
                      <td>Length (s)</td>
                      <td>Status</td>
                    </tr>
                  </thead>
                    <tbody>
                    {
                      videoData.map((video) => (
                        <tr key={video.url}>
                          <td>
                            {video.url}
                          </td>
                          <td>
                            {video.frame_rate}
                          </td>
                          <td>
                            {video.frame_count}
                          </td>
                          <td>
                            {video.seconds}
                          </td>
                          <td>
                            {video.status}
                          </td>
                        </tr>
                      ))
                    }
                  </tbody>
                </table>
              </div>
            </div>

            <div className="card-body">
              <div className="table-responsive">
                <table width="100%">
                  <thead>
                    <tr>
                      <td width="60%">
                        <NavLink
                          to={story_path + params.id }
                          key={params.id}
                          className="story-link">
                            <span className="story_link_text">Video Summary</span>
                          </NavLink>
                        </td>
                      <td width="40%">Sound Transcript Summary</td>
                    </tr>
                  </thead>
                    <tbody>
                    {
                      videoData.map((video) => (
                        <tr key={video.url}>
                          <td>
                            <textarea className="textarea_video" defaultValue={video.video_summary}/>
                          </td>
                          <td>
                          <textarea className="textarea_sound" defaultValue={video.sound_summary}/>
                          </td>
                        </tr>
                      ))
                    }
                  </tbody>
                </table>
              </div>
            </div>

          </div>
          {/* end video <detail */}

          {/* list labeled (by LLM) scene change scores */}
          <div className="card">
            <div className="card-header">
              <h3>LLM Scene Changes</h3>
            </div>

            <div className="card-body">
    
              <div className="table-responsive">
                <table width="90%">
                  <thead>
                    <tr>
                      <td></td>
                      <td>Last Frame</td>
                      <td></td>
                      <td>First Frame</td>
                      <td>Similarity Score</td>
                      <td>New Scene?</td>
                    </tr>
                  </thead>
                  <tbody>
                      <LLMVideoScenes hasScenes={Object.entries(videoData).length > 0 && Object.entries(videoData[0].llm_scenes).length >= 0 } videoData={videoData}/>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
    
          {/* list local scene change scores */}
          <div className="card">
            <div className="card-header">
              <h3>ORB Scene Changes</h3>
            </div>

            <div className="card-body">
    
              <div className="table-responsive">
                <table width="90%">
                  <thead>
                    <tr>
                      <td></td>
                      <td>Last Frame</td>
                      <td></td>
                      <td>First Frame</td>
                      <td>Similarity Score</td>
                    </tr>
                  </thead>
                  <tbody>
                      <ORBVideoScenes hasScenes={Object.entries(videoData).length > 0 && Object.entries(videoData[0].orb_scenes).length >= 0 } videoData={videoData}/>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
          
        </div>
      );
    
}

export default Video;