// define global variables
module.exports = global.config = {
    noi_server: {
        // root: "http://localhost:8080/noi-server"
        root: process.env.REACT_APP_NOI_SERVER
    }
};
