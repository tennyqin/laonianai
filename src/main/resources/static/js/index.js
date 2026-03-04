let recognition = null;
const maxLen = 32;
const voiceBtn = document.getElementById('voiceBtn');
const voiceTip = document.getElementById('voiceTip');
const inputBox = document.getElementById('questionInput');
const answerText = document.getElementById('answerText');
const aiAnswer = document.getElementById('aiAnswer');

// 开始语音识别
function startVoice(e) {
    e.preventDefault();
    voiceTip.innerText = "";
    if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
        voiceTip.innerText = "⚠️ 您的浏览器不支持语音识别（建议用Chrome/Edge）";
        return;
    }
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    recognition = new SpeechRecognition();
    recognition.lang = 'zh-CN';
    recognition.interimResults = false;
    recognition.continuous = false;
    recognition.maxAlternatives = 1;

    recognition.onresult = (e) => {
        let text = e.results[0][0].transcript.trim();
        if (text) {
            if (text.length > maxLen) text = text.substring(0, maxLen);
            inputBox.value = text;
            voiceTip.innerText = "✅ 识别成功：" + text;
        } else {
            voiceTip.innerText = "⚠️ 未识别到语音，请重新尝试";
        }
    };

    recognition.onerror = (e) => {
        voiceTip.innerText = "⚠️ 语音识别失败：" + e.error;
        stopVoice(e);
    };

    recognition.onend = () => {
        if (voiceBtn.classList.contains('recording')) {
            voiceTip.innerText = "⚠️ 未说话或识别超时";
            stopVoice(e);
        }
    };

    try {
        recognition.start();
        voiceBtn.innerText = "🔊 正在听...松开结束";
        voiceBtn.classList.add('recording');
        voiceTip.innerText = "🎤 请说话（支持中文）...";
    } catch (err) {
        voiceTip.innerText = "⚠️ 启动失败：" + err.message;
    }
}

// 结束语音识别
function stopVoice(e) {
    e.preventDefault();
    if (recognition) {
        recognition.stop();
        recognition = null;
    }
    voiceBtn.innerText = "🔊 按住说话提问";
    voiceBtn.classList.remove('recording');
}

// 热门问题填充
function setQuestion(text) {
    if (text.length > maxLen) text = text.substring(0, maxLen);
    inputBox.value = text;
    voiceTip.innerText = "";
}

// 发送提问
function doAsk() {
    let text = inputBox.value.trim();
    if (!text) {
        voiceTip.innerText = "⚠️ 请输入或语音提问内容";
        return;
    }
    voiceTip.innerText = "";

    // 显示AI回答区域 + 加载动画
    aiAnswer.style.display = 'block';
    answerText.innerHTML = '<div class="loading">AI正在思考中...</div>';

    // 调用AI接口
    callAIApi(text, (answer) => {
        answerText.innerText = answer;
    }, (answer) => {
        answerText.innerText = answer;
    });
}

// 朗读AI回答
function readAnswer() {
    let text = document.getElementById('answerText').innerText.trim();
    if (!text || text.includes("正在思考")) {
        alert("暂无回答可朗读");
        return;
    }
    readText(text, 0.9);
}

// 页面卸载清理
window.onbeforeunload = () => {
    if (recognition) recognition.stop();
};