import { React, useState, useEffect } from "react";
import { useParams, NavLink } from "react-router-dom";
import axios from "axios";
import './../config';

function Video () {
    const params = useParams();

    const [videoData, setVideoData] = useState([]);

    const [similarityDistance, setSimilarityDistance] = useState(60)
    const [scoreThreshold, setScoreThreshold] = useState(.70)
    const [callLLMs, setCallLLMs] = useState({selected: false})

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
    
    const LLMVideoScenes = (props) => {
      if(props.hasScenes === true){
        var scenes = props.videoData[0].llm_scenes 
        var isLLM = true
        if(Object.entries(scenes).length <= 0){
          scenes = props.videoData[0].orb_scenes
          isLLM = false
        }

        return (
            scenes.map((scene) => (
                  <tr key={scene.last_scene_image_id}>
                    <td className="image_thumb"><a id={scene.last_scene_frame} name={scene.last_scene_frame} href="#"> </a><img className="image_thumb" src={global.config.noi_server.root + '/api/image/' + scene.last_scene_image_id } alt={scene.last_scene_image_id}/></td>
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

                    <td className="image_thumb"><a id={scene.first_scene_frame} name={scene.first_scene_frame} href="#"> </a><img className="image_thumb" src={global.config.noi_server.root + '/api/image/' + scene.first_scene_image_id } alt={scene.first_scene_image_id} /></td>
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
                        <Checkbox value={scene.is_new_video_scene && isLLM}/>
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
      axios.post(global.config.noi_server.root + '/api/labels/' + params.id).then((response) => {
        console.log(response.data);
        hideProgressbar();
      });
    }

    async function handleEmbeddingClick() {
        if (Object.entries(videoData).length <= 0){
          console.log("no videoData!");
          return;
        }
        showProgressbar();
        console.log("create embeddings for all images for video " + params.id);
        axios.post(global.config.noi_server.root + '/video-embeddings/' + params.id).then((response) => {
          console.log(response.data);
          hideProgressbar();
        });
    }

    async function handleDelete() {
      if (Object.entries(videoData).length <= 0){
        console.log("no videoData to delete!");
        return;
      }

      // delete the video and return to the list
      // var postData = {"video_id": params.id, "video_name": videoData[0].name}
      axios.delete(global.config.noi_server.root + '/video/' + params.id).then((response) => {
        var data = response.data;
        if(data.hasOwnProperty('error')){
          alert(data.error);
        }else{
          // redirect to /videos 
          window.location.href='/videos';
        }
      }).catch(error => {
        console.log("error -> ", error);
        alert(error);;
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
      setVideoData([]);
      let postData = {"video_id": params.id, "video_name": videoData[0].name, "refresh": true, "llm": false, "maxSimilarityDistance": similarityDistance, "sceneChangeScoreThreshold": scoreThreshold}
      // Note!: this requires the python server (ORB script) to be running (on port 8000)!
      axios.post(global.config.noi_server.root + '/orbX', postData).then((response) => {
        console.log(response.data);
        let data = response.data;
        setVideoData(data.videos);
        if(data.videos.length > 0){
          setScoreThreshold(data.videos[0].orb_scoring_threshold);
          setSimilarityDistance(data.videos[0].orb_scoring_max_distance);
        }

        // now call the same endpoint, but this time ask to call the LLM(s) to label the scene changes, and do the rest (video summary, audio summary, ...)
        if(callLLMs.selected === true){
          console.log('now call the LLMs ...');
          //postData = {"video_name": videoData[0].name, "refresh": true, "llm": true}
          axios.post(global.config.noi_server.root + '/video-llms/' + params.id).then((llmResponse) => {
            console.log(llmResponse.data);
            let data = llmResponse.data;
            setVideoData(data.videos);
            if(data.videos.length > 0){
              setScoreThreshold(data.videos[0].orb_scoring_threshold);
              setSimilarityDistance(data.videos[0].orb_scoring_max_distance);
            }
    
            hideProgressbar();
          });
        }
        else{
          hideProgressbar();
        }
        
        // let videoJson = response.data;
        // if (Object.entries(videoJson).length > 0){
        //   // post the results from the py script to be parsed and stored in the db
        //   axios.post(global.config.noi_server.root + '/video/' + params.id, videoJson).then((sqlResponse) => {
        //     // we posted the json to be parsed and written into the db, now what? 
        //     let data = sqlResponse.data;
        //     setVideoData(data.videos);

        //     // now call the same endpoint, but this time ask to call the LLM(s) to label the scene changes, and do the rest (video summary, audio summary, ...)
        //     if(callLLMs.selected === true){
        //       console.log('now call the LLMs ...');
        //       //postData = {"video_name": videoData[0].name, "refresh": true, "llm": true}
        //       axios.post(global.config.noi_server.root + '/video-llms/' + params.id).then((llmResponse) => {
        //         console.log(llmResponse.data);
        //         let data = llmResponse.data;
        //         setVideoData(data.videos);
        //         hideProgressbar();
        //       });
        //     }
        //     else{
        //       hideProgressbar();
        //     }
        //   })
        // }
      });
    }

    useEffect(() => {
      // Call the async function
      const fetchData = async (videoId) => {
        try {
          console.log("calling for video meta...")
          showProgressbar();
          axios.get(global.config.noi_server.root + "/videos/" + videoId).then((response) => {
            let data = response.data;
              if (Object.entries(data).length >= 0) {
                setVideoData(data.videos);
                if(data.videos.length > 0){
                  setScoreThreshold(data.videos[0].orb_scoring_threshold);
                  setSimilarityDistance(data.videos[0].orb_scoring_max_distance);
                }
              }
              hideProgressbar();
            });
        } catch (error) {
          console.error(error);
        }
      };
  
      console.log({params});
      fetchData(params.id);
    }, []);

    return (
        <div className='video'>
          <div className="card">
            <div className="card-body">
              <video width="400" controls>
                <source src={global.config.noi_server.root + "/video/" + params.id} type="video/mp4" />
                Your browser does not support HTML video.
              </video>
            </div>
            <div className="card-button">
              <button onClick={async () => { await handleClick();}}>Process Video</button>
              <button onClick={async () => { await handleDelete();}}>Delete!</button>
              <div className="left-padding">
                <label className="label-padding">Call LLMs</label><input name='llms' type="checkbox" checked={callLLMs.selected} onChange={(event) => {setCallLLMs({selected: !callLLMs.selected});}}></input>
              </div>
              <div className="left-padding">
                <label className="label-padding">Max Similarity Distance</label><input name='maxSimilarityDistance' defaultValue={similarityDistance} value={similarityDistance} onChange={(event) => {setSimilarityDistance(parseInt(event.target.value));}}></input>
              </div>
              <div className="left-padding">
                <label className="label-padding">Similarity Score Threshold</label>
                <input name='sceneChangeScoreThreshold' type='number' min='0.10' max='0.99' step='0.01' value={scoreThreshold} onChange={(event) => {setScoreThreshold(parseFloat(event.target.value));}}></input>
              </div>
            </div>
            <div className="card-button">
              <button onClick={async () => { await handleLabelClick();}}>Label All Images</button>
            </div>
            <div className="card-button">
              <button onClick={async () => { await handleEmbeddingClick();}}>Create Embeddings for All Images</button>
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
                      <td>Brand</td>
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
                            {video.brand}
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
                        <span>Video Summary</span>
                        {/* <div style={{float: "right", paddingRight: 25}}>
                          <NavLink
                            to={story_path + params.id }
                            key={params.id}
                            className="story-link">
                              <span className="story_link_text">Video Timeline</span>
                          </NavLink>
                        </div> */}
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
              <h3>Scene Changes</h3>
              <div style={{float: "right", paddingRight: 50}}>
                          <NavLink
                            to={story_path + params.id }
                            key={params.id}
                            className="story-link">
                              <span className="story_link_text">Video Timeline</span>
                          </NavLink>
                        </div>

            </div>

            <div className="card-body">
    
              <div className="table-responsive">
                <table width="90%">
                  <thead>
                    <tr>
                      <td></td>
                      <td>Old Scene</td>
                      <td></td>
                      <td>New Scene</td>
                      <td>Similarity Score</td>
                      <td>LLM New Scene?</td>
                    </tr>
                  </thead>
                  <tbody>
                      <LLMVideoScenes hasScenes={Object.entries(videoData).length > 0 } videoData={videoData}/>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
    
          {/* list local scene change scores */}
          {/* <div className="card">
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
          </div> */}
          
        </div>
      );
    
}

export default Video;