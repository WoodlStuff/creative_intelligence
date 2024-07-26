import React from "react";
import { useState } from "react";
import axios from "axios";
import BrandAutocomplete from "../Components/BrandAutocomplete";
import Redirect from "react-router-dom"

function ImageUpload() {
  const [brand, setBrand] = useState();
  const [name, setName] = useState();
  const [file, setFile] = useState();

  const handleSubmit = (event) => {
    event.preventDefault();
    const url = 'http://localhost:8080/noi-server/uploadImageFile';
    const formData = new FormData();
    formData.append('fileName', name);
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
        window.location.href='/images';
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

  function handleNameChange(event) {
    setName(event.target.name);
  }
  
  function handleFileChange(event) {
    if(event.target.files.length === 0){
      return;
    }
    setName(event.target.files[0].name);
    setFile(event.target.files[0]);
  }
  
  return (
    <div className='image'>
      <h3>Upload New Image</h3>
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

export default ImageUpload;