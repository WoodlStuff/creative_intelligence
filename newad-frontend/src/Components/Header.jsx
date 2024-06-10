import React from "react";
function Header() {
    
    return (
        <div className='grid-header'>
            <div className="top-section" style={{ backgroundImage: "url(/insight-sciences-logo_sm2.png)" }} >
                <div className="header-brand">
                    Insight Sciences: 
                </div>
                <div className="header-pitch">CI (<u>C</u>reative <u>I</u>ntelligence)</div>
            </div>
        </div>
    );
}

export default Header;