import React, { useState } from "react";
import {
  FaTh
} from "react-icons/fa";

import { AiFillVideoCamera, AiOutlinePicture } from "react-icons/ai";

import { NavLink } from "react-router-dom";

export default function Sidebar({ children }) {
  const [isOpen, setIsOpen] = useState(true);
  const toggle = () => setIsOpen(!isOpen);

  const menuItem = [
    {
      path: "/",
      name: "Dashboard",
      icon: <FaTh />,
    },
    {
      path: "/images",
      name: "Images",
      icon: <AiOutlinePicture />,
    },
    {
      path: "/videos",
      name: "Videos",
      icon: <AiFillVideoCamera />,
    },
  ];

  return (
    <>
      <div style={{ width: isOpen ? "200px" : "60px" }} className="grid-sidebar">
        <div className="top-section">
        </div>
        {menuItem.map((item, index) => (
          <NavLink
            to={item.path}
            key={index}
            className="link"
            activeclassname="active"
          >
            <div className="icon">{item.icon}</div>
            <div  style={{ display: isOpen ? "block" : "none" }} className="link_text">{item.name}</div>
          </NavLink>
        ))}
      </div>
      <main className="grid-main">{children}</main>
      </>
  );
}