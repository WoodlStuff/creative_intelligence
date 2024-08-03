import React from "react";

import { Outlet, Route } from "react-router-dom";
import { BrowserRouter, Routes } from "react-router-dom";

import Dashboard from "./Pages/Dashboard.jsx";
import Images from "./Pages/Images.jsx";
import Image from "./Pages/Image.jsx";
import Videos from "./Pages/Videos.jsx";
import Video from "./Pages/Video.jsx";
import Prompts from "./Pages/Prompts.jsx";
import Prompt from "./Pages/Prompt.jsx";
import Story from "./Pages/Story.jsx";
import VideoUpload from "./Pages/VideoUpload.jsx"
import ImageUpload from "./Pages/ImageUpload.jsx"

import Sidebar from "./Components/Sidebar.jsx";
import Header from "./Components/Header.jsx";

import "./App.css"

export default function App() {
  return (
    <div className='grid-container'>
      <Header />
      <BrowserRouter>
        <Sidebar>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/images" element={<Images />} />
            <Route path="/image/:id/:category?" element={<Image />}
              action={({ params }) => { }} />
            <Route path="/videos" element={<Videos />} />
            <Route path="/video/:id" element={<Video />}
              action={({ params }) => { }} />
            <Route path="/prompts" element={<Prompts />} />
            <Route path="/prompt/:id" element={<Prompt />}
              action={({ params }) => { }} />
            <Route path="/story/:id" element={<Story />} 
              action={({ params }) => { }} />
            <Route path="/upload-video" element={<VideoUpload />} />
            <Route path="/upload-image" element={<ImageUpload />} />
            </Routes>
        </Sidebar>
      </BrowserRouter>
      <Outlet />
    </div>
  );
}
