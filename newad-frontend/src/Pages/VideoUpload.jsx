import React from "react";
import { useState } from "react";
import axios from "axios";
import BrandAutocomplete from "./../Components/BrandAutocomplete";

function VideoUpload() {
  const [brand, setBrand] = useState();
  const [name, setName] = useState();
  const [file, setFile] = useState();

  const handleSubmit = (event) => {
    event.preventDefault();
    const url = 'http://localhost:8080/noi-server/uploadVideoFile';
    const formData = new FormData();
    formData.append('name', name);
    formData.append('brand', brand);
    formData.append('file', file);
    console.log('posting with ' + formData);
    const config = {
      headers: {
        'content-type': 'multipart/form-data',
      },
    };
    axios.post(url, formData, config).then((response) => {
      var data = response.data;
      if(data.hasOwnProperty('error')){
        alert(data.error);
      }else{
        // redirect to /videos 
        window.location.href='/videos';
      }
    }).catch(error => {
      console.log("error -> ", error);
      alert(error);
      // if(error.response.status === 406){
      //     setError(error.response.data.message);
      // };
    });
  }

  // Define the callback function to update the state in the parent component
  const updateBrand = (newValue) => { 
    setBrand(newValue); 
  };

  function handleFileChange(event) {
    if(event.target.files.length === 0){
      return;
    }
    setFile(event.target.files[0]);
    //setFileName(event.target.files[0].name);
    let split = event.target.files[0].name.split('.');
    split.pop();
    let name = split.join("."); 
    setName(name);
  }
  
  return (
    <div className='video'>
      <h3>Upload New Video</h3>
      <form onSubmit={handleSubmit}>
        <div className="card">
          <div className="card-body">
            <table width="100%">
              <tbody>
                <tr>
                  <td className="label" width="10%">Brand</td>
                  <td className="value" width="90%"><BrandAutocomplete brandNameCallback={updateBrand}/></td>
                </tr>
                <tr>
                  <td className="label" width="10%">Name</td>
                  <td className="value" width="90%"><input type="text" value={name} onChange={e => setName(e.target.value)} /></td>
                </tr>
                <tr>
                  <td className="label" width="10%">File</td>
                  <td className="value" width="90%"><input type="file" onChange={handleFileChange}/></td>
                </tr>
                <tr>
                  <td></td>
                  <td className="button" width="90%"><button type="submit">Upload</button></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </form>
    </div>
  );
}

export default VideoUpload;