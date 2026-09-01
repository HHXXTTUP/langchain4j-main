const form = document.querySelector("#generate-form");
const promptInput = document.querySelector("#prompt");
const characterCount = document.querySelector("#character-count");
const generateButton = document.querySelector("#generate-button");
const buttonLabel = generateButton.querySelector(".button-label");
const statusText = document.querySelector("#status");
const errorText = document.querySelector("#error");
const emptyState = document.querySelector("#empty-state");
const result = document.querySelector("#result");
const generatedImage = document.querySelector("#generated-image");
const revisedPrompt = document.querySelector("#revised-prompt");
const downloadLink = document.querySelector("#download-link");

promptInput.addEventListener("input", updateCharacterCount);

document.querySelectorAll(".example-chip").forEach((button) => {
    button.addEventListener("click", () => {
        promptInput.value = button.dataset.prompt;
        updateCharacterCount();
        promptInput.focus();
    });
});

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const prompt = promptInput.value.trim();
    if (!prompt) {
        showError("请先输入图片提示词。");
        promptInput.focus();
        return;
    }

    setLoading(true);
    hideError();

    try {
        const response = await fetch("/api/images", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({prompt})
        });
        const payload = await readJson(response);

        if (!response.ok) {
            throw new Error(payload.message || `请求失败（HTTP ${response.status}）`);
        }
        if (!payload.imageSrc) {
            throw new Error("接口没有返回可展示的图片。");
        }

        await displayImage(payload.imageSrc, prompt);
        revisedPrompt.textContent = payload.revisedPrompt
            ? `模型优化后的提示词：${payload.revisedPrompt}`
            : "图片已根据原始提示词生成。";
        downloadLink.href = payload.imageSrc;
        statusText.textContent = "生成成功，可以继续修改提示词生成新版本。";
    } catch (error) {
        showError(error.message || "图片生成失败，请稍后重试。");
        statusText.textContent = "生成未完成。";
    } finally {
        setLoading(false);
    }
});

function updateCharacterCount() {
    characterCount.textContent = promptInput.value.length;
}

function setLoading(loading) {
    generateButton.disabled = loading;
    generateButton.classList.toggle("is-loading", loading);
    buttonLabel.textContent = loading ? "正在生成" : "生成图片";
    if (loading) {
        statusText.textContent = "模型正在创作，请耐心等待，不要关闭页面……";
    }
}

function showError(message) {
    errorText.textContent = message;
    errorText.hidden = false;
}

function hideError() {
    errorText.hidden = true;
    errorText.textContent = "";
}

function readJson(response) {
    return response.json().catch(() => ({}));
}

function displayImage(source, prompt) {
    return new Promise((resolve, reject) => {
        generatedImage.onload = () => {
            emptyState.hidden = true;
            result.hidden = false;
            resolve();
        };
        generatedImage.onerror = () => reject(new Error("图片已生成，但浏览器加载图片失败。"));
        generatedImage.alt = `根据提示词“${prompt}”生成的图片`;
        generatedImage.src = source;
    });
}
