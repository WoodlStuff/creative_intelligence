import React from "react";
import { useEffect, useState, useRef } from "react";
import axios from "axios";
import { HiOutlineArrowSmRight } from "react-icons/hi";
import "./Images.css"
import { NavLink, useNavigate } from "react-router-dom";

function Images() {
  const [imageData, setImageData] = useState([]);
  const [currentImageId, setCurrentImageId] = useState();
  const [currentVideoId, setCurrentVideoId] = useState();

  const video_path = '/video';
  const image_path = '/image';
  const delay = ms => new Promise(res => setTimeout(res, ms));

  const navigate = useNavigate();

  const Checkbox = (props) => {
    return <input type="checkbox" checked={props.value} disabled/>
  }

  const VideoNav = (props) => {
    if(props.hasFrame && props.frame > 0){
      return <NavLink
      to={video_path + '/' + props.video_id + "#" + props.frame }
      key={props.image_url}
      className="image-link"
    >
      {/* <div className="icon">{image.icon}</div> */}
      <div className="image_link_text">{props.frame}</div>
    </NavLink> 
    }
    return <></>
  }

  useEffect(() => {
    let isCalled = false;
    // Call the async function
    const fetchData = async () => {
      // Perform async operations here
      // call http endpoint and assign the resulting data to local array
      try {
        axios.get("http://localhost:8080/noi-server/api/images").then((response) => {
          let data = response.data;
          if (!isCalled) {
            if (Object.entries(data).length >= 0) {
              setImageData(data.images);
            }
          }
        });
      } catch (error) {
        console.error(error);
        imageData = []
      }
    };

    fetchData();
    return () => isCalled = true;
  }, []);

  function videoClickHandler(id){
    navigate('/video/' + id);
  }

  function imageClickHandler(id) {
    navigate('/image/' + id);
  }

  return (
    <div className='images'>
      <div className="card">
        <div className="card-header">
          <h3>Recent Images</h3>
          <div className="new_button"><a href="/upload-image">Upload New Image ...</a></div>
          <button>
            See all
            <span className="las la-arrow-right">
              <HiOutlineArrowSmRight />
            </span>
          </button>
        </div>

        <div className="card-body">
          <div className="table-responsive">
            <table width="100%">
              <thead>
                <tr>
                <td className="image_thumb"></td>
                <td className="image_link">Image URL</td>
                  <td className="nav_link">Video Frame</td>
                  <td className="status">Status</td>
                  <td className="image_checkbox">New Scene</td>
                  <td className="image_checkbox">Snap</td>
                </tr>
              </thead>
              <tbody>
                {
                  imageData.map((image) => (
                    <tr key={image.id}  >
                      <td className="image_thumb"><img className="image_thumb" src={'http://localhost:8080/noi-server/api/image/' + image.id} /></td>
                      <td className="image_link">
                        {/* {image.url} */}
                        <NavLink
                          to={image_path + '/' + image.id }
                          key={image.id}
                          className="image-link"
                        >
                          {/* <div className="icon">{image.icon}</div> */}
                          <div className="image_link_text">{image.url}</div>
                        </NavLink>
                      </td>
                      <td className="nav_link">
                        <VideoNav hasFrame={image.hasOwnProperty('frame')} video_id={image.video_id} image_url={image.url} frame={image.frame} />
                      </td>
                      <td className="status">
                        <span className="status"></span>
                        {image.status}
                      </td>
                      <td className="image_checkbox">
                        <Checkbox value={image.is_new_video_scene}/>
                      </td>
                      <td className="image_checkbox">
                        <Checkbox value={image.is_video_scene_snap}/>
                      </td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Images;