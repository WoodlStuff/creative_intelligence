import React from "react";
import { useEffect, useState } from "react";
import axios from "axios";
import { HiOutlineArrowSmRight } from "react-icons/hi";
import { useParams } from "react-router-dom";

function Story() {
  const params = useParams();
  const [storyData, setStoryData] = useState({}) 
  const [selectedCategoryName, setSelectedCategoryName] = useState('situation')
  const [selectedKeyName, setSelectedKeyName] = useState('*')

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
  }

  // render a drop down for category selection
  const  CategorySelector = (props) => {
    if(props.hasStory == true){
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

  function filterCategories(ai_image_id) {
    // return [{name, key, values}, ...]
    // find the category data for this image
    let categories = storyData.categories.filter( (img_categories) => img_categories.image_id == ai_image_id );
    if(Object.entries(categories).length > 0){
      return categories[0].story_elements.filter( (elements) => elements.category_name == selectedCategoryName);
    }
    
    return[]
  }

  const StoryMomentRow = (props) => {
    return(
        <tr>
          <td className="image_thumb"><a id={props.moment.image_id} name={props.moment.video_frame_number}></a><img className="image_thumb" src={'http://localhost:8080/noi-server/api/image/' + props.moment.image_id } /></td>
          <td>{props.moment.image_id}</td>
          <td>{selectedCategoryName}</td>
          <td>{filterCategories(props.moment.image_id).length}</td>
        </tr>
    )
  }

  // render all rows for one moment / image, but all its category data (after the filter is applied)  
  const StoryCategoryRows = (props) => {
      return(
        props.categories.map((category) => (
          <tr key={category.key}>
            <td className="image_thumb"><a id={props.moment.image_id} name={props.moment.video_frame_number}></a><img className="image_thumb" src={'http://localhost:8080/noi-server/api/image/' + props.moment.image_id } /></td>
            <td>
              {props.moment.video_frame_number}
            </td>
            <td>
              {props.moment.seconds}
            </td>
            <td>
              {category.category_name}
            </td>
            <td>
              {category.key}
            </td>
            <td>
              {category.values}
            </td>
          </tr>
        ))
      )
  }

  const StoryRows = (props) => {
    if(props.hasStory == true){
        return (
            props.storyData.moments.map((moment) => (
              <StoryCategoryRows key={moment.video_frame_number} moment={moment} categories={filterCategories(moment.image_id)} />
              // <StoryMomentRow moment={moment} />
            ))
        )
    }
    return <></>
  }

  useEffect(() => {
    // let isCalled = false;
    // Call the async function
    const fetchData = async (videoId) => {
      // Perform async operations here
      // call http endpoint and assign the resulting data to local array
      try {
        // if (!isCalled) {
          console.log("calling for video story data...")
          console.log(videoId);
          axios.get("http://localhost:8080/noi-server/api/video-story/" + videoId).then((response) => {
            let data = response.data;
            if (Object.entries(data).length >= 0) {
              setStoryData(data.story);
              // isCalled = true;
            }
        });
      // }
    } catch (error) {
        console.error(error);
      }
    };

    console.log({ params });
    fetchData(params.id);
    // return () => isCalled = true;
  }, []);

  return (
    <div className='prompt'>
      <div className="card">
        <div className="card-header">
          <h3>Video Story</h3>
        </div>
        <div className="card-body">
          <div className="table-responsive">
            <table width="90%">
              <thead>
                <tr>
                  <td></td>
                  <td>Frame</td>
                  <td>Seconds</td>
                  <td><CategorySelector hasStory={Object.entries(storyData).length > 0 } categories={storyData.category_names}/></td>
                  <td>Key</td>
                  <td>Values</td>
                </tr>
              </thead>
              <tbody>
                {/* <tr>
                  <td></td>
                  <td></td>
                  <td></td>
                  <td>
                    <CategorySelector hasStory={Object.entries(storyData).length > 0 } categories={storyData.category_names}/>
                  </td>
                  <td>
                    {/* <KeySelector hasStory={Object.entries(storyData).length > 0 }/>  */}
                    
                  {/* </td> */}
                  {/* <td></td> */}
                  {/* </tr> */} 
                  <StoryRows hasStory={Object.entries(storyData).length > 0 } storyData={storyData}/>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Story;

/* TODO
[see https://stackoverflow.com/questions/67959871/how-to-filter-a-table-using-multiple-user-inputs-on-a-react-js-app]

const filterCriteria = (obj) => {
  return obj.firstName.includes(firstName)
  && obj.lastName.includes(lastName)
  && obj.city.includes(city);
}

return(
  <InfoTable items={dummyData.filter(filterCriteria)} />
);

function InfoTable = (props) => {
  return(
    <Table virtualized height={500} data={props.items} rowHeight={30}>
       <Column width={60}>
         <HeaderCell>First Name</HeaderCell>
         <Cell dataKey="firstName"/>
       </Column>
       <Column width={60}>
         <HeaderCell>Last Name</HeaderCell>
         <Cell dataKey="lastName"/>
       </Column>
       <Column width={60}>
         <HeaderCell>City</HeaderCell>
         <Cell dataKey="city"/>
       </Column>
    </Table>
  );
}
export default InfoTable;
*/