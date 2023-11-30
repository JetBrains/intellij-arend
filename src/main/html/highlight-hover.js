// Copyright 2023, Andreas Abel.
// Falls under the Agda license at https://github.com/agda/agda/blob/master/LICENSE

const dict = new Map();

window.onload = function () {
    const objs  = document.querySelectorAll('a[href]');

    for (const obj of objs) {
        const key = obj.href;
        const set = dict.get(key) ?? new Set();
        set.add(obj);
        dict.set(key, set);
    }

    for (const obj of objs) {
        obj.onmouseover = function () {
            for (const o of dict.get(this.href)) { o.classList.add('hover-highlight'); }
        }
        obj.onmouseout = function () {
            for (const o of dict.get(this.href)) { o.classList.remove('hover-highlight'); }
        }
    }
};