cordova.define('cordova/plugin_list', function(require, exports, module) {
module.exports = [
    {
        "file": "plugins/cordova-plugin-whitelist/whitelist.js",
        "id": "cordova-plugin-whitelist.whitelist",
        "pluginId": "cordova-plugin-whitelist",
        "runs": true
    },
    {
        "file": "plugins/cordova-plugin-poshw/cordova/www/PosHw.js",
        "id": "cordova-plugin-poshw.PosHw",
        "pluginId": "cordova-plugin-poshw",
        "clobbers": [
            "PosHw"
        ]
    }
];
module.exports.metadata = 
// TOP OF METADATA
{
    "cordova-plugin-whitelist": "1.2.0",
    "cordova-plugin-crosswalk-webview": "1.4.0",
    "cordova-plugin-poshw": "0.99"
}
// BOTTOM OF METADATA
});