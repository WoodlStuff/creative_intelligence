import React from "react";
import { useEffect, useState, useRef } from "react";
import axios from "axios";
import { HiOutlineArrowSmRight } from "react-icons/hi";
import "./Images.css"
import { NavLink, useParams } from "react-router-dom";

function Image() {
  const params = useParams();

  // const [currentImageId, setCurrentImageId] = useState();
  // const [currentVideoId, setCurrentVideoId] = useState();

  const [imageLabelData, setImageLabelData] = useState([]);
  const [imageAnnotationData, setImageAnnotationData] = useState([]);
  const [imageURL, setImageURL] = useState();
  
  const progress = document.getElementById('progressbar');

  function showProgressbar(){
    // find progress bar with id='progressbar' and show it
    progress.hidden=false;
  }

  function hideProgressbar(){
    // find progress bar with id='progressbar' and hide it
    progress.hidden=true;
  }

  async function handleLabelClick() {
    if (Object.entries(params.id).length <= 0){
      console.log("no image Id!");
      return;
    }

    // set the progress bar to visible
    showProgressbar();
    console.log("label image for id: " + params.id);
    // Note!: this requires the python server to be running!
    axios.post('http://localhost:8080/noi-server/api/label/' + params.id).then((response) => {
      // todo: take the response.data json, and post it to be inserted into the db (post to Java code on :8080)
      console.log(response.data);
      let data = response.data; 
      setImageLabelData(data.categories);
      setImageAnnotationData(data.annotations);
      hideProgressbar();
    });
  }

  useEffect(() => {
    let isCalled = false;
    // Call the async function
    const fetchData = async (imageId) => {
      // Perform async operations here
      // call http endpoint and assign the resulting data to local array
      try {
        console.log("calling for image meta...")
        console.log(imageId);
        axios.get("http://localhost:8080/noi-server/api/label/" + imageId).then((response) => {
          let data = response.data;
          if (!isCalled) {
            if (Object.entries(data).length >= 0) {
              setImageURL(data.path)
              setImageLabelData(data.categories);
              setImageAnnotationData(data.annotations);
            }
          }
        });
      } catch (error) {
        console.error(error);
      }
    };

    console.log({ params });
    fetchData(params.id);
    return () => isCalled = true;
  }, []);

  return (
    <div className='image'>
      <div className="card">
        <div className="card-body">
          <img className="image_detail" src={"http://localhost:8080/noi-server/api/image/" + params.id} />
        </div>
        <div className="image_url">
          <span>{imageURL}</span>
        </div>
        <div className="card-button">
          <button onClick={async () => { await handleLabelClick();}}>Label Image</button>
        </div>
      </div>

      <progress id='progressbar' value={null} hidden />

      <div className="card">
        <div className="card-header">
          <h3>Image Labels</h3>
        </div>
        <div className="card-body">
          <div className="table-responsive">
            <table width="100%">
              <thead>
                <tr>
                  <td>Category</td>
                  <td>Key</td>
                  <td>Value</td>
                  <td>request uuid</td>
                  <td>Model</td>
                </tr>
              </thead>
              <tbody>
                {
                  imageLabelData.map((meta) => (
                    <tr key={meta.key + '-'+ meta.value + '-' + meta.request_uuid}>
                      <td>
                        {meta.category_name}
                      </td>
                      <td>
                        {meta.key}
                      </td>
                      <td>
                        {meta.value}
                      </td>
                      <td>
                        {meta.request_uuid}
                      </td>
                      <td>
                        {meta.model_name}
                      </td>
                    </tr>
                  ))
                }
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h3>Image Annotations</h3>
        </div>

        <div className="card-body">

          <div className="table-responsive">
            <table width="100%">
              <thead>
                <tr>
                  <td>Mid</td>
                  <td>Score</td>
                  <td>Topicality</td>
                  <td>Model</td>
                </tr>
              </thead>
              <tbody>
                {
                  imageAnnotationData.map((anno) => (
                    <tr key={anno.mid}>
                      <td>
                        {anno.mid}
                      </td>
                      <td>
                        {anno.score}
                      </td>
                      <td>
                        {anno.topicality}
                      </td>
                      <td>
                        {anno.model_name}
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

export default Image;