import React from "react";
import { useEffect, useState } from "react";
import axios from "axios";
import "./Images.css"
import { useParams } from "react-router-dom";
import { MdOutlineCompress } from "react-icons/md";
import './../config';

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
  const [promptSelectorData, setPromptSelectorData] = useState([{"prompt_name": "- all -", "prompt_id": -1}]);
  const [selectedPromptId, setSelectedPromptId] = useState(-1);

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

  function changePrompt(newValue) {
    setSelectedPromptId(newValue);
    fetchLabelData(params.id, newValue);
  }

  function lookupTheme(event){
    let s = event.target.closest('.compressButton');
    let sp = s.querySelector('span');
    if(sp !== 'undefined' && sp !== null){
      fetchWordTheme(sp.getAttribute('category'), sp.textContent);
    }
  }

  const fetchWordTheme = async (category, words) => {
    try {
      console.log("looking for word theme ...")
      let data = {"category_name": category, "words": words}
      axios.post(global.config.noi_server.root + "/category-theme", data).then((response) => {
        let data = response.data;
        if (Object.entries(data).length >= 0) {
          alert("word list consensus:" + data.themes);
        }
      });
    } catch (error) {
      console.error(error);
    }
  };


  const fetchLabelData = async (imageId, promptId) => {
    try {
      console.log("reading labels for image ...")
      console.log(imageId);
      axios.get(global.config.noi_server.root + "/categories/" + imageId + '?p=' + promptId).then((response) => {
        let data = response.data;
        if (Object.entries(data).length >= 0) {
          setImageLabelData(data.categories);
          // var filterData = data.category_names;
          // filterData.push("- all -");
          // setCatSelectorData(filterData);
          
          // var promptData = data.prompts;
          // promptData.push({"prompt_name": "- all -", "prompt_id": -1});
          // setPromptSelectorData(promptData);
          // setSelectedPromptId(-1);

          setImageAnnotationData(data.annotations);
          setEmbedding(data.has_embedding)

          if(params.category !== undefined){
            console.log(params.category);
            changeCategory(params.category);
          }
        }
      });
    } catch (error) {
      console.error(error);
    }
  };

const WordTheme = (props) => {
  if(props.meta.value_count > 1){
    // todo: call the LLM for a word theme
    return <div className='compressButton'>
      <MdOutlineCompress onClick={lookupTheme}/>&nbsp;<span category={props.meta.category_name}>{props.meta.value}</span>
    </div>
  }
  else{
    return <span>{props.meta.value}</span>
  }
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
                {/* {meta.value} */}
                {/* todo: of meta.value_count > 1, add (but hide until hover) a popup to call LLM for consolidation */}
                <WordTheme meta={meta}/>
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
  
  const PromptOptions = (props) => {
    if(Object.entries(props).length > 0 && Object.entries(props.prompts).length > 0){
      return(
        props.prompts.map( (p) => (
          <option key={p.prompt_id} value={p.prompt_id}>{p.prompt_name}</option>
        ))
      )
    }

    return(<></>)
  }

  // render a drop down for category selection
  const  PromptSelector = (props) => {
    if(props.hasPrompts === true){
      return(
          <label style={{padding: 10}}>
            Select Prompt:
            <select
              name="selectedPrompt"
              value={selectedPromptId}
              multiple={false}
              onChange={e => changePrompt(e.target.value) }
            >
                <PromptOptions prompts={props.prompts}/>
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
    axios.post(global.config.noi_server.root + '/categories/' + params.id + "?p=" + selectedPromptId).then((response) => {
      // todo: take the response.data json, and post it to be inserted into the db (post to Java code on :8080)
      console.log(response.data);
      let data = response.data; 
      setImageLabelData(data.categories);

      var filterData = data.category_names;
      filterData.push("- all -");
      setCatSelectorData(filterData);

      setImageAnnotationData(data.annotations);
      
      hideProgressbar();

      fetchSimilarityData(params.id, selectedCategoryName);
      handleEmbeddingClick();
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
    axios.post(global.config.noi_server.root + '/embeddings/' + params.id).then((response) => {
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
      axios.get(global.config.noi_server.root + "/vectors/" + imageId + "/" + category + "?sameVideo=false").then((response) => {
        let data = response.data;
        if (Object.entries(data).length >= 0) {
          setSimilarityData(data.results);
        }
      });

    } catch (error) {
      console.error(error);
    }
  };

  function assembleImageHref(imageId){
    var imageUrl = '/image/' + imageId;
    if('- all -' !== selectedCategoryName){
      imageUrl = imageUrl + "/" + selectedCategoryName;
    }
    return imageUrl;
  }

  useEffect(() => {
    const fetchData = async (imageId) => {
      try {
        console.log("calling for image meta...")
        console.log(imageId);
        axios.get(global.config.noi_server.root + "/categories/" + imageId + '?p=' + selectedPromptId).then((response) => {
          let data = response.data;
          if (Object.entries(data).length >= 0) {
            setImageURL(data.path)
            setImageLabelData(data.categories);
            var filterData = data.category_names;
            filterData.push("- all -");
            setCatSelectorData(filterData);
            
            var promptData = data.prompts;
            promptData.push({"prompt_name": "- all -", "prompt_id": -1});
            setPromptSelectorData(promptData);
            setSelectedPromptId(-1);

            setImageAnnotationData(data.annotations);
            if(data.video_id != null){
              setVideoId(data.video_id);
              setVideoFrameNumber(data.video_frame_number);
            }
            if(data.brand != null){
              setBrand(data.brand);
            }
            setEmbedding(data.has_embedding)

            if(params.category !== undefined){
              console.log(params.category);
              changeCategory(params.category);
            }
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
          <img className="image_detail" src={global.config.noi_server.root + "/api/image/" + params.id} alt={params.id}/>
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
          <PromptSelector hasPrompts={Object.entries(promptSelectorData).length > 0} prompts={promptSelectorData} />
        </div>
        <div className="card-button">
          <button onClick={async () => { await handleEmbeddingClick();}}>Create Embeddings</button>
          <span><label style={{padding: 10}}>Has Embeddings</label><input type="checkbox" checked={embedding}/></span>
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
                      <a id={image.image_id} name={image.frame_number} href={ assembleImageHref(image.image_id)}><img className="image_thumb" src={global.config.noi_server.root + '/api/image/' + image.image_id } alt={image.image_id} /></a>
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
                  <td>Description</td>
                  <td>Mid</td>
                  <td>Score</td>
                  <td>Topicality</td>
                  <td>Model</td>
                </tr>
              </thead>
              <tbody>
                {
                  imageAnnotationData.map((anno) => (
                    <tr key={anno.label_id}>
                      <td>
                        {anno.description}
                      </td>
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