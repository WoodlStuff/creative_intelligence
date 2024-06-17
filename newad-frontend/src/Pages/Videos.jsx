import React from "react";
import { useEffect, useState } from "react";
import axios from "axios";
import { HiOutlineArrowSmRight } from "react-icons/hi";
import "./Images.css"
import { NavLink } from "react-router-dom";

function Videos() {
  const [videoData, setVideoData] = useState([]);
  const video_path = '/video';
  
  useEffect(() => {
    let isCalled = false;
    const fetchData = async () => {
      try {
        axios.get("http://localhost:8080/noi-server/api/videos").then((response) => {
          let data = response.data;
          if (!isCalled) {
            if (Object.entries(data).length >= 0) {
              setVideoData(data.videos);
            }
          }
        });
      } catch (error) {
        console.error(error);
        videoData = []
      }
    };

    fetchData();
    return () => isCalled = true;
  }, []);

  return (
    <div className='videos'>
      <div className="card">
        <div className="card-header">
          <h3>Recent Videos</h3>
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
                  <td>Video URL</td>
                  <td>Frame Rate</td>
                  <td>#Frames</td>
                  <td>Seconds</td>
                  <td>Status</td>
                </tr>
              </thead>
              <tbody>
                {videoData.map(video => (
                  <tr key={video.id}>
                    <td>
                      <NavLink
                        to={video_path + '/' + video.id}
                        key={video.id}
                        className="video-link"
                      >
                        {/* <div className="icon">{image.icon}</div> */}
                        <div className="video_link_text">{video.url}</div>
                      </NavLink>
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
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Videos;