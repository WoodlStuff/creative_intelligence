import React, { useEffect, useState } from 'react';
import axios from "axios";

// see https://www.dhiwise.com/post/how-to-implement-react-autocomplete-input-in-your-next-project
function BrandAutocomplete(props) {
  const [brandName, setBrandName] = useState('');
  const [brandNames, setBrandNames] = useState([]);
  const [brandsData, setBrandsData] = useState([]);

  const handleBrandChange = (event) => {
    const value = event.target.value;
    if(value === undefined || value === null){
      value = '';
    }
    setBrandName(value);
    props.brandNameCallback(value);
  
    const filteredSuggestions = brandsData.brand_names.filter(suggestion =>
      suggestion.toLowerCase().includes(value.toLowerCase())
    );
    setBrandNames(filteredSuggestions);
  };

  const handleBrandClick = (value) => {
    setBrandName(value);
    setBrandNames([]);
    props.brandNameCallback(value);
  }

  useEffect(() => {
    const fetchLookupData = async () => {
      try {
        axios.get("http://localhost:8080/noi-server/api/brands-lookup").then((response) => {
          let data = response.data;
          console.log(data);
          setBrandsData(data);
          //setBrandNames(data.brand_names);
        });
      } catch (error) {
        console.error(error);
      }
    };
    fetchLookupData();
  }, []);

  return (
    <div className="autocomplete-wrapper">
      <input
        name="brand_name"
        type="text"
        value={brandName}
        // onChange={handleBrandChange}
        // e.target.value.attributes.getNamedItem("brand_name").
        onChange={(e) => handleBrandChange(e)}
      />
      {brandNames.length > 0 && (
        <ul className="suggestions-list">
          {brandNames.map((suggestion, index) => (
            <li
              key={index}
              onClick={() => handleBrandClick(suggestion)}
              // Additional props
            >
              {suggestion}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default BrandAutocomplete;