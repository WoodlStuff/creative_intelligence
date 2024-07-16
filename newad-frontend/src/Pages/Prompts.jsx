import React from "react";
import { useEffect, useState, useRef } from "react";
import axios from "axios";
import { HiOutlineArrowSmRight } from "react-icons/hi";
import "./Images.css"
import { NavLink, useNavigate } from "react-router-dom";

function Prompts() {
  const [promptData, setPromptData] = useState([]);

  const navigate = useNavigate();

  const prompt_path = '/prompt/';

  useEffect(() => {
    let isCalled = false;
    // Call the async function
    const fetchData = async () => {
      // Perform async operations here
      // call http endpoint and assign the resulting data to local array
      try {
        axios.get("http://localhost:8080/noi-server/api/prompts").then((response) => {
          let data = response.data;
          if (!isCalled) {
            if (Object.entries(data).length >= 0) {
              setPromptData(data.prompts);
            }
          }
        });
      } catch (error) {
        console.error(error);
        promptData = []
      }
    };

    fetchData();
    return () => isCalled = true;
  }, []);

  function promptClickHandler(id){
    navigate(prompt_path + id);
  }

  return (
    <div className='prompts'>
      <div className="card">
        <div className="card-header">
          <h3>Prompts</h3>
          <div className="new_button"><a href="/prompt/new">New Prompt ...</a></div>
        </div>

        <div className="card-body">
          <div className="table-responsive">
            <table width="100%">
              <thead>
                <tr>
                <td className="id" width="10%">Name</td>
                <td className="status" width="10%">Status</td>
                <td className="model" width="10%">Model</td>
                <td className="prompt_type" width="10%">Type</td>
                <td className="nav_link" width="70%">Prompt</td>
                {/* <td className="status" width="10%">Status</td> */}
                </tr>
              </thead>
              <tbody>
                {
                  promptData.map((prompt) => (
                    <tr key={prompt.id}>
                      <td className="id" width="2%">
                        <NavLink
                          to={prompt_path + prompt.id }
                          key={prompt.id}
                          className="prompt-link"
                        >
                          <span className="prompt_link_text">{prompt.hasOwnProperty('name') ? prompt.name : prompt.id}</span>
                        </NavLink>
                      </td>
                      <td className="status" width="10%">{prompt.status}</td>
                      <td className="model" width="10%">{prompt.model_name}</td>
                      <td className="prompt_type" width="10%">{prompt.type_name}</td>
                      <td className="prompt" width="80%">
                        <textarea disabled className="textarea_prompt" defaultValue={prompt.prompt}/>
                      </td>
                      {/* <td className="status" width="10%">
                        <span className="status"></span>
                        {prompt.status}
                      </td> */}
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

export default Prompts;