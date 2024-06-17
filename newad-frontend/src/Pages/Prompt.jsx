import React from "react";
import { useEffect, useState } from "react";
import axios from "axios";
import { HiOutlineArrowSmRight } from "react-icons/hi";
import { useParams } from "react-router-dom";

function Prompt() {
  const params = useParams();
  const [promptData, setPromptData] = useState({}) 

  useEffect(() => {
    let isCalled = false;
    // Call the async function
    const fetchData = async (promptId) => {
      // Perform async operations here
      // call http endpoint and assign the resulting data to local array
      try {
        console.log("calling for prompt meta...")
        console.log(promptId);
        axios.get("http://localhost:8080/noi-server/api/prompt/" + promptId).then((response) => {
          let data = response.data;
          if (!isCalled) {
            if (Object.entries(data).length >= 0) {
              setPromptData(data)
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
    <div className='prompt'>
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
                      <td className="label" width="10%">Type</td>
                      <td className="value" width="90%">{promptData.type}: {promptData.type_name}</td>
                    </tr>
                    <tr>
                      <td className="label" width="10%">Prompt</td>
                      <td className="value" width="90%">{promptData.prompt}</td>
                    </tr>
                    <tr>
                      <td className="label" width="10%">System Prompt</td>
                      <td className="value" width="90%">{promptData.system_prompt}</td>
                    </tr>
                    <tr>
                      <td className="label" width="10%">Status</td>
                      <td className="value" width="90%">{promptData.status}</td>
                    </tr>
                </tbody>
          </table>
        </div>
        <div className="card-button">
          <button>Update</button>
        </div>

      </div>
    </div>
  );
}

export default Prompt;