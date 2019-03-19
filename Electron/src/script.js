var remote = require('electron').remote;
console.log('Initializing app');
document.onreadystatechange = function () {
    if (document.readyState === 'complete') {
        document
            .getElementById('minimize-btn')
            .addEventListener('click', function (e) {
            var window = remote.getCurrentWindow();
            window.minimize();
        });
        document
            .getElementById('close-btn')
            .addEventListener('click', function (e) {
            var window = remote.getCurrentWindow();
            window.close();
        });
        var holder = document.getElementById('button-image');
        holder.ondragleave = function () {
            console.log("NOT DRAGGING");
            return false;
        };
        holder.ondragend = function () {
            console.log("NOT DRAGGING");
            return false;
        };
        holder.ondragover = function () {
            console.log("DRAGGING");
            return false;
        };
        holder.ondrop = function (e) {
            e.preventDefault();
            console.log(e);
            for (var _i = 0, _a = e.dataTransfer.files; _i < _a.length; _i++) {
                var f = _a[_i];
                console.log('File(s) you dragged here: ', f.path);
            }
            return false;
        };
        var ipcRenderer_1 = require('electron').ipcRenderer;
        var actions = document.getElementsByClassName("action");
        actions.forEach(function (elem) {
            //elem.addEventListener('dragstart', function(e){
            //    e.dataTransfer.setData('path', 'foo');
            //});
            elem.ondragstart = function (event) {
                event.preventDefault();
                ipcRenderer_1.send('ondragstart', "/stuff/asd");
            };
        });
    }
};
function setKeyAction(deck_id, key_index, action_id) {
}
function getActionList() {
    list = [{ "action_id": "asdsa", "action_icon": "asd", "name": "open" }];
    return list;
}
function sendKeyEvent(deck_id, key_index, is_pressed_or_released) {
}
//Server side event
function receiveKeyImage(deck_id, key_index, base64string) {
}
