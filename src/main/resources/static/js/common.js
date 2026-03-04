// 通用朗读功能
function readText(text, rate = 0.9) {
    if (!text) return;
    window.speechSynthesis.cancel();
    let utterance = new SpeechSynthesisUtterance();
    utterance.text = text;
    utterance.lang = 'zh-CN';
    utterance.rate = rate;
    utterance.volume = 1;
    window.speechSynthesis.speak(utterance);
}

// 通用AI提问接口调用
function callAIApi(question, successCallback, errorCallback) {
    fetch('/tutorial/ai/ask', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: `question=${encodeURIComponent(question)}`
    })
    .then(res => {
        if (!res.ok) throw new Error("接口响应失败");
        return res.json();
    })
    .then(data => {
        let answer = "";
        if (data && data.code === 200 && data.data && data.data.trim()) {
            answer = data.data.trim();
        } else {
            answer = `您好！关于「${question}」的解答：
暂时没有找到对应的详细答案，您可以尝试：
1. 换个简单的说法重新提问；
2. 查看相关教程内容；
3. 返回分类列表，找相关问题。`;
        }
        successCallback && successCallback(answer);
    })
    .catch(err => {
        console.log("接口调用失败：", err);
        let answer = `您好！关于「${question}」的解答：
暂时没有找到对应的详细答案，您可以尝试：
1. 换个简单的说法重新提问；
2. 查看相关教程内容；
3. 返回分类列表，找相关问题。`;
        errorCallback && errorCallback(answer, err);
    });
}

// 弹窗通用操作
function openModal(modalId) {
    document.getElementById(modalId).style.display = 'flex';
}

function closeModal(modalId) {
    document.getElementById(modalId).style.display = 'none';
    // 清空弹窗输入框
    const input = document.querySelector(`#${modalId} input`);
    if (input) input.value = '';
}