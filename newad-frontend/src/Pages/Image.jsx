import React from "react";
import { useEffect, useState } from "react";
import axios from "axios";
import "./Images.css"
import { useParams } from "react-router-dom";

function Image() {
  const params = useParams();

  const [imageLabelData, setImageLabelData] = useState([]);
  const [similarityData, setSimilarityData] = useState([]);
  const [imageAnnotationData, setImageAnnotationData] = useState([]);
  const [imageURL, setImageURL] = useState();
  const [videoId, setVideoId] = useState();
  const [videoFrameNumber, setVideoFrameNumber] = useState();
  const [brand, setBrand] = useState();
  const [embedding, setEmbedding] = useState(false);
  
  const progress = document.getElementById('progressbar');

  const [selectedCategoryName, setSelectedCategoryName] = useState("- all -");
  const [catSelectorData, setCatSelectorData] = useState(["- all -"]);

  function showProgressbar(){
    // find progress bar with id='progressbar' and show it
    progress.hidden=false;
  }

  function hideProgressbar(){
    // find progress bar with id='progressbar' and hide it
    progress.hidden=true;
  }

  const CategoryOptions = (props) => {
    if(Object.entries(props).length > 0 && Object.entries(props.categories).length > 0){
      return(
        props.categories.map( (o) => (
          <option key={o} value={o}>{o}</option>
        ))
      )
    }

    return(<></>)
  }

  function changeCategory(newValue) {
    setSelectedCategoryName(newValue);
    fetchSimilarityData(params.id, newValue);
  }

  const LabelRows = (props) => {
    if(props.hasLabels === true){
        return (
          props.categories.map((meta) => (
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
          // }
          ))
        )
    }
    return <></>
  }

  function filterCategories() {
    // allow only rows with the selected category name (or '- all -')
    let categories = imageLabelData.filter( (label_category) => (label_category.category_name === selectedCategoryName || selectedCategoryName === '- all -' ));
    if(Object.entries(categories).length > 0){
      return categories;
    }
    
    return[]
  }

  // render a drop down for category selection
  const  CategorySelector = (props) => {
    if(props.hasLabels === true){
      return(
          <label>
            Filter Categories:
            <select
              name="selectedCategory"
              value={selectedCategoryName}
              multiple={false}
              onChange={e => changeCategory(e.target.value) }
            >
                <CategoryOptions categories={props.categories}/>
            </select>
          </label>
      )
    }
    return(<label>no categories loaded!</label>)
  }
  
  async function handleLabelClick() {
    if (Object.entries(params.id).length <= 0){
      console.log("no image Id!");
      return;
    }

    // set the progress bar to visible
    showProgressbar();
    console.log("label image for id: " + params.id);
    axios.post('http://localhost:8080/noi-server/categories/' + params.id).then((response) => {
      // todo: take the response.data json, and post it to be inserted into the db (post to Java code on :8080)
      console.log(response.data);
      let data = response.data; 
      setImageLabelData(data.categories);

      var filterData = data.category_names;
      filterData.push("- all -");
      setCatSelectorData(filterData);

      setImageAnnotationData(data.annotations);
      
      // todo call endpoint to create embeddings and send them to the vector db

      hideProgressbar();

      fetchSimilarityData(params.id, selectedCategoryName);
    });
  }

  async function handleEmbeddingClick() {
    if (Object.entries(params.id).length <= 0){
      console.log("no image Id!");
      return;
    }

    // set the progress bar to visible
    showProgressbar();
    console.log("create embeddings for image id: " + params.id);
    axios.post('http://localhost:8080/noi-server/embeddings/' + params.id).then((response) => {
      console.log(response.data);
      if(Object.entries(response.data.upsertCounts.length > 0)){
        setEmbedding(true);
      }
      else{
        setEmbedding(false);
      }
      
      hideProgressbar();
    });
  }

  const fetchSimilarityData = async (imageId, category) => {
    try {
      console.log("calling for image similarity data ...")
      console.log(imageId);
      if(category === "- all -"){
        setSimilarityData([]);
        return;
      }
      axios.get("http://localhost:8080/noi-server/vectors/" + imageId + "/" + category + "?sameVideo=false").then((response) => {
        let data = response.data;
        if (Object.entries(data).length >= 0) {
          setSimilarityData(data.results);
        }
      });

    } catch (error) {
      console.error(error);
    }
  };

  useEffect(() => {
    const fetchData = async (imageId) => {
      try {
        console.log("calling for image meta...")
        console.log(imageId);
        axios.get("http://localhost:8080/noi-server/categories/" + imageId).then((response) => {
          let data = response.data;
          if (Object.entries(data).length >= 0) {
            setImageURL(data.path)
            setImageLabelData(data.categories);
            var filterData = data.category_names;
            filterData.push("- all -");
            setCatSelectorData(filterData);
            setImageAnnotationData(data.annotations);
            if(data.video_id != null){
              setVideoId(data.video_id);
              setVideoFrameNumber(data.video_frame_number);
            }
            if(data.brand != null){
              setBrand(data.brand);
            }
            setEmbedding(data.has_embedding)
          }
        });
      } catch (error) {
        console.error(error);
      }
    };

    console.log({ params });
    fetchData(params.id);
    fetchSimilarityData(params.id, selectedCategoryName);
  }, []);

  return (
    <div className='image'>
      <div className="card">
        <div className="card-body">
          <img className="image_detail" src={"http://localhost:8080/noi-server/api/image/" + params.id} alt={params.id}/>
        </div>
        <div className="image_url">
          <span>{imageURL}</span>
        </div>
        <div>
        <span><label>Brand:</label>{brand}</span>
        </div>
        <div style={{paddingBottom: 10}}>
          <span><label>Video Frame #</label><a href={'/video/' + videoId + "#frame-" + videoFrameNumber} >{videoFrameNumber}</a></span>
        </div>
        <div className="card-button">
          <button onClick={async () => { await handleLabelClick();}}>Label Image</button>
        </div>
        <div className="card-button">
          <button onClick={async () => { await handleEmbeddingClick();}}>Create Embeddings</button>
          <span><label>Hash Embeddings</label><input type="checkbox" checked={embedding}/></span>
        </div>
      </div>

      <progress id='progressbar' value={null} hidden />

      <div className="card">
        <div className="card-header">
          <h4>Similar Images in other videos</h4>
        </div>
        <div className="card-body">
          <div className="table-responsive">
            <table width="100%">
              <tbody>
                <tr>
                {
                  similarityData.map((image) => (
                    <td key={image.image_id} className="image_thumb">
                      <a id={image.image_id} name={image.frame_number} href={'/image/' + image.image_id}><img className="image_thumb" src={'http://localhost:8080/noi-server/api/image/' + image.image_id } alt={image.image_id} /></a>
                      <div><span><label>Score:</label>{image.score}</span></div>
                    </td>
                  ))
                }
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      
      <div className="card">
        <div className="card-header">
          <h3>Image Labels</h3>
          <CategorySelector hasLabels={Object.entries(imageLabelData).length > 0 } categories={catSelectorData}/>
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
                  <LabelRows hasLabels={Object.entries(imageLabelData).length > 0 } categories={filterCategories(imageLabelData)}/>
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