// 朗读教程内容
function readContent() {
    let text = document.querySelector('.detail-content').innerText;
    readText(text, 0.8);
}

// 打开AI询问弹窗
function openAskModal() {
    openModal('askModal');
    // 默认填充当前教程标题
    document.getElementById('askInput').value = document.querySelector('.detail-title').innerText;
    document.getElementById('askInput').focus();
}

// 关闭AI询问弹窗
function closeAskModal() {
    closeModal('askModal');
}

// 提交AI询问
function submitAsk() {
    let text = document.getElementById('askInput').value.trim();
    if (!text) {
        alert('⚠️ 请输入你想提问的内容');
        return;
    }

    // 关闭弹窗
    closeAskModal();

    // 显示AI回答区域 + 加载动画
    let aiAnswer = document.getElementById('aiAnswer');
    let answerText = document.getElementById('answerText');
    aiAnswer.style.display = 'block';
    answerText.innerHTML = '<div class="loading">AI正在思考中...</div>';

    // 调用AI接口
    callAIApi(text, (answer) => {
        answerText.innerText = answer;
    }, (answer) => {
        answerText.innerText = answer;
    });
}