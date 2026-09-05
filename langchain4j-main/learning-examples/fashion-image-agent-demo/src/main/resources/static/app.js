const form = document.querySelector("#generation-form");
const promptInput = document.querySelector("#prompt");
const characterCount = document.querySelector("#character-count");
const submitButton = document.querySelector("#submit-button");
const portraitGenerationModeInputs = [...document.querySelectorAll('input[name="portrait-generation-mode"]')];
const formError = document.querySelector("#form-error");
const readinessMessage = document.querySelector("#readiness-message");
const systemState = document.querySelector("#system-state");
const jobStatus = document.querySelector("#job-status");
const progressMessage = document.querySelector("#progress-message");
const agentItems = [...document.querySelectorAll(".agent-list li")];
const resultSection = document.querySelector("#result-section");
const emptyResult = document.querySelector("#empty-result");
const resultGallery = document.querySelector("#result-gallery");
const resultTemplate = document.querySelector("#result-card-template");
const resultBadge = document.querySelector("#result-badge");
const debugPanel = document.querySelector("#debug-panel");
const debugLog = document.querySelector("#debug-log");
const copyDebugLog = document.querySelector("#copy-debug-log");
const runtimeLog = document.querySelector("#runtime-log");
const refreshRuntimeLog = document.querySelector("#refresh-runtime-log");
const copyRuntimeLog = document.querySelector("#copy-runtime-log");
const stepLog = document.querySelector("#step-log");
const stepLogJob = document.querySelector("#step-log-job");
const refreshStepLog = document.querySelector("#refresh-step-log");
const copyStepLog = document.querySelector("#copy-step-log");
const startCurrentJob = document.querySelector("#start-current-job");
const stopCurrentJob = document.querySelector("#stop-current-job");
const historyBody = document.querySelector("#history-body");
const videoHistoryBody = document.querySelector("#video-history-body");
const refreshHistory = document.querySelector("#refresh-history");
const refreshClothingCatalog = document.querySelector("#refresh-clothing-catalog");
const catalogStatus = document.querySelector("#catalog-status");
const catalogProgress = document.querySelector("#catalog-progress");
const clothingProfileGrid = document.querySelector("#clothing-profile-grid");
const experienceList = document.querySelector("#experience-list");
const experienceCount = document.querySelector("#experience-count");
const knowledgeTabs = [...document.querySelectorAll("[data-knowledge-tab]")];
const knowledgePanels = [...document.querySelectorAll("[data-knowledge-panel]")];
const refreshVideoCatalog = document.querySelector("#refresh-video-catalog");
const videoCatalogStatus = document.querySelector("#video-catalog-status");
const openVideoImport = document.querySelector("#open-video-import");
const videoImportForm = document.querySelector("#video-import-form");
const videoImportFolder = document.querySelector("#video-import-folder");
const videoImportContent = document.querySelector("#video-import-content");
const submitVideoImport = document.querySelector("#submit-video-import");
const videoImportStatus = document.querySelector("#video-import-status");
const videoImportItems = document.querySelector("#video-import-items");
const videoFolderTabs = document.querySelector("#video-folder-tabs");
const videoCatalogGrid = document.querySelector("#video-catalog-grid");
const historyMessage = document.querySelector("#history-message");
const videoHistoryMessage = document.querySelector("#video-history-message");
const taskTabs = [...document.querySelectorAll("[data-task-tab]")];
const taskPanels = [...document.querySelectorAll("[data-task-panel]")];
const navItems = [...document.querySelectorAll(".nav-item")];
const viewPanels = [...document.querySelectorAll("[data-view-panel]")];
const logAlertDot = document.querySelector("#log-alert-dot");
const closeTaskDetail = document.querySelector("#close-task-detail");
const taskDetailPrompt = document.querySelector("#task-detail-prompt");
const taskDetailId = document.querySelector("#task-detail-id");
const taskDetailStage = document.querySelector("#task-detail-stage");
const taskDetailTime = document.querySelector("#task-detail-time");
const batchGenerateVideo = document.querySelector("#batch-generate-video");
const selectedVideoCount = document.querySelector("#selected-video-count");
const selectAllVideoSources = document.querySelector("#select-all-video-sources");
const imagePreviewModal = document.querySelector("#image-preview-modal");
const imagePreview = document.querySelector("#image-preview");
const imagePreviewCaption = document.querySelector("#image-preview-caption");
const closeImagePreview = document.querySelector("#close-image-preview");
const comfyUiVideoForm = document.querySelector("#comfyui-video-form");
const comfyUiVideoPrompt = document.querySelector("#comfyui-video-prompt");
const comfyUiVideoDuration = document.querySelector("#comfyui-video-duration");
const comfyUiVideoResolution = document.querySelector("#comfyui-video-resolution");
const comfyUiVideoImages = document.querySelector("#comfyui-video-images");
const comfyUiVideoImagePreview = document.querySelector("#comfyui-video-image-preview");
const comfyUiVideoSubmit = document.querySelector("#comfyui-video-submit");
const comfyUiVideoFormMessage = document.querySelector("#comfyui-video-form-message");
const refreshComfyUiVideoHistory = document.querySelector("#refresh-comfyui-video-history");
const comfyUiVideoHistoryMessage = document.querySelector("#comfyui-video-history-message");
const comfyUiVideoHistory = document.querySelector("#comfyui-video-history");
const canvasVideoHistory = document.querySelector("#canvas-video-history");
const canvasVideoHistoryMessage = document.querySelector("#canvas-video-history-message");
const refreshCanvasVideoHistory = document.querySelector("#refresh-canvas-video-history");
const storyPlannerForm = document.querySelector("#story-planner-form");
const storyPlannerInput = document.querySelector("#story-planner-input");
const storyPlannerSubmit = document.querySelector("#story-planner-submit");
const storyPlannerMessage = document.querySelector("#story-planner-message");
const storyPlanResult = document.querySelector("#story-plan-result");
const storyPlanSave = document.querySelector("#story-plan-save");
const storyReplicationForm = document.querySelector("#story-replication-form");
const storyReplicationVideo = document.querySelector("#story-replication-video");
const storyReplicationSubmit = document.querySelector("#story-replication-submit");
const storyReplicationMessage = document.querySelector("#story-replication-message");
const storyReplicationResult = document.querySelector("#story-replication-result");
const storyReplicationUrlForm = document.querySelector("#story-replication-url-form");
const storyReplicationUrl = document.querySelector("#story-replication-url");
const storyReplicationUrlSubmit = document.querySelector("#story-replication-url-submit");
const dialogueExtractionForm = document.querySelector("#dialogue-extraction-form");
const dialogueExtractionVideo = document.querySelector("#dialogue-extraction-video");
const dialogueExtractionSubmit = document.querySelector("#dialogue-extraction-submit");
const dialogueExtractionUrlForm = document.querySelector("#dialogue-extraction-url-form");
const dialogueExtractionUrl = document.querySelector("#dialogue-extraction-url");
const dialogueExtractionUrlSubmit = document.querySelector("#dialogue-extraction-url-submit");
const dialogueExtractionMessage = document.querySelector("#dialogue-extraction-message");
const dialogueExtractionResult = document.querySelector("#dialogue-extraction-result");
const videoBgmForm = document.querySelector("#video-bgm-form");
const videoBgmVideo = document.querySelector("#video-bgm-video");
const videoBgmSelect = document.querySelector("#video-bgm-select");
const videoBgmName = document.querySelector("#video-bgm-name");
const videoBgmEnding = document.querySelector("#video-bgm-ending");
const videoBgmEndingWrap = document.querySelector("#video-bgm-ending-wrap");
const videoBgmEndingSelect = document.querySelector("#video-bgm-ending-select");
const videoBgmAudio = document.querySelector("#video-bgm-audio");
const videoBgmEndingAudio = document.querySelector("#video-bgm-ending-audio");
const videoBgmSubmit = document.querySelector("#video-bgm-submit");
const videoBgmMessage = document.querySelector("#video-bgm-message");
const videoBgmResult = document.querySelector("#video-bgm-result");
const directOutfitForm = document.querySelector("#direct-outfit-form");
const directOutfitPerson = document.querySelector("#direct-outfit-person");
const directOutfitClothing = document.querySelector("#direct-outfit-clothing");
const directOutfitPrompt = document.querySelector("#direct-outfit-prompt");
const directOutfitSubmit = document.querySelector("#direct-outfit-submit");
const directOutfitMessage = document.querySelector("#direct-outfit-message");
const directOutfitResult = document.querySelector("#direct-outfit-result");
const directOutfitPersonPreview = document.querySelector("#direct-outfit-person-preview");
const directOutfitClothingPreview = document.querySelector("#direct-outfit-clothing-preview");
const auditRedrawForm = document.querySelector("#audit-redraw-form");
const auditRedrawImage = document.querySelector("#audit-redraw-image");
const auditRedrawPreview = document.querySelector("#audit-redraw-preview");
const auditRedrawSubmit = document.querySelector("#audit-redraw-submit");
const auditRedrawMessage = document.querySelector("#audit-redraw-message");
const auditRedrawResult = document.querySelector("#audit-redraw-result");
const gptImagesForm = document.querySelector("#gpt-images-form");
const gptImagesPrompt = document.querySelector("#gpt-images-prompt");
const gptImagesSubmit = document.querySelector("#gpt-images-submit");
const gptImagesMessage = document.querySelector("#gpt-images-message");
const gptImagesResult = document.querySelector("#gpt-images-result");
const videoScriptForm = document.querySelector("#video-script-form");
const videoScriptAddress = document.querySelector("#video-script-address");
const videoScriptAddressWrap = document.querySelector("#video-script-address-wrap");
const videoScriptFile = document.querySelector("#video-script-file");
const videoScriptFileWrap = document.querySelector("#video-script-file-wrap");
const videoScriptSourceOptions = document.querySelectorAll("input[name='video-script-source']");
const videoScriptSubmit = document.querySelector("#video-script-submit");
const videoScriptDownload = document.querySelector("#video-script-download");
const videoScriptMessage = document.querySelector("#video-script-message");
const refreshVideoScripts = document.querySelector("#refresh-video-scripts");
const videoScriptListMessage = document.querySelector("#video-script-list-message");
const videoScriptList = document.querySelector("#video-script-list");
const videoScriptResult = document.querySelector("#video-script-result");
const videoScriptTabs = document.querySelectorAll("[data-video-script-tab]");
const videoScriptPanels = document.querySelectorAll("[data-video-script-panel]");
const shortDramaDirectorForm = document.querySelector("#short-drama-director-form");
const shortDramaMode = document.querySelector("#short-drama-mode");
const shortDramaTier = document.querySelector("#short-drama-tier");
const shortDramaPlatform = document.querySelector("#short-drama-platform");
const shortDramaRatio = document.querySelector("#short-drama-ratio");
const shortDramaText = document.querySelector("#short-drama-text");
const shortDramaFile = document.querySelector("#short-drama-file");
const shortDramaSubmit = document.querySelector("#short-drama-submit");
const shortDramaMessage = document.querySelector("#short-drama-message");
const shortDramaResult = document.querySelector("#short-drama-result");
const shortDramaHistory = document.querySelector("#short-drama-history");
const refreshShortDramaTasks = document.querySelector("#refresh-short-drama-tasks");
const myScriptList = document.querySelector("#my-script-list");
const myScriptDetail = document.querySelector("#my-script-detail");
const refreshMyScripts = document.querySelector("#refresh-my-scripts");
const scriptReplicationEmpty = document.querySelector("#script-replication-empty");
const scriptReplicationContent = document.querySelector("#script-replication-content");
const refreshScriptReplication = document.querySelector("#refresh-script-replication");
const videoWorkflowCanvas = document.querySelector("#video-workflow-canvas");
const workflowEdgeLayer = document.querySelector("#workflow-edge-layer");
const canvasRunState = document.querySelector("#canvas-run-state");
const canvasEdgeCount = document.querySelector("#canvas-edge-count");
const canvasSelectionLabel = document.querySelector("#canvas-selection-label");
const canvasZoomLabel = document.querySelector("#canvas-zoom-label");
const sessionUsername = document.querySelector("#session-username");
const logoutButton = document.querySelector("#logout-button");
const createAccountButton = document.querySelector("#create-account-button");
const packageApplicationButton = document.querySelector("#package-application-button");
const accountSettingsMessage = document.querySelector("#account-settings-message");
const accountList = document.querySelector("#account-list");
const selfSettingsForm = document.querySelector("#self-settings-form");
const selfSettingsFields = document.querySelector("#self-settings-fields");
const accountEditor = document.querySelector("#account-editor");
const accountEditorForm = document.querySelector("#account-editor-form");
const accountEditorFields = document.querySelector("#account-editor-fields");
const accountMenuGrid = document.querySelector("#account-menu-grid");
const menuConfigList = document.querySelector("#menu-config-list");
const menuConfigMessage = document.querySelector("#menu-settings-message");
const saveMenuConfigButton = document.querySelector("#save-menu-config");
let currentAccountSession = null;
let accountRows = [];
let menuConfigRows = [];
let canvasZoom = 1;
let canvasPortSelection = null;
let canvasDragState = null;
let canvasWorkflowRunning = false;
// Keeps node outputs available for downstream nodes even after a DOM refresh.
const canvasNodeOutputs = new Map();
const canvasEdges = [
    {from: "script-1", to: "audit-1"},
    {from: "audit-1", to: "seedance-1"},
    {from: "seedance-1", to: "compose-1"}
];

const stages = [
    "AI_ENRICHING_PORTRAIT_PROMPT",
    "AGENT1_GENERATING_PERSON",
    "AI_VERIFYING_PORTRAIT",
    "AGENT2_SELECTING_CLOTHING",
    "AI_ANALYZING_CLOTHING",
    "AGENT3_REPLACING_OUTFIT",
    "AI_VERIFYING_OUTFIT",
    "AGENT4_PRESENTING_RESULT",
    "RAG_LEARNING_EXPERIENCE"
];

let pollTimer;
let systemReady = false;
let runtimeLogLoading = false;
let stepLogLoading = false;
let stepLogJobId = null;
let activeJobId = null;
let currentJobId = null;
let catalogPollTimer = null;
let lastCatalogProcessed = -1;
let selectedTaskId = null;
let currentView = "workbench";
let currentTaskTab = "images";
let hasDebugError = false;
const jobsById = new Map();
const videoJobsBySource = new Map();
const selectedVideoSourceIds = new Set();
const pendingVideoJobsBySource = new Map();
let currentImageJobs = [];
let currentVideoJobs = [];
let batchVideoSubmitting = false;
let selectedVideoFolder = null;
const selectedVideoSourceFolders = new Set();
let currentStoryPlan = null;
let storyReplicationTask = null;
let dialogueExtractionTask = null;
let videoBgmTask = null;
// File inputs are cleared when a shot card is re-rendered after polling. Keep the ordered
// data URLs separately so all selected images survive status refreshes and are submitted together.
const storyShotImageState = new Map();
const storyShotImageReadState = new Map();
let selectedComfyUiFiles = [];
let myScripts = [];
let selectedMyScriptId = null;
let selectedMyScriptEpisodeId = null;
let selectedMyScriptReplicationVersionId = null;
let selectedMyScriptSection = "settings";
const expandedMyScriptProjects = new Set();
const loadingReplicationEpisodes = new Set();

initializeAccountSession();
loadReadiness();
loadHistory();
loadRuntimeLogs();
loadClothingCatalog();
loadCatalogStatus();
loadExperiences();
loadVideoCatalog();
loadComfyUiVideoHistory();

navItems.forEach((item) => item.addEventListener("click", () => authorizeAndActivateView(item.dataset.view, true)));
window.addEventListener("hashchange", () => authorizeAndActivateView(normalizeView(location.hash.slice(1))));
if (logoutButton) logoutButton.addEventListener("click", logoutAccount);
if (createAccountButton) createAccountButton.addEventListener("click", () => openAccountEditor());
if (packageApplicationButton) packageApplicationButton.addEventListener("click", packageApplication);
if (saveMenuConfigButton) saveMenuConfigButton.addEventListener("click", saveMenuConfig);
if (selfSettingsForm) selfSettingsForm.addEventListener("submit", saveSelfSettings);
if (accountEditorForm) accountEditorForm.addEventListener("submit", saveAccountEditor);
document.querySelectorAll("[data-account-close]").forEach(button => button.addEventListener("click", () => accountEditor?.close()));
copyDebugLog.addEventListener("click", () => copyText(debugLog.textContent, copyDebugLog));
copyRuntimeLog.addEventListener("click", () => copyText(runtimeLog.textContent, copyRuntimeLog));
copyStepLog.addEventListener("click", () => copyText(
    `${stepLogJob.textContent}\n${stepLog.textContent}`,
    copyStepLog));
refreshRuntimeLog.addEventListener("click", loadRuntimeLogs);
refreshStepLog.addEventListener("click", () => loadStepLogs());
refreshHistory.addEventListener("click", loadHistory);
refreshClothingCatalog.addEventListener("click", startCatalogRefresh);
refreshVideoCatalog.addEventListener("click", loadVideoCatalog);
openVideoImport.addEventListener("click", () => {
    videoImportForm.hidden = !videoImportForm.hidden;
    if (!videoImportForm.hidden) videoImportFolder.focus();
});
videoImportForm.addEventListener("submit", startVideoImport);
knowledgeTabs.forEach((tab) => tab.addEventListener("click", () => activateKnowledgeTab(tab.dataset.knowledgeTab)));
taskTabs.forEach((tab) => tab.addEventListener("click", () => activateTaskTab(tab.dataset.taskTab)));
if (comfyUiVideoForm) comfyUiVideoForm.addEventListener("submit", submitComfyUiVideo);
if (comfyUiVideoImages) comfyUiVideoImages.addEventListener("change", previewComfyUiVideoImages);
if (refreshComfyUiVideoHistory) refreshComfyUiVideoHistory.addEventListener("click", loadComfyUiVideoHistory);
if (refreshCanvasVideoHistory) refreshCanvasVideoHistory.addEventListener("click", loadComfyUiVideoHistory);
if (storyPlannerForm) storyPlannerForm.addEventListener("submit", previewStoryPlan);
if (storyPlanSave) storyPlanSave.addEventListener("click", saveCurrentStoryPlan);
if (storyReplicationForm) storyReplicationForm.addEventListener("submit", analyzeStoryReplication);
if (storyReplicationUrlForm) storyReplicationUrlForm.addEventListener("submit", resolveStoryReplicationUrl);
if (dialogueExtractionForm) dialogueExtractionForm.addEventListener("submit", analyzeDialogueExtraction);
if (dialogueExtractionUrlForm) dialogueExtractionUrlForm.addEventListener("submit", resolveDialogueExtractionUrl);
if (videoBgmForm) videoBgmForm.addEventListener("submit", submitVideoBgm);
if (videoBgmEnding) videoBgmEnding.addEventListener("change", async () => {
    const enabled = videoBgmEnding.checked;
    if (videoBgmEndingWrap) videoBgmEndingWrap.hidden = !enabled;
    if (enabled) await loadVideoBgmEndingFiles();
});
if (videoBgmSelect) videoBgmSelect.addEventListener("change", () => previewBgmSelection(videoBgmSelect, videoBgmAudio, false));
if (videoBgmEndingSelect) videoBgmEndingSelect.addEventListener("change", () => previewBgmSelection(videoBgmEndingSelect, videoBgmEndingAudio, true));
if (directOutfitForm) directOutfitForm.addEventListener("submit", submitDirectOutfit);
if (directOutfitPerson) directOutfitPerson.addEventListener("change", () => previewDirectOutfitFile(directOutfitPerson, directOutfitPersonPreview));
if (directOutfitClothing) directOutfitClothing.addEventListener("change", () => previewDirectOutfitFile(directOutfitClothing, directOutfitClothingPreview));
if (auditRedrawForm) auditRedrawForm.addEventListener("submit", submitAuditRedraw);
if (auditRedrawImage) auditRedrawImage.addEventListener("change", () => previewDirectOutfitFile(auditRedrawImage, auditRedrawPreview));
if (gptImagesForm) gptImagesForm.addEventListener("submit", submitGptImages);
if (videoScriptForm) videoScriptForm.addEventListener("submit", submitVideoScript);
if (videoScriptDownload) videoScriptDownload.addEventListener("click", () => submitVideoScriptTask(false));
videoScriptSourceOptions.forEach((option) => option.addEventListener("change", syncVideoScriptSource));
syncVideoScriptSource();
videoScriptTabs.forEach((tab) => tab.addEventListener("click", () => activateVideoScriptTab(tab.dataset.videoScriptTab)));
if (refreshVideoScripts) refreshVideoScripts.addEventListener("click", loadVideoScripts);
if (shortDramaDirectorForm) shortDramaDirectorForm.addEventListener("submit", submitShortDramaDirector);
if (refreshShortDramaTasks) refreshShortDramaTasks.addEventListener("click", loadShortDramaTasks);
if (refreshMyScripts) refreshMyScripts.addEventListener("click", loadMyScripts);
if (refreshScriptReplication) refreshScriptReplication.addEventListener("click", () => selectedMyScriptEpisodeId && loadScriptReplication(selectedMyScriptEpisodeId));
loadVideoScripts();
loadShortDramaTasks();
loadMyScripts();
loadVideoBgmFiles();
if (videoWorkflowCanvas) initVideoWorkflowCanvas();
batchGenerateVideo.addEventListener("click", startBatchVideoGeneration);
selectAllVideoSources.addEventListener("change", () => {
    const eligibleIds = eligibleVideoSourceIds();
    if (selectAllVideoSources.checked) {
        eligibleIds.forEach((id) => selectedVideoSourceIds.add(id));
    } else {
        eligibleIds.forEach((id) => selectedVideoSourceIds.delete(id));
    }
    renderHistoryTable(currentImageJobs);
});
closeTaskDetail.addEventListener("click", closeSelectedTask);
closeImagePreview.addEventListener("click", closeImagePreviewModal);
imagePreviewModal.addEventListener("click", (event) => {
    if (event.target === imagePreviewModal) closeImagePreviewModal();
});
document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !imagePreviewModal.hidden) closeImagePreviewModal();
});
stopCurrentJob.addEventListener("click", () => cancelJob(activeJobId));
startCurrentJob.addEventListener("click", () => {
    if (currentJobId) {
        restartJob(currentJobId);
    } else {
        form.requestSubmit();
    }
});

promptInput.addEventListener("input", () => {
    characterCount.textContent = `${promptInput.value.length} / 2000`;
    if (!currentJobId) setStartButton(Boolean(promptInput.value.trim()));
});

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const prompt = promptInput.value.trim();
    const portraitGenerationMode = portraitGenerationModeInputs.find((input) => input.checked)?.value || "STANDARD";
    if (!prompt) {
        showError("请先输入人物描述词。");
        promptInput.focus();
        return;
    }

    clearTimeout(pollTimer);
    resetView();
    setLoading(true);
    setStartButton(false);

    try {
        const response = await fetch("/api/generations", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({prompt, portraitGenerationMode})
        });
        const payload = await readJson(response);
        if (!response.ok) {
            const error = new Error(payload.message || `创建任务失败（HTTP ${response.status}）`);
            error.details = payload.details;
            throw error;
        }
        activeJobId = payload.jobId;
        currentJobId = payload.jobId;
        setStopButton(true);
        setStartButton(false);
        const createdJobCount = Number(payload.jobCount) || 1;
        if (createdJobCount > 1) {
            jobStatus.textContent = `已创建 ${createdJobCount} 条任务`;
            progressMessage.textContent = `${createdJobCount} 条图片任务已进入执行队列，当前跟踪第一条任务。`;
            await loadHistory();
        }
        await poll(payload.statusUrl);
    } catch (error) {
        failView(error.message || "创建任务失败。", error.details);
    }
});

async function poll(statusUrl) {
    try {
        const response = await fetch(statusUrl, {cache: "no-store"});
        const job = await readJson(response);
        if (!response.ok) {
            throw new Error(job.message || `查询任务失败（HTTP ${response.status}）`);
        }

        renderJob(job);
        upsertHistoryRow(job, true);
        loadStepLogs(job.id);
        if (job.status === "SUCCESS") {
            activeJobId = null;
            setStopButton(false);
            setStartButton(true);
            setLoading(false);
            showResult(job);
            loadExperiences();
            return;
        }
        if (job.status === "FAILED") {
            activeJobId = null;
            setStopButton(false);
            setStartButton(true);
            failView(job.error || "生成流程失败。", job.errorDetails);
            return;
        }
        if (job.status === "CANCELLED") {
            activeJobId = null;
            setStopButton(false);
            setStartButton(true);
            setLoading(false);
            upsertResultCard(job, true);
            jobStatus.textContent = "已停止";
            progressMessage.textContent = job.message || "任务已手动停止";
            loadHistory();
            return;
        }
        pollTimer = setTimeout(() => poll(statusUrl), 2000);
    } catch (error) {
        failView(error.message || "查询生成状态失败。", error.details);
    }
}

async function startCatalogRefresh() {
    refreshClothingCatalog.disabled = true;
    catalogStatus.textContent = "正在创建服装资料任务……";
    lastCatalogProcessed = -1;
    try {
        const response = await fetch("/api/clothing-catalog/refresh", {
            method: "POST",
            cache: "no-store"
        });
        const payload = await readJson(response);
        if (!response.ok) {
            throw new Error(payload.message || `创建资料任务失败（HTTP ${response.status}）`);
        }
        renderCatalogStatus(payload);
        scheduleCatalogPoll();
    } catch (error) {
        catalogStatus.textContent = error.message || "服装资料生成失败";
        refreshClothingCatalog.disabled = false;
    }
}

async function loadCatalogStatus() {
    try {
        const response = await fetch("/api/clothing-catalog/status", {cache: "no-store"});
        const status = await readJson(response);
        if (!response.ok) return;
        renderCatalogStatus(status);
        if (isCatalogRefreshRunning(status)) scheduleCatalogPoll();
    } catch (error) {
        catalogStatus.textContent = "无法读取资料生成状态";
    }
}

function scheduleCatalogPoll() {
    clearTimeout(catalogPollTimer);
    catalogPollTimer = setTimeout(async () => {
        try {
            const response = await fetch("/api/clothing-catalog/status", {cache: "no-store"});
            const status = await readJson(response);
            if (!response.ok) throw new Error(status.message || "状态读取失败");
            renderCatalogStatus(status);
            if (status.processed !== lastCatalogProcessed) {
                lastCatalogProcessed = status.processed;
                await loadClothingCatalog();
            }
            if (isCatalogRefreshRunning(status)) {
                scheduleCatalogPoll();
            } else {
                await loadClothingCatalog();
            }
        } catch (error) {
            catalogStatus.textContent = error.message || "状态读取失败";
            refreshClothingCatalog.disabled = false;
        }
    }, 2000);
}

function renderCatalogStatus(status) {
    const running = isCatalogRefreshRunning(status);
    const total = Math.max(1, status.total || 0);
    catalogProgress.max = total;
    catalogProgress.value = Math.min(total, status.processed || 0);
    const errorSuffix = status.errors?.length ? `；失败：${status.errors.join(" | ")}` : "";
    catalogStatus.textContent = `${status.message || status.status}${errorSuffix}`;
    refreshClothingCatalog.disabled = running;
    refreshClothingCatalog.textContent = running ? "资料生成中" : "生成本地资料";
}

function isCatalogRefreshRunning(status) {
    return status?.running === true || status?.status === "RUNNING";
}

async function loadClothingCatalog() {
    try {
        const response = await fetch("/api/clothing-catalog", {cache: "no-store"});
        const profiles = await readJson(response);
        if (!response.ok || !Array.isArray(profiles)) {
            throw new Error(profiles.message || "本地资料库尚未初始化");
        }
        renderClothingProfiles(profiles);
        if (profiles.length && !catalogStatus.textContent.includes("正在")) {
            catalogStatus.textContent = `本地数据库已保存 ${profiles.length} 套结构化服装资料`;
        }
    } catch (error) {
        clothingProfileGrid.replaceChildren(emptyKnowledge(error.message || "暂无服装资料"));
    }
}

function renderClothingProfiles(profiles) {
    clothingProfileGrid.replaceChildren();
    if (!profiles.length) {
        clothingProfileGrid.append(emptyKnowledge("尚未生成服装资料"));
        return;
    }
    profiles.forEach((profile) => {
        const card = document.createElement("article");
        card.className = "clothing-profile-card";
        const image = document.createElement("img");
        image.src = withVersion(profile.imageUrl, profile.updatedAt);
        image.alt = profile.analysis?.name || profile.fileName;
        const body = document.createElement("div");
        body.className = "clothing-profile-body";
        const title = document.createElement("h3");
        title.textContent = profile.analysis?.name || "未命名造型";
        const summary = document.createElement("p");
        summary.textContent = profile.analysis?.summary || "暂无摘要";
        const tags = document.createElement("div");
        tags.className = "profile-tags";
        const values = [
            ...(profile.analysis?.styles || []),
            ...(profile.analysis?.occasions || []),
            ...(profile.analysis?.colors || [])
        ].slice(0, 8);
        values.forEach((value) => {
            const tag = document.createElement("span");
            tag.textContent = value;
            tags.append(tag);
        });
        const file = document.createElement("span");
        file.className = "profile-file";
        file.textContent = profile.fileName;
        body.append(title, summary, tags, file);
        card.append(image, body);
        clothingProfileGrid.append(card);
    });
}

async function loadExperiences() {
    try {
        const response = await fetch("/api/fashion-knowledge/experiences", {cache: "no-store"});
        const experiences = await readJson(response);
        if (!response.ok || !Array.isArray(experiences)) return;
        experienceList.replaceChildren();
        experienceCount.textContent = `${experiences.length} 条经验`;
        if (!experiences.length) {
            experienceList.append(emptyKnowledge("暂无通过质检并完成提取的任务经验"));
            return;
        }
        experiences.slice().reverse().forEach((experience) => {
            const item = document.createElement("article");
            item.className = "experience-item";
            const header = document.createElement("header");
            const title = document.createElement("h4");
            title.textContent = experience.content?.title || "换装经验";
            const score = document.createElement("span");
            score.textContent = `证据分 ${experience.qualityScore}`;
            header.append(title, score);
            const scenario = document.createElement("p");
            scenario.textContent = experience.content?.scenario || "";
            const strategy = document.createElement("p");
            strategy.textContent = experience.content?.successfulStrategy || "";
            const source = document.createElement("small");
            source.textContent = `来源任务 ${experience.sourceJobId} · ${formatTime(experience.createdAt)}`;
            item.append(header, scenario, strategy, source);
            experienceList.append(item);
        });
    } catch (error) {
        // Knowledge history is supplementary to the main generation workflow.
    }
}

function emptyKnowledge(message) {
    const empty = document.createElement("p");
    empty.className = "knowledge-empty";
    empty.textContent = message;
    return empty;
}

function renderJob(job) {
    jobsById.set(job.id, job);
    currentJobId = job.id;
    syncPortraitGenerationMode(job.portraitGenerationMode);
    jobStatus.textContent = job.status === "QUEUED" ? "排队中" : displayStatus(job);
    progressMessage.textContent = job.message || "Agent 正在处理";
    setStopButton(job.status === "QUEUED" || job.status === "RUNNING");
    setStartButton(job.status !== "QUEUED" && job.status !== "RUNNING");

    const clothingStep = document.querySelector('[data-stage="AGENT2_SELECTING_CLOTHING"] small');
    const clothingRule = document.querySelector('[data-stage="AGENT2_SELECTING_CLOTHING"] .clothing-match-rule');
    if (clothingStep) {
        if (job.clothingMatchName) {
            clothingStep.textContent = job.clothingMatchPercentage == null
                ? `${job.clothingMatchName} · 随机选择（无匹配度）`
                : `${job.clothingMatchName} · 匹配度 ${Number(job.clothingMatchPercentage).toFixed(1)}%`;
            clothingStep.title = job.clothingMatchRule || "";
            if (clothingRule) {
                clothingRule.textContent = `匹配规则：${job.clothingMatchRule || "未记录"}`;
                clothingRule.hidden = false;
            }
        } else {
            clothingStep.textContent = clothingStep.dataset.defaultText || "根据人物语义检索本地服装资料";
            clothingStep.removeAttribute("title");
            if (clothingRule) {
                clothingRule.textContent = "";
                clothingRule.hidden = true;
            }
        }
    }

    let displayStage = job.stage;
    if (job.stage === "AI_REFINING_PORTRAIT_PROMPT") displayStage = "AGENT1_GENERATING_PERSON";
    if (job.stage === "AI_REFINING_PROMPT") displayStage = "AGENT3_REPLACING_OUTFIT";
    const currentIndex = stages.indexOf(displayStage);
    agentItems.forEach((item, index) => {
        const state = item.querySelector(".agent-state");
        item.classList.toggle("is-active", index === currentIndex);
        item.classList.toggle("is-complete", currentIndex > index || job.stage === "COMPLETED");
        state.textContent = currentIndex > index || job.stage === "COMPLETED"
            ? "完成"
            : index === currentIndex ? "处理中" : "等待";
    });

    if (selectedTaskId === job.id) {
        renderSelectedTask(job);
    }
}

function showResult(job) {
    jobsById.set(job.id, job);
    if (selectedTaskId === job.id) renderSelectedTask(job);
    jobStatus.textContent = "已完成";
    progressMessage.textContent = job.message || "八个协作环节已完成全部流程";
    loadHistory();
}

async function loadHistory() {
    try {
        const [jobsResponse, videoResponse] = await Promise.all([
            fetch("/api/generations", {cache: "no-store"}),
            fetch("/api/video-generations", {cache: "no-store"})
        ]);
        const jobs = await readJson(jobsResponse);
        const videoJobs = await readJson(videoResponse);
        if (!jobsResponse.ok || !Array.isArray(jobs)) {
            return;
        }
        currentVideoJobs = videoResponse.ok && Array.isArray(videoJobs) ? videoJobs : [];
        videoJobsBySource.clear();
        if (currentVideoJobs.length) {
            currentVideoJobs.forEach((videoJob) => {
                if (!videoJobsBySource.has(videoJob.sourceJobId)) {
                    videoJobsBySource.set(videoJob.sourceJobId, videoJob);
                }
            });
        }
        pendingVideoJobsBySource.forEach((pendingJob, sourceJobId) => {
            const newestServerJob = videoJobsBySource.get(sourceJobId);
            if (newestServerJob && newestServerJob.id !== pendingJob.previousVideoJobId) {
                pendingVideoJobsBySource.delete(sourceJobId);
            }
        });
        jobsById.clear();
        jobs.forEach((job) => jobsById.set(job.id, job));
        currentImageJobs = jobs;
        renderHistoryTable(currentImageJobs);
        displayVideoJobs();
        if (selectedTaskId && jobsById.has(selectedTaskId)) {
            renderSelectedTask(jobsById.get(selectedTaskId));
        }
        if (!activeJobId) {
            const runningJob = jobs.find((job) => job.status === "RUNNING" || job.status === "QUEUED");
            if (runningJob) {
                currentJobId = runningJob.id;
                activeJobId = runningJob.id;
                setLoading(true);
                setStopButton(true);
                clearTimeout(pollTimer);
                poll(`/api/generations/${runningJob.id}`);
            } else if (jobs.length > 0) {
                currentJobId = jobs[0].id;
                setStartButton(true);
            } else {
                currentJobId = null;
                setStartButton(Boolean(promptInput.value.trim()));
            }
        }
    } catch (error) {
        // History is supplementary; current task polling remains available.
    }
}

function renderHistoryTable(jobs) {
    historyBody.replaceChildren();
    if (jobs.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 10;
        cell.className = "history-empty";
        cell.textContent = "暂无历史记录。新任务会自动写入本地数据库。";
        row.append(cell);
        historyBody.append(row);
        updateBatchVideoControls();
        return;
    }
    jobs.forEach((job) => upsertHistoryRow(job, false));
    updateBatchVideoControls();
}

function upsertHistoryRow(job, prepend) {
    let row = historyBody.querySelector(`tr[data-history-job-id="${job.id}"]`);
    if (!row) {
        row = document.createElement("tr");
        row.dataset.historyJobId = job.id;
        row.innerHTML = "<td class=\"selection-column\"></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td>";
        if (prepend && historyBody.firstChild) {
            historyBody.prepend(row);
        } else {
            historyBody.append(row);
        }
    }
    row.classList.toggle("is-selected", selectedTaskId === job.id);

    const cells = row.querySelectorAll("td");
    const videoJob = videoJobsBySource.get(job.id);
    const videoActive = pendingVideoJobsBySource.has(job.id)
        || (videoJob && !["SUCCESS", "FAILED"].includes(videoJob.status));
    const selectable = job.status === "SUCCESS" && !videoActive;
    const selectionCell = cells[0];
    selectionCell.replaceChildren();
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.ariaLabel = `选择图片任务：${job.prompt || job.id}`;
    checkbox.checked = selectedVideoSourceIds.has(job.id);
    checkbox.disabled = !selectable;
    checkbox.addEventListener("change", () => {
        if (checkbox.checked) {
            selectedVideoSourceIds.add(job.id);
        } else {
            selectedVideoSourceIds.delete(job.id);
        }
        updateBatchVideoControls();
    });
    selectionCell.append(checkbox);

    cells[1].textContent = formatTime(job.createdAt);
    renderPromptWithMode(cells[2], job);
    renderHistoryImage(cells[3], job.originalImageUrl, "人物生成原图", job.updatedAt);
    renderHistoryImage(cells[4], job.finalImageUrl, "智能换装结果", job.updatedAt);
    cells[5].textContent = displayStatus(job);
    cells[5].dataset.status = job.status;
    renderQualityScore(cells[6], job.finalPortraitQualityReport?.overallScore, "人物");
    cells[7].textContent = stageLabel(job.stage);
    cells[8].textContent = job.reply || job.error || job.message || "等待结果";

    const actions = cells[9];
    actions.replaceChildren();
    const stepsButton = document.createElement("button");
    stepsButton.type = "button";
    stepsButton.textContent = "运行日志";
    stepsButton.addEventListener("click", () => openTaskDetail(job));
    actions.append(stepsButton);

    if (job.status === "RUNNING" || job.status === "QUEUED") {
        const cancelButton = document.createElement("button");
        cancelButton.type = "button";
        cancelButton.className = "danger-button";
        cancelButton.textContent = "停止";
        cancelButton.addEventListener("click", () => cancelJob(job.id));
        actions.append(cancelButton);
    } else {
        const restartButton = document.createElement("button");
        restartButton.type = "button";
        restartButton.textContent = "启动";
        restartButton.addEventListener("click", () => restartJob(job.id));
        actions.append(restartButton);
    }
    if (job.status === "SUCCESS") {
        const videoButton = document.createElement("button");
        videoButton.type = "button";
        videoButton.disabled = Boolean(videoActive);
        videoButton.textContent = videoActive ? "视频处理中" : videoJob?.status === "SUCCESS" ? "重新生成视频" : "生成视频";
        videoButton.addEventListener("click", () => startVideoGeneration(job.id));
        actions.append(videoButton);
    }
    if (["SUCCESS", "FAILED", "CANCELLED"].includes(job.status)) {
        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "danger-button";
        deleteButton.textContent = "删除";
        deleteButton.addEventListener("click", () => deleteImageJob(job.id, deleteButton));
        actions.append(deleteButton);
    }
}

function renderPromptWithMode(cell, job) {
    cell.replaceChildren();
    const prompt = document.createElement("span");
    prompt.textContent = job.prompt || "未命名任务";
    const mode = document.createElement("small");
    mode.className = "portrait-mode-badge";
    mode.dataset.mode = job.portraitGenerationMode || "STANDARD";
    mode.textContent = mode.dataset.mode === "ENHANCED" ? "增强版人物生成" : "普通版人物生成";
    cell.append(prompt, mode);
}

function renderHistoryImage(cell, url, alt, updatedAt) {
    cell.replaceChildren();
    if (!url) {
        const placeholder = document.createElement("span");
        placeholder.className = "history-image-placeholder";
        placeholder.textContent = "等待生成";
        cell.append(placeholder);
        return;
    }
    const button = document.createElement("button");
    button.type = "button";
    button.className = "history-image-button";
    button.title = "点击放大查看";
    button.setAttribute("aria-label", `放大查看${alt}`);
    const image = document.createElement("img");
    image.src = withVersion(url, updatedAt);
    image.alt = alt;
    image.loading = "lazy";
    button.append(image);
    button.addEventListener("click", () => openImagePreview(url, alt, updatedAt));
    cell.append(button);
}

function eligibleVideoSourceIds() {
    return currentImageJobs
        .filter((job) => {
            const videoJob = videoJobsBySource.get(job.id);
            const active = pendingVideoJobsBySource.has(job.id)
                || (videoJob && !["SUCCESS", "FAILED"].includes(videoJob.status));
            return job.status === "SUCCESS" && !active;
        })
        .map((job) => job.id);
}

function updateBatchVideoControls() {
    const eligibleIds = eligibleVideoSourceIds();
    const eligibleSet = new Set(eligibleIds);
    [...selectedVideoSourceIds].forEach((id) => {
        if (!eligibleSet.has(id)) selectedVideoSourceIds.delete(id);
    });
    const selectedCount = selectedVideoSourceIds.size;
    selectedVideoCount.textContent = `已选 ${selectedCount} 项`;
    batchGenerateVideo.textContent = selectedCount > 0 ? `批量生成视频（${selectedCount}）` : "批量生成视频";
    batchGenerateVideo.disabled = selectedCount === 0 || batchVideoSubmitting;
    selectAllVideoSources.disabled = eligibleIds.length === 0 || batchVideoSubmitting;
    selectAllVideoSources.checked = eligibleIds.length > 0 && selectedCount === eligibleIds.length;
    selectAllVideoSources.indeterminate = selectedCount > 0 && selectedCount < eligibleIds.length;
}

function displayVideoJobs() {
    const optimisticJobs = [...pendingVideoJobsBySource.values()];
    renderVideoHistoryTable([...optimisticJobs, ...currentVideoJobs]);
}

function renderVideoHistoryTable(videoJobs) {
    videoHistoryBody.replaceChildren();
    if (videoJobs.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 10;
        cell.className = "history-empty";
        cell.textContent = "暂无视频生成记录。请先在图片生成列表中点击“生成视频”。";
        row.append(cell);
        videoHistoryBody.append(row);
        return;
    }
    videoJobs.forEach((videoJob) => {
        const sourceJob = jobsById.get(videoJob.sourceJobId);
        const row = document.createElement("tr");
        row.dataset.videoJobId = videoJob.id;
        row.innerHTML = "<td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td><td></td>";
        const cells = row.querySelectorAll("td");
        cells[0].textContent = formatTime(videoJob.createdAt);
        cells[1].textContent = sourceJob?.prompt || videoJob.sourceJobId;
        cells[1].title = cells[1].textContent;
        cells[2].textContent = videoJob.sourceVideoFileName || "等待选择";
        cells[2].title = cells[2].textContent;
        cells[3].textContent = videoStatusLabel(videoJob.status);
        cells[3].dataset.status = videoJob.status;
        cells[4].textContent = videoJob.firstSegmentStatus || "等待";
        cells[5].textContent = videoJob.secondSegmentStatus || "等待";
        renderQualityScore(cells[6], videoJob.qualityReport?.overallScore, "视频");
        cells[4].title = cells[4].textContent;
        cells[5].title = cells[5].textContent;
        renderHistoryVideo(cells[7], videoJob);
        renderVideoExecutionResult(cells[8], videoJob);

        const actions = cells[9];
        if (sourceJob) {
            const sourceButton = document.createElement("button");
            sourceButton.type = "button";
            sourceButton.textContent = "图片任务";
            sourceButton.addEventListener("click", () => {
                activateTaskTab("images");
                openTaskDetail(sourceJob);
            });
            actions.append(sourceButton);
        }
        if (videoJob.status === "SUCCESS") {
            const folderButton = document.createElement("button");
            folderButton.type = "button";
            folderButton.textContent = "打开文件夹";
            folderButton.addEventListener("click", () => openVideoFolder(videoJob.id));
            actions.append(folderButton);
        }
        if (videoJob.status === "FAILED" && videoJob.downloadRetryable) {
            const retryButton = document.createElement("button");
            retryButton.type = "button";
            retryButton.textContent = "重新下载";
            retryButton.addEventListener("click", () => retryVideoDownload(videoJob.id, retryButton));
            actions.append(retryButton);
        }
        if (["SUCCESS", "FAILED"].includes(videoJob.status)) {
            const deleteButton = document.createElement("button");
            deleteButton.type = "button";
            deleteButton.className = "danger-button";
            deleteButton.textContent = "删除";
            deleteButton.addEventListener("click", () => deleteVideoJob(videoJob.id, deleteButton));
            actions.append(deleteButton);
        }
        videoHistoryBody.append(row);
    });
}

function renderVideoExecutionResult(cell, videoJob) {
    const value = videoJob.error || videoJob.message || "等待执行";
    cell.replaceChildren();
    cell.title = value;
    if (value.length <= 180) {
        cell.textContent = value;
        return;
    }
    const summaryText = document.createElement("p");
    summaryText.className = "video-result-summary";
    summaryText.textContent = `${value.slice(0, 160)}…`;
    const details = document.createElement("details");
    const summary = document.createElement("summary");
    summary.textContent = "查看完整错误";
    const fullText = document.createElement("pre");
    fullText.textContent = value;
    details.append(summary, fullText);
    cell.append(summaryText, details);
}

function renderQualityScore(cell, value, label) {
    cell.replaceChildren();
    if (value == null) {
        cell.textContent = "-";
        return;
    }
    const score = document.createElement("span");
    score.className = `table-score ${Number(value) >= 70 ? "is-high" : "is-low"}`;
    score.textContent = `${label} ${Number(value)} 分`;
    cell.append(score);
}

function renderHistoryVideo(cell, videoJob) {
    cell.replaceChildren();
    if (!videoJob || videoJob.status !== "SUCCESS" || !videoJob.finalVideoUrl) {
        cell.textContent = videoJob ? videoStatusLabel(videoJob.status) : "未生成";
        return;
    }
    const video = document.createElement("video");
    video.controls = true;
    video.preload = "metadata";
    video.src = withVersion(videoJob.finalVideoUrl, videoJob.updatedAt);
    video.title = videoJob.finalVideoFileName || "最终视频";
    cell.append(video);
}

async function startVideoGeneration(sourceJobId) {
    await submitVideoGeneration([sourceJobId], false);
}

async function startBatchVideoGeneration() {
    const sourceJobIds = [...selectedVideoSourceIds];
    if (!sourceJobIds.length || batchVideoSubmitting) return;
    await submitVideoGeneration(sourceJobIds, true);
}

async function submitVideoGeneration(sourceJobIds, batch) {
    const selectedBeforeSubmit = new Set(sourceJobIds);
    const submittedAt = new Date().toISOString();
    sourceJobIds.forEach((sourceJobId, index) => {
        pendingVideoJobsBySource.set(sourceJobId, {
            id: `pending-${sourceJobId}`,
            sourceJobId,
            previousVideoJobId: videoJobsBySource.get(sourceJobId)?.id || null,
            status: "QUEUED",
            message: batch ? "批量视频任务正在提交，等待进入单任务队列" : "视频任务正在提交，等待进入单任务队列",
            sourceVideoFileName: null,
            firstSegmentStatus: "等待入队",
            secondSegmentStatus: "等待入队",
            qualityReport: null,
            finalVideoUrl: null,
            error: null,
            createdAt: new Date(Date.parse(submittedAt) + index).toISOString(),
            updatedAt: submittedAt
        });
        selectedVideoSourceIds.delete(sourceJobId);
    });
    batchVideoSubmitting = batch;
    renderHistoryTable(currentImageJobs);
    displayVideoJobs();
    activateTaskTab("videos");
    videoHistoryMessage.textContent = batch
        ? `正在提交 ${sourceJobIds.length} 条视频任务。任务将逐条排队，每条任务的两个 Plus 片段并发生成。`
        : "正在提交视频任务。任务入队后，两个 Plus 片段将并发生成。";

    try {
        const response = await fetch(batch ? "/api/video-generations/batch" : `/api/video-generations/source/${sourceJobIds[0]}`, {
            method: "POST",
            headers: batch ? {"Content-Type": "application/json"} : undefined,
            body: batch ? JSON.stringify({sourceJobIds}) : undefined
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `创建视频任务失败（HTTP ${response.status}）`);
        const createdJobs = Array.isArray(payload) ? payload : [payload];
        createdJobs.forEach((videoJob) => {
            pendingVideoJobsBySource.delete(videoJob.sourceJobId);
            currentVideoJobs = currentVideoJobs.filter((current) => current.id !== videoJob.id);
            currentVideoJobs.unshift(videoJob);
            videoJobsBySource.set(videoJob.sourceJobId, videoJob);
        });
        videoHistoryMessage.textContent = `${createdJobs.length} 条视频任务已进入单任务队列。每条任务的两个 Plus 片段并发生成，RunningHub 状态每 2 分钟查询一次。`;
        displayVideoJobs();
        await loadHistory();
    } catch (error) {
        sourceJobIds.forEach((sourceJobId) => pendingVideoJobsBySource.delete(sourceJobId));
        selectedBeforeSubmit.forEach((sourceJobId) => selectedVideoSourceIds.add(sourceJobId));
        videoHistoryMessage.textContent = `视频任务创建失败：${error.message || error}`;
        renderHistoryTable(currentImageJobs);
        displayVideoJobs();
    } finally {
        batchVideoSubmitting = false;
        updateBatchVideoControls();
    }
}

async function openVideoFolder(videoJobId) {
    try {
        const response = await fetch(`/api/video-generations/${videoJobId}/open-folder`, {method: "POST"});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "打开文件夹失败");
        videoHistoryMessage.textContent = `已打开：${payload.folder}`;
    } catch (error) {
        videoHistoryMessage.textContent = `打开文件夹失败：${error.message || error}`;
    }
}

async function retryVideoDownload(videoJobId, button) {
    button.disabled = true;
    button.textContent = "重新下载中";
    videoHistoryMessage.textContent = "正在使用 RunningHub 已返回的视频地址重新下载，不会再次生成视频。";
    try {
        const response = await fetch(`/api/video-generations/${videoJobId}/retry-download`, {method: "POST"});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `重新下载失败（HTTP ${response.status}）`);
        currentVideoJobs = currentVideoJobs.map((job) => job.id === payload.id ? payload : job);
        displayVideoJobs();
        await loadHistory();
    } catch (error) {
        videoHistoryMessage.textContent = `重新下载提交失败：${error.message || error}`;
        button.disabled = false;
        button.textContent = "重新下载";
    }
}

async function deleteImageJob(jobId, button) {
    const confirmed = window.confirm(
        "确定物理删除这条图片任务吗？生成的人物原图、换装图片、过程文件，以及关联的视频任务和视频文件都会永久删除，无法恢复。"
    );
    if (!confirmed) return;
    button.disabled = true;
    try {
        const response = await fetch(`/api/generations/${jobId}`, {method: "DELETE"});
        const payload = response.ok ? {} : await readJson(response);
        if (!response.ok) throw new Error(payload.message || `删除图片任务失败（HTTP ${response.status}）`);
        selectedVideoSourceIds.delete(jobId);
        pendingVideoJobsBySource.delete(jobId);
        if (selectedTaskId === jobId) closeSelectedTask();
        if (currentJobId === jobId) currentJobId = null;
        historyMessage.textContent = "图片任务、关联数据库记录和本地生成文件已删除。";
        await loadHistory();
    } catch (error) {
        historyMessage.textContent = `删除图片任务失败：${error.message || error}`;
        button.disabled = false;
    }
}

async function deleteVideoJob(videoJobId, button) {
    const confirmed = window.confirm(
        "确定物理删除这条视频任务吗？拆分片段、动作迁移结果和最终视频都会永久删除，无法恢复。"
    );
    if (!confirmed) return;
    button.disabled = true;
    try {
        const response = await fetch(`/api/video-generations/${videoJobId}`, {method: "DELETE"});
        const payload = response.ok ? {} : await readJson(response);
        if (!response.ok) throw new Error(payload.message || `删除视频任务失败（HTTP ${response.status}）`);
        videoHistoryMessage.textContent = "视频任务、数据库记录和本地生成文件已删除。";
        await loadHistory();
    } catch (error) {
        videoHistoryMessage.textContent = `删除视频任务失败：${error.message || error}`;
        button.disabled = false;
    }
}

function videoStatusLabel(status) {
    const labels = {
        QUEUED: "视频排队中",
        SPLITTING: "正在拆分视频",
        GENERATING: "动作迁移生成中",
        DOWNLOADING: "视频下载中",
        MERGING: "视频转场合并中",
        QUALITY_CHECKING: "视频质检中",
        SUCCESS: "视频已完成",
        FAILED: "视频失败"
    };
    return labels[status] || status || "未生成";
}

function activateTaskTab(name) {
    currentTaskTab = name === "videos" ? "videos" : "images";
    taskTabs.forEach((tab) => {
        const active = tab.dataset.taskTab === currentTaskTab;
        tab.classList.toggle("is-active", active);
        tab.setAttribute("aria-selected", String(active));
    });
    taskPanels.forEach((panel) => {
        panel.hidden = panel.dataset.taskPanel !== currentTaskTab;
    });
}

function activateKnowledgeTab(name) {
    knowledgeTabs.forEach((tab) => {
        const active = tab.dataset.knowledgeTab === name;
        tab.classList.toggle("is-active", active);
        tab.setAttribute("aria-selected", String(active));
    });
    knowledgePanels.forEach((panel) => {
        panel.hidden = panel.dataset.knowledgePanel !== name;
    });
    if (name === "videos") loadVideoCatalog();
}

function activateVideoScriptTab(name) {
    const activeName = name === "list" ? "list" : "submit";
    videoScriptTabs.forEach((tab) => {
        const active = tab.dataset.videoScriptTab === activeName;
        tab.classList.toggle("is-active", active);
        tab.setAttribute("aria-selected", String(active));
    });
    videoScriptPanels.forEach((panel) => { panel.hidden = panel.dataset.videoScriptPanel !== activeName; });
    if (activeName === "list") loadVideoScripts();
}

async function startVideoImport(event) {
    event.preventDefault();
    const folderName = videoImportFolder.value.trim();
    const content = videoImportContent.value.trim();
    if (!folderName || !content) return;
    submitVideoImport.disabled = true;
    videoImportStatus.textContent = "正在提交 SnapAny 提取任务……";
    videoImportItems.replaceChildren();
    try {
        const response = await fetch("/api/video-catalog/imports", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({folderName, content})
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`);
        renderVideoImport(payload);
        await pollVideoImport(payload.id);
    } catch (error) {
        videoImportStatus.textContent = `视频提取提交失败：${error.message || error}`;
        submitVideoImport.disabled = false;
    }
}

async function pollVideoImport(importId) {
    try {
        const response = await fetch(`/api/video-catalog/imports/${importId}`, {cache: "no-store"});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`);
        renderVideoImport(payload);
        if (["SUCCESS", "PARTIAL", "FAILED"].includes(payload.status)) {
            submitVideoImport.disabled = false;
            if (payload.succeeded > 0) {
                await selectVideoFolder(payload.folderName);
            } else {
                await loadVideoCatalog();
            }
            return;
        }
        window.setTimeout(() => pollVideoImport(importId), 1000);
    } catch (error) {
        videoImportStatus.textContent = `视频提取状态查询失败：${error.message || error}`;
        submitVideoImport.disabled = false;
    }
}

function renderVideoImport(payload) {
    const statusLabels = {
        QUEUED: "排队中", RUNNING: "提取中", SUCCESS: "已完成", PARTIAL: "部分完成", FAILED: "全部失败"
    };
    videoImportStatus.textContent = `${payload.folderName}：${statusLabels[payload.status] || payload.status}，完成 ${payload.completed}/${payload.total}，成功 ${payload.succeeded}，失败 ${payload.failed}`;
    videoImportItems.replaceChildren();
    (payload.items || []).forEach((item) => {
        const line = document.createElement("div");
        line.className = "video-import-item";
        line.dataset.status = item.status;
        line.title = item.error || item.fileName || item.url;
        const detail = item.fileName ? ` -> ${item.fileName}` : item.error ? `：${item.error}` : "";
        line.textContent = `${item.index}. ${item.status}${detail}`;
        videoImportItems.append(line);
    });
}

async function loadVideoCatalog() {
    try {
        const foldersResponse = await fetch("/api/video-catalog/folders", {cache: "no-store"});
        const folders = await readJson(foldersResponse);
        if (!foldersResponse.ok || !Array.isArray(folders)) {
            throw new Error(folders.message || "Failed to read video folders");
        }
        const availableNames = new Set(folders.map((folder) => folder.name));
        if (!selectedVideoFolder || !availableNames.has(selectedVideoFolder)) {
            selectedVideoFolder = folders[0]?.name || null;
        }
        selectedVideoSourceFolders.clear();
        folders.filter((folder) => folder.selected).forEach((folder) => selectedVideoSourceFolders.add(folder.name));
        if (selectedVideoSourceFolders.size === 0 && folders[0]) selectedVideoSourceFolders.add(folders[0].name);
        renderVideoFolderTabs(folders);
        const query = selectedVideoFolder ? `?folder=${encodeURIComponent(selectedVideoFolder)}` : "";
        const response = await fetch(`/api/video-catalog${query}`, {cache: "no-store"});
        const videos = await readJson(response);
        if (!response.ok || !Array.isArray(videos)) throw new Error(videos.message || "读取视频目录失败");
        videoCatalogGrid.replaceChildren();
        const sourceLabel = [...selectedVideoSourceFolders].join(", ") || "-";
        videoCatalogStatus.textContent = `当前查看：${selectedVideoFolder || "video_ai"}，${videos.length} 个视频；生成来源：${sourceLabel}`;
        if (!videos.length) {
            videoCatalogGrid.append(emptyKnowledge("video_ai 目录中没有视频"));
            return;
        }
        videos.forEach((item) => {
            const card = document.createElement("article");
            card.className = "video-catalog-card";
            const video = document.createElement("video");
            video.controls = true;
            video.preload = "metadata";
            video.src = withVersion(item.videoUrl, item.updatedAt);
            const body = document.createElement("div");
            const title = document.createElement("strong");
            title.textContent = item.fileName;
            const meta = document.createElement("small");
            const duration = item.durationSeconds == null ? "等待 FFmpeg 读取时长" : `${Number(item.durationSeconds).toFixed(2)} 秒`;
            const resolution = item.width && item.height ? ` · ${item.width}×${item.height}` : "";
            meta.textContent = `${duration}${resolution} · ${(item.sizeBytes / 1024 / 1024).toFixed(2)} MB`;
            video.addEventListener("loadedmetadata", () => {
                const duration = Number.isFinite(video.duration) ? `${video.duration.toFixed(2)} s` : "metadata unavailable";
                const resolution = video.videoWidth && video.videoHeight ? ` · ${video.videoWidth}×${video.videoHeight}` : "";
                meta.textContent = `${duration}${resolution} · ${(item.sizeBytes / 1024 / 1024).toFixed(2)} MB`;
            }, {once: true});
            body.append(title, meta);
            card.append(video, body);
            videoCatalogGrid.append(card);
        });
    } catch (error) {
        videoCatalogStatus.textContent = error.message || "读取视频目录失败";
        videoCatalogGrid.replaceChildren(emptyKnowledge(videoCatalogStatus.textContent));
    }
}

function renderVideoFolderTabs(folders) {
    if (!videoFolderTabs) return;
    videoFolderTabs.replaceChildren();
    videoFolderTabs.hidden = folders.length === 0;
    folders.forEach((folder) => {
        const option = document.createElement("div");
        option.className = "video-folder-option";
        const checkbox = document.createElement("input");
        checkbox.type = "checkbox";
        checkbox.checked = selectedVideoSourceFolders.has(folder.name);
        checkbox.setAttribute("aria-label", `选择 ${folder.name} 作为视频生成来源`);
        checkbox.title = "选择或取消生成来源";
        checkbox.addEventListener("click", (event) => event.stopPropagation());
        checkbox.addEventListener("change", () => updateVideoSourceFolders(folder.name, checkbox.checked));
        const button = document.createElement("button");
        button.type = "button";
        button.className = "video-folder-tab";
        button.dataset.folder = folder.name;
        button.setAttribute("role", "tab");
        button.setAttribute("aria-selected", String(folder.name === selectedVideoFolder));
        button.textContent = folder.name === "__root__" ? "\u9ED8\u8BA4\u76EE\u5F55" : folder.name;
        button.title = `查看 ${folder.name}（${folder.videoCount} 个视频）`;
        button.addEventListener("click", () => selectVideoFolder(folder.name));
        option.append(checkbox, button);
        videoFolderTabs.append(option);
    });
}

async function selectVideoFolder(folder) {
    if (!folder) return;
    selectedVideoFolder = folder;
    await loadVideoCatalog();
}

async function updateVideoSourceFolders(folder, checked) {
    const previous = new Set(selectedVideoSourceFolders);
    if (checked) selectedVideoSourceFolders.add(folder);
    else selectedVideoSourceFolders.delete(folder);
    if (selectedVideoSourceFolders.size === 0) {
        selectedVideoSourceFolders.add(folder);
        await loadVideoCatalog();
        videoCatalogStatus.textContent = "至少保留一个视频生成来源文件夹";
        return;
    }
    videoCatalogStatus.textContent = "正在更新视频生成来源...";
    try {
        const response = await fetch("/api/video-catalog/folders/selection", {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({folders: [...selectedVideoSourceFolders]})
        });
        const payload = await readJson(response);
        if (!response.ok || !Array.isArray(payload)) throw new Error(payload.message || "更新视频生成来源失败");
        await loadVideoCatalog();
    } catch (error) {
        selectedVideoSourceFolders.clear();
        previous.forEach((item) => selectedVideoSourceFolders.add(item));
        await loadVideoCatalog();
        videoCatalogStatus.textContent = error.message || "更新视频生成来源失败";
    }
}

function openTaskDetail(job) {
    selectedTaskId = job.id;
    stepLogJobId = job.id;
    activateView("tasks", true);
    renderSelectedTask(job);
    loadStepLogs(job.id);
    historyBody.querySelectorAll("tr[data-history-job-id]").forEach((row) => {
        row.classList.toggle("is-selected", row.dataset.historyJobId === job.id);
    });
    resultSection.scrollIntoView({behavior: "smooth", block: "start"});
}

function renderSelectedTask(job) {
    if (!job || selectedTaskId !== job.id) return;
    resultSection.hidden = currentView !== "tasks";
    taskDetailPrompt.textContent = job.prompt || "未命名任务";
    taskDetailId.textContent = job.id;
    taskDetailStage.textContent = stageLabel(job.stage);
    taskDetailTime.textContent = formatTime(job.createdAt);
    resultGallery.replaceChildren();
    upsertResultCard(job, false);
    resultBadge.textContent = displayStatus(job);
    resultBadge.dataset.status = job.status;
}

function closeSelectedTask() {
    selectedTaskId = null;
    stepLogJobId = null;
    resultGallery.replaceChildren();
    historyBody.querySelectorAll("tr.is-selected").forEach((row) => row.classList.remove("is-selected"));
    activateView(currentView);
}

async function loadJobEvents(jobId, detailRow) {
    const cell = detailRow.querySelector("td");
    try {
        const events = await fetchJobEvents(jobId);
        const table = document.createElement("table");
        table.className = "step-table";
        table.innerHTML = "<thead><tr><th>时间</th><th>事件</th><th>阶段</th><th>说明</th><th>结构化结果</th></tr></thead>";
        const body = document.createElement("tbody");
        events.forEach((event) => {
            const row = document.createElement("tr");
            [formatTime(event.createdAt), event.eventType, stageLabel(event.stage), event.message]
                .forEach((value) => {
                    const dataCell = document.createElement("td");
                    dataCell.textContent = value || "-";
                    row.append(dataCell);
                });
            const resultCell = document.createElement("td");
            if (event.resultJson) {
                const details = document.createElement("details");
                const summary = document.createElement("summary");
                summary.textContent = "查看数据";
                const pre = document.createElement("pre");
                pre.textContent = prettyJson(event.resultJson);
                details.append(summary, pre);
                resultCell.append(details);
            } else {
                resultCell.textContent = "-";
            }
            row.append(resultCell);
            body.append(row);
        });
        table.append(body);
        cell.replaceChildren(table);
        detailRow.dataset.loaded = "true";
    } catch (error) {
        cell.textContent = error.message || "步骤记录读取失败";
    }
}

async function fetchJobEvents(jobId) {
    const response = await fetch(`/api/generations/${jobId}/events`, {cache: "no-store"});
    const events = await readJson(response);
    if (!response.ok || !Array.isArray(events)) {
        throw new Error(events.message || `步骤记录读取失败（HTTP ${response.status}）`);
    }
    return events;
}

async function loadStepLogs(jobId = stepLogJobId || currentJobId) {
    if (!jobId || stepLogLoading) {
        if (!jobId) {
            stepLogJob.textContent = "等待选择任务";
            stepLog.textContent = "创建任务后，这里会按顺序显示每一步的时间、阶段、事件和具体内容。";
        }
        return;
    }

    stepLogLoading = true;
    stepLogJobId = jobId;
    const requestedJobId = jobId;
    const shouldStickToBottom = stepLog.scrollHeight - stepLog.scrollTop - stepLog.clientHeight < 80;
    try {
        const events = await fetchJobEvents(requestedJobId);
        if (stepLogJobId !== requestedJobId) return;
        renderStepLogs(events);
        stepLogJob.textContent = `任务 ${requestedJobId}`;
        if (shouldStickToBottom) stepLog.scrollTop = stepLog.scrollHeight;
    } catch (error) {
        if (stepLogJobId === requestedJobId) {
            stepLog.textContent = `步骤日志读取失败：${error.message || error}`;
        }
    } finally {
        stepLogLoading = false;
    }
}

function renderStepLogs(events) {
    if (events.length === 0) {
        stepLog.textContent = "任务已创建，等待第一个步骤事件。";
        return;
    }

    const list = document.createElement("ol");
    list.className = "step-log-list";
    events.forEach((event, index) => {
        const item = document.createElement("li");
        item.className = "step-log-item";
        if (event.eventType?.includes("FAILED")) item.classList.add("is-error");
        if (event.eventType?.includes("COMPLETED")) item.classList.add("is-complete");

        const heading = document.createElement("div");
        heading.className = "step-log-item-heading";
        const sequence = document.createElement("strong");
        sequence.textContent = String(index + 1).padStart(2, "0");
        const stage = document.createElement("span");
        stage.textContent = stageLabel(event.stage);
        const time = document.createElement("time");
        time.textContent = formatTime(event.createdAt);
        heading.append(sequence, stage, time);

        const eventName = document.createElement("div");
        eventName.className = "step-log-event";
        eventName.textContent = eventLabel(event.eventType);
        const message = document.createElement("p");
        message.textContent = event.message || "该步骤未提供说明";
        item.append(heading, eventName, message);

        if (event.resultJson) {
            const details = document.createElement("details");
            const summary = document.createElement("summary");
            summary.textContent = "查看步骤数据";
            const pre = document.createElement("pre");
            pre.textContent = prettyJson(event.resultJson);
            details.append(summary, pre);
            item.append(details);
        }
        list.append(item);
    });
    stepLog.replaceChildren(list);
}

function eventLabel(eventType) {
    const labels = {
        JOB_CREATED: "任务创建",
        JOB_STARTED: "任务开始",
        STAGE: "阶段进度",
        PORTRAIT_PROMPT: "人物提示词",
        PORTRAIT_ATTEMPT: "人物候选图",
        ORIGINAL_IMAGE: "人物原图归档",
        CLOTHING_SELECTED: "服装选择",
        FASHION_ANALYSIS: "服装分析",
        RAG_RETRIEVAL: "RAG 知识检索",
        OUTFIT_ATTEMPT: "换装候选图",
        FINAL_IMAGE: "最终图片归档",
        JOB_COMPLETED: "任务完成",
        JOB_FAILED: "任务失败",
        JOB_CANCELLED: "任务停止",
        JOB_RESTARTED: "任务重启"
    };
    const label = labels[eventType] || eventType || "未知事件";
    return eventType && labels[eventType] ? `${label} · ${eventType}` : label;
}

async function cancelJob(jobId) {
    if (!jobId) return;
    stopCurrentJob.disabled = true;
    try {
        const response = await fetch(`/api/generations/${jobId}/cancel`, {method: "POST"});
        const job = await readJson(response);
        if (!response.ok) {
            throw new Error(job.message || `停止任务失败（HTTP ${response.status}）`);
        }
        clearTimeout(pollTimer);
        currentJobId = jobId;
        if (activeJobId === jobId) activeJobId = null;
        setLoading(false);
        setStopButton(false);
        setStartButton(true);
        renderJob(job);
        upsertHistoryRow(job, true);
        upsertResultCard(job, true);
        jobStatus.textContent = "已停止";
        progressMessage.textContent = job.message || "任务已手动停止";
        loadRuntimeLogs();
    } catch (error) {
        showError(error.message || "停止任务失败");
        setStopButton(Boolean(activeJobId));
    }
}

async function restartJob(jobId) {
    if (!jobId) {
        form.requestSubmit();
        return;
    }
    setStartButton(false);
    hideError();
    try {
        const response = await fetch(`/api/generations/${jobId}/restart`, {method: "POST"});
        const payload = await readJson(response);
        if (!response.ok) {
            throw new Error(payload.message || `启动任务失败（HTTP ${response.status}）`);
        }
        clearTimeout(pollTimer);
        resetView();
        currentJobId = payload.jobId;
        activeJobId = payload.jobId;
        loadStepLogs(payload.jobId);
        setLoading(true);
        setStopButton(true);
        setStartButton(false);
        await poll(payload.statusUrl);
    } catch (error) {
        showError(error.message || "启动任务失败");
        setStartButton(true);
    }
}

function prettyJson(value) {
    try {
        return JSON.stringify(JSON.parse(value), null, 2);
    } catch (error) {
        return value;
    }
}

function stageLabel(stage) {
    const labels = {
        ACCEPTED: "任务受理",
        AI_ENRICHING_PORTRAIT_PROMPT: "提示词扩写",
        AGENT1_GENERATING_PERSON: "人物生成",
        AI_VERIFYING_PORTRAIT: "人物质检",
        AI_REFINING_PORTRAIT_PROMPT: "人物提示词纠正",
        AGENT2_SELECTING_CLOTHING: "服装挑选",
        AI_ANALYZING_CLOTHING: "服装理解",
        RAG_RETRIEVING_FASHION_KNOWLEDGE: "RAG 经验检索",
        AGENT3_REPLACING_OUTFIT: "智能换装",
        AI_VERIFYING_OUTFIT: "换装质检",
        AI_REFINING_PROMPT: "换装提示词纠正",
        AGENT4_PRESENTING_RESULT: "结果呈现",
        COMPLETED: "已完成",
        FAILED: "失败",
        CANCELLED: "已停止"
    };
    return labels[stage] || stage || "-";
}

function upsertResultCard(job, prepend) {
    let card = resultGallery.querySelector(`[data-job-id="${job.id}"]`);
    if (!card) {
        card = resultTemplate.content.firstElementChild.cloneNode(true);
        card.dataset.jobId = job.id;
        if (prepend) {
            resultGallery.prepend(card);
        } else {
            resultGallery.append(card);
        }
    }

    card.classList.toggle("is-success", job.status === "SUCCESS");
    card.classList.toggle("is-failed", job.status === "FAILED");
    card.classList.toggle("is-cancelled", job.status === "CANCELLED");
    card.querySelector(".result-card-time").textContent = formatTime(job.createdAt);
    card.querySelector(".result-card-prompt").textContent = job.prompt || "未命名人物任务";
    card.querySelector(".result-card-status").textContent = displayStatus(job);
    const cardCancel = card.querySelector(".result-card-cancel");
    const cancellable = job.status === "RUNNING" || job.status === "QUEUED";
    cardCancel.hidden = !cancellable;
    cardCancel.onclick = cancellable ? () => cancelJob(job.id) : null;
    card.querySelector(".result-card-id").textContent = `任务 ID · ${job.id}`;
    card.querySelector(".result-card-message").textContent = job.reply || job.error || job.message || "任务处理中";

    setCardImage(
        card.querySelector(".result-original-image"),
        card.querySelector(".result-original-placeholder"),
        job.originalImageUrl,
        job.updatedAt);
    setCardImage(
        card.querySelector(".result-final-image"),
        card.querySelector(".result-final-placeholder"),
        job.finalImageUrl,
        job.updatedAt);
    setCardImage(
        card.querySelector(".result-clothing-image"),
        null,
        job.clothingPreviewUrl,
        job.updatedAt);

    const clothingCaption = card.querySelector(".result-clothing-caption");
    clothingCaption.textContent = job.clothingFileName
        ? `本次服装 · ${job.clothingFileName}`
        : "尚未选择服装";

    const finalLink = card.querySelector(".result-final-link");
    finalLink.hidden = !job.finalImageUrl;
    if (job.finalImageUrl) {
        finalLink.href = withVersion(job.finalImageUrl, job.updatedAt);
    }

    renderPortraitAnalysis(card, job.portraitPrompt);
    renderPortraitAttempts(card, job.portraitAttempts || [], job.updatedAt);
    renderFashionAnalysis(card, job.fashionAnalysis);
    renderAttempts(card, job.attempts || [], job.updatedAt);

    updateResultSummary();
}

function renderPortraitAnalysis(card, promptSpec) {
    const section = card.querySelector(".portrait-analysis");
    section.hidden = !promptSpec;
    if (!promptSpec) return;

    card.querySelector(".portrait-analysis-mode").textContent = promptSpec.mode === "MULTIMODAL_AI"
        ? "LangChain4j AI 扩写"
        : "规则降级扩写";
    card.querySelector(".portrait-analysis-summary").textContent =
        `原始描述：${promptSpec.originalDescription || "未提供"}`;
    card.querySelector(".portrait-appearance").textContent = promptSpec.appearance || "未补充";
    card.querySelector(".portrait-environment").textContent =
        `${promptSpec.environment || "未补充"}；${promptSpec.lighting || "未补充光线"}`;
    card.querySelector(".portrait-pose").textContent = promptSpec.bodyAndPose || "未补充";
    card.querySelector(".portrait-composition").textContent =
        `${promptSpec.composition || "未补充"}；${promptSpec.visualStyle || "未补充风格"}`;
    card.querySelector(".portrait-generation-prompt").textContent = promptSpec.generationPrompt || "";
}

function renderPortraitAttempts(card, attempts, updatedAt) {
    const section = card.querySelector(".portrait-history");
    const list = card.querySelector(".portrait-attempt-list");
    section.hidden = attempts.length === 0;
    list.replaceChildren();
    if (attempts.length === 0) return;

    card.querySelector(".portrait-attempt-count").textContent = `${attempts.length} 张候选图`;
    attempts.forEach((attempt) => {
        const row = document.createElement("article");
        row.className = "attempt-row";
        if (attempt.selected) row.classList.add("is-selected");

        const image = document.createElement("img");
        image.src = withVersion(attempt.imageUrl, updatedAt);
        image.alt = `第 ${attempt.attemptNumber} 张人物候选图`;

        const body = document.createElement("div");
        body.className = "attempt-body";
        const heading = document.createElement("div");
        heading.className = "attempt-heading";
        const title = document.createElement("strong");
        title.textContent = `第 ${attempt.attemptNumber} 张人物底图`;
        const state = document.createElement("span");
        const report = attempt.qualityReport;
        const qualityState = !report?.evaluated
            ? "质检失败 · 已跳过"
            : report.passed ? "质检通过" : "质检未通过";
        state.textContent = attempt.selected ? `最终采用 · ${qualityState}` : qualityState;
        heading.append(title, state);
        body.append(heading);

        if (report?.evaluated) {
            const scores = document.createElement("div");
            scores.className = "quality-scores";
            scores.append(
                scoreItem("综合", report.overallScore),
                scoreItem("一致", report.promptAlignmentScore),
                scoreItem("人体", report.anatomyScore),
                scoreItem("画质", report.imageQualityScore));
            const summary = document.createElement("p");
            summary.textContent = report.summary || "人物质检完成";
            body.append(scores, summary);
            if (report.issues?.length) {
                const issues = document.createElement("p");
                issues.className = "missing-elements";
                issues.textContent = `问题：${report.issues.join("、")}`;
                body.append(issues);
            }
        } else {
            const summary = document.createElement("p");
            summary.textContent = report?.summary || "未执行多模态人物质检";
            body.append(summary);
        }

        const details = document.createElement("details");
        const detailsTitle = document.createElement("summary");
        detailsTitle.textContent = "查看本轮人物生成提示词";
        const prompt = document.createElement("pre");
        prompt.textContent = attempt.prompt || "";
        details.append(detailsTitle, prompt);
        body.append(details);
        row.append(image, body);
        list.append(row);
    });
}

function renderFashionAnalysis(card, analysis) {
    const section = card.querySelector(".ai-analysis");
    section.hidden = !analysis;
    if (!analysis) return;

    card.querySelector(".analysis-mode").textContent = analysis.mode === "MULTIMODAL_AI"
        ? "多模态 AI"
        : "规则降级";
    card.querySelector(".analysis-summary").textContent = analysis.summary || "暂无分析摘要";
    card.querySelector(".analysis-head-accessories").textContent = formatList(
        analysis.headAccessories, "未识别到明确头饰");
    card.querySelector(".analysis-must-transfer").textContent = formatList(
        analysis.mustTransfer, "暂无明确元素");
    card.querySelector(".analysis-prompt").textContent = analysis.replacementPrompt || "";
}

function renderAttempts(card, attempts, updatedAt) {
    const section = card.querySelector(".attempt-history");
    const list = card.querySelector(".attempt-list");
    section.hidden = attempts.length === 0;
    list.replaceChildren();
    if (attempts.length === 0) return;

    card.querySelector(".attempt-count").textContent = `${attempts.length} 次尝试`;
    attempts.forEach((attempt) => {
        const row = document.createElement("article");
        row.className = "attempt-row";
        if (attempt.selected) row.classList.add("is-selected");

        const image = document.createElement("img");
        image.src = withVersion(attempt.imageUrl, updatedAt);
        image.alt = `第 ${attempt.attemptNumber} 次换装结果`;

        const body = document.createElement("div");
        body.className = "attempt-body";
        const heading = document.createElement("div");
        heading.className = "attempt-heading";
        const title = document.createElement("strong");
        title.textContent = `第 ${attempt.attemptNumber} 次生成`;
        const state = document.createElement("span");
        const qualityState = !attempt.qualityReport?.evaluated
            ? "质检失败 · 已跳过"
            : attempt.qualityReport.passed ? "质检通过" : "质检未通过";
        state.textContent = attempt.selected ? `最终采用 · ${qualityState}` : qualityState;
        heading.append(title, state);
        body.append(heading);

        const report = attempt.qualityReport;
        if (report?.evaluated) {
            const scores = document.createElement("div");
            scores.className = "quality-scores";
            scores.append(
                scoreItem("综合", report.overallScore),
                scoreItem("服装", report.clothingMatchScore),
                scoreItem("头饰", report.headAccessoryMatchScore),
                scoreItem("身份", report.identityPreservationScore));
            const summary = document.createElement("p");
            summary.textContent = report.summary || "质检完成";
            body.append(scores, summary);
            if (report.missingElements?.length) {
                const missing = document.createElement("p");
                missing.className = "missing-elements";
                missing.textContent = `遗漏：${report.missingElements.join("、")}`;
                body.append(missing);
            }
        } else {
            const summary = document.createElement("p");
            summary.textContent = report?.summary || "未执行多模态质检";
            body.append(summary);
        }

        const details = document.createElement("details");
        const detailsTitle = document.createElement("summary");
        detailsTitle.textContent = "查看本轮提示词";
        const prompt = document.createElement("pre");
        prompt.textContent = attempt.prompt || "";
        details.append(detailsTitle, prompt);
        body.append(details);
        row.append(image, body);
        list.append(row);
    });
}

function scoreItem(label, value) {
    const item = document.createElement("span");
    item.textContent = `${label} ${value}`;
    return item;
}

function formatList(values, emptyText) {
    return Array.isArray(values) && values.length ? values.join("、") : emptyText;
}

function setCardImage(image, placeholder, url, updatedAt) {
    image.hidden = !url;
    if (placeholder) {
        placeholder.hidden = Boolean(url);
    }
    if (url) {
        image.src = withVersion(url, updatedAt);
    }
}

function withVersion(url, updatedAt) {
    const separator = url.includes("?") ? "&" : "?";
    return `${url}${separator}v=${encodeURIComponent(updatedAt || Date.now())}`;
}

function openImagePreview(url, alt, updatedAt) {
    if (!url) return;
    imagePreview.src = withVersion(url, updatedAt);
    imagePreview.alt = alt || "图片预览";
    imagePreviewCaption.textContent = alt || "图片预览";
    imagePreviewModal.hidden = false;
    closeImagePreview.focus();
}

function closeImagePreviewModal() {
    imagePreviewModal.hidden = true;
    imagePreview.removeAttribute("src");
}

function displayStatus(job) {
    if (job.status === "SUCCESS") return "已完成";
    if (job.status === "FAILED") return "失败";
    if (job.status === "CANCELLED") return "已停止";
    if (job.status === "QUEUED") return "排队中";
    return "处理中";
}

function formatTime(value) {
    if (!value) return "";
    return new Date(value).toLocaleString("zh-CN", {hour12: false});
}

function updateResultSummary() {
    const count = resultGallery.children.length;
    emptyResult.hidden = count > 0;
    resultBadge.textContent = count > 0 ? `${count} 组结果` : "尚未生成";
}

function previewComfyUiVideoImages() {
    if (!comfyUiVideoImagePreview) return;
    const incoming = [...(comfyUiVideoImages.files || [])];
    const merged = [...selectedComfyUiFiles];
    incoming.forEach((file) => {
        const exists = merged.some((current) => current.name === file.name && current.size === file.size && current.lastModified === file.lastModified);
        if (!exists) merged.push(file);
    });
    if (merged.length > 9) {
        comfyUiVideoFormMessage.textContent = "最多选择 9 张图片";
        selectedComfyUiFiles = merged.slice(0, 9);
    } else {
        selectedComfyUiFiles = merged;
    }
    comfyUiVideoImages.value = "";
    comfyUiVideoImagePreview.replaceChildren();
    const count = document.createElement("span");
    count.className = "standalone-video-image-count";
    count.textContent = `已选择 ${selectedComfyUiFiles.length} / 9 张图片`;
    comfyUiVideoImagePreview.append(count);
    selectedComfyUiFiles.forEach((file, index) => {
        const item = document.createElement("span");
        item.className = "standalone-video-image-item";
        const image = document.createElement("img");
        image.alt = file.name;
        image.title = file.name;
        image.src = URL.createObjectURL(file);
        image.onload = () => URL.revokeObjectURL(image.src);
        const name = document.createElement("small");
        name.textContent = `${index + 1}. ${file.name}`;
        name.title = file.name;
        item.append(image, name);
        comfyUiVideoImagePreview.append(item);
    });
}

function readFileAsDataUrl(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = () => reject(new Error(`读取图片失败: ${file.name}`));
        reader.readAsDataURL(file);
    });
}

async function submitComfyUiVideo(event) {
    event.preventDefault();
    const prompt = comfyUiVideoPrompt.value.trim();
    const files = [...selectedComfyUiFiles];
    if (!prompt) {
        comfyUiVideoFormMessage.textContent = "请输入视频描述词";
        return;
    }
    if (!files.length || files.length > 9) {
        comfyUiVideoFormMessage.textContent = "请选择 1-9 张图片";
        return;
    }
    comfyUiVideoSubmit.disabled = true;
    comfyUiVideoFormMessage.textContent = "正在读取图片并提交任务...";
    try {
        const images = await Promise.all(files.map(readFileAsDataUrl));
        const response = await fetch("/api/comfyui-video-generations", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                prompt,
                duration: Number(comfyUiVideoDuration.value),
                resolution: comfyUiVideoResolution.value,
                images
            })
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `提交失败 (HTTP ${response.status})`);
        comfyUiVideoFormMessage.textContent = "任务已进入队列，生成过程会显示在下方记录中。";
        comfyUiVideoForm.reset();
        selectedComfyUiFiles = [];
        comfyUiVideoImagePreview.replaceChildren();
        await loadComfyUiVideoHistory();
    } catch (error) {
        comfyUiVideoFormMessage.textContent = error.message || "提交视频任务失败";
    } finally {
        comfyUiVideoSubmit.disabled = false;
    }
}

async function previewStoryPlan(event) {
    event.preventDefault();
    const story = storyPlannerInput.value.trim();
    if (!story) return;
    storyPlannerSubmit.disabled = true;
    if (storyPlanSave) storyPlanSave.disabled = true;
    storyPlannerMessage.textContent = "正在让大模型拆分剧情，请稍候...";
    storyPlanResult.hidden = true;
    try {
        const response = await fetch("/api/comfyui-video-plans/preview", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({story})
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `剧情拆分失败 (HTTP ${response.status})`);
        currentStoryPlan = payload;
        renderStoryPlan(payload);
        if (storyPlanSave) storyPlanSave.disabled = false;
        storyPlannerMessage.textContent = "已生成镜头计划。当前只保存页面预览，没有提交视频接口。";
    } catch (error) {
        storyPlannerMessage.textContent = error.message || "剧情拆分失败";
    } finally {
        storyPlannerSubmit.disabled = false;
    }
}

function saveCurrentStoryPlan() {
    if (!storyPlanResult || storyPlanResult.hidden || !currentStoryPlan) return;
    const shots = [...storyPlanResult.querySelectorAll(".story-shot-card")].map((card, index) => {
        const value = (selector) => card.querySelector(selector)?.value?.trim() || "";
        const imageInput = card.querySelector("input[type=file]");
        return {
            sequence: Number(card.dataset.sequence || index + 1),
            duration: Math.max(1, Math.min(10, Number(value("[data-story-field='duration']")) || 5)),
            interfaceType: value("[data-story-field='interfaceType']") || "TEXT_TO_VIDEO_IMAGE",
            prompt: value("[data-story-field='prompt']"),
            environment: value("[data-story-field='environment']"),
            firstFrameSource: value("[data-story-field='firstFrameSource']"),
            lastFrameSource: value("[data-story-field='lastFrameSource']"),
            characterImageRequired: card.dataset.characterImageRequired === "true",
            characterImageHint: value("[data-story-field='characterImageHint']"),
            dialogue: value("[data-story-field='dialogue']"),
            characterImageName: imageInput?.files?.[0]?.name || "",
            characterImageSelected: Boolean(imageInput?.files?.length),
            characterImageFile: imageInput?.files?.[0] || null
        };
    });
    currentStoryPlan = {...currentStoryPlan, shots};
    storyPlannerMessage.textContent = "当前镜头计划已保存到页面状态；尚未提交视频接口。";
    storyPlanSave.disabled = true;
}

function renderStoryPlan(plan) {
    storyPlanResult.replaceChildren();
    storyPlanResult.hidden = false;
    const heading = document.createElement("div");
    heading.className = "story-plan-heading";
    const title = document.createElement("h4");
    title.textContent = `共 ${plan.shots?.length || 0} 个镜头`;
    const notes = document.createElement("p");
    notes.textContent = plan.planningNotes || "请检查每个镜头后再提交。";
    heading.append(title, notes);
    storyPlanResult.append(heading);
    (plan.shots || []).forEach((shot, index) => {
        const card = document.createElement("article");
        card.className = "story-shot-card";
        card.dataset.sequence = shot.sequence || index + 1;
        card.dataset.characterImageRequired = String(Boolean(shot.characterImageRequired));
        const cardHeading = document.createElement("div");
        cardHeading.className = "story-shot-heading";
        const label = document.createElement("strong");
        label.textContent = `镜头 ${shot.sequence || index + 1}`;
        const typeLabel = document.createElement("label");
        typeLabel.className = "story-shot-type";
        typeLabel.textContent = "接口类型";
        const type = document.createElement("select");
        type.dataset.storyField = "interfaceType";
        [["TEXT_TO_VIDEO_IMAGE", "普通图生视频"], ["FIRST_LAST_FRAME", "首尾帧视频"]].forEach(([value, text]) => {
            const option = document.createElement("option");
            option.value = value;
            option.textContent = text;
            option.selected = value === shot.interfaceType;
            type.append(option);
        });
        typeLabel.append(type);
        cardHeading.append(label, typeLabel);

        const fields = document.createElement("div");
        fields.className = "story-shot-fields";
        const duration = storyShotInput("时长（秒）", "number", shot.duration, 1, 10);
        duration.querySelector("input").dataset.storyField = "duration";
        const prompt = storyShotTextarea("镜头提示词", shot.prompt);
        prompt.querySelector("textarea").dataset.storyField = "prompt";
        const environment = storyShotTextarea("环境细节", shot.environment || "");
        environment.querySelector("textarea").dataset.storyField = "environment";
        const first = storyShotInput("首帧来源", "text", shot.firstFrameSource);
        first.querySelector("input").dataset.storyField = "firstFrameSource";
        const last = storyShotInput("尾帧来源", "text", shot.lastFrameSource);
        last.querySelector("input").dataset.storyField = "lastFrameSource";
        const character = storyShotInput("人物图片要求", "text", shot.characterImageHint || (shot.characterImageRequired ? "需要选择人物图片" : "无需人物图片"));
        character.querySelector("input").dataset.storyField = "characterImageHint";
        const dialogue = storyShotTextarea("对白与语气（可修改）", shot.dialogue || "");
        dialogue.querySelector("textarea").dataset.storyField = "dialogue";
        fields.append(duration, prompt, environment, first, last, character, dialogue);
        if (shot.characters?.length) {
            const characterMap = document.createElement("p");
            characterMap.className = "story-character-map";
            characterMap.textContent = `人物参考图：${shot.characters.join("；")}（可多选上传，顺序对应图2、图3）`;
            fields.append(characterMap);
        }
        if (shot.characterImageRequired || shot.firstFrameSource === "USER_IMAGE") {
            const imageLabel = document.createElement("label");
            imageLabel.textContent = "选择本镜头人物图片";
            const imageInput = document.createElement("input");
            imageInput.type = "file";
            imageInput.multiple = true;
            imageInput.accept = "image/png,image/jpeg,image/webp";
            imageInput.dataset.shotSequence = shot.sequence || index + 1;
            const imageHint = document.createElement("small");
            imageHint.className = "story-shot-image-name";
            imageHint.textContent = "尚未选择";
            imageInput.addEventListener("change", () => {
                imageHint.textContent = imageInput.files?.[0]?.name || "尚未选择";
                if (storyPlanSave) storyPlanSave.disabled = false;
            });
            imageLabel.append(imageInput, imageHint);
            fields.append(imageLabel);
        }
        [type, ...fields.querySelectorAll("input, textarea, select")].forEach((control) => {
            control.addEventListener("input", () => { if (storyPlanSave) storyPlanSave.disabled = false; });
            control.addEventListener("change", () => { if (storyPlanSave) storyPlanSave.disabled = false; });
        });
        card.append(cardHeading, fields);
        storyPlanResult.append(card);
    });
}

function storyShotInput(labelText, type, value, min, max) {
    const label = document.createElement("label");
    label.textContent = labelText;
    const input = document.createElement("input");
    input.type = type;
    input.value = value ?? "";
    if (min != null) input.min = min;
    if (max != null) input.max = max;
    label.append(input);
    return label;
}

function storyShotTextarea(labelText, value) {
    const label = document.createElement("label");
    label.textContent = labelText;
    const textarea = document.createElement("textarea");
    textarea.rows = 4;
    textarea.value = value || "";
    label.append(textarea);
    return label;
}

async function analyzeStoryReplication(event) {
    event.preventDefault();
    const file = storyReplicationVideo?.files?.[0];
    if (!file) return;
    storyReplicationSubmit.disabled = true;
    storyReplicationMessage.textContent = "正在上传并抽取关键帧，请稍候...";
    try {
        const body = new FormData();
        body.append("video", file);
        const response = await fetch("/api/story-video-replications/analyze", {method: "POST", body});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `视频分析提交失败 (HTTP ${response.status})`);
        storyReplicationTask = payload;
        storyReplicationMessage.textContent = "任务已创建，正在后台分析；可稍后刷新状态。";
        renderStoryReplication(payload);
        pollStoryReplication(payload.id);
    } catch (error) {
        storyReplicationMessage.textContent = error.message || "视频分析失败";
    } finally {
        storyReplicationSubmit.disabled = false;
    }
}

async function resolveStoryReplicationUrl(event) {
    event.preventDefault();
    const address = storyReplicationUrl.value.trim();
    if (!address) {
        storyReplicationMessage.textContent = "请输入视频地址";
        return;
    }
    storyReplicationUrlSubmit.disabled = true;
    storyReplicationMessage.textContent = "正在解析地址并下载到 E:\\AI影视复刻，请稍候...";
    try {
        const response = await fetch("/api/story-video-replications/resolve-url", {
            method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({address})
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `地址解析失败 (HTTP ${response.status})`);
        storyReplicationTask = payload;
        renderStoryReplication(payload);
        pollStoryReplication(payload.id);
    } catch (error) {
        storyReplicationMessage.textContent = error.message || "地址解析失败";
    } finally {
        storyReplicationUrlSubmit.disabled = false;
    }
}

async function pollStoryReplication(id) {
    for (let i = 0; i < 900; i++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const response = await fetch(`/api/story-video-replications/${id}`, {cache: "no-store"});
            const payload = await readJson(response);
            if (!response.ok) throw new Error(payload.message || "读取视频分析状态失败");
            storyReplicationTask = payload;
            renderStoryReplication(payload);
            if (payload.status === "DOWNLOADED") {
                storyReplicationMessage.textContent = "视频下载完成，请点击分析视频";
                return;
            }
            if (["READY", "FAILED", "SUCCESS"].includes(payload.status)) return;
            storyReplicationMessage.textContent = payload.message || payload.status;
        } catch (error) {
            storyReplicationMessage.textContent = error.message || "读取视频分析状态失败";
            return;
        }
    }
}

function renderStoryReplication(task) {
    if (!storyReplicationResult) return;
    storyReplicationResult.replaceChildren();
    storyReplicationResult.hidden = false;
    const heading = document.createElement("div");
    heading.className = "story-plan-heading";
    const title = document.createElement("h4");
    title.textContent = `${task.sourceFileName || "视频"} · ${task.status}`;
    const note = document.createElement("p");
    note.textContent = task.speechSummary || task.message || "";
    heading.append(title, note);
    storyReplicationResult.append(heading);
    if (task.error) {
        const error = document.createElement("p"); error.className = "form-error"; error.textContent = task.error; storyReplicationResult.append(error); return;
    }
    if (task.status === "DOWNLOADED") {
        const analyzeButton = document.createElement("button");
        analyzeButton.type = "button";
        analyzeButton.textContent = "分析视频";
        analyzeButton.addEventListener("click", async () => {
            analyzeButton.disabled = true;
            storyReplicationMessage.textContent = "正在分析已下载视频，请稍候...";
            try {
                const response = await fetch(`/api/story-video-replications/${task.id}/analyze`, {method: "POST"});
                const payload = await readJson(response);
                if (!response.ok) throw new Error(payload.message || "启动视频分析失败");
                storyReplicationTask = payload;
                renderStoryReplication(payload);
                pollStoryReplication(task.id);
            } catch (error) {
                storyReplicationMessage.textContent = error.message || "启动视频分析失败";
                analyzeButton.disabled = false;
            }
        });
        storyReplicationResult.append(analyzeButton);
        return;
    }
    if (!task.plan?.shots?.length) return;
    const form = document.createElement("div"); form.className = "story-replication-shot-list";
    task.plan.shots.forEach((shot, index) => {
        const card = document.createElement("article"); card.className = "story-shot-card"; card.dataset.sequence = shot.sequence || index + 1;
        const heading = document.createElement("div"); heading.className = "story-shot-heading";
        const label = document.createElement("strong"); label.textContent = `镜头 ${shot.sequence || index + 1}`;
        const duration = document.createElement("input"); duration.type = "number"; duration.min = 1; duration.max = 10; duration.value = shot.duration || 5; duration.dataset.field = "duration";
        const durationLabel = document.createElement("label"); durationLabel.className = "story-shot-duration"; durationLabel.textContent = "时长"; durationLabel.append(duration);
        const type = document.createElement("select"); type.dataset.field = "interfaceType";
        [["TEXT_TO_VIDEO_IMAGE", "普通图生视频"], ["FIRST_LAST_FRAME", "首尾帧视频"]].forEach(([value, text]) => { const o = document.createElement("option"); o.value = value; o.textContent = text; o.selected = value === shot.interfaceType; type.append(o); });
        const typeLabel = document.createElement("label"); typeLabel.className = "story-shot-type"; typeLabel.textContent = "接口"; typeLabel.append(type);
        heading.append(label, durationLabel, typeLabel); card.append(heading);
        const prompt = document.createElement("textarea"); prompt.rows = 4; prompt.value = stripStoryPromptSections(shot.prompt || ""); prompt.dataset.field = "prompt";
        const promptLabel = document.createElement("label"); promptLabel.textContent = "镜头提示词"; promptLabel.append(prompt);
        const environment = document.createElement("textarea"); environment.rows = 2; environment.value = shot.environment || ""; environment.placeholder = "地点、时间、天气、背景、道具、光线"; environment.dataset.field = "environment";
        const environmentLabel = document.createElement("label"); environmentLabel.textContent = "环境细节"; environmentLabel.append(environment);
        const grid = document.createElement("div"); grid.className = "story-shot-fields"; grid.append(promptLabel, environmentLabel);
        const dialogueEditor = buildDialogueEditor(shot.dialogueLines, shot.dialogue);
        grid.append(dialogueEditor);
        if (shot.characters?.length) {
            const characterNote = document.createElement("p"); characterNote.className = "story-character-map"; characterNote.textContent = `人物参考：${shot.characters.join("；")}（请选择对应图片，提交时按图2、图3顺序传入）`; grid.append(characterNote);
        }
        if (shot.characterImageRequired || shot.firstFrameSource === "USER_IMAGE") {
            const image = document.createElement("input"); image.type = "file"; image.multiple = true; image.accept = "image/png,image/jpeg,image/webp"; image.dataset.field = "image";
            const imageLabel = document.createElement("label"); imageLabel.textContent = shot.characterImageHint || "选择本镜头人物图片（可多选，顺序对应图2、图3）"; imageLabel.append(image); grid.append(imageLabel);
        }
        if (shot.interfaceType === "FIRST_LAST_FRAME") {
            const last = document.createElement("input"); last.type = "file"; last.accept = "image/png,image/jpeg,image/webp"; last.dataset.field = "lastFrame";
            const lastLabel = document.createElement("label"); lastLabel.textContent = "可选尾帧图片（留空则使用上一镜头最后一帧）"; lastLabel.append(last); grid.append(lastLabel);
        }
        card.append(grid); form.append(card);
    });
    storyReplicationResult.append(form);
    if (task.status === "READY") {
        const execute = document.createElement("button"); execute.type = "button"; execute.textContent = "确认镜头并开始生成";
        execute.addEventListener("click", () => executeStoryReplication(task.id, form, execute));
        storyReplicationResult.append(execute);
    }
    if (task.finalVideoUrl) {
        const link = document.createElement("a"); link.href = task.finalVideoUrl; link.target = "_blank"; link.textContent = "打开最终视频"; storyReplicationResult.append(link);
    }
}

async function executeStoryReplication(id, form, button) {
    button.disabled = true;
    storyReplicationMessage.textContent = "正在确认镜头并排队生成，后续镜头会等待前一镜头完成...";
    try {
        const shots = [];
        for (const card of form.querySelectorAll(".story-shot-card")) {
            const get = name => card.querySelector(`[data-field='${name}']`);
            const imageInput = get("image");
            const lastFrame = get("lastFrame")?.files?.[0];
            const imageFiles = imageInput ? [...imageInput.files] : [];
            const dialogue = collectDialogueLines(card);
            const environment = get("environment")?.value?.trim() || "";
            const characterFiles = imageFiles.map((file, index) => `图${index + 2}：${file.name}`);
            const prompt = composeStoryPrompt(get("prompt")?.value?.trim() || "", environment, characterFiles, dialogue);
            shots.push({sequence: Number(card.dataset.sequence), duration: Math.max(1, Math.min(10, Number(get("duration")?.value) || 5)), interfaceType: get("interfaceType")?.value,
                prompt, resolution: "768p竖", firstFrame: null, lastFrame: lastFrame ? await readFileAsDataUrl(lastFrame) : null,
                images: await Promise.all(imageFiles.map(readFileAsDataUrl))});
        }
        const response = await fetch(`/api/story-video-replications/${id}/execute`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({shots})});
        const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "提交故事视频失败");
        storyReplicationTask = payload; renderStoryReplication(payload); pollStoryReplication(id);
    } catch (error) { storyReplicationMessage.textContent = error.message || "提交故事视频失败"; button.disabled = false; }
}

function composeStoryPrompt(prompt, environment, characters, dialogue) {
    let result = stripStoryPromptSections(prompt);
    if (environment && !result.includes(environment)) result += `\n环境细节：${environment}`;
    if (characters?.length) result += `\n人物参考图映射：${characters.join("；")}`;
    if (dialogue && !result.includes(dialogue)) result += `\n对白与语气：${dialogue}`;
    return result.trim();
}

function stripStoryPromptSections(prompt) {
    return String(prompt || "").split(/\n(?:环境细节|人物参考图映射|对白与语气)：/)[0].trim();
}

function buildDialogueEditor(lines, fallback) {
    const wrapper = document.createElement("section");
    wrapper.className = "story-dialogue-editor";
    const heading = document.createElement("div"); heading.className = "story-dialogue-heading";
    const title = document.createElement("strong"); title.textContent = "对白与语气";
    const add = document.createElement("button"); add.type = "button"; add.textContent = "添加对白";
    heading.append(title, add); wrapper.append(heading);
    const list = document.createElement("div"); list.className = "story-dialogue-list"; wrapper.append(list);
    const initial = Array.isArray(lines) && lines.length ? lines : (fallback ? [{speaker: "人物", text: fallback, tone: ""}] : []);
    initial.forEach(line => addDialogueRow(list, line));
    if (!initial.length) addDialogueRow(list, {speaker: "", text: "", tone: ""});
    add.addEventListener("click", () => addDialogueRow(list, {speaker: "", text: "", tone: ""}));
    return wrapper;
}

function addDialogueRow(list, line) {
    const row = document.createElement("div"); row.className = "story-dialogue-row";
    const speaker = document.createElement("input"); speaker.type = "text"; speaker.placeholder = "说话人"; speaker.value = line.speaker || ""; speaker.dataset.dialogueField = "speaker";
    const text = document.createElement("input"); text.type = "text"; text.placeholder = "说话内容"; text.value = line.text || ""; text.dataset.dialogueField = "text";
    const tone = document.createElement("input"); tone.type = "text"; tone.placeholder = "语气/语速/停顿"; tone.value = line.tone || ""; tone.dataset.dialogueField = "tone";
    const remove = document.createElement("button"); remove.type = "button"; remove.textContent = "删除"; remove.addEventListener("click", () => row.remove());
    row.append(speaker, text, tone, remove); list.append(row);
}

function collectDialogueLines(card) {
    return [...card.querySelectorAll(".story-dialogue-row")].map(row => {
        const value = name => row.querySelector(`[data-dialogue-field='${name}']`)?.value?.trim() || "";
        const speaker = value("speaker"), text = value("text"), tone = value("tone");
        if (!text) return "";
        return `${speaker || "人物"}：${text}${tone ? `（${tone}）` : ""}`;
    }).filter(Boolean).join("；");
}

async function loadComfyUiVideoHistory() {
    const containers = [comfyUiVideoHistory, canvasVideoHistory].filter(Boolean);
    if (!containers.length) return;
    try {
        const response = await fetch("/api/comfyui-video-generations", {cache: "no-store"});
        const payload = await readJson(response);
        if (!response.ok || !Array.isArray(payload)) throw new Error(payload.message || "读取视频记录失败");
        containers.forEach(container => renderComfyUiVideoHistoryInto(container, payload));
        [comfyUiVideoHistoryMessage, canvasVideoHistoryMessage].filter(Boolean).forEach(message => { message.textContent = payload.length ? `共 ${payload.length} 条视频生成记录` : "暂无视频生成记录"; });
    } catch (error) {
        [comfyUiVideoHistoryMessage, canvasVideoHistoryMessage].filter(Boolean).forEach(message => { message.textContent = error.message || "读取视频记录失败"; });
    }
}

function renderComfyUiVideoHistory(items) {
    if (comfyUiVideoHistory) renderComfyUiVideoHistoryInto(comfyUiVideoHistory, items);
    if (canvasVideoHistory) renderComfyUiVideoHistoryInto(canvasVideoHistory, items);
}

function renderComfyUiVideoHistoryInto(container, items) {
    container.replaceChildren();
    if (!items.length) {
        const empty = document.createElement("p");
        empty.className = "knowledge-empty";
        empty.textContent = "提交描述词和图片后，任务会显示在这里。";
        container.append(empty);
        return;
    }
    items.forEach((item) => {
        const card = document.createElement("article");
        card.className = "standalone-video-history-card";
        const heading = document.createElement("div");
        heading.className = "standalone-video-history-heading";
        const title = document.createElement("strong");
        title.textContent = item.prompt || "未命名视频任务";
        const status = document.createElement("span");
        status.className = `standalone-video-status ${String(item.status || "").toLowerCase()}`;
        status.textContent = comfyUiVideoStatusLabel(item.status);
        heading.append(title, status);
        const meta = document.createElement("p");
        meta.textContent = `${formatTime(item.createdAt)} · ${item.duration}s · ${item.resolution} · ${item.imageCount} 张参考图`;
        card.append(heading, meta);
        const message = document.createElement("p");
        message.textContent = item.error || item.message || "等待处理";
        card.append(message);
        if (item.status === "SUCCESS" && item.finalVideoUrl) {
            const video = document.createElement("video");
            video.controls = true;
            video.preload = "metadata";
            video.src = withVersion(item.finalVideoUrl, item.updatedAt);
            card.append(video);
        }
        const actions = document.createElement("div");
        actions.className = "standalone-video-history-actions";
        if (item.status === "SUCCESS" && item.finalVideoUrl) {
            const folder = document.createElement("button");
            folder.type = "button";
            folder.textContent = "打开文件夹";
            folder.addEventListener("click", () => openComfyUiVideoFolder(item.id));
            actions.append(folder);
        }
        if (["SUCCESS", "FAILED"].includes(item.status)) {
            const remove = document.createElement("button");
            remove.type = "button";
            remove.className = "danger-button";
            remove.textContent = "删除";
            remove.addEventListener("click", () => deleteComfyUiVideo(item.id, remove));
            actions.append(remove);
        }
        card.append(actions);
        container.append(card);
    });
}

function comfyUiVideoStatusLabel(status) {
    const labels = {QUEUED: "排队中", SUBMITTING: "提交中", RUNNING: "生成中", DOWNLOADING: "下载中", SUCCESS: "已完成", FAILED: "失败"};
    return labels[status] || status || "未知";
}

async function openComfyUiVideoFolder(id) {
    try {
        const response = await fetch(`/api/comfyui-video-generations/${id}/open-folder`, {method: "POST"});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "打开文件夹失败");
        [comfyUiVideoHistoryMessage, canvasVideoHistoryMessage].filter(Boolean).forEach(message => { message.textContent = `已打开: ${payload.folder}`; });
    } catch (error) { [comfyUiVideoHistoryMessage, canvasVideoHistoryMessage].filter(Boolean).forEach(message => { message.textContent = error.message || "打开文件夹失败"; }); }
}

async function deleteComfyUiVideo(id, button) {
    if (!window.confirm("确定删除这条视频生成记录和本地文件吗？")) return;
    button.disabled = true;
    try {
        const response = await fetch(`/api/comfyui-video-generations/${id}`, {method: "DELETE"});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "删除失败");
        await loadComfyUiVideoHistory();
    } catch (error) {
        [comfyUiVideoHistoryMessage, canvasVideoHistoryMessage].filter(Boolean).forEach(message => { message.textContent = error.message || "删除失败"; });
        button.disabled = false;
    }
}

async function submitShortDramaDirector(event) {
    event.preventDefault();
    const text = shortDramaText?.value?.trim() || "";
    const file = shortDramaFile?.files?.[0];
    if (!text && !file) { if (shortDramaMessage) shortDramaMessage.textContent = "请输入创作内容或选择一个文件"; return; }
    if (shortDramaSubmit) shortDramaSubmit.disabled = true;
    if (shortDramaMessage) shortDramaMessage.textContent = "短剧导演任务已提交，正在调用千问…";
    try {
        const data = new FormData();
        data.append("mode", shortDramaMode?.value || "FULL_EPISODE");
        data.append("text", text);
        data.append("actionTier", shortDramaTier?.value || "R2");
        data.append("platform", shortDramaPlatform?.value || "抖音");
        data.append("aspectRatio", shortDramaRatio?.value || "9:16");
        if (file) data.append("file", file);
        const response = await fetch("/api/short-drama-director/tasks", {method: "POST", body: data});
        const task = await readJson(response);
        if (!response.ok) throw new Error(task.message || "短剧导演任务提交失败");
        renderShortDramaResult(task); pollShortDramaTask(task.id); loadShortDramaTasks();
    } catch (error) {
        if (shortDramaMessage) shortDramaMessage.textContent = error.message || "短剧导演任务提交失败";
    } finally { if (shortDramaSubmit) shortDramaSubmit.disabled = false; }
}

async function pollShortDramaTask(id) {
    for (let index = 0; index < 900; index += 1) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const response = await fetch(`/api/short-drama-director/tasks/${id}`, {cache: "no-store"});
            const task = await readJson(response);
            if (!response.ok) throw new Error(task.message || "读取短剧导演任务失败");
            renderShortDramaResult(task); loadShortDramaTasks();
            if (shortDramaMessage) shortDramaMessage.textContent = task.message || shortDramaStatusLabel(task.status);
            if (["SUCCESS", "FAILED"].includes(task.status)) return;
        } catch (error) { if (shortDramaMessage) shortDramaMessage.textContent = error.message || "读取任务状态失败"; return; }
    }
}

async function loadShortDramaTasks() {
    if (!shortDramaHistory) return;
    try {
        const response = await fetch("/api/short-drama-director/tasks", {cache: "no-store"});
        const items = await readJson(response);
        if (!response.ok) throw new Error(items.message || "读取短剧导演记录失败");
        renderShortDramaHistory(Array.isArray(items) ? items : []);
    } catch (error) {
        shortDramaHistory.replaceChildren();
        const hint = document.createElement("p"); hint.className = "knowledge-empty"; hint.textContent = error.message || "读取短剧导演记录失败"; shortDramaHistory.append(hint);
    }
}

function renderShortDramaHistory(items) {
    shortDramaHistory.replaceChildren();
    if (!items.length) { const empty = document.createElement("p"); empty.className = "knowledge-empty"; empty.textContent = "暂无短剧导演创作记录"; shortDramaHistory.append(empty); return; }
    const table = document.createElement("table"); table.className = "short-drama-table";
    const head = document.createElement("thead"); const heading = document.createElement("tr");
    ["任务", "来源", "状态", "信息", "操作"].forEach(label => { const cell = document.createElement("th"); cell.textContent = label; heading.append(cell); });
    head.append(heading); table.append(head); const body = document.createElement("tbody");
    items.forEach(item => {
        const row = document.createElement("tr");
        const mode = document.createElement("td"); const title = document.createElement("strong"); title.textContent = shortDramaModeLabel(item.mode); const meta = document.createElement("small"); meta.textContent = `${item.platform || "通用"} · ${item.aspectRatio || "9:16"} · ${item.actionTier || "R2"}`; mode.append(title, meta); row.append(mode);
        const source = document.createElement("td"); source.className = "short-drama-ellipsis"; source.textContent = item.sourceFileName || item.sourceText || "文本输入"; source.title = source.textContent; row.append(source);
        const statusCell = document.createElement("td"); const status = document.createElement("span"); status.className = `standalone-video-status ${String(item.status || "").toLowerCase()}`; status.textContent = shortDramaStatusLabel(item.status); statusCell.append(status); row.append(statusCell);
        const info = document.createElement("td"); info.className = "short-drama-ellipsis"; info.textContent = item.error || item.message || ""; info.title = info.textContent; row.append(info);
        const actions = document.createElement("td"); actions.className = "short-drama-actions";
        if (item.result) { const view = document.createElement("button"); view.type = "button"; view.textContent = "查看"; view.addEventListener("click", () => { renderShortDramaResult(item); shortDramaResult?.scrollIntoView({behavior: "smooth", block: "center"}); }); actions.append(view); const copy = document.createElement("button"); copy.type = "button"; copy.textContent = "复制"; copy.addEventListener("click", () => copyText(item.result, copy)); actions.append(copy); }
        if (item.status === "FAILED") { const retry = document.createElement("button"); retry.type = "button"; retry.textContent = "重试"; retry.addEventListener("click", () => retryShortDramaTask(item.id, retry)); actions.append(retry); }
        if (!actions.childElementCount) { const muted = document.createElement("span"); muted.className = "muted"; muted.textContent = "-"; actions.append(muted); }
        row.append(actions); body.append(row);
    });
    table.append(body); shortDramaHistory.append(table);
}

async function retryShortDramaTask(id, button) {
    button.disabled = true;
    try { const response = await fetch(`/api/short-drama-director/tasks/${id}/retry`, {method: "POST"}); const task = await readJson(response); if (!response.ok) throw new Error(task.message || "重试失败"); renderShortDramaResult(task); pollShortDramaTask(id); loadShortDramaTasks(); }
    catch (error) { button.disabled = false; if (shortDramaMessage) shortDramaMessage.textContent = error.message || "重试失败"; }
}

function renderShortDramaResult(task) {
    if (!shortDramaResult) return;
    shortDramaResult.replaceChildren();
    const heading = document.createElement("div"); heading.className = "short-drama-result-heading"; const title = document.createElement("h3"); title.textContent = shortDramaModeLabel(task.mode); const state = document.createElement("span"); state.className = `standalone-video-status ${String(task.status || "").toLowerCase()}`; state.textContent = shortDramaStatusLabel(task.status); heading.append(title, state); shortDramaResult.append(heading);
    const message = document.createElement("p"); message.className = "short-drama-result-message"; message.textContent = task.error || task.message || ""; shortDramaResult.append(message);
    if (task.result) { const copy = document.createElement("button"); copy.type = "button"; copy.textContent = "复制导演输出"; copy.addEventListener("click", () => copyText(task.result, copy)); const output = document.createElement("pre"); output.className = "short-drama-output"; output.textContent = task.result; shortDramaResult.append(copy, output); }
}

function shortDramaStatusLabel(status) { return {QUEUED: "排队中", RUNNING: "创作中", SUCCESS: "已完成", FAILED: "失败"}[status] || status || "未知"; }
function shortDramaModeLabel(mode) { return {FULL_EPISODE: "创建剧本设定", SCREENPLAY: "写剧本设定", DIALOGUE_DOCTOR: "台词诊断", ASSET_BREAKDOWN: "拆资产", STORYBOARD: "做分镜", SPEECH_SPEED: "语速自检", VIDEO_PROMPT: "生成视频提示词", QUALITY_REVIEW: "审查"}[mode] || mode || "短剧导演"; }

async function loadMyScripts() {
    if (!myScriptList) return;
    try {
        const response = await fetch("/api/my-scripts", {cache: "no-store"});
        const items = await readJson(response);
        if (!response.ok) throw new Error(items.message || "读取剧本失败");
        myScripts = Array.isArray(items) ? items : [];
        if (!selectedMyScriptId && myScripts.length) selectedMyScriptId = myScripts[0].id;
        if (selectedMyScriptId && !myScripts.some(item => item.id === selectedMyScriptId)) selectedMyScriptId = myScripts[0]?.id || null;
        if (selectedMyScriptId && !expandedMyScriptProjects.size) expandedMyScriptProjects.add(selectedMyScriptId);
        renderMyScripts();
    } catch (error) {
        myScriptList.replaceChildren(); const hint = document.createElement("p"); hint.className = "knowledge-empty"; hint.textContent = error.message || "读取剧本失败"; myScriptList.append(hint);
    }
}

function renderMyScripts() {
    if (!myScriptList || !myScriptDetail) return;
    myScriptList.replaceChildren();
    if (!myScripts.length) { const empty = document.createElement("p"); empty.className = "knowledge-empty"; empty.textContent = "暂无已归档剧本。请先在短剧导演创建剧本设定。"; myScriptList.append(empty); myScriptDetail.innerHTML = '<div class="short-drama-result-empty"><span>SCRIPT</span><p>暂无剧本项目。</p></div>'; return; }
    myScripts.forEach(project => {
        const group = document.createElement("div"); group.className = "my-script-tree-group";
        const button = document.createElement("button"); button.type = "button"; button.className = `my-script-tree-parent ${project.id === selectedMyScriptId ? "is-selected" : ""}`;
        const caret = document.createElement("span"); caret.className = "my-script-tree-caret"; caret.textContent = expandedMyScriptProjects.has(project.id) ? "▾" : "▸";
        const copy = document.createElement("span"); const title = document.createElement("strong"); title.textContent = project.title || "未命名剧本"; const meta = document.createElement("small"); meta.textContent = `${(project.episodes || []).length} 集 · ${new Date(project.updatedAt).toLocaleString()}`; copy.append(title, meta); button.append(caret, copy);
        button.addEventListener("click", () => { selectedMyScriptId = project.id; const wasExpanded = expandedMyScriptProjects.has(project.id); if (wasExpanded) expandedMyScriptProjects.delete(project.id); else expandedMyScriptProjects.add(project.id); if (!wasExpanded) selectedMyScriptSection = "settings"; renderMyScripts(); }); group.append(button);
        if (expandedMyScriptProjects.has(project.id)) {
            const children = document.createElement("div"); children.className = "my-script-tree-children";
            const settingsChild = document.createElement("button"); settingsChild.type = "button"; settingsChild.className = `my-script-tree-episode my-script-tree-settings ${selectedMyScriptSection === "settings" && project.id === selectedMyScriptId ? "is-selected" : ""}`;
            settingsChild.textContent = "剧本设定";
            settingsChild.addEventListener("click", event => { event.stopPropagation(); selectedMyScriptId = project.id; selectedMyScriptSection = "settings"; selectedMyScriptEpisodeId = null; selectedMyScriptReplicationVersionId = null; expandedMyScriptProjects.add(project.id); renderMyScripts(); });
            children.append(settingsChild);
            (project.episodes || []).forEach(episode => {
                const child = document.createElement("button"); child.type = "button"; child.className = `my-script-tree-episode ${selectedMyScriptSection === episode.id || episode.id === selectedMyScriptEpisodeId ? "is-selected" : ""}`;
                const defaultTitle = `第${episode.number}集`; const episodeTitle = episode.title && episode.title !== defaultTitle ? `${defaultTitle} · ${episode.title}` : defaultTitle;
                child.textContent = `${episodeTitle} · ${shortDramaStatusLabel(episode.status)}`;
                child.addEventListener("click", event => { event.stopPropagation(); selectedMyScriptId = project.id; selectedMyScriptEpisodeId = episode.id; selectedMyScriptReplicationVersionId = null; selectedMyScriptSection = episode.id; expandedMyScriptProjects.add(project.id); renderMyScripts(); }); children.append(child);
            }); group.append(children);
        }
        myScriptList.append(group);
    });
    const project = myScripts.find(item => item.id === selectedMyScriptId);
    if (project) renderMyScriptDetail(project);
}

function renderMyScriptDetail(project) {
    myScriptDetail.replaceChildren();
    const title = document.createElement("h3"); title.textContent = project.title;
    const episodes = project.episodes || [];
    if (!selectedMyScriptSection || (selectedMyScriptSection !== "settings" && !episodes.some(item => item.id === selectedMyScriptSection))) selectedMyScriptSection = "settings";
    if (selectedMyScriptSection === "settings") {
        const label = document.createElement("h4"); label.textContent = "剧本设定";
        const settings = document.createElement("div"); settings.className = "my-script-settings"; settings.textContent = project.settings || "暂无剧本设定";
        const actions = document.createElement("div"); actions.className = "my-script-actions";
        const advance = document.createElement("button"); advance.type = "button"; advance.textContent = episodes.length ? "再来一集" : "开始剧情推演";
        advance.addEventListener("click", () => continueMyScript(project.id, advance, episodes.length === 0)); actions.append(advance);
        const characterTools = document.createElement("section"); characterTools.className = "script-character-tools";
        const characterHeader = document.createElement("div"); characterHeader.className = "script-character-header";
        const characterHeading = document.createElement("h4"); characterHeading.textContent = "剧本人物基础图";
        const generateAll = document.createElement("button"); generateAll.type = "button"; generateAll.textContent = "生成基础人物图";
        const characterHint = document.createElement("p"); characterHint.className = "knowledge-empty"; characterHint.textContent = "根据完整剧本设定和导演规则自动提取人物，生成纯白背景基础图，后续剧集复刻会自动复用。";
        const characterGallery = document.createElement("div"); characterGallery.className = "script-character-gallery";
        const renderCharacterAssets = assets => {
            characterGallery.replaceChildren();
            (assets || []).forEach(asset => {
                const urls = parseStoredImages(asset.imageSourcesJson);
                (urls.length ? urls : [""]).forEach(url => {
                const wrap = document.createElement("figure"); wrap.className = "script-character-asset-card";
                const header = document.createElement("div"); header.className = "script-character-asset-header";
                const caption = document.createElement("strong"); caption.textContent = asset.characterName || "未命名人物";
                const retry = document.createElement("button"); retry.type = "button"; retry.className = "button-secondary"; retry.textContent = "重新生成";
                const imageStatus = document.createElement("small"); imageStatus.className = "script-character-asset-status"; imageStatus.textContent = url ? "已保存" : "尚未生成";
                const img = document.createElement("img"); img.className = "script-character-generated-image"; img.alt = asset.characterName || "人物基础图";
                const download = document.createElement("a"); download.className = "script-character-download"; download.download = `${asset.characterName || "人物"}-基础人物图.png`; download.textContent = "查看 / 下载";
                if (url) { img.src = url; download.href = url; } else { img.hidden = true; download.hidden = true; }
                retry.addEventListener("click", async () => {
                    retry.disabled = true; imageStatus.textContent = "生成中…";
                    try {
                        const response = await fetch(`/api/my-scripts/${project.id}/characters/generate`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({characterName: asset.characterName, prompt: ""})});
                        const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "人物图生成失败");
                        const urls = parseStoredImages(payload.imageSourcesJson); const next = urls[urls.length - 1];
                        if (!next) throw new Error("接口未返回人物图片");
                        img.hidden = false; download.hidden = false; img.src = next; download.href = next; imageStatus.textContent = "已重新生成并保存";
                    } catch (error) { imageStatus.textContent = error.message || "重新生成失败"; }
                    finally { retry.disabled = false; }
                });
                header.append(caption, retry, imageStatus); wrap.append(header, img, download); characterGallery.append(wrap);
            });
            });
        };
        generateAll.addEventListener("click", async () => {
            generateAll.disabled = true; characterHint.textContent = "正在调用 Gemini 规划人物，并生成基础人物图…";
            try { const response = await fetch(`/api/my-scripts/${project.id}/characters/generate-all`, {method: "POST"}); const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "基础人物图生成失败"); characterHint.textContent = `已生成 ${payload.length || 0} 个基础人物资产。`; renderCharacterAssets(payload); }
            catch (error) { characterHint.textContent = error.message || "基础人物图生成失败"; } finally { generateAll.disabled = false; }
        });
        characterHeader.append(characterHeading, generateAll); characterTools.append(characterHeader, characterHint, characterGallery);
        myScriptDetail.append(title, label, settings, actions, characterTools);
        fetch(`/api/my-scripts/${project.id}/characters`, {cache: "no-store"}).then(response => response.ok ? response.json() : []).then(renderCharacterAssets).catch(() => {});
        return;
    }
    selectedMyScriptEpisodeId = selectedMyScriptSection;
    const selected = episodes.find(episode => episode.id === selectedMyScriptSection);
    if (!selected) return;
    myScriptDetail.append(title);
    const episodeHeading = document.createElement("div"); episodeHeading.className = "my-script-selected-episode-heading";
    const defaultTitle = `第${selected.number}集`; const displayTitle = selected.title && selected.title !== defaultTitle ? `${defaultTitle} · ${selected.title}` : defaultTitle;
    const episodeLabel = document.createElement("h4"); episodeLabel.textContent = `${displayTitle} · ${shortDramaStatusLabel(selected.status)}`; episodeHeading.append(episodeLabel); myScriptDetail.append(episodeHeading);
    const message = document.createElement("p"); message.className = "short-drama-result-message"; message.textContent = selected.error || selected.message || "";
    const summary = document.createElement("section"); summary.className = "my-script-episode-summary"; summary.hidden = !selected.summary;
    const summaryLabel = document.createElement("strong"); summaryLabel.textContent = "本集概述";
    const summaryText = document.createElement("p"); summaryText.textContent = selected.summary || ""; summary.append(summaryLabel, summaryText);
    const content = document.createElement("div"); content.className = "my-script-episode-content"; content.textContent = selected.content || "该集正在生成，请稍后刷新。";
    const replicate = document.createElement("button"); replicate.type = "button"; replicate.textContent = "剧本翻拍"; replicate.disabled = !selected.content; replicate.addEventListener("click", () => openScriptReplication(selected.id, replicate));
    const rewrite = document.createElement("button"); rewrite.type = "button"; rewrite.textContent = "重写本集"; rewrite.disabled = !selected.content || ["QUEUED", "RUNNING"].includes(selected.status);
    const rewritePanel = document.createElement("div"); rewritePanel.className = "my-script-rewrite-panel"; rewritePanel.hidden = true;
    const rewriteLabel = document.createElement("label"); rewriteLabel.textContent = "告诉导演你希望如何修改这一集";
    const rewriteIdea = document.createElement("textarea"); rewriteIdea.rows = 5; rewriteIdea.maxLength = 12000; rewriteIdea.placeholder = "例如：保留人物和世界观，把冲突提前到开场，让结尾更有反转，删掉重复对白。"; rewriteLabel.append(rewriteIdea);
    const rewriteActions = document.createElement("div"); rewriteActions.className = "my-script-rewrite-actions";
    const cancelRewrite = document.createElement("button"); cancelRewrite.type = "button"; cancelRewrite.textContent = "取消"; cancelRewrite.addEventListener("click", () => { rewritePanel.hidden = true; });
    const submitRewrite = document.createElement("button"); submitRewrite.type = "button"; submitRewrite.textContent = "提交重写"; submitRewrite.addEventListener("click", () => rewriteMyScriptEpisode(selected.id, rewriteIdea.value, submitRewrite, rewritePanel));
    rewriteActions.append(cancelRewrite, submitRewrite); rewritePanel.append(rewriteLabel, rewriteActions);
    rewrite.addEventListener("click", () => { rewritePanel.hidden = false; rewriteIdea.focus(); });
    const episodeActions = document.createElement("div"); episodeActions.className = "my-script-episode-actions"; episodeActions.append(replicate, rewrite);
    myScriptDetail.append(message, summary, content, episodeActions, rewritePanel);
    renderMyScriptPromptHistory(selected, project, myScriptDetail);
    renderMyScriptReplicationHistory(selected, myScriptDetail);
}

function renderMyScriptPromptHistory(episode, project, container) {
    const heading = document.createElement("h4"); heading.textContent = "生成提示词与版本"; container.append(heading);
    const prompts = Array.isArray(episode.prompts) ? episode.prompts : [];
    if (!prompts.length) { const empty = document.createElement("p"); empty.className = "knowledge-empty"; empty.textContent = "暂无提示词记录，提交剧情推演或重写后会在这里保存。"; container.append(empty); return; }
    const wrap = document.createElement("div"); wrap.className = "my-script-prompt-table-wrap";
    const table = document.createElement("table"); table.className = "my-script-prompt-table";
    const head = document.createElement("thead"); const tr = document.createElement("tr"); ["版本", "来源", "提示词", "生成剧本", "状态", "操作"].forEach(text => { const th = document.createElement("th"); th.textContent = text; tr.append(th); }); head.append(tr); table.append(head);
    const body = document.createElement("tbody");
    prompts.forEach(prompt => {
        const row = document.createElement("tr");
        const version = document.createElement("td"); version.textContent = `v${prompt.version}`;
        const source = document.createElement("td"); source.textContent = prompt.sourceLabel || (prompt.sourceType === "SYSTEM" ? "系统推演" : "用户重写");
        const promptCell = document.createElement("td"); const promptText = document.createElement("pre"); promptText.className = "my-script-prompt-text"; promptText.textContent = prompt.promptText || ""; promptCell.append(promptText); if (prompt.idea) { const idea = document.createElement("small"); idea.className = "my-script-prompt-idea"; idea.textContent = `重写想法：${prompt.idea}`; promptCell.append(idea); }
        const result = document.createElement("td"); const resultText = document.createElement("pre"); resultText.className = "my-script-prompt-result"; resultText.textContent = prompt.resultContent || (prompt.status === "FAILED" ? (prompt.error || "生成失败") : "等待生成…"); result.append(resultText);
        const status = document.createElement("td"); status.textContent = shortDramaStatusLabel(prompt.status);
        const actions = document.createElement("td"); const rewrite = document.createElement("button"); rewrite.type = "button"; rewrite.textContent = "重写再生成"; rewrite.disabled = ["QUEUED", "RUNNING"].includes(episode.status) || prompt.status === "QUEUED" || prompt.status === "RUNNING"; rewrite.addEventListener("click", () => showPromptRewriteEditor(episode, prompt, project, rewrite)); actions.append(rewrite);
        row.append(version, source, promptCell, result, status, actions); body.append(row);
    });
    table.append(body); wrap.append(table); container.append(wrap);
}

function renderMyScriptReplicationHistory(episode, container) {
    const heading = document.createElement("h4"); heading.textContent = "剧集复刻版本"; container.append(heading);
    const versions = Array.isArray(episode.replicationVersions) ? episode.replicationVersions : [];
    if (!versions.length) { const empty = document.createElement("p"); empty.className = "knowledge-empty"; empty.textContent = "暂无复刻版本。点击“剧本翻拍”后会创建并保存第一版。"; container.append(empty); return; }
    const wrap = document.createElement("div"); wrap.className = "my-script-prompt-table-wrap";
    const table = document.createElement("table"); table.className = "my-script-prompt-table";
    const head = document.createElement("thead"); const tr = document.createElement("tr"); ["版本", "创建时间", "段落", "资产", "状态", "操作"].forEach(label => { const th = document.createElement("th"); th.textContent = label; tr.append(th); }); head.append(tr); table.append(head);
    const body = document.createElement("tbody");
    versions.forEach(version => {
        const row = document.createElement("tr");
        const number = document.createElement("td"); number.textContent = `复刻 v${version.versionNumber}`;
        const created = document.createElement("td"); created.textContent = version.createdAt ? new Date(version.createdAt).toLocaleString() : "-";
        const segments = document.createElement("td"); segments.textContent = `${version.segmentCount || 0} 段`;
        const assets = document.createElement("td"); assets.textContent = `${version.assetCount || 0} 项`;
        const status = document.createElement("td"); status.textContent = version.status || "已保存";
        const action = document.createElement("td"); const detail = document.createElement("button"); detail.type = "button"; detail.textContent = "查看详情"; detail.addEventListener("click", () => openScriptReplicationVersion(version.id, episode.id)); action.append(detail);
        row.append(number, created, segments, assets, status, action); body.append(row);
    });
    table.append(body); wrap.append(table); container.append(wrap);
}

function showPromptRewriteEditor(episode, prompt, project, trigger) {
    const panel = document.createElement("div"); panel.className = "my-script-rewrite-panel";
    const label = document.createElement("label"); label.textContent = `基于 v${prompt.version} 继续修改本集`;
    const input = document.createElement("textarea"); input.rows = 4; input.maxLength = 12000; input.value = prompt.idea || ""; input.placeholder = "补充你希望如何重写本集的想法"; label.append(input);
    const actions = document.createElement("div"); actions.className = "my-script-rewrite-actions"; const cancel = document.createElement("button"); cancel.type = "button"; cancel.textContent = "取消"; cancel.addEventListener("click", () => panel.remove()); const submit = document.createElement("button"); submit.type = "button"; submit.textContent = "提交重写"; submit.addEventListener("click", () => rewriteMyScriptEpisode(episode.id, input.value, submit, panel, prompt.id)); actions.append(cancel, submit); panel.append(label, actions); trigger.closest("td")?.append(panel);
}

async function rewriteMyScriptEpisode(episodeId, idea, button, panel, promptId = null) {
    const value = idea?.trim();
    if (!value) { window.alert("请先填写本集重写想法"); return; }
    button.disabled = true; button.textContent = "正在提交…";
    try {
        const response = await fetch(`/api/my-scripts/episodes/${episodeId}/rewrite`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({idea: value, promptId})});
        const episode = await readJson(response); if (!response.ok) throw new Error(episode.message || "本集重写提交失败");
        if (panel) panel.hidden = true;
        await loadMyScripts(); pollMyScriptEpisode(episode.projectId, episode.id);
    } catch (error) { window.alert(error.message || "本集重写提交失败"); }
    finally { button.disabled = false; button.textContent = "提交重写"; }
}

async function continueMyScript(projectId, button, first = false) {
    button.disabled = true; const originalLabel = button.textContent; button.textContent = "正在提交…";
    try { const response = await fetch(`/api/my-scripts/${projectId}/episodes${first ? "/first" : ""}`, {method: "POST"}); const episode = await readJson(response); if (!response.ok) throw new Error(episode.message || "剧情推演提交失败"); selectedMyScriptEpisodeId = episode.id; selectedMyScriptSection = episode.id; await loadMyScripts(); pollMyScriptEpisode(projectId, episode.id); }
    catch (error) { window.alert(error.message || "续写提交失败"); } finally { button.disabled = false; button.textContent = originalLabel; }
}

async function pollMyScriptEpisode(projectId, episodeId) {
    for (let attempt = 0; attempt < 180; attempt++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const response = await fetch(`/api/my-scripts/${projectId}`, {cache: "no-store"});
            const project = await readJson(response);
            if (!response.ok) return;
            const current = (project.episodes || []).find(item => item.id === episodeId);
            const previous = myScripts.find(item => item.id === project.id);
            const changed = JSON.stringify(previous) !== JSON.stringify(project);
            myScripts = myScripts.map(item => item.id === project.id ? project : item);
            // Polling remains task-scoped, but avoid rebuilding the whole page when nothing changed.
            if (changed && selectedMyScriptId === project.id) renderMyScripts();
            if (current && ["SUCCESS", "FAILED"].includes(current.status)) { if (!changed && selectedMyScriptId === project.id) renderMyScripts(); return; }
        } catch (_) { return; }
    }
}

async function openScriptReplication(episodeId, trigger = null) {
    if (trigger) trigger.disabled = true;
    try {
        const response = await fetch(`/api/my-scripts/episodes/${episodeId}/replication-segments`, {method: "POST"});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "创建剧本复刻版本失败");
        selectedMyScriptEpisodeId = episodeId;
        selectedMyScriptReplicationVersionId = payload.versionId || null;
        await authorizeAndActivateView("script-replication", true);
    } catch (error) { window.alert(error.message || "创建剧本复刻版本失败"); }
    finally { if (trigger) trigger.disabled = false; }
}

async function openScriptReplicationVersion(versionId, episodeId) {
    selectedMyScriptEpisodeId = episodeId;
    selectedMyScriptReplicationVersionId = versionId;
    await authorizeAndActivateView("script-replication", true);
}

async function loadScriptReplication(episodeId, versionId = selectedMyScriptReplicationVersionId) {
    if (!scriptReplicationContent || !episodeId) return;
    const loadingKey = `${episodeId}:${versionId || "current"}`;
    if (loadingReplicationEpisodes.has(loadingKey)) return;
    loadingReplicationEpisodes.add(loadingKey);
    try {
        const endpoint = versionId ? `/api/my-scripts/replication-versions/${versionId}` : `/api/my-scripts/episodes/${episodeId}/replication-current`;
        const response = await fetch(endpoint, {cache: "no-store"});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "读取剧本复刻版本失败");
        selectedMyScriptReplicationVersionId = payload.versionId || versionId || null;
        const episode = myScripts.flatMap(item => item.episodes || []).find(item => item.id === episodeId);
        const charactersResponse = episode ? await fetch(`/api/my-scripts/${episode.projectId}/characters`, {cache: "no-store"}) : null;
        const characters = charactersResponse ? await readJson(charactersResponse) : [];
        if (charactersResponse && !charactersResponse.ok) throw new Error(characters.message || "读取角色资产失败");
        const episodeAssetsResponse = (!Array.isArray(payload.assets) || !payload.assets.length) && episode ? await fetch(`/api/my-scripts/episodes/${episodeId}/assets`, {cache: "no-store"}) : null;
        const episodeAssets = Array.isArray(payload.assets) && payload.assets.length ? payload.assets : (episodeAssetsResponse ? await readJson(episodeAssetsResponse) : []);
        if (episodeAssetsResponse && !episodeAssetsResponse.ok) throw new Error(episodeAssets.message || "读取剧集资产失败");
        scriptReplicationEmpty.hidden = true; scriptReplicationContent.hidden = false; renderScriptReplication(Array.isArray(payload.segments) ? payload.segments : [], Array.isArray(characters) ? characters : [], episode, payload.episodeMaterial || null, Array.isArray(episodeAssets) ? episodeAssets : []);
    } catch (error) { scriptReplicationEmpty.hidden = false; scriptReplicationEmpty.textContent = error.message || "加载剧本复刻失败"; scriptReplicationContent.hidden = true; }
    finally { loadingReplicationEpisodes.delete(loadingKey); }
}

function isPlaceholderCharacterName(value) { return /^(男主|女主|角色|人物|女子|男子)[一二三四五六七八九十\d]*$/i.test(String(value || "").trim()); }
function parseEpisodeCharacterSpecs(materialText) {
    const specs = new Map();
    String(materialText || "").split(/\r?\n|；|;/).map(value => value.trim()).filter(Boolean).forEach(value => {
        const match = value.match(/^([^：:，,（(]{1,20})[：:]/);
        if (!match) return;
        const name = match[1].trim();
        if (!name || isPlaceholderCharacterName(name) || /^(本集|人物与服装装束|角色身份)$/.test(name)) return;
        specs.set(name, value.substring(match[0].length).trim());
    });
    return specs;
}
function characterNamesForReplication(project, episode, assets, episodeMaterial = null) {
    const specs = parseEpisodeCharacterSpecs(episodeMaterial?.charactersWardrobe);
    const names = [...new Set([...(assets || []).map(item => item.characterName).filter(name => !isPlaceholderCharacterName(name)), ...specs.keys()])];
    return names.slice(0, 12);
}

function renderScriptReplication(segments, assets, episode, episodeMaterial = null, episodeAssets = []) {
    scriptReplicationContent.replaceChildren();
    const project = myScripts.find(item => item.id === episode?.projectId);
    const heading = document.createElement("div"); heading.className = "replication-flow-heading";
    const title = document.createElement("h3"); title.textContent = `${episode?.title || "当前剧集"} · 视频复刻流程${selectedMyScriptReplicationVersionId ? ` · v${(episode?.replicationVersions || []).find(item => item.id === selectedMyScriptReplicationVersionId)?.versionNumber || "历史"}` : ""}`;
    const copy = document.createElement("p"); copy.textContent = "先锁定本集人物参考图，再逐段审核完整生成提示词。每段可重复生成，完成视频会保留在对应段落下方。"; heading.append(title, copy); scriptReplicationContent.append(heading);

    const materialPanel = document.createElement("section"); materialPanel.className = "replication-episode-material";
    const materialTitle = document.createElement("h4"); materialTitle.textContent = "本集资料 · 人物装束、环境与剧情锁定"; materialPanel.append(materialTitle);
    const materialGrid = document.createElement("div"); materialGrid.className = "replication-material-grid";
    const materialItems = [["人物与服装装束", episodeMaterial?.charactersWardrobe], ["环境与场面氛围", episodeMaterial?.environment], ["本集主要剧情", episodeMaterial?.plot], ["与上一集连续性", episodeMaterial?.continuity]];
    materialItems.forEach(([label, value]) => { const item = document.createElement("article"); const name = document.createElement("strong"); name.textContent = label; const text = document.createElement("p"); text.textContent = value || "暂无资料"; item.append(name, text); materialGrid.append(item); });
    materialPanel.append(materialGrid);
    const environmentAction = document.createElement("div"); environmentAction.className = "replication-card-actions";
    const environmentButton = document.createElement("button"); environmentButton.type = "button"; environmentButton.textContent = "生成本集环境图";
    const savedEnvironment = (episodeAssets || []).find(asset => asset.assetType === "ENVIRONMENT");
    const savedEnvironmentImage = parseStoredImages(savedEnvironment?.imageSourcesJson)[0];
    const showEnvironment = image => { if (!image) return; const imageElement = document.createElement("img"); imageElement.className = "replication-environment-image"; imageElement.src = image; imageElement.alt = "本集环境图"; const link = document.createElement("a"); link.href = image; link.download = "本集环境图.png"; link.textContent = "查看 / 下载"; environmentAction.append(imageElement, link); };
    if (savedEnvironmentImage) showEnvironment(savedEnvironmentImage);
    environmentButton.addEventListener("click", async () => { environmentButton.disabled = true; environmentButton.textContent = "生成中…"; try { const response = await fetch(`/api/my-scripts/episodes/${episode.id}/assets/environment`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({prompt: episodeMaterial?.environment || "本集环境"})}); const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "生成环境图失败"); environmentAction.querySelectorAll("img, a").forEach(node => node.remove()); showEnvironment(payload.image); } catch (error) { window.alert(error.message || "生成环境图失败"); } finally { environmentButton.disabled = false; environmentButton.textContent = "生成本集环境图"; } });
    environmentAction.prepend(environmentButton); materialPanel.append(environmentAction); scriptReplicationContent.append(materialPanel);

    const assetPanel = document.createElement("section"); assetPanel.className = "replication-assets";
    const assetTitle = document.createElement("h4"); assetTitle.textContent = "步骤 01 · 剧集资产生成"; assetPanel.append(assetTitle);
    const assetHint = document.createElement("p"); assetHint.textContent = "先生成或上传人物基础图，再按本集服装设定生成专属人物图；段落视频会自动复用已保存资产。"; assetPanel.append(assetHint);
    const assetGrid = document.createElement("div"); assetGrid.className = "replication-asset-grid";
    const characterSpecs = parseEpisodeCharacterSpecs(episodeMaterial?.charactersWardrobe);
    const names = characterNamesForReplication(project, episode, assets, episodeMaterial);
    const supportingNames = (episodeAssets || []).filter(asset => asset.assetType === "SUPPORTING_CHARACTER").map(asset => asset.assetName);
    const allAssetNames = [...new Set([...names, ...supportingNames])];
    if (!allAssetNames.length) {
        const empty = document.createElement("p"); empty.className = "knowledge-empty"; empty.textContent = "本集资料暂未识别到真实人物，请先在“我的剧本”生成基础人物图，或重新整理本集复刻资料。"; assetPanel.append(empty);
    }
    const fields = [];
    names.forEach((name, index) => {
        const existing = assets.find(item => item.characterName === name); const item = document.createElement("article"); item.className = "replication-asset-item";
        const label = document.createElement("strong"); label.textContent = name; item.append(label);
        const input = document.createElement("input"); input.type = "file"; input.accept = "image/*"; input.multiple = true; input.dataset.character = name;
        const preview = document.createElement("div"); preview.className = "replication-image-preview";
        const oldImages = parseStoredImages(existing?.imageSourcesJson); oldImages.forEach(url => { const img = document.createElement("img"); img.src = url; preview.append(img); });
        const episodeImage = parseStoredImages((episodeAssets || []).find(asset => asset.assetType === "CHARACTER" && asset.assetName === name)?.imageSourcesJson)[0];
        if (episodeImage) { const img = document.createElement("img"); img.src = episodeImage; img.alt = `${name} 本集人物图`; preview.prepend(img); }
        const wardrobe = document.createElement("textarea"); wardrobe.rows = 4; wardrobe.placeholder = "只填写本角色本集服装、发型、配饰与状态设定"; wardrobe.value = characterSpecs.get(name) || "请填写本角色本集的服装、发型、配饰、妆容和状态";
        const generateAsset = document.createElement("button"); generateAsset.type = "button"; generateAsset.textContent = "生成本集人物图";
        generateAsset.addEventListener("click", async () => { generateAsset.disabled = true; generateAsset.textContent = "生成中…"; try { const response = await fetch(`/api/my-scripts/episodes/${episode.id}/assets/character`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({characterName: name, prompt: wardrobe.value})}); const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "生成剧集人物图失败"); const img = document.createElement("img"); img.src = payload.image; img.alt = `${name} 本集人物图`; const link = document.createElement("a"); link.href = payload.image; link.download = `${name}-本集人物图.png`; link.textContent = "查看 / 下载"; preview.prepend(link, img); } catch (error) { window.alert(error.message || "生成剧集人物图失败"); } finally { generateAsset.disabled = false; generateAsset.textContent = "生成本集人物图"; } });
        input.addEventListener("change", async () => { const images = await Promise.all([...input.files].map(readFileAsDataUrl)); preview.replaceChildren(); images.forEach(url => { const img = document.createElement("img"); img.src = url; preview.append(img); }); });
        item.append(wardrobe, generateAsset, input, preview); assetGrid.append(item); fields.push({name, input, existing, index});
    });
    assetPanel.append(assetGrid);
    const supportingPanel = document.createElement("section"); supportingPanel.className = "replication-supporting-character";
    const supportingTitle = document.createElement("h5"); supportingTitle.textContent = "自定义本集配角";
    const supportingHint = document.createElement("p"); supportingHint.textContent = "填写配角名称和关键细节，系统会结合剧本设定、本集剧情、服化道与画面风格补全形象并生成剧集参考图。";
    const supportingForm = document.createElement("div"); supportingForm.className = "replication-supporting-form";
    const supportingNameLabel = document.createElement("label"); supportingNameLabel.textContent = "配角名称";
    const supportingName = document.createElement("input"); supportingName.type = "text"; supportingName.maxLength = 30; supportingName.placeholder = "例如：打手、掌柜、巡街捕快"; supportingNameLabel.append(supportingName);
    const supportingDetailsLabel = document.createElement("label"); supportingDetailsLabel.textContent = "人物细节";
    const supportingDetails = document.createElement("textarea"); supportingDetails.rows = 4; supportingDetails.maxLength = 2000; supportingDetails.placeholder = "例如：两名二十多岁的赌坊打手，体格壮实，旧黑短褂，腰缠粗布带，神情凶横但不是武林高手"; supportingDetailsLabel.append(supportingDetails);
    const generateSupporting = document.createElement("button"); generateSupporting.type = "button"; generateSupporting.textContent = "生成配角图";
    const supportingStatus = document.createElement("small"); supportingStatus.className = "replication-supporting-status";
    const supportingGallery = document.createElement("div"); supportingGallery.className = "replication-supporting-gallery";
    const renderSupportingAsset = asset => {
        if (!asset?.assetName) return;
        const oldCard = [...supportingGallery.children].find(node => node.dataset.assetName === asset.assetName);
        if (oldCard) oldCard.remove();
        const card = document.createElement("article"); card.className = "replication-supporting-card"; card.dataset.assetName = asset.assetName;
        const cardName = document.createElement("strong"); cardName.textContent = asset.assetName;
        const image = parseStoredImages(asset.imageSourcesJson)[0];
        card.append(cardName);
        if (image) {
            const img = document.createElement("img"); img.src = image; img.alt = `${asset.assetName} 本集配角图`;
            const link = document.createElement("a"); link.href = image; link.download = `${asset.assetName}-本集配角图.png`; link.textContent = "查看 / 下载";
            card.append(img, link);
        }
        supportingGallery.prepend(card);
    };
    (episodeAssets || []).filter(asset => asset.assetType === "SUPPORTING_CHARACTER").forEach(renderSupportingAsset);
    generateSupporting.addEventListener("click", async () => {
        const name = supportingName.value.trim(); const prompt = supportingDetails.value.trim();
        if (!name || !prompt) { window.alert("请填写配角名称和人物细节"); return; }
        generateSupporting.disabled = true; generateSupporting.textContent = "正在设计并生成…"; supportingStatus.textContent = "Gemini 正在结合本集风格补全配角设定，随后生成图片";
        try {
            const response = await fetch(`/api/my-scripts/episodes/${episode.id}/assets/supporting-character`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({characterName: name, prompt})});
            const asset = await readJson(response); if (!response.ok) throw new Error(asset.message || "生成配角图失败");
            renderSupportingAsset(asset); supportingStatus.textContent = `${name} 已生成并保存，本集含该名称的段落会自动使用这张图。`;
        } catch (error) { supportingStatus.textContent = error.message || "生成配角图失败"; window.alert(supportingStatus.textContent); }
        finally { generateSupporting.disabled = false; generateSupporting.textContent = "生成配角图"; }
    });
    supportingForm.append(supportingNameLabel, supportingDetailsLabel, generateSupporting, supportingStatus);
    supportingPanel.append(supportingTitle, supportingHint, supportingForm, supportingGallery); assetPanel.append(supportingPanel);
    const saveAssets = document.createElement("button"); saveAssets.type = "button"; saveAssets.textContent = "保存人物参考图";
    saveAssets.addEventListener("click", () => saveReplicationAssets(project.id, fields, saveAssets)); assetPanel.append(saveAssets); scriptReplicationContent.append(assetPanel);

    const segmentHeader = document.createElement("div"); segmentHeader.className = "replication-segment-header";
    const segmentTitle = document.createElement("h4"); segmentTitle.className = "replication-step-title"; segmentTitle.textContent = "步骤 02 · 本集段落审核与视频复刻";
    const replan = document.createElement("button"); replan.type = "button"; replan.className = "button-secondary"; replan.textContent = "重新整理本集段落";
    replan.title = "仅在尚未生成视频时重新按本集正文拆分";
    replan.addEventListener("click", async () => {
        if (!window.confirm("将按当前集正文重新整理段落，尚未生成视频的旧段落会被替换，是否继续？")) return;
        replan.disabled = true; replan.textContent = "正在整理…";
        try {
            const response = await fetch(`/api/my-scripts/episodes/${episode.id}/replication-segments/replan`, {method: "POST"});
            const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "重新整理失败");
            const project = myScripts.find(item => item.id === episode.projectId); const saved = await fetch(`/api/my-scripts/${episode.projectId}/characters`, {cache: "no-store"}); const savedEpisodeAssets = await fetch(`/api/my-scripts/episodes/${episode.id}/assets`, {cache: "no-store"});
            renderScriptReplication(payload.segments || [], saved.ok ? await readJson(saved) : [], project?.episodes?.find(item => item.id === episode.id) || episode, payload.episodeMaterial || null, Array.isArray(payload.assets) ? payload.assets : (savedEpisodeAssets.ok ? await readJson(savedEpisodeAssets) : []));
        } catch (error) { window.alert(error.message || "重新整理失败"); }
        finally { replan.disabled = false; replan.textContent = "重新整理本集段落"; }
    });
    segmentHeader.append(segmentTitle, replan); scriptReplicationContent.append(segmentHeader);
    segments.forEach(segment => {
        const card = document.createElement("article"); card.className = "replication-card";
        const head = document.createElement("div"); head.className = "replication-card-head"; const name = document.createElement("strong"); name.textContent = `段落 ${segment.number}`; const state = document.createElement("small"); state.textContent = segment.status || "READY"; head.append(name, state);
        const content = document.createElement("textarea"); content.className = "replication-segment-editor"; content.value = segment.content; content.rows = 13;
        const segmentNames = allAssetNames.filter(item => (segment.content || "").includes(item));
        const references = document.createElement("p"); references.className = "replication-references"; references.textContent = `本段人物资产：${(segmentNames.length ? segmentNames : allAssetNames).join("、")}（系统会自动复用已保存图片）`;
        const action = document.createElement("div"); action.className = "replication-card-actions"; const save = document.createElement("button"); save.type = "button"; save.textContent = "保存提示词修改"; save.addEventListener("click", () => saveReplicationSegment(segment.id, content.value, segment.durationSeconds, save)); const generate = document.createElement("button"); generate.type = "button"; generate.textContent = "复刻本段视频";
        generate.addEventListener("click", () => replicateScriptSegment(segment.id, [], generate)); action.append(save, generate); card.append(head, content, references, action); scriptReplicationContent.append(card);
        if (segment.comfyTaskId) { const history = document.createElement("div"); history.className = "replication-generation-output"; card.append(history); pollScriptReplicationVideo(segment.comfyTaskId, state, generate, history); }
    });
}

function parseStoredImages(value) { try { const parsed = JSON.parse(value || "[]"); return Array.isArray(parsed) ? parsed.filter(item => typeof item === "string") : []; } catch (_) { return []; } }
async function saveReplicationAssets(projectId, fields, button) { button.disabled = true; try { const body = await Promise.all(fields.map(async field => ({characterName: field.name, roleLevel: "A", anchor: field.name, imageSourcesJson: JSON.stringify(field.input.files.length ? await Promise.all([...field.input.files].map(readFileAsDataUrl)) : parseStoredImages(field.existing?.imageSourcesJson))}))); const response = await fetch(`/api/my-scripts/${projectId}/characters`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)}); if (!response.ok) { const payload = await readJson(response); throw new Error(payload.message || "保存人物资产失败"); } button.textContent = "已保存"; } catch (error) { window.alert(error.message || "保存人物资产失败"); } finally { button.disabled = false; setTimeout(() => { button.textContent = "保存人物参考图"; }, 1200); } }
async function saveReplicationSegment(id, content, durationSeconds, button) { button.disabled = true; try { const response = await fetch(`/api/my-scripts/replication-segments/${id}`, {method: "PUT", headers: {"Content-Type": "application/json"}, body: JSON.stringify({content, durationSeconds})}); const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "保存提示词失败"); button.textContent = "已保存"; } catch (error) { window.alert(error.message || "保存提示词失败"); } finally { button.disabled = false; setTimeout(() => { button.textContent = "保存提示词修改"; }, 1200); } }

function readFileAsDataUrl(file) { return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = () => reject(new Error("读取图片失败")); reader.readAsDataURL(file); }); }
async function replicateScriptSegment(id, images, button) {
    button.disabled = true; button.textContent = "正在提交…";
    try { const response = await fetch(`/api/my-scripts/replication-segments/${id}/generate`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({images, resolution: "768p竖"})}); const segment = await readJson(response); if (!response.ok) throw new Error(segment.message || "视频任务提交失败"); button.textContent = "排队中"; pollScriptReplicationVideo(segment.comfyTaskId, null, button); }
    catch (error) { button.disabled = false; button.textContent = "复刻本段视频"; window.alert(error.message || "视频任务提交失败"); }
}

async function pollScriptReplicationVideo(taskId, stateElement, button, output) {
    if (!taskId) return;
    for (let attempt = 0; attempt < 900; attempt++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const response = await fetch(`/api/comfyui-video-generations/${taskId}`, {cache: "no-store"});
            const task = await readJson(response);
            if (!response.ok) return;
            const label = {QUEUED: "排队中", SUBMITTING: "提交中", RUNNING: "生成中", DOWNLOADING: "下载中", SUCCESS: "已完成", FAILED: "失败"}[task.status] || task.status;
            if (stateElement) stateElement.textContent = `${task.duration || 15} 秒 · ${label}`;
            if (button) { button.textContent = task.status === "SUCCESS" ? "再次复刻本段" : task.status === "FAILED" ? "重新复刻本段" : label; button.disabled = !["SUCCESS", "FAILED"].includes(task.status); }
            if (["SUCCESS", "FAILED"].includes(task.status)) { if (output) { output.replaceChildren(); if (task.finalVideoUrl) { const video = document.createElement("video"); video.controls = true; video.preload = "metadata"; video.src = task.finalVideoUrl; output.append(video); } else { const error = document.createElement("p"); error.className = "form-error"; error.textContent = task.error || task.message || "视频生成失败"; output.append(error); } } return; }
        } catch (_) { return; }
    }
}

function normalizeView(view) {
    return ["workbench", "tasks", "video-canvas", "dialogue-extraction", "video-bgm", "direct-outfit", "audit-redraw", "gpt-images", "video-script", "short-drama-director", "my-scripts", "script-replication", "knowledge", "logs", "account-settings", "menu-settings"].includes(view) ? view : "workbench";
}

function applyMenuOptions(options) {
    const labels = new Map((options || []).map(option => [option.id, option.label]));
    const order = new Map((options || []).map((option, index) => [option.id, index]));
    navItems.forEach(item => {
        const label = labels.get(item.dataset.view);
        const target = [...item.children].find(child => child.tagName === "SPAN" && !child.classList.contains("nav-alert"));
        if (target && label) target.textContent = label;
    });
    const nav = document.querySelector(".sidebar-nav");
    if (nav) [...navItems]
        .sort((a, b) => (order.get(a.dataset.view) ?? Number.MAX_SAFE_INTEGER) - (order.get(b.dataset.view) ?? Number.MAX_SAFE_INTEGER))
        .forEach(item => nav.append(item));
}

async function initializeAccountSession() {
    try {
        const response = await fetch("/api/auth/session", {cache: "no-store"});
        if (!response.ok) { location.replace("/login.html"); return; }
        currentAccountSession = await readJson(response);
        if (sessionUsername) sessionUsername.textContent = `${currentAccountSession.username}${currentAccountSession.administrator ? " · 管理员" : ""}`;
        applyMenuOptions(currentAccountSession.menuOptions);
        const allowed = new Set(currentAccountSession.allowedMenus || []);
        navItems.forEach(item => { item.hidden = !allowed.has(item.dataset.view); });
        if (createAccountButton) createAccountButton.hidden = !currentAccountSession.administrator;
        if (packageApplicationButton) packageApplicationButton.hidden = !currentAccountSession.administrator;
        const requested = normalizeView(location.hash.slice(1));
        const initial = allowed.has(requested) ? requested : ([...allowed][0] || "account-settings");
        activateView(initial, true);
    } catch (error) { location.replace("/login.html"); }
}

async function authorizeAndActivateView(view, updateHash = false) {
    const target = normalizeView(view);
    try {
        const response = await fetch(`/api/auth/authorize?menu=${encodeURIComponent(target)}`, {cache: "no-store"});
        const payload = await readJson(response);
        if (response.status === 401 || payload.loginRequired) { location.replace("/login.html"); return; }
        if (!response.ok) throw new Error(payload.message || "没有菜单权限");
        activateView(target, updateHash);
    } catch (error) {
        if (accountSettingsMessage) accountSettingsMessage.textContent = error.message || "菜单权限校验失败";
    }
}

async function logoutAccount() {
    try { await fetch("/api/auth/logout", {method: "POST"}); } finally { location.replace("/login.html"); }
}

function activateView(view, updateHash = false) {
    currentView = normalizeView(view);
    viewPanels.forEach((panel) => {
        let visible = panel.dataset.viewPanel === currentView;
        if (panel === resultSection) visible = visible && Boolean(selectedTaskId);
        if (panel === debugPanel) visible = visible && hasDebugError;
        panel.hidden = !visible;
    });
    navItems.forEach((item) => {
        const active = item.dataset.view === currentView;
        item.classList.toggle("is-active", active);
        if (active) {
            item.setAttribute("aria-current", "page");
        } else {
            item.removeAttribute("aria-current");
        }
    });
    if (updateHash && location.hash !== `#${currentView}`) {
        history.replaceState(null, "", `#${currentView}`);
    }
    if (currentView === "account-settings") loadAccountSettings();
    if (currentView === "menu-settings") loadMenuConfig();
    if (currentView === "short-drama-director") loadShortDramaTasks();
    if (currentView === "my-scripts") loadMyScripts();
    if (currentView === "script-replication" && selectedMyScriptEpisodeId) loadScriptReplication(selectedMyScriptEpisodeId);
}

async function loadRuntimeLogs() {
    if (runtimeLogLoading) return;
    runtimeLogLoading = true;
    const shouldStickToBottom = runtimeLog.scrollHeight - runtimeLog.scrollTop - runtimeLog.clientHeight < 80;
    try {
        const response = await fetch("/api/system/logs?lines=800", {cache: "no-store"});
        const text = await response.text();
        runtimeLog.textContent = response.ok ? text : `日志读取失败（HTTP ${response.status}）\n${text}`;
        if (shouldStickToBottom) {
            runtimeLog.scrollTop = runtimeLog.scrollHeight;
        }
    } catch (error) {
        runtimeLog.textContent = `日志读取失败：${error.message || error}`;
    } finally {
        runtimeLogLoading = false;
    }
}

async function copyText(value, button) {
    const originalText = button.textContent;
    try {
        await navigator.clipboard.writeText(value);
        button.textContent = "已复制";
    } catch (error) {
        button.textContent = "复制失败，请手动选择";
    }
    setTimeout(() => {
        button.textContent = originalText;
    }, 1800);
}

function resetView() {
    hideError();
    hasDebugError = false;
    logAlertDot.hidden = true;
    debugPanel.hidden = true;
    debugLog.textContent = "";
    copyDebugLog.textContent = "复制错误日志";
    jobStatus.textContent = "正在创建";
    progressMessage.textContent = "任务正在进入 Agent 流水线。";
    agentItems.forEach((item) => {
        item.classList.remove("is-active", "is-complete", "is-failed");
        item.querySelector(".agent-state").textContent = "等待";
    });
}

function failView(message, details) {
    clearTimeout(pollTimer);
    setStopButton(false);
    setLoading(false);
    showError(message);
    jobStatus.textContent = "失败";
    progressMessage.textContent = message;
    debugLog.textContent = details || `错误摘要: ${message}`;
    hasDebugError = true;
    logAlertDot.hidden = false;
    const activeItem = agentItems.find((item) => item.classList.contains("is-active"));
    if (activeItem) {
        activeItem.classList.add("is-failed");
        activeItem.querySelector(".agent-state").textContent = "失败";
    }
    activateView("logs", true);
    debugPanel.scrollIntoView({behavior: "smooth", block: "start"});
    loadRuntimeLogs();
    loadStepLogs();
    loadHistory();
}

function setLoading(loading) {
    submitButton.disabled = loading || !systemReady;
    portraitGenerationModeInputs.forEach((input) => {
        input.disabled = loading;
    });
    submitButton.classList.toggle("is-loading", loading);
    submitButton.querySelector("span").textContent = loading ? "Agent 协作中" : "开始生成与换装";
}

function syncPortraitGenerationMode(mode) {
    const normalizedMode = mode === "ENHANCED" ? "ENHANCED" : "STANDARD";
    portraitGenerationModeInputs.forEach((input) => {
        input.checked = input.value === normalizedMode;
    });
}

function setStopButton(enabled) {
    stopCurrentJob.disabled = !enabled;
}

function setStartButton(enabled) {
    startCurrentJob.disabled = !enabled;
}

function showError(message) {
    formError.textContent = message;
    formError.hidden = false;
}

function hideError() {
    formError.textContent = "";
    formError.hidden = true;
}

function readJson(response) {
    return response.json().catch(() => ({}));
}

async function loadReadiness() {
    try {
        const response = await fetch("/api/system/readiness", {cache: "no-store"});
        const readiness = await readJson(response);
        if (!response.ok) {
            throw new Error(readiness.message || "系统就绪检查失败");
        }
        systemReady = readiness.ready;
        readinessMessage.textContent = readiness.message;
        readinessMessage.classList.toggle("is-ready", readiness.ready);
        systemState.classList.toggle("is-ready", readiness.ready);
        systemState.querySelector("span").textContent = readiness.ready ? "Pipeline ready" : "Setup required";
        submitButton.disabled = !systemReady;
    } catch (error) {
        systemReady = false;
        readinessMessage.textContent = error.message || "无法检查系统状态";
        systemState.querySelector("span").textContent = "Setup required";
        submitButton.disabled = true;
    }
}

// Per-shot generation UI. This declaration intentionally overrides the legacy batch renderer above.
function renderStoryReplication(task) {
    if (!storyReplicationResult) return;
    storyReplicationResult.replaceChildren(); storyReplicationResult.hidden = false;
    const heading = document.createElement("div"); heading.className = "story-plan-heading";
    const title = document.createElement("h4"); title.textContent = `${task.sourceFileName || "视频"} · ${task.status}`;
    const note = document.createElement("p"); note.textContent = task.speechSummary || task.message || ""; heading.append(title, note); storyReplicationResult.append(heading);
    if (task.error) { const error = document.createElement("p"); error.className = "form-error"; error.textContent = task.error; storyReplicationResult.append(error); }
    if (task.status === "DOWNLOADED") {
        const button = document.createElement("button"); button.type = "button"; button.textContent = "分析视频";
        button.onclick = async () => { button.disabled = true; try { const response = await fetch(`/api/story-video-replications/${task.id}/analyze`, {method: "POST"}); const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "分析失败"); storyReplicationTask = payload; renderStoryReplication(payload); pollStoryReplication(task.id); } catch (e) { storyReplicationMessage.textContent = e.message; button.disabled = false; } };
        storyReplicationResult.append(button); return;
    }
    if (!task.plan?.shots?.length) return;
    const executions = new Map((task.shotExecutions || []).map(e => [Number(e.sequence), e]));
    const list = document.createElement("div"); list.className = "story-replication-shot-list";
    task.plan.shots.forEach((shot, index) => {
        const sequence = Number(shot.sequence || index + 1); const type = sequence === 1 ? "TEXT_TO_VIDEO_IMAGE" : "FIRST_LAST_FRAME"; const execution = executions.get(sequence);
        const card = document.createElement("article"); card.className = "story-shot-card"; card.dataset.sequence = sequence;
        const top = document.createElement("div"); top.className = "story-shot-heading";
        const label = document.createElement("strong"); label.textContent = `镜头 ${sequence}`;
        const duration = document.createElement("input"); duration.type = "number"; duration.min = 1; duration.max = 10; duration.value = shot.duration || 5; duration.dataset.field = "duration";
        const durationLabel = document.createElement("label"); durationLabel.textContent = "时长"; durationLabel.append(duration);
        const kind = document.createElement("span"); kind.textContent = type === "FIRST_LAST_FRAME" ? "首尾帧" : "普通图生"; top.append(label, durationLabel, kind); card.append(top);
        const grid = document.createElement("div"); grid.className = "story-shot-fields";
        const prompt = document.createElement("textarea"); prompt.rows = 4; prompt.value = stripStoryPromptSections(shot.prompt || ""); prompt.dataset.field = "prompt"; const promptLabel = document.createElement("label"); promptLabel.textContent = "镜头提示词"; promptLabel.append(prompt);
        const environment = document.createElement("textarea"); environment.rows = 2; environment.value = shot.environment || ""; environment.dataset.field = "environment"; const environmentLabel = document.createElement("label"); environmentLabel.textContent = "环境细节"; environmentLabel.append(environment); grid.append(promptLabel, environmentLabel, buildDialogueEditor(shot.dialogueLines, shot.dialogue));
        const image = document.createElement("input"); image.type = "file"; image.multiple = true; image.accept = "image/png,image/jpeg,image/webp"; image.dataset.field = "image";
        const imageKey = `${task.id}:${sequence}`;
        const imageLabel = document.createElement("label"); imageLabel.textContent = type === "FIRST_LAST_FRAME" ? "人物参考图（图2、图3…，可多选，按选择顺序发送）" : "人物参考图（图1、图2…，可多选，按选择顺序发送）"; imageLabel.append(image); grid.append(imageLabel);
        const imageCount = document.createElement("small"); imageCount.className = "story-shot-image-count";
        const imagePreview = document.createElement("div"); imagePreview.className = "story-shot-image-preview"; imagePreview.dataset.imageKey = imageKey;
        const renderImagePreview = (items) => { imagePreview.replaceChildren(); items.forEach((item, imageIndex) => { const wrapper = document.createElement("span"); wrapper.className = "story-shot-image-item"; const preview = document.createElement("img"); preview.src = item.dataUrl; preview.alt = item.name || `图片${imageIndex + 1}`; preview.title = `${imageIndex + 1}. ${item.name || "图片"}`; const caption = document.createElement("small"); caption.textContent = `${imageIndex + 1}. ${item.name || "图片"}`; wrapper.append(preview, caption); imagePreview.append(wrapper); }); };
        const storedImages = storyShotImageState.get(imageKey) || [];
        imageCount.textContent = storedImages.length ? `已选择 ${storedImages.length} 张图片` : "尚未选择人物图片";
        renderImagePreview(storedImages);
        image.addEventListener("change", () => {
            const selected = [...image.files];
            if (!selected.length) return;
            const existing = storyShotImageState.get(imageKey) || [];
            const existingKeys = new Set(existing.map(item => item.key));
            const pending = Promise.all(selected.map(async file => ({
                key: `${file.name}:${file.size}:${file.lastModified}`,
                name: file.name,
                dataUrl: await readFileAsDataUrl(file)
            }))).then(items => {
                const merged = existing.concat(items.filter(item => !existingKeys.has(item.key)));
                storyShotImageState.set(imageKey, merged);
                imageCount.textContent = `已选择 ${merged.length} 张图片`;
                renderImagePreview(merged);
                return merged;
            });
            storyShotImageReadState.set(imageKey, pending);
            // Clear the native input so selecting another batch appends instead of replacing it.
            image.value = "";
            pending.catch(error => { storyReplicationMessage.textContent = error.message || "读取人物图片失败"; });
        });
        grid.append(imageCount, imagePreview);
        if (type === "FIRST_LAST_FRAME") {
            const state = document.createElement("p"); state.textContent = execution?.firstFrameRecognized ? "首帧已识别" : "需要先识别上一镜头最后一帧"; grid.append(state);
            const recognize = document.createElement("button"); recognize.type = "button"; recognize.textContent = execution?.firstFrameRecognized ? "重新识别首帧" : "识别首帧"; recognize.disabled = !executions.get(sequence - 1)?.status?.includes("SUCCESS"); recognize.onclick = async () => { recognize.disabled = true; try { const r = await fetch(`/api/story-video-replications/${task.id}/shots/${sequence}/recognize-first-frame`, {method: "POST"}); const p = await readJson(r); if (!r.ok) throw new Error(p.message || "首帧识别失败"); storyReplicationTask = p; renderStoryReplication(p); } catch (e) { storyReplicationMessage.textContent = e.message; recognize.disabled = false; } }; grid.append(recognize);
            const last = document.createElement("input"); last.type = "file"; last.accept = "image/png,image/jpeg,image/webp"; last.dataset.field = "lastFrame"; const lastLabel = document.createElement("label"); lastLabel.textContent = "可选尾帧"; lastLabel.append(last); grid.append(lastLabel);
        }
        card.append(grid);
        const generate = document.createElement("button"); generate.type = "button"; generate.textContent = execution?.status === "SUCCESS" ? "重新生成本镜头" : "生成本镜头"; generate.disabled = execution?.status === "GENERATING" || (type === "FIRST_LAST_FRAME" && !execution?.firstFrameRecognized); generate.onclick = () => generateStoryShot(task.id, card, generate); card.append(generate);
        const status = document.createElement("span"); status.textContent = execution ? `${execution.status}${execution.message ? "：" + execution.message : ""}` : "WAITING"; card.append(status);
        if (execution?.videoUrl) { const link = document.createElement("a"); link.href = execution.videoUrl; link.target = "_blank"; link.textContent = "打开本镜头视频"; card.append(link); }
        list.append(card);
    });
    storyReplicationResult.append(list);
    const assemble = document.createElement("button"); assemble.type = "button"; assemble.textContent = "组装"; assemble.disabled = !task.shotExecutions?.length || !task.shotExecutions.every(e => e.status === "SUCCESS"); assemble.onclick = async () => { assemble.disabled = true; try { const r = await fetch(`/api/story-video-replications/${task.id}/assemble`, {method: "POST"}); const p = await readJson(r); if (!r.ok) throw new Error(p.message || "组装失败"); storyReplicationTask = p; renderStoryReplication(p); pollStoryReplication(task.id); } catch (e) { storyReplicationMessage.textContent = e.message; assemble.disabled = false; } }; storyReplicationResult.append(assemble);
    if (task.finalVideoUrl) { const link = document.createElement("a"); link.href = task.finalVideoUrl; link.target = "_blank"; link.textContent = "打开最终视频"; storyReplicationResult.append(link); }
}

async function generateStoryShot(id, card, button) {
    button.disabled = true;
    try {
        const get = name => card.querySelector(`[data-field='${name}']`); const sequence = Number(card.dataset.sequence); const imageKey = `${id}:${sequence}`;
        if (storyShotImageReadState.has(imageKey)) await storyShotImageReadState.get(imageKey);
        const selectedImages = storyShotImageState.get(imageKey) || [];
        const files = get("image") ? [...get("image").files] : [];
        const images = selectedImages.length ? selectedImages.map(item => item.dataUrl) : await Promise.all(files.map(readFileAsDataUrl));
        const characters = images.map((file, i) => `图${sequence === 1 ? i + 1 : i + 2}人物`); const dialogue = collectDialogueLines(card); const prompt = composeStoryPrompt(get("prompt")?.value?.trim() || "", get("environment")?.value?.trim() || "", characters, dialogue); const last = get("lastFrame")?.files?.[0];
        const body = {sequence, duration: Math.max(1, Math.min(10, Number(get("duration")?.value) || 5)), interfaceType: sequence === 1 ? "TEXT_TO_VIDEO_IMAGE" : "FIRST_LAST_FRAME", prompt, resolution: "768p竖", firstFrame: null, lastFrame: last ? await readFileAsDataUrl(last) : null, images};
        const response = await fetch(`/api/story-video-replications/${id}/shots/${sequence}/generate`, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)}); const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "镜头生成失败"); storyReplicationTask = payload; renderStoryReplication(payload); pollStoryReplication(id);
    } catch (error) { storyReplicationMessage.textContent = error.message || "镜头生成失败"; button.disabled = false; }
}

async function analyzeDialogueExtraction(event) {
    event.preventDefault();
    const file = dialogueExtractionVideo?.files?.[0];
    if (!file) return;
    dialogueExtractionSubmit.disabled = true;
    dialogueExtractionMessage.textContent = "正在上传视频并提取关键帧…";
    try {
        const body = new FormData(); body.append("video", file);
        const response = await fetch("/api/story-video-replications/analyze", {method: "POST", body});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `提交分析失败（HTTP ${response.status}）`);
        dialogueExtractionTask = payload;
        renderDialogueExtraction(payload);
        pollDialogueExtraction(payload.id, false);
    } catch (error) {
        dialogueExtractionMessage.textContent = error.message || "视频脚本提取失败";
    } finally {
        dialogueExtractionSubmit.disabled = false;
    }
}

async function resolveDialogueExtractionUrl(event) {
    event.preventDefault();
    const address = dialogueExtractionUrl?.value?.trim();
    if (!address) { dialogueExtractionMessage.textContent = "请输入视频地址"; return; }
    dialogueExtractionUrlSubmit.disabled = true;
    dialogueExtractionMessage.textContent = "正在解析地址并下载视频到 E:\\AI影视复刻…";
    try {
        const response = await fetch("/api/story-video-replications/resolve-url", {
            method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({address})
        });
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || `地址解析失败（HTTP ${response.status}）`);
        dialogueExtractionTask = payload;
        renderDialogueExtraction(payload);
        pollDialogueExtraction(payload.id, true);
    } catch (error) {
        dialogueExtractionMessage.textContent = error.message || "视频地址解析失败";
    } finally {
        dialogueExtractionUrlSubmit.disabled = false;
    }
}

async function pollDialogueExtraction(id, startAfterDownload) {
    let shouldStart = startAfterDownload;
    for (let i = 0; i < 900; i++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const response = await fetch(`/api/story-video-replications/${id}`, {cache: "no-store"});
            let payload = await readJson(response);
            if (!response.ok) throw new Error(payload.message || "读取脚本分析状态失败");
            if (payload.status === "DOWNLOADED" && shouldStart) {
                shouldStart = false;
                dialogueExtractionMessage.textContent = "视频下载完成，正在启动影视脚本分析…";
                const start = await fetch(`/api/story-video-replications/${id}/analyze`, {method: "POST"});
                payload = await readJson(start);
                if (!start.ok) throw new Error(payload.message || "启动视频分析失败");
            }
            dialogueExtractionTask = payload;
            renderDialogueExtraction(payload);
            dialogueExtractionMessage.textContent = payload.message || payload.status;
            if (["READY", "FAILED"].includes(payload.status)) return;
        } catch (error) {
            dialogueExtractionMessage.textContent = error.message || "读取脚本分析状态失败";
            return;
        }
    }
}

function renderDialogueExtraction(task) {
    if (!dialogueExtractionResult) return;
    dialogueExtractionResult.hidden = false;
    dialogueExtractionResult.replaceChildren();
    const header = document.createElement("div"); header.className = "production-script-heading";
    const title = document.createElement("div");
    const name = document.createElement("h3"); name.textContent = task.sourceFileName || "视频制作脚本";
    const meta = document.createElement("p"); meta.textContent = task.sourceDurationSeconds ? `原视频时长 ${Number(task.sourceDurationSeconds).toFixed(1)} 秒 · ${task.status}` : task.status;
    title.append(name, meta); header.append(title);
    if (task.error) { const error = document.createElement("p"); error.className = "form-error"; error.textContent = task.error; dialogueExtractionResult.append(header, error); return; }
    if (!task.plan?.shots?.length) { const waiting = document.createElement("p"); waiting.className = "video-catalog-status"; waiting.textContent = task.message || "正在分析视频…"; dialogueExtractionResult.append(header, waiting); return; }
    const copy = document.createElement("button"); copy.type = "button"; copy.textContent = "复制完整制作脚本";
    copy.addEventListener("click", () => copyText(buildProductionScriptText(task), copy)); header.append(copy);
    dialogueExtractionResult.append(header);
    if (task.speechSummary) { const summary = document.createElement("section"); summary.className = "production-script-summary"; const heading = document.createElement("strong"); heading.textContent = "整体内容与声音"; const text = document.createElement("p"); text.textContent = task.speechSummary; summary.append(heading, text); dialogueExtractionResult.append(summary); }
    if (task.plan.planningNotes) { const notes = document.createElement("p"); notes.className = "production-script-notes"; notes.textContent = task.plan.planningNotes; dialogueExtractionResult.append(notes); }
    const timeline = document.createElement("div"); timeline.className = "production-script-timeline";
    let cursor = 0;
    task.plan.shots.forEach((shot, index) => {
        const start = cursor; const end = cursor + Number(shot.duration || 0); cursor = end;
        const section = document.createElement("section"); section.className = "production-script-segment";
        const segmentHeader = document.createElement("div"); segmentHeader.className = "production-script-segment-heading";
        const segmentTitle = document.createElement("h4"); segmentTitle.textContent = `段落 ${index + 1}`;
        const time = document.createElement("span"); time.textContent = `${formatScriptTime(start)} - ${formatScriptTime(end)} · ${shot.duration || 0} 秒`;
        segmentHeader.append(segmentTitle, time); section.append(segmentHeader);
        appendProductionField(section, "场景与环境", shot.environment);
        appendProductionField(section, "镜头、动作与表演", stripStoryPromptSections(shot.prompt || ""));
        const dialogue = Array.isArray(shot.dialogueLines) && shot.dialogueLines.length
            ? shot.dialogueLines.map(line => `${line.speaker || "人物"}：${line.text || ""}${line.tone ? `（${line.tone}）` : ""}`).join("\n")
            : shot.dialogue;
        appendProductionField(section, "台词与语气", dialogue || "本段无明确台词");
        const transition = index === 0 ? "建立场景与人物关系" : "承接上一段最后一帧，保持环境、构图、光线和空间连续";
        appendProductionField(section, "衔接与转场", transition);
        timeline.append(section);
    });
    dialogueExtractionResult.append(timeline);
}

function appendProductionField(parent, label, value) {
    const field = document.createElement("div"); field.className = "production-script-field";
    const title = document.createElement("strong"); title.textContent = label;
    const content = document.createElement("p"); content.textContent = value || "未识别";
    field.append(title, content); parent.append(field);
}

function buildProductionScriptText(task) {
    const lines = [`《${task.sourceFileName || "视频"}》制作脚本`, `原视频时长：${Number(task.sourceDurationSeconds || 0).toFixed(1)} 秒`, ""];
    if (task.speechSummary) lines.push("【整体内容与声音】", task.speechSummary, "");
    let cursor = 0;
    (task.plan?.shots || []).forEach((shot, index) => {
        const start = cursor; const end = cursor + Number(shot.duration || 0); cursor = end;
        lines.push(`【段落 ${index + 1}｜${formatScriptTime(start)} - ${formatScriptTime(end)}】`);
        lines.push(`场景与环境：${shot.environment || "未识别"}`);
        lines.push(`镜头、动作与表演：${stripStoryPromptSections(shot.prompt || "")}`);
        const dialogue = Array.isArray(shot.dialogueLines) && shot.dialogueLines.length ? shot.dialogueLines.map(line => `${line.speaker || "人物"}：${line.text || ""}${line.tone ? `（${line.tone}）` : ""}`).join("；") : (shot.dialogue || "本段无明确台词");
        lines.push(`台词与语气：${dialogue}`);
        lines.push(`衔接与转场：${index === 0 ? "建立场景与人物关系" : "承接上一段最后一帧，保持环境、构图、光线和空间连续"}`, "");
    });
    return lines.join("\n");
}

function formatScriptTime(seconds) {
    const value = Math.max(0, Math.round(Number(seconds) || 0));
    return `${String(Math.floor(value / 60)).padStart(2, "0")}:${String(value % 60).padStart(2, "0")}`;
}

function initVideoWorkflowCanvas() {
    restoreCanvasLayout();
    canvasNodes().forEach(bindCanvasNodeMedia);
    document.querySelectorAll("[data-canvas-add]").forEach((item) => {
        item.addEventListener("click", () => addCanvasNode(item.dataset.canvasAdd));
        item.addEventListener("dragstart", (event) => event.dataTransfer?.setData("text/plain", item.dataset.canvasAdd));
    });
    videoWorkflowCanvas.addEventListener("dragover", (event) => event.preventDefault());
    videoWorkflowCanvas.addEventListener("drop", (event) => {
        event.preventDefault();
        const type = event.dataTransfer?.getData("text/plain");
        if (type) addCanvasNode(type, event.offsetX, event.offsetY);
    });
    videoWorkflowCanvas.addEventListener("click", handleCanvasClick);
    videoWorkflowCanvas.addEventListener("pointerdown", handleCanvasPointerDown);
    document.addEventListener("pointermove", handleCanvasPointerMove);
    document.addEventListener("pointerup", handleCanvasPointerUp);
    updateCanvasEdges();
    loadCanvasBgmFiles();
}

function canvasNodes() {
    return [...videoWorkflowCanvas.querySelectorAll(".workflow-node")];
}

function addCanvasNode(type, left, top) {
    if (!videoWorkflowCanvas || !["script", "audit", "seedance", "compose"].includes(type)) return;
    const nodeId = `${type}-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
    const node = createCanvasNode(type, nodeId);
    const offset = canvasNodes().length;
    node.style.left = `${Math.max(16, left ?? 80 + (offset % 3) * 38)}px`;
    node.style.top = `${Math.max(16, top ?? 80 + (offset % 3) * 54)}px`;
    videoWorkflowCanvas.append(node);
    bindCanvasNodeMedia(node);
    selectCanvasNode(node);
    updateCanvasEdges();
    saveCanvasLayout();
    return node;
}

function createCanvasNode(type, nodeId) {
    const node = document.createElement("article");
    node.className = `workflow-node workflow-node-${type}`;
    node.dataset.nodeId = nodeId;
    node.dataset.nodeType = type;
    const labels = {script: ["01", "脚本解析"], audit: ["02", "过审重绘"], seedance: ["03", "15秒多图视频"], compose: ["04", "视频合成"]};
    const [number, title] = labels[type];
    const body = type === "script"
        ? `<label>视频地址<input data-node-field="address" type="url" placeholder="粘贴视频地址"></label><button class="node-run-button" type="button" data-node-action="run">解析视频</button><p class="node-message" data-node-message>输入地址后生成可编辑脚本。</p><pre class="node-output" data-node-output hidden></pre>`
        : type === "audit"
            ? `<p class="node-context" data-node-context>等待脚本解析节点输出人物上下文。</p><label class="node-file-label">上传待处理图片<input data-node-field="images" type="file" accept="image/png,image/jpeg,image/webp" multiple></label><label class="node-check"><input data-node-field="skip" type="checkbox"> 已过审，跳过重绘</label><button class="node-run-button" type="button" data-node-action="run">处理图片</button><p class="node-message" data-node-message>可一次上传多张，按顺序处理。</p><div class="node-output-gallery" data-node-gallery></div>`
            : type === "seedance"
                ? `<p class="node-context" data-node-context>接收过审图片，生成 1-15 秒视频。</p><label>生成方式<select data-node-field="mode"><option value="online">线上生成（15秒多图工作流）</option><option value="local">本地视频</option></select></label><label>提示词<textarea data-node-field="prompt" rows="3" placeholder="描述镜头动作、环境和镜头运动"></textarea></label><label>时长<select data-node-field="duration"><option value="15">15 秒</option><option value="5">5 秒</option><option value="8">8 秒</option><option value="10">10 秒</option><option value="1">1 秒</option></select></label><label>输出分辨率<select data-node-field="resolution"><option value="768p竖">768p 竖版</option><option value="480p竖">480p 竖版</option><option value="768p横">768p 横版</option><option value="480p横">480p 横版</option><option value="768p(1:1)">768p 方形</option><option value="480p(1:1)">480p 方形</option></select></label><label>参考图片<input data-node-field="images" type="file" accept="image/png,image/jpeg,image/webp" multiple></label><label class="node-file-label" data-node-local-video>本地视频<input data-node-field="video" type="file" accept="video/mp4,video/quicktime,video/webm"></label><button class="node-run-button" type="button" data-node-action="run">生成多图视频</button><p class="node-message" data-node-message>线上任务完成后自动下载并传给第四步。</p><div class="node-video-output" data-node-video-output></div>`
             : `<p class="node-context" data-node-context>等待上游素材完成。</p><label class="node-file-label">原视频<input data-node-field="video" type="file" accept="video/mp4,video/quicktime,video/webm"></label><label>BGM<select data-node-field="bgm"><option value="">读取本地 BGM…</option></select><audio class="bgm-preview" data-node-bgm-audio controls preload="none" hidden></audio></label><label>成品名称<input data-node-field="name" type="text" placeholder="例如：夏日成片"></label><button class="node-run-button" type="button" data-node-action="run">合成成片</button><p class="node-message" data-node-message>输出保存到 E:\\AI影视复刻。</p><div class="node-video-output" data-node-video-output></div>`;
    node.innerHTML = `<div class="workflow-node-header"><span class="node-drag-handle"><span class="node-type-mark is-${type}">${number}</span><strong>${title}</strong></span><span class="workflow-node-status" data-node-status>待运行</span></div><div class="workflow-node-body">${body}</div><button class="node-port port-in" type="button" data-port="in" aria-label="${title}输入端点"></button><button class="node-port port-out" type="button" data-port="out" aria-label="${title}输出端点"></button>`;
    return node;
}

function bindCanvasNodeMedia(node) {
    ensureCanvasNodeControls(node);
    if (node.dataset.mediaBound === "true") return;
    node.dataset.mediaBound = "true";
    const input = node.querySelector('[data-node-field="images"]');
    if (input) input.addEventListener("change", () => {
        const count = input.files?.length || 0;
        node.querySelector("[data-node-message]").textContent = count ? `已选择 ${count} 张图片，按顺序提交。` : "可一次上传多张，按顺序处理。";
    });
    const mode = node.querySelector('[data-node-field="mode"]');
    const localVideo = node.querySelector("[data-node-local-video]");
    if (mode && localVideo) {
        const sync = () => {
            const local = mode.value === "local";
            localVideo.hidden = !local;
            const prompt = node.querySelector('[data-node-field="prompt"]')?.closest("label");
            if (prompt) prompt.hidden = false;
        };
        mode.addEventListener("change", sync);
        sync();
    }
    const bgmSelect = node.querySelector('[data-node-field="bgm"]');
    const bgmAudio = node.querySelector('[data-node-bgm-audio]');
    if (bgmSelect && bgmAudio) bgmSelect.addEventListener("change", () => previewBgmSelection(bgmSelect, bgmAudio, false));
}

function ensureCanvasNodeControls(node) {
    const header = node.querySelector(".workflow-node-header");
    const status = header?.querySelector("[data-node-status]");
    if (!header || !status || header.querySelector("[data-node-action='delete']")) return;
    const actions = document.createElement("span"); actions.className = "workflow-node-header-actions";
    const remove = document.createElement("button");
    remove.type = "button"; remove.className = "workflow-node-delete"; remove.dataset.nodeAction = "delete";
    remove.title = "删除节点"; remove.setAttribute("aria-label", "删除节点"); remove.textContent = "×";
    status.replaceWith(actions); actions.append(status, remove);
}

function handleCanvasClick(event) {
    const action = event.target.closest("[data-canvas-action]")?.dataset.canvasAction;
    if (action) {
        if (action === "run-workflow") runCanvasWorkflow();
        if (action === "save") { saveCanvasLayout(); setCanvasRunState("已保存"); }
        if (action === "zoom-in") setCanvasZoom(canvasZoom + .1);
        if (action === "zoom-out") setCanvasZoom(canvasZoom - .1);
        if (action === "center") centerCanvasNodes();
        return;
    }
    const addType = event.target.closest("[data-canvas-add]")?.dataset.canvasAdd;
    if (addType) return;
    const node = event.target.closest(".workflow-node");
    if (!node) return;
    if (event.target.closest("[data-node-action='delete']")) { deleteCanvasNode(node); return; }
    if (event.target.closest("[data-node-action='run']")) { runCanvasNode(node); return; }
    const port = event.target.closest(".node-port");
    if (port) { handleCanvasPortClick(node, port); return; }
    const interactive = event.target.closest("input, textarea, select, button, label, a, video");
    if (!interactive || event.target.closest(".workflow-node-header")) selectCanvasNode(node);
}

function handleCanvasPointerDown(event) {
    const handle = event.target.closest(".node-drag-handle");
    const node = event.target.closest(".workflow-node");
    if (!handle || !node || event.button !== 0) return;
    canvasDragState = {node, x: event.clientX, y: event.clientY, left: node.offsetLeft, top: node.offsetTop};
    node.classList.add("is-dragging");
    node.setPointerCapture?.(event.pointerId);
    event.preventDefault();
}

function handleCanvasPointerMove(event) {
    if (!canvasDragState) return;
    const {node, x, y, left, top} = canvasDragState;
    node.style.left = `${Math.max(10, left + (event.clientX - x) / canvasZoom)}px`;
    node.style.top = `${Math.max(10, top + (event.clientY - y) / canvasZoom)}px`;
    updateCanvasEdges();
}

function handleCanvasPointerUp() {
    if (!canvasDragState) return;
    canvasDragState.node.classList.remove("is-dragging");
    canvasDragState = null;
    saveCanvasLayout();
}

function selectCanvasNode(node) {
    canvasNodes().forEach((item) => item.classList.toggle("is-selected", item === node));
    if (canvasSelectionLabel) canvasSelectionLabel.textContent = `已选择：${node.querySelector(".workflow-node-header strong")?.textContent || "节点"}`;
}

function deleteCanvasNode(node) {
    if (!node || canvasWorkflowRunning || node.dataset.status === "RUNNING") {
        setCanvasRunState("节点执行中，完成后再删除");
        return;
    }
    const nodeId = node.dataset.nodeId;
    const output = canvasNodeOutputs.get(nodeId);
    if (output?.url?.startsWith?.("blob:")) URL.revokeObjectURL(output.url);
    canvasNodeOutputs.delete(nodeId);
    for (let index = canvasEdges.length - 1; index >= 0; index--) {
        if (canvasEdges[index].from === nodeId || canvasEdges[index].to === nodeId) canvasEdges.splice(index, 1);
    }
    if (canvasPortSelection?.nodeId === nodeId) canvasPortSelection = null;
    if (canvasDragState?.node === node) canvasDragState = null;
    node.remove();
    canvasNodes().forEach((item) => item.classList.remove("is-connecting"));
    if (canvasSelectionLabel) canvasSelectionLabel.textContent = "未选择节点";
    setCanvasRunState("节点已删除");
    updateCanvasEdges();
    saveCanvasLayout();
}

function handleCanvasPortClick(node, port) {
    const direction = port.dataset.port;
    if (direction === "out") {
        canvasPortSelection = {nodeId: node.dataset.nodeId};
        canvasNodes().forEach((item) => item.classList.toggle("is-connecting", item === node));
        if (canvasSelectionLabel) canvasSelectionLabel.textContent = "请选择下游输入端点";
        return;
    }
    if (!canvasPortSelection || canvasPortSelection.nodeId === node.dataset.nodeId) return;
    const exists = canvasEdges.some((edge) => edge.from === canvasPortSelection.nodeId && edge.to === node.dataset.nodeId);
    if (!exists) canvasEdges.push({from: canvasPortSelection.nodeId, to: node.dataset.nodeId});
    canvasPortSelection = null;
    canvasNodes().forEach((item) => item.classList.remove("is-connecting"));
    updateCanvasEdges();
    saveCanvasLayout();
}

function updateCanvasEdges() {
    if (!workflowEdgeLayer || !videoWorkflowCanvas) return;
    const canvasRect = videoWorkflowCanvas.getBoundingClientRect();
    workflowEdgeLayer.setAttribute("width", videoWorkflowCanvas.clientWidth);
    workflowEdgeLayer.setAttribute("height", videoWorkflowCanvas.clientHeight);
    workflowEdgeLayer.setAttribute("viewBox", `0 0 ${Math.max(1, videoWorkflowCanvas.clientWidth)} ${Math.max(1, videoWorkflowCanvas.clientHeight)}`);
    workflowEdgeLayer.replaceChildren();
    canvasEdges.forEach((edge) => {
        const source = videoWorkflowCanvas.querySelector(`[data-node-id="${CSS.escape(edge.from)}"] .port-out`);
        const target = videoWorkflowCanvas.querySelector(`[data-node-id="${CSS.escape(edge.to)}"] .port-in`);
        if (!source || !target) return;
        const a = source.getBoundingClientRect();
        const b = target.getBoundingClientRect();
        const x1 = a.left - canvasRect.left + a.width / 2;
        const y1 = a.top - canvasRect.top + a.height / 2;
        const x2 = b.left - canvasRect.left + b.width / 2;
        const y2 = b.top - canvasRect.top + b.height / 2;
        const curve = Math.max(48, Math.abs(x2 - x1) * .42);
        const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
        path.classList.add("workflow-edge");
        path.setAttribute("d", `M ${x1} ${y1} C ${x1 + curve} ${y1}, ${x2 - curve} ${y2}, ${x2} ${y2}`);
        workflowEdgeLayer.append(path);
    });
    if (canvasEdgeCount) canvasEdgeCount.textContent = `已连接 ${canvasEdges.length} 个节点`;
}

function setCanvasZoom(value) {
    canvasZoom = Math.max(.7, Math.min(1.25, Number(value.toFixed(2))));
    videoWorkflowCanvas?.style.setProperty("--canvas-zoom", canvasZoom);
    if (canvasZoomLabel) canvasZoomLabel.textContent = `${Math.round(canvasZoom * 100)}%`;
    requestAnimationFrame(updateCanvasEdges);
}

function centerCanvasNodes() {
    const defaults = {script: [34, 42], audit: [370, 184], seedance: [706, 184], compose: [706, 420]};
    canvasNodes().forEach((node, index) => {
        const type = node.dataset.nodeType;
        const position = defaults[type] || [80 + (index % 3) * 260, 80 + Math.floor(index / 3) * 190];
        node.style.left = `${position[0]}px`;
        node.style.top = `${position[1]}px`;
    });
    setCanvasZoom(1);
    saveCanvasLayout();
    updateCanvasEdges();
}

function saveCanvasLayout() {
    if (!videoWorkflowCanvas) return;
    const data = {version: 2, zoom: canvasZoom, edges: canvasEdges, nodes: canvasNodes().map((node) => ({id: node.dataset.nodeId, type: node.dataset.nodeType, left: node.offsetLeft, top: node.offsetTop}))};
    localStorage.setItem("atelier-flow-video-canvas", JSON.stringify(data));
}

function restoreCanvasLayout() {
    try {
        const saved = JSON.parse(localStorage.getItem("atelier-flow-video-canvas") || "null");
        if (!saved) return;
        if (Number(saved.version) >= 2 && Array.isArray(saved.nodes)) {
            const savedIds = new Set(saved.nodes.map((item) => item?.id).filter(Boolean));
            canvasNodes().forEach((node) => { if (!savedIds.has(node.dataset.nodeId)) node.remove(); });
        }
        if (Array.isArray(saved.edges)) { canvasEdges.splice(0, canvasEdges.length, ...saved.edges.filter((edge) => edge?.from && edge?.to)); }
        (saved.nodes || []).forEach((item) => {
            let node = videoWorkflowCanvas.querySelector(`[data-node-id="${CSS.escape(item.id)}"]`);
            if (!node && item.type) node = createCanvasNode(item.type, item.id), videoWorkflowCanvas.append(node), bindCanvasNodeMedia(node);
            if (node) { node.style.left = `${Number(item.left) || 20}px`; node.style.top = `${Number(item.top) || 20}px`; }
        });
        const hasSeedance = canvasNodes().some(node => node.dataset.nodeType === "seedance");
        if (hasSeedance) {
            // Migrate layouts saved before the Seedance stage was introduced.
            const direct = canvasEdges.findIndex(edge => edge.from === "audit-1" && edge.to === "compose-1");
            if (direct >= 0) canvasEdges.splice(direct, 1);
            if (!canvasEdges.some(edge => edge.from === "audit-1" && edge.to === "seedance-1")) canvasEdges.push({from: "audit-1", to: "seedance-1"});
            if (!canvasEdges.some(edge => edge.from === "seedance-1" && edge.to === "compose-1")) canvasEdges.push({from: "seedance-1", to: "compose-1"});
            const compose = canvasNodes().find(node => node.dataset.nodeId === "compose-1");
            if (compose && compose.offsetLeft === 706 && compose.offsetTop === 52) {
                compose.style.left = "706px";
                compose.style.top = "420px";
            }
        }
        if (saved.zoom) setCanvasZoom(Number(saved.zoom));
    } catch (error) { console.warn("画布状态读取失败", error); }
}

function findCanvasUpstreamOutput(node, expectedType) {
    const linked = canvasEdges
        .filter((edge) => edge.to === node.dataset.nodeId)
        .map((edge) => canvasNodes().find((item) => item.dataset.nodeId === edge.from))
        .find((item) => item?.dataset.nodeType === expectedType && canvasNodeOutputs.has(item.dataset.nodeId));
    if (linked) return canvasNodeOutputs.get(linked.dataset.nodeId);
    const fallback = canvasNodes().find((item) => item.dataset.nodeType === expectedType && canvasNodeOutputs.has(item.dataset.nodeId));
    return fallback ? canvasNodeOutputs.get(fallback.dataset.nodeId) : null;
}

function setCanvasRunState(text) {
    if (canvasRunState) canvasRunState.textContent = text;
}

function canvasNodeStatusText(status) {
    return {RUNNING: "执行中", QUEUED: "排队中", SUCCESS: "已完成", FAILED: "失败", SKIPPED: "已跳过"}[status] || "待运行";
}

function setCanvasNodeState(node, status, message) {
    node.dataset.status = status;
    const statusEl = node.querySelector("[data-node-status]");
    const messageEl = node.querySelector("[data-node-message]");
    if (statusEl) { statusEl.textContent = canvasNodeStatusText(status); statusEl.dataset.status = status; }
    if (message && messageEl) messageEl.textContent = message;
}

async function runCanvasWorkflow() {
    if (canvasWorkflowRunning) return;
    canvasWorkflowRunning = true;
    setCanvasRunState("工作流执行中");
    const order = ["script", "audit", "seedance", "compose"];
    let completed = true;
    try {
        for (const type of order) {
            const node = canvasNodes().find((item) => item.dataset.nodeType === type && item.dataset.nodeId.endsWith("1")) || canvasNodes().find((item) => item.dataset.nodeType === type);
            if (node && !(await runCanvasNode(node))) { completed = false; break; }
        }
    } finally {
        canvasWorkflowRunning = false;
        setCanvasRunState(completed ? "工作流完成" : "工作流已暂停");
        saveCanvasLayout();
    }
}

async function runCanvasNode(node) {
    if (!node) return false;
    const type = node.dataset.nodeType;
    if (type === "script") return runCanvasScriptNode(node);
    if (type === "audit") return runCanvasAuditNode(node);
    if (type === "seedance") return runCanvasSeedanceNode(node);
    if (type === "compose") return runCanvasComposeNode(node);
    return false;
}

async function runCanvasScriptNode(node) {
    const address = node.querySelector('[data-node-field="address"]')?.value?.trim();
    if (!address) { setCanvasNodeState(node, "FAILED", "请输入视频地址后再解析。"); return false; }
    setCanvasNodeState(node, "RUNNING", "正在拉取视频并生成脚本…");
    try {
        const response = await fetch("/api/qwen-video-scripts", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({address, parse: true})});
        const task = await readJson(response);
        if (!response.ok) throw new Error(task.message || "脚本任务提交失败");
        const result = await pollCanvasResource(`/api/qwen-video-scripts/${task.id}`, node, (item) => item.status === "SUCCESS" || item.status === "FAILED");
        if (result.status !== "SUCCESS") throw new Error(result.error || result.message || "脚本解析失败");
        const output = node.querySelector("[data-node-output]");
        if (output) { output.hidden = false; output.textContent = result.script || result.scriptText || "脚本解析完成，但接口未返回文案。"; }
        document.querySelectorAll('.workflow-node[data-node-type="audit"] [data-node-context]').forEach((context) => { context.textContent = `已接收脚本上下文：${(result.script || "").slice(0, 96) || "已识别人物与场景"}`; });
        setCanvasNodeState(node, "SUCCESS", "脚本已生成，可继续执行下游节点。");
        return true;
    } catch (error) { setCanvasNodeState(node, "FAILED", error.message || "脚本解析失败"); return false; }
}

async function runCanvasAuditNode(node) {
    const files = [...(node.querySelector('[data-node-field="images"]')?.files || [])];
    const skip = Boolean(node.querySelector('[data-node-field="skip"]')?.checked);
    if (!files.length) {
        const seedance = canvasNodes().find(item => item.dataset.nodeType === "seedance");
        const localVideo = seedance?.querySelector('[data-node-field="mode"]')?.value === "local"
            && Boolean(seedance?.querySelector('[data-node-field="video"]')?.files?.[0]);
        if (localVideo) {
            canvasNodeOutputs.set(node.dataset.nodeId, {images: []});
            setCanvasNodeState(node, "SKIPPED", "第三步使用本地视频，已跳过图片过审。");
            return true;
        }
        setCanvasNodeState(node, "FAILED", "请上传至少一张待处理图片。"); return false;
    }
    setCanvasNodeState(node, "RUNNING", skip ? "已选择跳过重绘，正在整理素材…" : `正在依次处理 ${files.length} 张图片…`);
    const outputs = [];
    try {
        for (const file of files) {
            if (skip) {
                // Blob URLs cannot be fetched by the server in online mode; keep a data URL for downstream APIs.
                outputs.push({name: file.name, localUrl: await readFileAsDataUrl(file)});
                continue;
            }
            const body = new FormData(); body.append("image", file);
            const response = await fetch("/api/audit-redraw", {method: "POST", body});
            const task = await readJson(response);
            if (!response.ok) throw new Error(task.message || "过审任务提交失败");
            const result = await pollCanvasResource(`/api/audit-redraw/${task.id}`, node, (item) => item.status === "SUCCESS" || item.status === "FAILED");
            if (result.status !== "SUCCESS") throw new Error(result.error || result.message || `${file.name} 过审失败`);
            outputs.push({name: result.outputFileName || file.name, url: result.outputUrl || `/api/audit-redraw/${task.id}/output`});
        }
        renderCanvasAuditOutputs(node, outputs);
        canvasNodeOutputs.set(node.dataset.nodeId, {images: outputs});
        document.querySelectorAll('.workflow-node[data-node-type="seedance"] [data-node-context]').forEach((context) => { context.textContent = `已接收 ${outputs.length} 个过审图片素材，可用于 Seedance 生成。`; });
        setCanvasNodeState(node, skip ? "SKIPPED" : "SUCCESS", skip ? "已跳过重绘，素材已传递。" : `已完成 ${outputs.length} 张图片过审重绘。`);
        return true;
    } catch (error) { setCanvasNodeState(node, "FAILED", error.message || "过审重绘失败"); return false; }
}

function renderCanvasAuditOutputs(node, outputs) {
    const gallery = node.querySelector("[data-node-gallery]");
    if (!gallery) return;
    gallery.replaceChildren();
    outputs.forEach((item, index) => {
        const image = document.createElement("img"); image.src = item.url || item.localUrl; image.alt = item.name; image.title = `${index + 1}. ${item.name}`; gallery.append(image);
    });
}

async function runCanvasSeedanceNode(node) {
    const mode = node.querySelector('[data-node-field="mode"]')?.value || "online";
    const prompt = node.querySelector('[data-node-field="prompt"]')?.value?.trim() || "";
    const duration = Number(node.querySelector('[data-node-field="duration"]')?.value) || 15;
    const resolution = node.querySelector('[data-node-field="resolution"]')?.value || "768p竖";
    const videoInput = node.querySelector('[data-node-field="video"]');
    const localVideo = videoInput?.files?.[0];
    const imageInput = node.querySelector('[data-node-field="images"]');
    let images = [...(imageInput?.files || [])];
    if (!images.length) {
        const upstream = findCanvasUpstreamOutput(node, "audit")?.images || [];
        images = upstream.map(item => item.url || item.localUrl).filter(Boolean);
    } else {
        images = await Promise.all(images.map(readFileAsDataUrl));
    }
    if (mode === "local") {
        if (!localVideo) { setCanvasNodeState(node, "FAILED", "本地模式请先选择视频文件。"); return false; }
        const url = URL.createObjectURL(localVideo);
        canvasNodeOutputs.set(node.dataset.nodeId, {file: localVideo, url, fileName: localVideo.name});
        renderCanvasVideoOutput(node, url, localVideo.name);
        document.querySelectorAll('.workflow-node[data-node-type="compose"] [data-node-context]').forEach((context) => { context.textContent = `已接收本地视频：${localVideo.name}`; });
        setCanvasNodeState(node, "SUCCESS", "本地视频已准备，第四步可直接合成。");
        return true;
    }
    if (!prompt) { setCanvasNodeState(node, "FAILED", "线上模式请输入视频生成提示词。"); return false; }
    if (!images.length) { setCanvasNodeState(node, "FAILED", "请上传参考图，或先完成第二步过审重绘。"); return false; }
    setCanvasNodeState(node, "RUNNING", "正在提交 15 秒多图视频任务…");
    try {
        const response = await fetch("/api/comfyui-video-generations", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({prompt, duration, resolution, images})});
        const task = await readJson(response);
        if (!response.ok) throw new Error(task.message || "Seedance 任务提交失败");
        const result = await pollCanvasResource(`/api/comfyui-video-generations/${task.id}`, node, (item) => item.status === "SUCCESS" || item.status === "FAILED");
        if (result.status !== "SUCCESS") throw new Error(result.error || result.message || "Seedance 视频生成失败");
        const url = result.finalVideoUrl || `/api/comfyui-video-generations/${task.id}/final`;
        canvasNodeOutputs.set(node.dataset.nodeId, {url, fileName: result.finalVideoFileName || "seedance-video.mp4"});
        renderCanvasVideoOutput(node, url, result.finalVideoFileName || "Seedance 视频");
        document.querySelectorAll('.workflow-node[data-node-type="compose"] [data-node-context]').forEach((context) => { context.textContent = `已接收 Seedance 视频：${result.finalVideoFileName || "生成完成"}`; });
        setCanvasNodeState(node, "SUCCESS", "多图视频已生成并保存到账号导出目录，第四步可继续合成。 ");
        return true;
    } catch (error) { setCanvasNodeState(node, "FAILED", error.message || "Seedance 视频生成失败"); return false; }
}

function renderCanvasVideoOutput(node, url, name) {
    const output = node.querySelector("[data-node-video-output]");
    if (!output || !url) return;
    output.replaceChildren();
    const player = document.createElement("video"); player.controls = true; player.preload = "metadata"; player.src = url;
    const label = document.createElement("small"); label.textContent = name || "视频已就绪";
    output.append(player, label);
}

async function runCanvasComposeNode(node) {
    let video = node.querySelector('[data-node-field="video"]')?.files?.[0];
    if (!video) {
        const upstream = findCanvasUpstreamOutput(node, "seedance");
        if (upstream?.file) video = upstream.file;
        else if (upstream?.url) {
            try {
                const response = await fetch(upstream.url);
                if (!response.ok) throw new Error(`读取第三步视频失败（HTTP ${response.status}）`);
                const blob = await response.blob();
                video = new File([blob], upstream.fileName || "seedance-video.mp4", {type: blob.type || "video/mp4"});
            } catch (error) { setCanvasNodeState(node, "FAILED", error.message || "读取第三步视频失败"); return false; }
        }
    }
    const bgm = node.querySelector('[data-node-field="bgm"]')?.value;
    const name = node.querySelector('[data-node-field="name"]')?.value?.trim();
    if (!video || !bgm) { setCanvasNodeState(node, "FAILED", "请先完成第三步，或上传原视频，并选择 BGM。"); return false; }
    setCanvasNodeState(node, "RUNNING", "正在提交视频合成任务…");
    try {
        const body = new FormData(); body.append("video", video); body.append("bgm", bgm); if (name) body.append("name", name);
        const response = await fetch("/api/video-bgm-compositions", {method: "POST", body});
        const task = await readJson(response);
        if (!response.ok) throw new Error(task.message || "视频合成提交失败");
        const result = await pollCanvasResource(`/api/video-bgm-compositions/${task.id}`, node, (item) => item.status === "SUCCESS" || item.status === "FAILED");
        if (result.status !== "SUCCESS") throw new Error(result.error || result.message || "视频合成失败");
        const output = node.querySelector("[data-node-video-output]");
        if (output && result.outputUrl) { output.replaceChildren(); const player = document.createElement("video"); player.controls = true; player.src = result.outputUrl; output.append(player); }
        setCanvasNodeState(node, "SUCCESS", `成片已生成：${result.outputFileName || "视频文件"}`);
        return true;
    } catch (error) { setCanvasNodeState(node, "FAILED", error.message || "视频合成失败"); return false; }
}

async function pollCanvasResource(url, node, done) {
    let last;
    for (let i = 0; i < 900; i++) {
        await new Promise((resolve) => setTimeout(resolve, 2000));
        const response = await fetch(url, {cache: "no-store"});
        last = await readJson(response);
        if (!response.ok) throw new Error(last.message || "读取节点状态失败");
        setCanvasNodeState(node, last.status === "SUCCESS" ? "SUCCESS" : "RUNNING", last.message || last.status);
        if (done(last)) return last;
    }
    throw new Error("节点执行超时，请稍后在任务列表查看。");
}

async function loadCanvasBgmFiles() {
    const selects = [...document.querySelectorAll('.workflow-node[data-node-type="compose"] [data-node-field="bgm"]')];
    if (!selects.length) return;
    try {
        const response = await fetch("/api/video-bgm-compositions/bgm", {cache: "no-store"});
        const files = await readJson(response);
        selects.forEach((select) => { select.replaceChildren(); (files || []).forEach((file) => { const option = document.createElement("option"); option.value = file.name; option.textContent = file.label || file.name; select.append(option); }); });
    } catch (error) { selects.forEach((select) => { select.innerHTML = `<option value="">BGM 读取失败</option>`; }); }
}

async function loadVideoBgmFiles() {
    if (!videoBgmSelect) return;
    try {
        const response = await fetch("/api/video-bgm-compositions/bgm", {cache: "no-store"});
        const files = await readJson(response);
        if (!response.ok || !Array.isArray(files)) throw new Error(files.message || "读取 BGM 列表失败");
        videoBgmSelect.replaceChildren();
        if (!files.length) { const option = document.createElement("option"); option.value = ""; option.textContent = "BGM 目录暂无音频文件"; videoBgmSelect.append(option); return; }
        const placeholder = document.createElement("option"); placeholder.value = ""; placeholder.textContent = "请选择 BGM"; videoBgmSelect.append(placeholder);
        files.forEach(file => { const option = document.createElement("option"); option.value = file.name; option.textContent = file.label || file.name; videoBgmSelect.append(option); });
    } catch (error) {
        videoBgmSelect.replaceChildren(); const option = document.createElement("option"); option.value = ""; option.textContent = error.message || "BGM 列表读取失败"; videoBgmSelect.append(option);
    }
}

function previewBgmSelection(select, audio, ending) {
    if (!select || !audio) return;
    const name = select.value;
    if (!name) {
        audio.pause();
        audio.removeAttribute("src");
        audio.hidden = true;
        return;
    }
    audio.src = `/api/video-bgm-compositions/bgm/preview?name=${encodeURIComponent(name)}&ending=${ending}`;
    audio.hidden = false;
    audio.load();
    // Selecting an option is a direct user gesture; browsers may still reject
    // autoplay, in which case the visible controls remain available.
    audio.play().catch(() => {});
}

async function loadVideoBgmEndingFiles() {
    if (!videoBgmEndingSelect) return;
    try {
        const response = await fetch("/api/video-bgm-compositions/bgm-ending", {cache: "no-store"});
        const files = await readJson(response);
        if (!response.ok || !Array.isArray(files)) throw new Error(files.message || "读取结尾 BGM 列表失败");
        videoBgmEndingSelect.replaceChildren();
        if (!files.length) { const option = document.createElement("option"); option.value = ""; option.textContent = "结尾目录暂无音频文件"; videoBgmEndingSelect.append(option); return; }
        const placeholder = document.createElement("option"); placeholder.value = ""; placeholder.textContent = "请选择结尾 BGM"; videoBgmEndingSelect.append(placeholder);
        files.forEach(file => { const option = document.createElement("option"); option.value = file.name; option.textContent = file.label || file.name; videoBgmEndingSelect.append(option); });
    } catch (error) { videoBgmEndingSelect.replaceChildren(); const option = document.createElement("option"); option.value = ""; option.textContent = error.message || "读取结尾 BGM 列表失败"; videoBgmEndingSelect.append(option); }
}

const accountSettingFields = [
    ["runninghubKey", "RunningHub API Key", "password"], ["snapanyKey", "SnapAny API Key", "password"],
    ["qwenKey", "千问 API Key", "password"], ["geminiKey", "Gemini API Key", "password"],
    ["gptImagesKey", "GPT Images API Key", "password"],
    ["zhipuKey", "智谱 GLM API Key", "password"],
    ["comfyuiToken", "ComfyUI Token", "password"], ["clothingDirectory", "服装资料库目录", "text"],
    ["videoDirectory", "参考视频目录", "text"], ["generatedDirectory", "任务生成目录", "text"],
    ["videoExportDirectory", "视频打包导出目录", "text"], ["auditOutputDirectory", "过审图片目录", "text"],
    ["storyOutputDirectory", "影视复刻/成片目录", "text"], ["bgmDirectory", "BGM 目录", "text"],
    ["qwenOutputDirectory", "视频脚本下载目录", "text"]
];

async function loadAccountSettings() {
    if (!accountList || !currentAccountSession) return;
    accountSettingsMessage.textContent = "正在读取账号配置…";
    try {
        const response = await fetch("/api/accounts", {cache: "no-store"});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "读取账号失败");
        accountRows = Array.isArray(payload) ? payload : [];
        renderAccountSettings();
        accountSettingsMessage.textContent = currentAccountSession.administrator
            ? `共 ${accountRows.length} 个账号。密码采用不可逆哈希，只能重置。`
            : "你可以更新自己的模型 Key 和文件目录；敏感字段不会明文返回。";
    } catch (error) { accountSettingsMessage.textContent = error.message || "读取账号失败"; }
}

function renderAccountSettings() {
    accountList.replaceChildren();
    if (currentAccountSession.administrator) {
        const table = document.createElement("table"); table.className = "account-table";
        table.innerHTML = "<thead><tr><th>账号</th><th>角色</th><th>状态</th><th>有效期</th><th>菜单数</th><th>操作</th></tr></thead>";
        const body = document.createElement("tbody");
        accountRows.forEach(account => {
            const row = document.createElement("tr");
            const values = [account.username, account.administrator ? "管理员" : "用户", account.enabled ? (account.expired ? "已过期" : "有效") : "已停用", account.expiresAt ? new Date(account.expiresAt).toLocaleString() : "长期有效", String((account.allowedMenus || []).length)];
            values.forEach(value => { const cell = document.createElement("td"); cell.textContent = value; row.append(cell); });
            const actions = document.createElement("td");
            const edit = document.createElement("button"); edit.type = "button"; edit.textContent = "编辑"; edit.onclick = () => openAccountEditor(account);
            const remove = document.createElement("button"); remove.type = "button"; remove.textContent = "删除"; remove.className = "danger-button"; remove.disabled = account.id === currentAccountSession.id; remove.onclick = () => deleteAccount(account);
            actions.append(edit, remove); row.append(actions); body.append(row);
        });
        table.append(body); accountList.append(table); selfSettingsForm.hidden = true;
    } else {
        accountList.replaceChildren(); selfSettingsForm.hidden = false;
        renderSettingInputs(selfSettingsFields, accountRows[0]?.settings || {}, "self-setting");
    }
}

function renderSettingInputs(container, settings, prefix, revealSecrets = false) {
    container.replaceChildren();
    accountSettingFields.forEach(([key, labelText, type]) => {
        const label = document.createElement("label"); label.textContent = labelText;
        const input = document.createElement("input"); input.type = revealSecrets && type === "password" ? "text" : type; input.dataset.setting = key; input.id = `${prefix}-${key}`;
        const value = settings?.[key] || "";
        if (type === "password" && !revealSecrets) { input.value = ""; input.placeholder = value ? "已配置，留空保持不变" : "未配置"; }
        else input.value = value;
        if (key.endsWith("Directory")) {
            const row = document.createElement("span"); row.className = "account-directory-input";
            const choose = document.createElement("button"); choose.type = "button"; choose.className = "directory-picker-button";
            choose.title = "从电脑选择文件夹"; choose.setAttribute("aria-label", `选择${labelText}`);
            const browserPicker = document.createElement("input");
            browserPicker.type = "file";
            browserPicker.multiple = true;
            browserPicker.setAttribute("webkitdirectory", "");
            browserPicker.setAttribute("directory", "");
            browserPicker.className = "directory-picker-input";
            browserPicker.tabIndex = -1;
            browserPicker.addEventListener("change", () => applyBrowserDirectorySelection(input, browserPicker));
            choose.addEventListener("click", () => selectAccountDirectory(input, choose, browserPicker));
            row.append(input, choose, browserPicker); label.append(row);
        } else label.append(input);
        container.append(label);
    });
}

async function selectAccountDirectory(input, button, browserPicker) {
    button.disabled = true; button.classList.add("is-loading");
    if (accountSettingsMessage) accountSettingsMessage.textContent = "正在打开电脑文件夹选择窗口…";
    try {
        const response = await fetch("/api/accounts/select-directory", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({initialPath: input.value.trim()})});
        if (response.status === 204) return;
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "打开文件夹选择窗口失败");
        input.value = payload.path || input.value;
        input.dispatchEvent(new Event("change", {bubbles: true}));
        if (accountSettingsMessage) accountSettingsMessage.textContent = "已选择文件夹，请保存账号配置。";
    } catch (error) {
        if (browserPicker) {
            browserPicker.value = "";
            browserPicker.click();
            if (accountSettingsMessage) accountSettingsMessage.textContent = "请选择文件夹；若浏览器不提供绝对路径，请将路径粘贴到输入框后保存。";
        } else if (accountSettingsMessage) accountSettingsMessage.textContent = error.message || "选择文件夹失败";
    } finally { button.disabled = false; button.classList.remove("is-loading"); }
}

function applyBrowserDirectorySelection(input, picker) {
    const files = [...(picker.files || [])];
    if (!files.length) return;
    const absolutePath = files.map(file => file.path).find(path => typeof path === "string" && path && !path.includes("fakepath"));
    if (absolutePath) {
        const separator = absolutePath.includes("\\") ? "\\" : "/";
        input.value = absolutePath.slice(0, absolutePath.lastIndexOf(separator));
        input.dispatchEvent(new Event("change", {bubbles: true}));
        if (accountSettingsMessage) accountSettingsMessage.textContent = "已选择文件夹，请保存账号配置。";
        return;
    }
    const relative = files[0].webkitRelativePath || files[0].name;
    const folder = relative.split(/[\\/]/)[0];
    if (accountSettingsMessage) accountSettingsMessage.textContent = `已选择“${folder}”文件夹。浏览器出于安全限制不会暴露绝对路径，请将完整路径粘贴到输入框后保存。`;
}

function openAccountEditor(account = null) {
    if (!currentAccountSession?.administrator || !accountEditor) return;
    document.querySelector("#account-id").value = account?.id || "";
    accountEditorFields.replaceChildren();
    const fields = [
        ["username", "用户名", "text", account?.username || ""],
        ["password", account ? "重置密码（留空不修改）" : "初始密码", "password", ""],
        ["expiresAt", "有效期（留空为长期）", "datetime-local", toLocalDateTime(account?.expiresAt)],
    ];
    fields.forEach(([key, textValue, type, value]) => { const label = document.createElement("label"); label.textContent = textValue; const input = document.createElement("input"); input.name = key; input.type = type; input.value = value; if (key === "username" || (!account && key === "password")) input.required = true; label.append(input); accountEditorFields.append(label); });
    [["administrator", "管理员", account?.administrator || false], ["enabled", "允许登录", account ? account.enabled : true]].forEach(([key, textValue, checked]) => { const label = document.createElement("label"); label.className = "account-check"; const input = document.createElement("input"); input.type = "checkbox"; input.name = key; input.checked = checked; label.append(input, document.createTextNode(textValue)); accountEditorFields.append(label); });
    const settingsWrap = document.createElement("div"); settingsWrap.className = "account-field-grid account-settings-fields"; renderSettingInputs(settingsWrap, account?.settings || {}, "admin-setting", true); accountEditorFields.append(settingsWrap);
    accountMenuGrid.replaceChildren();
    (currentAccountSession.menuOptions || []).forEach(option => { const label = document.createElement("label"); const input = document.createElement("input"); input.type = "checkbox"; input.value = option.id; input.checked = account?.administrator || (account?.allowedMenus || ["account-settings"]).includes(option.id); label.append(input, document.createTextNode(option.label)); accountMenuGrid.append(label); });
    accountEditor.showModal();
}

async function saveAccountEditor(event) {
    event.preventDefault();
    const id = document.querySelector("#account-id").value;
    const settingInputs = [...accountEditorFields.querySelectorAll("[data-setting]")];
    const settings = Object.fromEntries(settingInputs.map(input => [input.dataset.setting, input.value.trim()]));
    const expires = accountEditorFields.querySelector('[name="expiresAt"]').value;
    const body = {username: accountEditorFields.querySelector('[name="username"]').value.trim(), password: accountEditorFields.querySelector('[name="password"]').value, administrator: accountEditorFields.querySelector('[name="administrator"]').checked, enabled: accountEditorFields.querySelector('[name="enabled"]').checked, expiresAt: expires ? new Date(expires).toISOString() : null, allowedMenus: [...accountMenuGrid.querySelectorAll("input:checked")].map(input => input.value), settings};
    try {
        const response = await fetch(id ? `/api/accounts/${id}` : "/api/accounts", {method: id ? "PUT" : "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)});
        const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "保存账号失败");
        accountEditor.close(); await loadAccountSettings();
    } catch (error) { accountSettingsMessage.textContent = error.message || "保存账号失败"; }
}

async function saveSelfSettings(event) {
    event.preventDefault(); const settings = Object.fromEntries([...selfSettingsFields.querySelectorAll("[data-setting]")].map(input => [input.dataset.setting, input.value.trim()]));
    try {
        const response = await fetch("/api/accounts/me/settings", {method: "PUT", headers: {"Content-Type": "application/json"}, body: JSON.stringify(settings)});
        const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "保存失败");
        accountSettingsMessage.textContent = "配置已保存，新提交的任务会使用当前账号配置。"; await loadAccountSettings();
    } catch (error) { accountSettingsMessage.textContent = error.message || "保存失败"; }
}

async function deleteAccount(account) {
    if (!confirm(`确定删除账号 ${account.username} 吗？`)) return;
    const response = await fetch(`/api/accounts/${account.id}`, {method: "DELETE"}); const payload = await readJson(response);
    if (!response.ok) { accountSettingsMessage.textContent = payload.message || "删除失败"; return; } await loadAccountSettings();
}

async function packageApplication() {
    packageApplicationButton.disabled = true; accountSettingsMessage.textContent = "正在生成应用包…";
    try {
        const response = await fetch("/api/accounts/package", {method: "POST"});
        if (!response.ok) { const payload = await readJson(response); throw new Error(payload.message || "打包失败"); }
        const blob = await response.blob(); const url = URL.createObjectURL(blob); const link = document.createElement("a"); link.href = url; link.download = "atelier-flow-application.zip"; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); accountSettingsMessage.textContent = "应用包已生成并开始下载。";
    } catch (error) { accountSettingsMessage.textContent = error.message || "打包失败"; } finally { packageApplicationButton.disabled = false; }
}

function toLocalDateTime(value) {
    if (!value) return ""; const date = new Date(value); const offset = date.getTimezoneOffset() * 60000; return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

async function submitVideoBgm(event) {
    event.preventDefault();
    const file = videoBgmVideo?.files?.[0]; const bgm = videoBgmSelect?.value;
    if (!file) { videoBgmMessage.textContent = "请选择原视频"; return; }
    if (!bgm) { videoBgmMessage.textContent = "请选择 BGM"; return; }
    const ending = Boolean(videoBgmEnding?.checked);
    const endingBgm = videoBgmEndingSelect?.value || "";
    if (ending && !endingBgm) { videoBgmMessage.textContent = "请选择结尾 BGM"; return; }
    videoBgmSubmit.disabled = true; videoBgmMessage.textContent = "正在提交视频合成任务…";
    try {
        const body = new FormData(); body.append("video", file); body.append("bgm", bgm);
        if (videoBgmName?.value?.trim()) body.append("name", videoBgmName.value.trim());
        body.append("ending", String(ending));
        if (ending) body.append("endingBgm", endingBgm);
        const response = await fetch("/api/video-bgm-compositions", {method: "POST", body});
        const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "视频合成提交失败");
        videoBgmTask = payload; renderVideoBgmTask(payload); pollVideoBgm(payload.id);
    } catch (error) { videoBgmMessage.textContent = error.message || "视频合成失败"; }
    finally { videoBgmSubmit.disabled = false; }
}

function previewDirectOutfitFile(input, image) {
    const file = input?.files?.[0];
    if (!file || !image) return;
    if (image.dataset.url) URL.revokeObjectURL(image.dataset.url);
    const url = URL.createObjectURL(file); image.dataset.url = url; image.src = url; image.hidden = false;
}

async function submitDirectOutfit(event) {
    event.preventDefault();
    const person = directOutfitPerson?.files?.[0]; const clothing = directOutfitClothing?.files?.[0];
    if (!person || !clothing) { directOutfitMessage.textContent = "请同时上传人物原图和服装参考图"; return; }
    directOutfitSubmit.disabled = true; directOutfitMessage.textContent = "正在提交 RunningHub 换装任务…";
    try {
        const body = new FormData(); body.append("person", person); body.append("clothing", clothing); body.append("prompt", directOutfitPrompt?.value?.trim() || "");
        const response = await fetch("/api/direct-outfit-replacements", {method: "POST", body}); const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "换装提交失败");
        renderDirectOutfit(payload); pollDirectOutfit(payload.id);
    } catch (error) { directOutfitMessage.textContent = error.message || "换装失败"; }
    finally { directOutfitSubmit.disabled = false; }
}

async function pollDirectOutfit(id) {
    for (let i = 0; i < 900; i++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const response = await fetch(`/api/direct-outfit-replacements/${id}`, {cache: "no-store"}); const payload = await readJson(response);
            if (!response.ok) throw new Error(payload.message || "读取换装状态失败");
            renderDirectOutfit(payload); directOutfitMessage.textContent = payload.message || payload.status;
            if (["SUCCESS", "FAILED"].includes(payload.status)) return;
        } catch (error) { directOutfitMessage.textContent = error.message || "读取换装状态失败"; return; }
    }
}

function renderDirectOutfit(task) {
    if (!directOutfitResult) return;
    directOutfitResult.hidden = false; directOutfitResult.replaceChildren();
    const title = document.createElement("h3"); title.textContent = `${task.personFileName || "人物原图"} → ${task.clothingFileName || "服装图"}`;
    const status = document.createElement("p"); status.textContent = task.message || task.status; directOutfitResult.append(title, status);
    if (task.error) { const error = document.createElement("p"); error.className = "form-error"; error.textContent = task.error; directOutfitResult.append(error); }
    if (task.outputUrl) { const image = document.createElement("img"); image.src = task.outputUrl; image.alt = "换装结果"; image.className = "direct-outfit-output"; const link = document.createElement("a"); link.href = task.outputUrl; link.target = "_blank"; link.download = task.outputFileName || "outfit-result.png"; link.textContent = `下载换装结果（${task.outputFileName || "outfit-result.png"}）`; directOutfitResult.append(image, link); }
}

async function submitAuditRedraw(event) {
    event.preventDefault();
    const image = auditRedrawImage?.files?.[0];
    if (!image) { if (auditRedrawMessage) auditRedrawMessage.textContent = "请选择一张图片"; return; }
    if (auditRedrawSubmit) auditRedrawSubmit.disabled = true;
    if (auditRedrawMessage) auditRedrawMessage.textContent = "正在提交过审重绘任务…";
    try {
        const body = new FormData(); body.append("image", image);
        const response = await fetch("/api/audit-redraw", {method: "POST", body});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "过审重绘提交失败");
        renderAuditRedraw(payload); pollAuditRedraw(payload.id);
    } catch (error) {
        if (auditRedrawMessage) auditRedrawMessage.textContent = error.message || "过审重绘失败";
    } finally { if (auditRedrawSubmit) auditRedrawSubmit.disabled = false; }
}

async function loadMenuConfig() {
    if (!menuConfigList || !currentAccountSession?.administrator) return;
    menuConfigMessage.textContent = "正在读取菜单配置…";
    try {
        const response = await fetch("/api/accounts/menu-config", {cache: "no-store"});
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "读取菜单配置失败");
        menuConfigRows = Array.isArray(payload) ? payload : [];
        renderMenuConfig();
        menuConfigMessage.textContent = "可改名、排序或停用菜单；账号权限仍按内部菜单标识保存。";
    } catch (error) { menuConfigMessage.textContent = error.message || "读取菜单配置失败"; }
}

function renderMenuConfig() {
    menuConfigList.replaceChildren();
    if (!menuConfigRows.length) { const empty = document.createElement("p"); empty.className = "knowledge-empty"; empty.textContent = "暂无菜单配置"; menuConfigList.append(empty); return; }
    menuConfigRows.forEach((item, index) => {
        const row = document.createElement("div"); row.className = "menu-config-row"; row.dataset.menuId = item.id;
        const order = document.createElement("span"); order.className = "menu-config-order"; order.textContent = String(index + 1).padStart(2, "0");
        const label = document.createElement("input"); label.type = "text"; label.maxLength = 40; label.value = item.label || ""; label.setAttribute("aria-label", `${item.id} 菜单名称`);
        const id = document.createElement("code"); id.textContent = item.id;
        const enabledLabel = document.createElement("label"); enabledLabel.className = "account-check"; const enabled = document.createElement("input"); enabled.type = "checkbox"; enabled.checked = item.enabled !== false; enabled.disabled = item.id === "account-settings" || item.id === "menu-settings"; enabledLabel.append(enabled, document.createTextNode("启用"));
        const actions = document.createElement("span"); actions.className = "menu-config-actions";
        const up = document.createElement("button"); up.type = "button"; up.textContent = "↑"; up.title = "上移"; up.disabled = index === 0; up.addEventListener("click", () => moveMenuRow(index, -1));
        const down = document.createElement("button"); down.type = "button"; down.textContent = "↓"; down.title = "下移"; down.disabled = index === menuConfigRows.length - 1; down.addEventListener("click", () => moveMenuRow(index, 1)); actions.append(up, down);
        row.append(order, label, id, enabledLabel, actions); menuConfigList.append(row);
    });
}

function moveMenuRow(index, delta) {
    const target = index + delta; if (target < 0 || target >= menuConfigRows.length) return;
    const [item] = menuConfigRows.splice(index, 1); menuConfigRows.splice(target, 0, item); renderMenuConfig();
}

async function saveMenuConfig() {
    if (!currentAccountSession?.administrator || !saveMenuConfigButton) return;
    const rows = [...menuConfigList.querySelectorAll(".menu-config-row")].map((row, index) => ({
        id: row.dataset.menuId, label: row.querySelector('input[type="text"]').value.trim(), sortOrder: index,
        enabled: row.querySelector('input[type="checkbox"]').checked
    }));
    saveMenuConfigButton.disabled = true; menuConfigMessage.textContent = "正在保存菜单配置…";
    try {
        const response = await fetch("/api/accounts/menu-config", {method: "PUT", headers: {"Content-Type": "application/json"}, body: JSON.stringify(rows)});
        const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "保存菜单配置失败");
        menuConfigRows = Array.isArray(payload) ? payload : rows; renderMenuConfig();
        const sessionResponse = await fetch("/api/auth/session", {cache: "no-store"}); if (sessionResponse.ok) { currentAccountSession = await sessionResponse.json(); applyMenuOptions(currentAccountSession.menuOptions); const allowed = new Set(currentAccountSession.allowedMenus || []); navItems.forEach(item => { item.hidden = !allowed.has(item.dataset.view); }); }
        menuConfigMessage.textContent = "菜单配置已保存。";
    } catch (error) { menuConfigMessage.textContent = error.message || "保存菜单配置失败"; }
    finally { saveMenuConfigButton.disabled = false; }
}

async function submitGptImages(event) {
    event.preventDefault();
    const prompt = gptImagesPrompt?.value?.trim();
    if (!prompt) { if (gptImagesMessage) gptImagesMessage.textContent = "请输入图片提示词"; return; }
    gptImagesSubmit.disabled = true; gptImagesMessage.textContent = "正在提交 GPT 文生图任务…";
    try {
        const response = await fetch("/api/gpt-images", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({prompt})});
        const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "文生图提交失败");
        renderGptImages(payload); pollGptImages(payload.id);
    } catch (error) { gptImagesMessage.textContent = error.message || "文生图失败"; }
    finally { gptImagesSubmit.disabled = false; }
}
async function pollGptImages(id) {
    for (let i = 0; i < 180; i++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try { const response = await fetch(`/api/gpt-images/${id}`, {cache: "no-store"}); const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "读取图片状态失败"); renderGptImages(payload); gptImagesMessage.textContent = payload.message || payload.status; if (["SUCCESS", "FAILED"].includes(payload.status)) return; }
        catch (error) { gptImagesMessage.textContent = error.message || "读取图片状态失败"; return; }
    }
}
function renderGptImages(task) {
    if (!gptImagesResult) return; gptImagesResult.hidden = false; gptImagesResult.replaceChildren();
    const title = document.createElement("h3"); title.textContent = "GPT Image 2 文生图";
    const status = document.createElement("p"); status.textContent = task.message || task.status; gptImagesResult.append(title, status);
    if (task.error) { const error = document.createElement("p"); error.className = "form-error"; error.textContent = task.error; gptImagesResult.append(error); }
    if (task.outputUrl) { const image = document.createElement("img"); image.src = task.outputUrl; image.alt = "GPT 文生图结果"; image.className = "direct-outfit-output"; const link = document.createElement("a"); link.href = task.outputUrl; link.target = "_blank"; link.download = "gpt-image.png"; link.textContent = "查看 / 下载图片"; gptImagesResult.append(image, link); }
}

async function pollAuditRedraw(id) {
    for (let i = 0; i < 900; i++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const response = await fetch(`/api/audit-redraw/${id}`, {cache: "no-store"});
            const payload = await readJson(response);
            if (!response.ok) throw new Error(payload.message || "读取过审重绘状态失败");
            renderAuditRedraw(payload);
            if (auditRedrawMessage) auditRedrawMessage.textContent = payload.message || payload.status;
            if (["SUCCESS", "FAILED"].includes(payload.status)) return;
        } catch (error) {
            if (auditRedrawMessage) auditRedrawMessage.textContent = error.message || "读取过审重绘状态失败";
            return;
        }
    }
}

function renderAuditRedraw(task) {
    if (!auditRedrawResult) return;
    auditRedrawResult.hidden = false; auditRedrawResult.replaceChildren();
    const title = document.createElement("h3"); title.textContent = task.inputFileName || "过审重绘任务";
    const status = document.createElement("p"); status.textContent = task.message || task.status;
    auditRedrawResult.append(title, status);
    if (task.error) { const error = document.createElement("p"); error.className = "form-error"; error.textContent = task.error; auditRedrawResult.append(error); }
    if (task.outputUrl) {
        const image = document.createElement("img"); image.src = task.outputUrl; image.alt = "过审重绘结果"; image.className = "direct-outfit-output";
        const link = document.createElement("a"); link.href = task.outputUrl; link.target = "_blank"; link.download = task.outputFileName || "audit-redraw.png"; link.textContent = `下载过审图（${task.outputFileName || "audit-redraw.png"}）`;
        auditRedrawResult.append(image, link);
    }
}

async function submitVideoScript(event) {
    event.preventDefault();
    await submitVideoScriptTask(true);
}

async function submitVideoScriptTask(parse) {
    const source = document.querySelector("input[name='video-script-source']:checked")?.value || "url";
    const address = videoScriptAddress?.value?.trim();
    const file = videoScriptFile?.files?.[0];
    if (source === "upload" && !file) { if (videoScriptMessage) videoScriptMessage.textContent = "请选择视频文件"; return; }
    if (source === "url" && !address) { if (videoScriptMessage) videoScriptMessage.textContent = "请输入视频地址"; return; }
    const button = parse ? videoScriptSubmit : videoScriptDownload;
    if (button) button.disabled = true;
    if (videoScriptMessage) videoScriptMessage.textContent = source === "upload"
        ? (parse ? "正在上传并解析视频…" : "正在上传视频…")
        : (parse ? "正在拉取并解析视频…" : "正在拉取视频…");
    try {
        const request = source === "upload"
            ? (() => { const body = new FormData(); body.append("video", file); body.append("parse", String(parse)); return {method: "POST", body}; })()
            : {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({address, parse})};
        const response = await fetch("/api/qwen-video-scripts", request);
        const payload = await readJson(response);
        if (!response.ok) throw new Error(payload.message || "视频任务提交失败");
        renderVideoScript(payload); pollVideoScript(payload.id); loadVideoScripts();
    } catch (error) {
        if (videoScriptMessage) videoScriptMessage.textContent = error.message || "视频任务失败";
    } finally { if (button) button.disabled = false; }
}

function syncVideoScriptSource() {
    const upload = document.querySelector("input[name='video-script-source']:checked")?.value === "upload";
    if (videoScriptAddressWrap) videoScriptAddressWrap.hidden = upload;
    if (videoScriptFileWrap) videoScriptFileWrap.hidden = !upload;
    if (videoScriptAddress) videoScriptAddress.required = !upload;
    if (videoScriptFile) videoScriptFile.required = upload;
    if (!upload && videoScriptFile) videoScriptFile.value = "";
    if (upload && videoScriptAddress) videoScriptAddress.value = "";
}

async function pollVideoScript(id) {
    for (let i = 0; i < 900; i++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const response = await fetch(`/api/qwen-video-scripts/${id}`, {cache: "no-store"});
            const payload = await readJson(response);
            if (!response.ok) throw new Error(payload.message || "读取视频脚本状态失败");
            renderVideoScript(payload);
            if (videoScriptMessage) videoScriptMessage.textContent = payload.message || payload.status;
            loadVideoScripts();
            if (["SUCCESS", "FAILED"].includes(payload.status)) return;
        } catch (error) {
            if (videoScriptMessage) videoScriptMessage.textContent = error.message || "读取视频脚本状态失败";
            return;
        }
    }
}

async function loadVideoScripts() {
    if (!videoScriptList) return;
    try {
        const response = await fetch("/api/qwen-video-scripts", {cache: "no-store"});
        const items = await readJson(response);
        if (!response.ok) throw new Error(items.message || "读取视频列表失败");
        renderVideoScriptList(Array.isArray(items) ? items : []);
        if (videoScriptListMessage) videoScriptListMessage.textContent = items.length ? `共 ${items.length} 条视频记录` : "暂无视频记录";
    } catch (error) {
        if (videoScriptListMessage) videoScriptListMessage.textContent = error.message || "读取视频列表失败";
    }
}

function renderVideoScriptList(items) {
    videoScriptList.replaceChildren();
    if (!items.length) { const empty = document.createElement("p"); empty.className = "knowledge-empty"; empty.textContent = "暂无视频记录"; videoScriptList.append(empty); return; }
    const table = document.createElement("table"); table.className = "video-script-table";
    const head = document.createElement("thead"); const headRow = document.createElement("tr");
    ["视频", "来源", "状态", "信息", "操作"].forEach((label) => { const th = document.createElement("th"); th.textContent = label; headRow.append(th); });
    head.append(headRow); table.append(head);
    const body = document.createElement("tbody"); table.append(body);
    items.forEach((item) => {
        const row = document.createElement("tr");
        const titleCell = document.createElement("td"); titleCell.className = "video-script-ellipsis"; titleCell.title = item.sourceFileName || "";
        const title = document.createElement("strong"); title.textContent = item.sourceFileName || "未命名视频"; titleCell.append(title); row.append(titleCell);
        const address = document.createElement("td"); address.className = "video-script-ellipsis"; address.textContent = item.address || ""; address.title = item.address || ""; row.append(address);
        const statusCell = document.createElement("td"); const status = document.createElement("span"); status.className = `standalone-video-status ${String(item.status || "").toLowerCase()}`; status.textContent = videoScriptStatusLabel(item.status); statusCell.append(status); row.append(statusCell);
        const info = document.createElement("td"); info.className = "video-script-ellipsis"; info.textContent = item.error || item.message || ""; info.title = info.textContent; row.append(info);
        const actionsCell = document.createElement("td"); actionsCell.className = "video-script-table-actions";
        if (item.sourceFileName && !["QUEUED", "DOWNLOADING", "ANALYZING"].includes(item.status)) {
            const generate = document.createElement("button"); generate.type = "button"; generate.textContent = item.status === "SUCCESS" ? "重新解析" : "解析视频"; generate.addEventListener("click", () => generateVideoScript(item.id, generate)); actionsCell.append(generate);
        }
        if (item.status === "SUCCESS" && item.script) {
            const copy = document.createElement("button"); copy.type = "button"; copy.textContent = "复制文案"; copy.addEventListener("click", () => copyText(item.script, copy)); actionsCell.append(copy);
            const show = document.createElement("button"); show.type = "button"; show.textContent = "查看文案"; show.addEventListener("click", () => { renderVideoScript(item); videoScriptResult?.scrollIntoView({behavior: "smooth", block: "start"}); }); actionsCell.append(show);
        }
        if (!actionsCell.childElementCount) { const muted = document.createElement("span"); muted.textContent = "-"; muted.className = "muted"; actionsCell.append(muted); }
        row.append(actionsCell); body.append(row);
    });
    videoScriptList.append(table);
}

function videoScriptStatusLabel(status) { return {QUEUED: "排队中", DOWNLOADING: "准备中", DOWNLOADED: "已准备", ANALYZING: "解析中", SUCCESS: "已完成", FAILED: "失败"}[status] || status || "未知"; }

async function generateVideoScript(id, button) {
    button.disabled = true;
    try {
        const response = await fetch(`/api/qwen-video-scripts/${id}/generate`, {method: "POST"});
        const payload = await readJson(response); if (!response.ok) throw new Error(payload.message || "生成文案失败");
        renderVideoScript(payload); pollVideoScript(id); loadVideoScripts();
    } catch (error) { if (videoScriptListMessage) videoScriptListMessage.textContent = error.message || "生成文案失败"; button.disabled = false; }
}

function renderVideoScript(task) {
    if (!videoScriptResult) return;
    videoScriptResult.hidden = false; videoScriptResult.replaceChildren();
    const heading = document.createElement("div"); heading.className = "production-script-heading";
    const title = document.createElement("h3"); title.textContent = task.sourceFileName || "视频脚本任务";
    const meta = document.createElement("p"); meta.textContent = task.message || task.status;
    heading.append(title, meta); videoScriptResult.append(heading);
    if (task.error) { const error = document.createElement("p"); error.className = "form-error"; error.textContent = task.error; videoScriptResult.append(error); }
    if (task.script) {
        const copy = document.createElement("button"); copy.type = "button"; copy.textContent = "复制脚本";
        copy.addEventListener("click", () => copyText(task.script, copy));
        const content = document.createElement("pre"); content.className = "production-script-text"; content.textContent = task.script;
        videoScriptResult.append(copy, content);
    }
}

async function pollVideoBgm(id) {
    for (let i = 0; i < 900; i++) {
        await new Promise(resolve => setTimeout(resolve, 2000));
        try {
            const response = await fetch(`/api/video-bgm-compositions/${id}`, {cache: "no-store"}); const payload = await readJson(response);
            if (!response.ok) throw new Error(payload.message || "读取合成状态失败");
            videoBgmTask = payload; renderVideoBgmTask(payload); videoBgmMessage.textContent = payload.message || payload.status;
            if (["SUCCESS", "FAILED"].includes(payload.status)) return;
        } catch (error) { videoBgmMessage.textContent = error.message || "读取合成状态失败"; return; }
    }
}

function renderVideoBgmTask(task) {
    if (!videoBgmResult) return;
    videoBgmResult.hidden = false; videoBgmResult.replaceChildren();
    const title = document.createElement("h3"); title.textContent = `${task.sourceFileName || "原视频"} + ${task.bgmName || "BGM"}${task.endingBgmName ? ` + 结尾：${task.endingBgmName}` : ""}`;
    const status = document.createElement("p"); status.textContent = task.message || task.status; videoBgmResult.append(title, status);
    if (task.error) { const error = document.createElement("p"); error.className = "form-error"; error.textContent = task.error; videoBgmResult.append(error); }
    if (task.outputUrl) { const video = document.createElement("video"); video.controls = true; video.preload = "metadata"; video.src = task.outputUrl; video.className = "video-bgm-output"; const link = document.createElement("a"); link.href = task.outputUrl; link.target = "_blank"; link.download = task.outputFileName || "bgm-video.mp4"; link.textContent = `打开成品（${task.outputFileName || "bgm-video.mp4"}）`; videoBgmResult.append(video, link); }
}
