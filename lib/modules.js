

function exported_require (name) {
    switch (name) {
    case "react":            return require("react");
    case "react-dom":        return require("react-dom");
    case "react-dom/server": return require("react-dom/server");
    default:
        console.error("Unknown module: ", name);
    }
}

if (!global.require) {
    global.require = exported_require;
}
