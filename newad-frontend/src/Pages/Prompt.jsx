import React from "react";
import { useEffect, useState } from "react";
import axios from "axios";
import { useParams } from "react-router-dom";

function Prompt() {
  const params = useParams();
  const [promptData, setPromptData] = useState({});

  const [name, setName] = useState();
  const [modelId, setModelId] = useState();
  const [promptType, setPromptType] = useState();
  const [status, setStatus] = useState();
  const [prompt, setPrompt] = useState();
  const [systemPrompt, setSystemPrompt] = useState();

  const handleNameChange = (event) => {
    setName(event.target.value);
  }

  
  const handleSystemPromptChange = (event) => {
    setSystemPrompt(event.target.value);
  }

  const handlePromptChange = (event) => {
    setPrompt(event.target.value);
  }

  const handleSubmit = (event) => {
    event.preventDefault();
    //alert({ name });
    // todo: format a json with: id, name, modelId, promptType, status, prompt, system_prompt
    // and post to /api/prompt/<id>
    let promptId = params.id;
    let postData={id: promptId, name: name, model_id: modelId, prompt_type: promptType, status: status, prompt: prompt, system_prompt: systemPrompt};
    axios.post("http://localhost:8080/noi-server/api/prompt/" + promptId, postData).then((response) => {
      let data = response.data;
      refreshState(data);
      // rewrite the URL (replace /new with /<id> from data.id)
      window.history.replaceState(null, data.name, "/prompt/" + data.id);
    });
  }

  // Types
  const TypeSelector = (props) => {
    return (
      <select
        name="prompt_type"
        value={promptType}
        multiple={false}
        onChange={e => changePromptType(e.target.value)}
      >
        <TypeOptions types={props.types} p_type={promptType}/>
      </select>
    )
  }

  function changePromptType(newValue) {
    setPromptType(newValue);
  }

  const TypeOptions = (props) => {
    if (props.hasOwnProperty('types') && props.types !== undefined) {
      return (
        props.types.map((p) => (
          <option key={p.code} value={p.code} selected={p.code === props.p_type ? true : false}>{p.name}</option>
          // <ModelOption id={m.id} name={m.name} selected={m.id === props.modelId}/>
        ))
      )
    }
    else{
      return <></>
    }
  }

  // Statuses
  const StatusSelector = (props) => {
    return (
      <select
        name="status"
        value={status}
        multiple={false}
        onChange={e => changeStatus(e.target.value)}
      >
        <StatusOptions statuses={props.statuses} status={status}/>
      </select>
    )
  }

  function changeStatus(newValue) {
    setStatus(newValue);
  }

  const StatusOptions = (props) => {
    if (props.hasOwnProperty('statuses') && props.statuses !== undefined) {
      return (
        props.statuses.map((s) => (
          <option key={s.code} value={s.code} selected={s.code === props.status ? true : false}>{s.name}</option>
        ))
      )
    }
    else{
      return <></>
    }
  }


  // Models
  const ModelSelector = (props) => {
    return (
      <select
        name="model_id"
        value={modelId}
        multiple={false}
        onChange={e => changeModelId(e.target.value)}
      >
        <ModelOptions models={props.models} modelId={parseInt(modelId)}/>
      </select>
    )
  }

  const ModelOption = (props) => {
    if(props.selected){
      return(
        <option key={props.id} value={props.id} selected>{props.name}</option>
      )
    }
    else{
      return(
        <option key={props.id} value={props.id}>{props.name}</option>
      )
    }
  }

  const ModelOptions = (props) => {
    if (props.hasOwnProperty('models') && props.models !== undefined) {
      return (
        props.models.map((m) => (
          // <option key={m.id} value={m.name} x={m.id} y={props.modelId} {m.id == props.modelId ? selected : ''}>{m.name}</option>
          <ModelOption id={m.id} name={m.name} selected={m.id === props.modelId}/>
        ))
      )
    }
    else{
      return <></>
    }
  }

  function changeModelId(newValue) {
    setModelId(newValue);
  }

  function refreshState(data) {
    if (Object.entries(data).length >= 0) {
      setPromptData(data);
      setModelId(data.model_id);
      setPromptType(data.type);
      setStatus(data.status_code)
      setPrompt(data.prompt);
      setSystemPrompt(data.system_prompt);
    }else{
      setPromptData({});
      setModelId();
      setPromptType();
      setStatus();
      setPrompt();
      setSystemPrompt();
    }
  }

  // todo: see text area hacks: 
  // https://stackoverflow.com/questions/73486986/how-to-dinamically-resize-a-textarea-in-react

  useEffect(() => {
    // Call the async function
    const fetchData = async (promptId) => {
      // Perform async operations here
      // call http endpoint and assign the resulting data to local array
      try {
        console.log("calling for prompt meta...")
        console.log(promptId);
        axios.get("http://localhost:8080/noi-server/api/prompt/" + promptId).then((response) => {
          let data = response.data;
          refreshState(data);
        });
      } catch (error) {
        console.error(error);
      }
    };

    const fetchLookupData = async () => {
      try {
        axios.get("http://localhost:8080/noi-server/api/prompt-lookup").then((response) => {
          let data = response.data;
          console.log(data);
          console.log(data.id);
          refreshState(data);
        });
      } catch (error) {
        console.error(error);
      }
    };

    console.log({ params });
    if(params.id === 'new'){
      fetchLookupData();
    }
    else{
      fetchData(params.id);
    }
  }, []);

  return (
    <div className='prompt'>
      <form onSubmit={handleSubmit}>
        <div className="card">
          <div className="card-body">
            
            <table width="100%">
              <thead>
                <tr>
                  <td className="label" width="10%">Label</td>
                  <td className="value" width="90%">Value</td>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td className="label" width="10%">Name</td>
                  <td className="value" width="90%"><input type="text" name="name" defaultValue={promptData.name} onChange={handleNameChange} /></td>
                </tr>
                <tr>
                  <td className="label" width="10%">Model</td>
                  <td className="value" width="90%"><ModelSelector models={promptData.models} /></td>
                </tr>
                <tr>
                  <td className="label" width="10%">Type</td>
                  <td className="value" width="90%"><TypeSelector types={promptData.types}/></td>
                </tr>
                <tr>
                  <td className="label" width="10%">Prompt</td>
                  <td className="value" width="90%"><textarea name="prompt" defaultValue={promptData.prompt} onChange={handlePromptChange} style={{
              width: "95%",
              padding: "15px",
              borderRadius: "5px",
              outline: "none",
              resize: "none"
            }}></textarea></td>
                </tr>
                <tr>
                  <td className="label" width="10%">System Prompt</td>
                  <td className="value" width="90%"><textarea name="system_prompt" defaultValue={promptData.system_prompt}  onChange={handleSystemPromptChange} style={{
              width: "95%",
              padding: "15px",
              borderRadius: "5px",
              outline: "none",
              resize: "none"
            }}></textarea></td>
                </tr>
                <tr>
                  <td className="label" width="10%">Status</td>
                  {/* <td className="value" width="90%">{promptData.status}</td> */}
                  <td className="value" width="90%"><StatusSelector statuses={promptData.statuses}/></td>
                </tr>
                <tr>
                  <td className="label" width="10%"></td>
                  <td className="value" width="90%"><input type="submit" value="Update"></input></td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </form>
    </div>
  );
}

export default Prompt;